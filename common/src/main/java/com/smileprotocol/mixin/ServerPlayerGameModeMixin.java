package com.smileprotocol.mixin;

import com.smileprotocol.event.SPEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Romper, usar bloques y usar items: partida y lobby (README 4.2, 8.3). */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$destroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SPEvents.cancelBreak(player, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$useItemOn(ServerPlayer p, Level level, ItemStack stack, InteractionHand hand,
                                         BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult r = SPEvents.useItemOn(p, stack, hit);
        if (r != null) cir.setReturnValue(r);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$useItem(ServerPlayer p, Level level, ItemStack stack, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (SPEvents.cancelUseItem(p, stack)) cir.setReturnValue(InteractionResult.PASS);
    }
}
