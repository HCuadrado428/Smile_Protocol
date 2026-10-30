package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dentro de la partida no hay muerte de vanilla: la vida a 0 derriba o mata a su manera (README 9.3). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$die(DamageSource source, CallbackInfo ci) {
        if (SPEvents.interceptDeath((ServerPlayer) (Object) this, source)) ci.cancel();
    }
}
