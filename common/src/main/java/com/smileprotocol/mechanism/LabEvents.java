package com.smileprotocol.mechanism;

import com.smileprotocol.debug.SPDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Disparadores de eventos de laboratorio (README 14.7). Los eventos (sustos, apagones...) llegan
 * en la fase 15; de momento solo se registran para depurar.
 */
public final class LabEvents {

    private LabEvents() {}

    public static void fire(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, String trigger, String arg) {
        SPDebug.log("evento " + trigger + (arg.isEmpty() ? "" : " " + arg) + " en " + pos.toShortString()
                + (player != null ? " por " + player.getGameProfile().getName() : ""));
    }
}
