package com.smileprotocol.mixin.client;

import com.smileprotocol.client.ClientIsolation;
import com.smileprotocol.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pantallas, ataque y elegir bloque dentro de la partida (README 7.3, 8.2, 8.3). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$setScreen(Screen screen, CallbackInfo ci) {
        if (ClientState.inSmileProtocol() && !ClientIsolation.isScreenAllowed(screen)) ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$startAttack(CallbackInfoReturnable<Boolean> cir) {
        // Dentro, el click izquierdo solo lanza el objeto que se esta apuntando (README 12.7)
        if (ClientState.inSmileProtocol()) {
            com.smileprotocol.client.ClientItems.tryThrow();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$continueAttack(boolean leftClick, CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }

    /** El click derecho dentro es la interaccion mantenida (README 11.1), no el uso vanilla. */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$startUseItem(CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }

    /**
     * Antes de que vanilla procese las teclas: Q (soltar) y F (cambiar de mano) no hacen nada dentro.
     * Q es asomarse a la izquierda y F la linterna; si llegaban a vanilla, el objeto se soltaba (y se
     * duplicaba, porque el inventario de la partida es aparte) o se iba a la mano secundaria.
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void smileprotocol$handleKeybinds(CallbackInfo ci) {
        if (!ClientState.inSmileProtocol()) return;
        Minecraft mc = (Minecraft) (Object) this;
        while (mc.options.keyDrop.consumeClick()) { /* nada */ }
        while (mc.options.keySwapOffhand.consumeClick()) { /* nada */ }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$pickBlock(CallbackInfo ci) {
        if (ClientState.inSmileProtocol()) ci.cancel();
    }
}
