package com.smileprotocol.mixin.client;

import com.smileprotocol.client.ClientController;
import com.smileprotocol.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La rueda del raton recorre los 4 huecos de la partida (README 10). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!ClientState.inSmileProtocol() || minecraft.screen != null || window != minecraft.getWindow().getWindow()) return;
        ci.cancel();
        if (yOffset != 0) ClientController.scroll(yOffset > 0 ? -1 : 1);
    }
}
