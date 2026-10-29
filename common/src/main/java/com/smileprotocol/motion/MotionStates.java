package com.smileprotocol.motion;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de movimiento visible desde los dos lados sin depender de clases de cliente. El
 * servidor lo lleva {@link MotionManager}; en el cliente se rellena con los paquetes
 * {@code SyncMotion} (y la prediccion del jugador local).
 */
public final class MotionStates {

    /** Lado cliente: id de entidad -> estado. */
    public static final Map<Integer, MotionView> CLIENT = new ConcurrentHashMap<>();

    private MotionStates() {}

    public static MotionView view(Player player) {
        if (player.level().isClientSide) return CLIENT.getOrDefault(player.getId(), MotionView.NONE);
        return MotionManager.view(player);
    }

    /** Pose tumbada forzada (dive, escondite bajo). La usa el mixin de {@code Player#updatePlayerPose}. */
    public static boolean forceLowPose(Player player) {
        return view(player).lowPose();
    }
}
