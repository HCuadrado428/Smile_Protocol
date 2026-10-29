package com.smileprotocol.mixin.client;

import com.smileprotocol.client.ClientMotion;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bloquea el movimiento durante el vault y dentro de un escondite (README 12.4, 15.4). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void smileprotocol$lock(boolean slow, float sneakMultiplier, CallbackInfo ci) {
        if (!ClientMotion.inputLocked()) return;
        Input self = (Input) (Object) this;
        self.forwardImpulse = 0;
        self.leftImpulse = 0;
        self.up = self.down = self.left = self.right = false;
        self.jumping = false;
        self.shiftKeyDown = false;
    }
}
