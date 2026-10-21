package dev.echo.item;

import dev.echo.*;
import dev.echo.entity.EchoEntity;
import dev.echo.world.CompanionLedger;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Item contains an identity token only. Canonical companion state stays in the world save. */
public final class EchoCubeItem extends Item {
    public EchoCubeItem(Settings settings) { super(settings); }
    public static ItemStack token(UUID entity) {
        ItemStack stack=new ItemStack(EchoMod.CUBE); NbtCompound data=new NbtCompound();data.putUuid("Echo",entity);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data)); return stack;
    }
    public static void pickup(EchoEntity echo,ServerPlayerEntity player) {
        var ledger=CompanionLedger.get(player.getServer());
        if(!player.getUuid().equals(echo.ownerId) || !echo.getUuid().equals(ledger.companions.get(player.getUuid()))) throw new IllegalArgumentException("Only Echo's owner can pick him up.");
        if(!echo.isAlive() || echo.getWorld()!=player.getWorld() || echo.hasPassengers() || echo.hasVehicle() || echo.squaredDistanceTo(player)>16) throw new IllegalArgumentException("Move close to an unmounted Echo.");
        if(player.getInventory().getEmptySlot()<0) throw new IllegalArgumentException("Make room in your inventory first.");
        echo.setPaused(true);
        NbtCompound saved=new NbtCompound();echo.writeNbt(saved);
        ItemStack token=token(echo.getUuid());
        if(!player.getInventory().insertStack(token)) throw new IllegalArgumentException("Couldn't insert Echo's cube.");
        ledger.carried.put(echo.getUuid(),saved);ledger.locations.put(player.getUuid(),"Carried cube (tasks paused)");ledger.markDirty();
        echo.discard();player.sendMessage(Text.literal("[Echo] Pocket mode. Hold my cube and use it on solid ground to put me down."),false);
    }
    public static void recover(ServerPlayerEntity player) {
        var ledger=CompanionLedger.get(player.getServer());UUID id=ledger.companions.get(player.getUuid());
        if(id==null || !ledger.carried.containsKey(id)) throw new IllegalArgumentException("Echo is not stored as a carried cube.");
        if(player.getInventory().getEmptySlot()<0) throw new IllegalArgumentException("Make room in your inventory first.");
        player.getInventory().insertStack(token(id));
        player.sendMessage(Text.literal("[Echo] Recovered my carried cube. Put me down on solid ground."),false);
    }
    public static boolean deploy(ServerPlayerEntity player,ItemStack stack,BlockPos target) {
        NbtCompound data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        if(!data.containsUuid("Echo")) throw new IllegalArgumentException("This cube has no stored Echo.");
        UUID id=data.getUuid("Echo");var ledger=CompanionLedger.get(player.getServer());
        if(!id.equals(ledger.companions.get(player.getUuid()))) throw new IllegalArgumentException("This Echo belongs to another player.");
        NbtCompound saved=ledger.carried.get(id);
        if(saved==null) throw new IllegalArgumentException("Echo is already in the world; this is an inactive token.");
        for(var world:player.getServer().getWorlds()) if(world.getEntity(id)!=null) throw new IllegalArgumentException("Echo is already loaded.");
        var world=player.getServerWorld();
        if(!target.isWithinDistance(player.getPos(),6) || !world.isChunkLoaded(target) || !world.getWorldBorder().contains(target)
            || !world.canPlayerModifyAt(player,target) || !player.getAbilities().allowModifyWorld
            || !world.getBlockState(target.down()).isSideSolidFullSquare(world,target.down(),net.minecraft.util.math.Direction.UP)
            || !world.getFluidState(target).isEmpty()) throw new IllegalArgumentException("Place Echo on clear, permitted solid ground nearby.");
        EchoEntity echo=EchoMod.COMPANION.create(world);if(echo==null) return false;
        echo.readNbt(saved.copy());echo.setUuid(id);echo.refreshPositionAndAngles(target.getX()+0.5,target.getY(),target.getZ()+0.5,player.getYaw()+180,0);
        echo.setVelocity(net.minecraft.util.math.Vec3d.ZERO);echo.fallDistance=0;
        if(!world.isSpaceEmpty(echo) || !world.getOtherEntities(echo,echo.getBoundingBox()).isEmpty()) throw new IllegalArgumentException("Echo's placement space is occupied.");
        if(!world.spawnEntity(echo)) return false;
        ledger.carried.remove(id);ledger.locations.put(player.getUuid(),world.getRegistryKey().getValue()+" "+target.toShortString());ledger.markDirty();
        dev.echo.world.EchoArrival.anchor(echo);
        stack.decrement(1);echo.say("Back on the ground. My task is paused; use /echo resume or /echo follow.");return true;
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        if(context.getWorld().isClient) return ActionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.FAIL;
        BlockPos target=context.getWorld().getBlockState(context.getBlockPos()).isReplaceable()?context.getBlockPos():context.getBlockPos().offset(context.getSide());
        try { return deploy(player,context.getStack(),target)?ActionResult.CONSUME:ActionResult.FAIL; }
        catch(IllegalArgumentException ex) { player.sendMessage(Text.literal("[Echo] "+ex.getMessage()),true);return ActionResult.FAIL; }
    }
}
