package com.smileprotocol.neoforge.client;

import com.smileprotocol.client.anim.PoseCalculator;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Puente con PlayerAnimator (README 13.2): anade a cada jugador una capa que suma la pose de
 * {@link PoseCalculator} a la animacion vanilla. Solo se carga si PlayerAnimator esta instalado.
 */
public final class PalBridge {

    private static final int PRIORITY = 1100;
    private static final Map<AbstractClientPlayer, Boolean> ATTACHED = new WeakHashMap<>();

    private PalBridge() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null) return;
        for (Player p : mc.level.players()) {
            if (!(p instanceof AbstractClientPlayer acp) || ATTACHED.containsKey(acp)) continue;
            ATTACHED.put(acp, Boolean.TRUE);
            try {
                PlayerAnimationAccess.getPlayerAnimLayer(acp).addAnimLayer(PRIORITY, new Layer(acp));
            } catch (RuntimeException e) {
                // Jugador sin soporte de animaciones: se deja la pose vanilla
            }
        }
    }

    /** Capa aditiva sobre la pose vanilla. */
    static final class Layer implements IAnimation {
        private final AbstractClientPlayer player;
        private final PoseCalculator.Pose pose = new PoseCalculator.Pose();

        Layer(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public boolean isActive() {
            return pose.isActive() || com.smileprotocol.client.ClientState.inSmileProtocol();
        }

        @Override
        public void setupAnim(float tickDelta) {
            PoseCalculator.compute(player, tickDelta, pose);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
            float[] d = pose.get(part);
            if (d == null) return value0;
            boolean[] abs = pose.flags(part);
            if (type == TransformType.ROTATION) {
                Vec3f r = new Vec3f(d[0], d[1], d[2]);
                return abs != null && abs[0] ? r : value0.add(r);
            }
            if (type == TransformType.POSITION) {
                Vec3f p = new Vec3f(d[3], d[4], d[5]);
                return abs != null && abs[1] ? p : value0.add(p);
            }
            if (type == TransformType.BEND) return value0.add(new Vec3f(d[6], d[7], 0f));
            return value0;
        }
    }
}
