package dev.echo.world;

import dev.echo.*;
import dev.echo.ai.SoundMemory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class EchoHearing {
    public static void sound(ServerWorld world,String sound,Vec3d position,float volume) {
        if(EchoMod.CONFIG==null || !Float.isFinite(volume) || volume<=0)return;
        double radius=Math.min(EchoMod.CONFIG.hearingRadius,16*Math.max(1,volume));
        for(var player:world.getServer().getPlayerManager().getPlayerList()) {
            var echo=EchoArrival.loaded(player);
            if(echo==null || echo.getWorld()!=world || echo.squaredDistanceTo(position)>radius*radius)continue;
            echo.hear(new SoundMemory.Cue(sound,position.x,position.y,position.z,world.getTime()));
        }
    }
}
