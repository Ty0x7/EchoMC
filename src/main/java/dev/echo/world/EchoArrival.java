package dev.echo.world;

import dev.echo.*;
import dev.echo.entity.EchoEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.*;

/** Owner-scoped arrival/recall; never synthesizes a second entity for a missing UUID. */
public final class EchoArrival {
    private record Pending(int remaining, boolean explicit) { }
    private static final Map<UUID,Pending> pending=new HashMap<>();
    private static final Map<UUID,java.lang.ref.WeakReference<EchoEntity>> live=new HashMap<>();
    public static void bind(EchoEntity e){live.put(e.getUuid(),new java.lang.ref.WeakReference<>(e));}
    public static void cancel(ServerPlayerEntity p){pending.remove(p.getUuid());}
    public static void queue(ServerPlayerEntity p,boolean explicit) {
        if(!explicit && !EchoMod.CONFIG.autoSpawnOnJoin) return;
        var ledger=CompanionLedger.get(p.getServer());UUID id=ledger.companions.get(p.getUuid());
        if(id!=null && ledger.carried.containsKey(id)) { p.sendMessage(Text.literal("[Echo] I'm in my carried cube. Place me down when you're ready."),false);return; }
        if(pending.containsKey(p.getUuid())) return;
        pending.put(p.getUuid(),new Pending(200,explicit));
        if(id!=null && loaded(p)==null && !ledger.parked.containsKey(id)) wake(p);
    }
    public static boolean wake(ServerPlayerEntity p) {
        var ledger=CompanionLedger.get(p.getServer());
        try {
            NbtCompound anchor=ledger.anchors.get(p.getUuid());
            if(anchor!=null) {
                ServerWorld origin=p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(anchor.getString("dimension"))));
                BlockPos pos=BlockPos.fromLong(anchor.getLong("position"));
                // One known source chunk, asynchronously. No search over the rest of the world.
                if(origin!=null) {origin.getChunkManager().getChunkFutureSyncOnMainThread(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,true);return true;}
            }
        } catch(RuntimeException ignored) { }
        return false;
    }
    public static EchoEntity loaded(ServerPlayerEntity player) {
        UUID id=CompanionLedger.get(player.getServer()).companions.get(player.getUuid());
        if(id==null) return null;
        for(ServerWorld w:player.getServer().getWorlds()) if(w.getEntity(id) instanceof EchoEntity e && !e.isRemoved() && player.getUuid().equals(e.ownerId)) return e;
        var reference=live.get(id);var e=reference==null?null:reference.get();
        if(e!=null && !e.isRemoved() && e.getServer()==player.getServer() && player.getUuid().equals(e.ownerId))return e;
        live.remove(id);
        return null;
    }
    public static void anchor(EchoEntity e) {
        if(e.ownerId==null) return;
        var ledger=CompanionLedger.get(e.getServer());NbtCompound anchor=new NbtCompound();
        anchor.putString("dimension",e.getWorld().getRegistryKey().getValue().toString());anchor.putLong("position",e.getBlockPos().asLong());ledger.anchors.put(e.ownerId,anchor);
        ledger.locations.put(e.ownerId,e.getWorld().getRegistryKey().getValue()+" "+e.getBlockPos().toShortString());ledger.markDirty();
    }
    public static void park(ServerPlayerEntity player) {
        pending.remove(player.getUuid());EchoEntity e=loaded(player);if(e==null || !e.isAlive())return;
        e.setPaused(true);anchor(e);NbtCompound data=new NbtCompound();e.writeNbt(data);
        var ledger=CompanionLedger.get(player.getServer());ledger.parked.put(e.getUuid(),data);ledger.markDirty();e.discard();
    }
    public static void stop(MinecraftServer server) { for(var p:List.copyOf(server.getPlayerManager().getPlayerList()))park(p);pending.clear();live.clear(); }
    public static void tick(MinecraftServer server) {
        for(UUID owner:List.copyOf(pending.keySet())) {
            var p=server.getPlayerManager().getPlayer(owner);if(p==null){pending.remove(owner);continue;}
            Pending state=pending.get(owner);
            if(state.remaining()%10==0 && arrive(p)) {pending.remove(owner);continue;}
            if(state.remaining()<=0) {pending.remove(owner);p.sendMessage(Text.literal("[Echo] Couldn't arrive safely. Clear nearby solid ground and use /echo here. Existing Echo: "+CompanionLedger.get(server).locations.getOrDefault(owner,"not created yet")),false);}
            else pending.put(owner,new Pending(state.remaining()-1,state.explicit()));
        }
    }
    public static boolean arrive(ServerPlayerEntity p) {
        if(!p.isAlive() || p.isSpectator())return false;
        var ledger=CompanionLedger.get(p.getServer());UUID id=ledger.companions.get(p.getUuid());
        if(id!=null && ledger.carried.containsKey(id))return true;
        EchoEntity existing=loaded(p);
        NbtCompound parked=id==null?null:ledger.parked.get(id);
        if(id!=null && existing==null && parked==null)return false; // Unknown/unloaded never means safe to duplicate.
        EchoEntity probe=EchoMod.COMPANION.create(p.getServerWorld());if(probe==null)return false;
        BlockPos safe=safePosition(p,probe);if(safe==null)return false;
        EchoEntity echo;
        if(existing!=null) {
            existing.setPaused(true);
            var moved=existing.teleportTo(new TeleportTarget(p.getServerWorld(),Vec3d.ofBottomCenter(safe),Vec3d.ZERO,p.getYaw(),0,TeleportTarget.NO_OP));
            if(!(moved instanceof EchoEntity result))return false;echo=result;
        } else {
            echo=probe;
            if(parked!=null) {echo.readNbt(parked.copy());echo.setUuid(id);}
            else echo.ownerId=p.getUuid();
            echo.refreshPositionAndAngles(safe.getX()+0.5,safe.getY(),safe.getZ()+0.5,p.getYaw(),0);
            echo.setVelocity(Vec3d.ZERO);echo.fallDistance=0;
            if(!p.getServerWorld().spawnEntity(echo))return false;
            ledger.companions.put(p.getUuid(),echo.getUuid());ledger.parked.remove(echo.getUuid());ledger.markDirty();
        }
        anchor(echo);
        echo.say(id==null?"Hey, I'm Echo. I'm here beside you. Use /echo chat or hold V with voice enabled. Sneak-right-click to carry me.":"Here I am. Same cube, same memories. Use /echo resume or /echo follow when ready.");return true;
    }
    private static BlockPos safePosition(ServerPlayerEntity p,EchoEntity probe) {
        ServerWorld w=p.getServerWorld();
        for(int radius=1;radius<=4;radius++)for(int dy:new int[]{0,1,-1,2,-2})for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos at=p.getBlockPos().add(dx,dy,dz);
            if(!w.isInBuildLimit(at) || !w.isChunkLoaded(at) || !w.getWorldBorder().contains(at)
                || !w.getFluidState(at).isEmpty() || !w.getBlockState(at.down()).isSideSolidFullSquare(w,at.down(),Direction.UP))continue;
            var floor=w.getBlockState(at.down()).getBlock();
            if(floor==net.minecraft.block.Blocks.MAGMA_BLOCK || floor==net.minecraft.block.Blocks.CAMPFIRE || floor==net.minecraft.block.Blocks.SOUL_CAMPFIRE)continue;
            probe.refreshPositionAndAngles(at.getX()+0.5,at.getY(),at.getZ()+0.5,0,0);
            if(w.isSpaceEmpty(probe) && w.getOtherEntities(probe,probe.getBoundingBox()).isEmpty())return at;
        }
        return null;
    }
}
