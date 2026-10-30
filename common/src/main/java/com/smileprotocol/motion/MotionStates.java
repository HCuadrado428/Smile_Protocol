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

    /**
     * Conducto o hueco de un bloque de alto justo delante (README 12.9): al acercarse a el, el
     * jugador se tumba con el gateo de vanilla (pose {@code SWIMMING} fuera del agua, 0,6 de alto)
     * y entra. Dentro, vanilla le mantiene gateando hasta que haya sitio para levantarse. Se calcula
     * igual en el cliente y en el servidor (posicion, giro y bloques).
     */
    public static boolean ventAhead(Player player) {
        if (!com.smileprotocol.dimension.SPDimension.isSP(player.level()) || !player.onGround()
                || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                || view(player).action() != Action.NONE) return false;
        net.minecraft.world.level.Level level = player.level();
        float yaw = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        double dx = -net.minecraft.util.Mth.sin(yaw), dz = net.minecraft.util.Mth.cos(yaw);
        for (double reach : new double[]{0.45, 0.75}) {
            net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(
                    player.getX() + dx * reach, player.getY() + 0.1, player.getZ() + dz * reach);
            if (feet.equals(player.blockPosition())) continue;
            if (free(level, feet) && !free(level, feet.above())) return true;
        }
        return false;
    }

    private static boolean free(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
