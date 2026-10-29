package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import com.smileprotocol.motion.MotionStates;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sin ataques ni interaccion con entidades dentro de la partida ni en el lobby; pose tumbada
 * forzada durante el dive y en escondites bajos (README 12.3, 13.3); y dentro de la partida no
 * se tira nada al morir (los objetos de la partida no son del inventario real).
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$pose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (MotionStates.forceLowPose(self)) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$dropEquipment(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && SPPlayerData.inSP(self)) ci.cancel();
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$attack(Entity target, CallbackInfo ci) {
        if (SPEvents.cancelAttack((Player) (Object) this, target)) ci.cancel();
    }

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$interactOn(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SPEvents.cancelInteract((Player) (Object) this, target)) cir.setReturnValue(InteractionResult.PASS);
    }
}
