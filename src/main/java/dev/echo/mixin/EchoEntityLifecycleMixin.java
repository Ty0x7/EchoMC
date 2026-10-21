package dev.echo.mixin;

import dev.echo.entity.EchoEntity;
import dev.echo.world.EchoArrival;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Track successfully admitted entities even before inactive chunks expose their UUID lookup. */
@Mixin(ServerWorld.class)
public abstract class EchoEntityLifecycleMixin {
    @Inject(method="addEntity",at=@At("RETURN"))
    private void echo$admitted(Entity entity,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValueZ() && entity instanceof EchoEntity e)EchoArrival.bind(e);}
    @Inject(method="onDimensionChanged",at=@At("TAIL"))
    private void echo$transferred(Entity entity,CallbackInfo ci){if(entity instanceof EchoEntity e)EchoArrival.bind(e);}
}
