package dev.echo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import dev.echo.ai.*;
import dev.echo.entity.EchoEntity;
import dev.echo.world.CompanionLedger;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class EchoCommands {
    public static EchoEntity find(ServerPlayerEntity p) {
        return dev.echo.world.EchoArrival.loaded(p);
    }
    private static EchoEntity require(ServerCommandSource source) throws Exception {
        var p=source.getPlayerOrThrow(); EchoEntity e=find(p);
        if(e==null) throw new IllegalArgumentException("Echo isn't loaded in your dimension. /echo locate shows the last location; /echo summon creates one if none exists.");
        e.wakeForInteraction();return e;
    }
    @FunctionalInterface interface Operation { void run(ServerCommandSource source) throws Exception; }
    private static int run(ServerCommandSource source, Operation op) {
        try { op.run(source); return 1; } catch(Exception e) { source.sendError(Text.literal(e.getMessage()==null?"Echo command failed":e.getMessage())); return 0; }
    }
    private static void submit(ServerCommandSource source, Plan.Step step) throws Exception { require(source).submit(new Plan("On it.",List.of(step))); }
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        var root=literal("echo").requires(s->s.getEntity() instanceof ServerPlayerEntity)
            .executes(c->run(c.getSource(),s->require(s).say("Commands: summon, locate, say, follow, stay, stop, pause, resume, status, allow, give, equip, deliver, mine, craft, place, platform, goto, explore, remember, recall, plan.")));
        root.then(literal("summon").executes(c->run(c.getSource(),s->{
            var p=s.getPlayerOrThrow(); var ledger=CompanionLedger.get(p.getServer());
            if(ledger.companions.containsKey(p.getUuid())) throw new IllegalArgumentException("You already have Echo. Last seen: "+ledger.locations.getOrDefault(p.getUuid(),"unknown"));
            BlockPos spawn=p.getBlockPos().offset(p.getHorizontalFacing(),2);
            EchoEntity e=EchoMod.COMPANION.create(p.getServerWorld()); if(e==null) throw new IllegalArgumentException("Entity creation failed");
            e.refreshPositionAndAngles(spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,p.getYaw(),0);
            if(!p.getServerWorld().isSpaceEmpty(e) || !p.getServerWorld().getBlockState(spawn.down()).isSolidBlock(p.getWorld(),spawn.down())) throw new IllegalArgumentException("Stand near a clear solid floor with two blocks of space in front of you");
            e.ownerId=p.getUuid(); if(!p.getServerWorld().spawnEntity(e)) throw new IllegalArgumentException("Spawn rejected");
            ledger.companions.put(p.getUuid(),e.getUuid()); ledger.locations.put(p.getUuid(),p.getWorld().getRegistryKey().getValue()+" "+spawn.toShortString()); ledger.markDirty();
            dev.echo.world.EchoArrival.anchor(e);
            e.say("Hey. I'm Echo. I'll follow your lead. Give me tools and materials with /echo give; mining and building need your permission.");
        })));
        root.then(literal("locate").executes(c->run(c.getSource(),s->{var p=s.getPlayerOrThrow(); s.sendFeedback(()->Text.literal(CompanionLedger.get(p.getServer()).locations.getOrDefault(p.getUuid(),"No companion yet.")),false);} )));
        for(String verb:List.of("say","chat"))root.then(literal(verb).then(argument("message",StringArgumentType.greedyString()).executes(c->run(c.getSource(),s->EchoCommunication.message(s.getPlayerOrThrow(),StringArgumentType.getString(c,"message"))))));
        root.then(literal("here").executes(c->run(c.getSource(),s->dev.echo.world.EchoArrival.queue(s.getPlayerOrThrow(),true))));
        root.then(literal("senses").executes(c->run(c.getSource(),s->{var e=require(s);e.say("Recent sounds near me: "+e.hearing.summary(e.getWorld().getTime()));})));
        for(var pair:Map.of("follow",Plan.Kind.FOLLOW,"stay",Plan.Kind.STAY,"explore",Plan.Kind.EXPLORE,"deliver",Plan.Kind.DELIVER).entrySet())
            root.then(literal(pair.getKey()).executes(c->run(c.getSource(),s->require(s).submit(Plan.single(pair.getValue())))));
        root.then(literal("stop").executes(c->run(c.getSource(),s->require(s).cancel())));
        root.then(literal("pause").executes(c->run(c.getSource(),s->require(s).setPaused(true))));
        root.then(literal("resume").executes(c->run(c.getSource(),s->require(s).setPaused(false))));
        root.then(literal("status").executes(c->run(c.getSource(),s->{var e=require(s); e.say(e.status()+" Inventory: "+e.inventoryText());})));
        for(String permission:List.of("mine","build","combat","explore")) root.then(literal("allow").then(literal(permission)
            .then(argument("enabled",BoolArgumentType.bool()).executes(c->run(c.getSource(),s->{
                var e=require(s); boolean enabled=BoolArgumentType.getBool(c,"enabled");
                switch(permission) {case "mine"->e.mineAllowed=enabled; case "build"->e.buildAllowed=enabled; case "combat"->e.combatAllowed=enabled; case "explore"->e.exploreAllowed=enabled;}
                if(!enabled) e.cancel(); e.say(permission+" permission: "+enabled);
            })))));
        root.then(literal("give").executes(c->run(c.getSource(),s->{
            var e=require(s); var p=s.getPlayerOrThrow(); if(e.getWorld()!=p.getWorld() || e.squaredDistanceTo(p)>16) throw new IllegalArgumentException("Move within four blocks of Echo");
            ItemStack held=p.getMainHandStack(); if(held.isEmpty()) throw new IllegalArgumentException("Hold an item stack");
            ItemStack remainder=e.bag.addStack(held.copy()); held.setCount(remainder.getCount()); e.say("Thanks. Inventory: "+e.inventoryText());
        })));
        root.then(literal("equip").then(argument("item",StringArgumentType.word()).executes(c->run(c.getSource(),s->{
            var e=require(s); var item=net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(StringArgumentType.getString(c,"item")));
            var stack=e.findItem(item); if(stack.isEmpty()) throw new IllegalArgumentException("I don't have that item"); e.equipStack(EquipmentSlot.MAINHAND,stack); e.say("Equipped "+item.getName().getString());
        }))));
        for(var pair:Map.of("mine",Plan.Kind.MINE,"craft",Plan.Kind.CRAFT).entrySet()) root.then(literal(pair.getKey())
            .then(argument("item",StringArgumentType.word()).then(argument("count",IntegerArgumentType.integer(1,64))
                .executes(c->run(c.getSource(),s->submit(s,new Plan.Step(pair.getValue(),StringArgumentType.getString(c,"item"),IntegerArgumentType.getInteger(c,"count"),0,0,0)))))));
        root.then(literal("goto").then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->run(c.getSource(),s->{
            BlockPos p=BlockPosArgumentType.getBlockPos(c,"pos"); submit(s,new Plan.Step(Plan.Kind.GOTO,"minecraft:air",1,p.getX(),p.getY(),p.getZ()));
        }))));
        root.then(literal("place").then(argument("item",StringArgumentType.word()).then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->run(c.getSource(),s->{
            BlockPos p=BlockPosArgumentType.getBlockPos(c,"pos"); submit(s,new Plan.Step(Plan.Kind.PLACE,StringArgumentType.getString(c,"item"),1,p.getX(),p.getY(),p.getZ()));
        })))));
        root.then(literal("platform").then(argument("item",StringArgumentType.word()).then(argument("pos",BlockPosArgumentType.blockPos())
            .then(argument("width",IntegerArgumentType.integer(1,8)).then(argument("depth",IntegerArgumentType.integer(1,8)).executes(c->run(c.getSource(),s->{
                int width=IntegerArgumentType.getInteger(c,"width"), depth=IntegerArgumentType.getInteger(c,"depth"); if(width*depth>32) throw new IllegalArgumentException("Maximum 32 blocks per plan");
                BlockPos p=BlockPosArgumentType.getBlockPos(c,"pos"); List<Plan.Step> steps=new ArrayList<>();
                for(int z=0;z<depth;z++) for(int x=0;x<width;x++) steps.add(new Plan.Step(Plan.Kind.PLACE,StringArgumentType.getString(c,"item"),1,p.getX()+x,p.getY(),p.getZ()+z));
                require(s).submit(new Plan("Building a "+width+" by "+depth+" platform.",steps));
            })))))));
        root.then(literal("remember").then(argument("name",StringArgumentType.greedyString()).executes(c->run(c.getSource(),s->require(s).rememberPlace(StringArgumentType.getString(c,"name"))))));
        root.then(literal("recall").then(argument("name",StringArgumentType.greedyString()).executes(c->run(c.getSource(),s->{
            var e=require(s); BlockPos p=e.place(StringArgumentType.getString(c,"name")); if(p==null) throw new IllegalArgumentException("Unknown remembered location");
            submit(s,new Plan.Step(Plan.Kind.GOTO,"minecraft:air",1,p.getX(),p.getY(),p.getZ()));
        }))));
        root.then(literal("plan").then(argument("json",StringArgumentType.greedyString()).executes(c->run(c.getSource(),s->require(s).submit(Plan.parse(StringArgumentType.getString(c,"json")))))));
        // Explicit operator recovery only; companion removal by other mods can otherwise leave a stale ownership index.
        root.then(literal("reset-index").requires(s->s.hasPermissionLevel(2)).executes(c->run(c.getSource(),s->{
            var p=s.getPlayerOrThrow(); var ledger=CompanionLedger.get(p.getServer()); UUID id=ledger.companions.get(p.getUuid());
            if(id!=null && ledger.carried.containsKey(id)) throw new IllegalArgumentException("Echo is carried. Use /echo recover-cube if his item is lost.");
            if(id!=null && ledger.parked.containsKey(id)) throw new IllegalArgumentException("Echo is parked. Use /echo here.");
            if(id!=null) for(var w:p.getServer().getWorlds()) if(w.getEntity(id)!=null) throw new IllegalArgumentException("Existing Echo is loaded; don't reset its index");
            ledger.companions.remove(p.getUuid()); ledger.locations.remove(p.getUuid()); ledger.markDirty(); s.sendFeedback(()->Text.literal("Index cleared. Only use after confirming the old entity is permanently gone."),false);
        })));
        root.then(literal("carry").executes(c->run(c.getSource(),s->dev.echo.item.EchoCubeItem.pickup(require(s),s.getPlayerOrThrow()))));
        root.then(literal("recover-cube").executes(c->run(c.getSource(),s->dev.echo.item.EchoCubeItem.recover(s.getPlayerOrThrow()))));
        d.register(root);
    }
}
