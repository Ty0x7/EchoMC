package dev.echo.mixin;

import dev.echo.world.EchoHearing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldSoundMixin {
    @Inject(method="playSound",at=@At("HEAD"))
    private void echo$positionSound(PlayerEntity source,double x,double y,double z,RegistryEntry<SoundEvent> sound,SoundCategory category,float volume,float pitch,long seed,CallbackInfo ci) {
        EchoHearing.sound((ServerWorld)(Object)this,sound.value().getId().toString(),new Vec3d(x,y,z),volume);
    }
    @Inject(method="playSoundFromEntity",at=@At("HEAD"))
    private void echo$entitySound(PlayerEntity source,Entity entity,RegistryEntry<SoundEvent> sound,SoundCategory category,float volume,float pitch,long seed,CallbackInfo ci) {
        EchoHearing.sound((ServerWorld)(Object)this,sound.value().getId().toString(),entity.getPos(),volume);
    }
    @Inject(method="syncWorldEvent",at=@At("HEAD"))
    private void echo$worldEvent(PlayerEntity source,int event,BlockPos pos,int data,CallbackInfo ci) {
        if(event==2001) EchoHearing.sound((ServerWorld)(Object)this,"minecraft:block.break",Vec3d.ofCenter(pos),1);
    }
}
