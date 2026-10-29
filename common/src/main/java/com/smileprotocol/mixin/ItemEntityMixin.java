package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sin recogida automatica dentro de la partida (README 10). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$playerTouch(Player player, CallbackInfo ci) {
        if (SPEvents.cancelPickup(player)) ci.cancel();
    }
}
