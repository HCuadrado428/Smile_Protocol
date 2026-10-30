package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sin regeneracion dentro (README 9.2) y sin dano en el lobby (README 4.2). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow private float swimAmount;
    @Shadow private float swimAmountO;

    /**
     * Al levantarse tras la reanimacion el modelo deja de estar tumbado al momento: la animacion
     * de levantarse (README 9.3) ya parte de la postura tumbada y no se mezcla con el giro de vanilla.
     */
    @Inject(method = "updateSwimAmount", at = @At("TAIL"))
    private void smileprotocol$getupSwim(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide && self instanceof net.minecraft.world.entity.player.Player p
                && com.smileprotocol.motion.MotionStates.view(p).action() == com.smileprotocol.motion.Action.GETUP) {
            swimAmount = 0f;
            swimAmountO = 0f;
        }
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$heal(float amount, CallbackInfo ci) {
        if (SPEvents.cancelHeal((LivingEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (SPEvents.cancelHurt((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}
