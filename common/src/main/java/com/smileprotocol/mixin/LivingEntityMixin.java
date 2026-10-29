package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sin regeneracion dentro (README 9.2) y sin dano en el lobby (README 4.2). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$heal(float amount, CallbackInfo ci) {
        if (SPEvents.cancelHeal((LivingEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (SPEvents.cancelHurt((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}
