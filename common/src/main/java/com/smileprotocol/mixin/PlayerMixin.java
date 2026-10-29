package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sin ataques ni interaccion con entidades dentro de la partida ni en el lobby. */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$attack(Entity target, CallbackInfo ci) {
        if (SPEvents.cancelAttack((Player) (Object) this, target)) ci.cancel();
    }

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$interactOn(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SPEvents.cancelInteract((Player) (Object) this, target)) cir.setReturnValue(InteractionResult.PASS);
    }
}
