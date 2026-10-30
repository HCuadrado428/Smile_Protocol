package com.smileprotocol.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.smileprotocol.client.ClientMotion;
import com.smileprotocol.client.ClientState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inclinacion (roll) de la camara al asomarse, en el dive y en el vault (README 12.3 a 12.6). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Shader de tension despues del mundo y antes de la HUD (README 21). */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void smileprotocol$tension(net.minecraft.client.DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        com.smileprotocol.client.ClientTension.render(delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void smileprotocol$roll(PoseStack pose, float pt, CallbackInfo ci) {
        if (!ClientState.inSmileProtocol()) return;
        float roll = ClientMotion.cameraRoll(pt);
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
    }
}
