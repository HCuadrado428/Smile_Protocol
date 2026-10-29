package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entidades de otros mods en las instancias y mobs en el lobby (README 4.2, 7.3). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$addFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SPEvents.cancelSpawn((ServerLevel) (Object) this, entity)) cir.setReturnValue(false);
    }
}
