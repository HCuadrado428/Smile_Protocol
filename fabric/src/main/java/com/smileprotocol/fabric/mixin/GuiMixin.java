package com.smileprotocol.fabric.mixin;

import com.smileprotocol.client.ClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric: oculta el HUD vanilla dentro de la partida (README 8.1). En NeoForge se hace con
 * {@code RenderGuiLayerEvent}. {@code require = 0} para no tumbar el juego si cambia un nombre.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true, require = 0)
    private void smileprotocol$hotbar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void smileprotocol$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void smileprotocol$xp(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void smileprotocol$effects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }
}
