package com.smileprotocol.mechanism;

import com.smileprotocol.debug.SPDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Disparadores de eventos de laboratorio (README 14.7). Avisan a las misiones (fase 14); los
 * eventos (sustos, apagones...) llegan en la fase 15.
 */
public final class LabEvents {

    private LabEvents() {}

    public static void fire(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, String trigger, String arg) {
        com.smileprotocol.mission.MissionManager.onEvent(level, pos, player, trigger, arg);
        SPDebug.log("evento " + trigger + (arg.isEmpty() ? "" : " " + arg) + " en " + pos.toShortString()
                + (player != null ? " por " + player.getGameProfile().getName() : ""));
    }
}
