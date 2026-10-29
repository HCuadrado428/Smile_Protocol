package com.smileprotocol.mixin.client;

import com.smileprotocol.client.ClientHide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Limita el giro de la camara dentro de un escondite en cada movimiento del raton (README 15.4). */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {

    @Inject(method = "turn", at = @At("TAIL"))
    private void smileprotocol$turn(double dx, double dy, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self == Minecraft.getInstance().player) ClientHide.clamp(self);
    }
}
