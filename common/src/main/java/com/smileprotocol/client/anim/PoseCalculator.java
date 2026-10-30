package com.smileprotocol.client.anim;

import com.smileprotocol.client.ClientMotion;
import com.smileprotocol.client.ClientState;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.motion.MotionStates;
import com.smileprotocol.motion.MotionView;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Animaciones del jugador (README 13), calculadas por codigo a partir de su estado
 * sincronizado: mirar atras, asomarse, correr, dive, vault e interacciones. El resultado son
 * incrementos sobre la pose vanilla por parte del modelo, que PlayerAnimator aplica (el puente
 * esta en cada loader). Sin PlayerAnimator la camara y la hitbox funcionan igual; solo falta
 * esta capa visual.
 * <p>
 * Rotaciones en radianes (x = cabeceo, y = giro, z = alabeo) y desplazamientos en pixeles.
 */
public final class PoseCalculator {

    /** Nombres de las partes en PlayerAnimator. {@code body} mueve el cuerpo entero. */
    public static final String HEAD = "head", TORSO = "torso", BODY = "body",
            RIGHT_ARM = "rightArm", LEFT_ARM = "leftArm", RIGHT_LEG = "rightLeg", LEFT_LEG = "leftLeg";

    /** Tipos de interaccion para la animacion ({@code MotionView.detail}). */
    public static final int INTERACT_GENERIC = 0, INTERACT_VALVE = 1, INTERACT_FUSE = 2, INTERACT_PICKUP = 3,
            INTERACT_DOOR = 4, INTERACT_CARD = 5, INTERACT_REVIVE = 6;

    /** Pose calculada: por parte, {pitch, yaw, roll, x, y, z}. */
    public static final class Pose {
        final Map<String, float[]> parts = new HashMap<>();
        boolean active;

        public float[] get(String part) {
            return active ? parts.get(part) : null;
        }

        public boolean isActive() {
            return active;
        }

        void clear() {
            for (float[] v : parts.values()) java.util.Arrays.fill(v, 0f);
            active = false;
        }

        void rot(String part, float pitch, float yaw, float roll) {
            float[] v = parts.computeIfAbsent(part, k -> new float[6]);
            v[0] += pitch;
            v[1] += yaw;
            v[2] += roll;
            if (pitch != 0 || yaw != 0 || roll != 0) active = true;
        }

        void pos(String part, float x, float y, float z) {
            float[] v = parts.computeIfAbsent(part, k -> new float[6]);
            v[3] += x;
            v[4] += y;
            v[5] += z;
            if (x != 0 || y != 0 || z != 0) active = true;
        }
    }

    private PoseCalculator() {}

    public static void compute(Player player, float pt, Pose pose) {
        pose.clear();
        if (!ClientState.inSmileProtocol()) return;
        MotionView m = MotionStates.view(player);
        ClientMotion.Visual vis = ClientMotion.visual(player.getId());
        float time = player.tickCount + pt;
        float age = vis.actionAge + pt;

        // Mirar atras (README 12.5): cabeza y torso girados por encima del hombro
        float look = vis.look(pt);
        if (look > 0.01f) {
            pose.rot(HEAD, 0, look * 1.35f, 0);
            pose.rot(TORSO, 0, look * 0.55f, 0);
            pose.rot(RIGHT_ARM, 0, look * 0.45f, 0);
            pose.rot(LEFT_ARM, 0, look * 0.45f, 0);
        }

        // Asomarse (README 12.6): el cuerpo entero se inclina hacia el lado
        float lean = vis.lean(pt);
        if (Math.abs(lean) > 0.01f) {
            pose.rot(BODY, 0, 0, -lean * 0.26f);
            pose.rot(HEAD, 0, 0, -lean * 0.12f);
        }

        switch (m.action()) {
            case DIVE -> {
                // Brazos por delante y piernas juntas mientras se desliza
                float k = Mth.clamp(age / 5f, 0f, 1f) * (age < MotionManager.DIVE_SLIDE ? 1f
                        : Mth.clamp(1f - (age - MotionManager.DIVE_SLIDE) / 8f, 0f, 1f));
                pose.rot(RIGHT_ARM, -1.1f * k, 0, 0.15f * k);
                pose.rot(LEFT_ARM, -1.1f * k, 0, -0.15f * k);
                pose.rot(RIGHT_LEG, 0.1f * k, 0, 0);
                pose.rot(LEFT_LEG, 0.1f * k, 0, 0);
            }
            case VAULT -> {
                // Apoyo de la mano derecha y piernas recogidas (README 12.4)
                float p = Mth.clamp(age / MotionManager.VAULT_TICKS, 0f, 1f);
                float arc = Mth.sin(p * Mth.PI);
                pose.rot(RIGHT_ARM, -1.5f * arc, 0, 0.2f * arc);
                pose.rot(LEFT_ARM, -0.7f * arc, 0, -0.5f * arc);
                pose.rot(RIGHT_LEG, -1.1f * arc, 0, 0);
                pose.rot(LEFT_LEG, -0.8f * arc, 0, 0);
                pose.rot(BODY, 0, 0, 0.25f * arc);
            }
            case INTERACT -> interact(pose, m.detail(), time, age);
            case GRABBED -> {
                // Forcejea: brazos empujando hacia delante, el cuerpo se retuerce
                float sh = Mth.sin(time * 0.9f);
                pose.rot(RIGHT_ARM, -1.6f + sh * 0.3f, 0.3f, 0.2f);
                pose.rot(LEFT_ARM, -1.6f - sh * 0.3f, -0.3f, -0.2f);
                pose.rot(TORSO, -0.15f, sh * 0.15f, 0);
                pose.rot(HEAD, 0.1f, -sh * 0.2f, 0);
            }
            case DOWNED -> {
                // Derribado: se arrastra tirando de los brazos, las piernas casi muertas
                float sw = player.walkAnimation.speed(pt) > 0.02f ? Mth.sin(time * 0.35f) : 0f;
                pose.rot(RIGHT_ARM, -2.6f + sw * 0.5f, 0, 0.25f);
                pose.rot(LEFT_ARM, -2.6f - sw * 0.5f, 0, -0.25f);
                pose.rot(RIGHT_LEG, 0.05f - sw * 0.15f, 0, 0.05f);
                pose.rot(LEFT_LEG, 0.05f + sw * 0.15f, 0, -0.05f);
                pose.rot(HEAD, -0.5f, 0, 0);
            }
            default -> {
                if (player.isSprinting()) {
                    // Carrera: torso adelantado y la cabeza compensa
                    pose.rot(TORSO, 0.22f, 0, 0);
                    pose.rot(HEAD, -0.15f, 0, 0);
                } else if (player.isCrouching() && player.walkAnimation.speed(pt) > 0.05f) {
                    // Sigilo: brazos algo adelantados y tensos
                    pose.rot(RIGHT_ARM, -0.35f, 0, 0.1f);
                    pose.rot(LEFT_ARM, -0.35f, 0, -0.1f);
                }
            }
        }
    }

    private static void interact(Pose pose, int kind, float time, float age) {
        float in = Mth.clamp(age / 4f, 0f, 1f);
        switch (kind) {
            case INTERACT_VALVE -> {
                // Girar la rueda: las manos describen un circulo
                float a = time * 0.35f;
                pose.rot(RIGHT_ARM, (-1.35f + Mth.sin(a) * 0.25f) * in, (-0.25f + Mth.cos(a) * 0.2f) * in, 0);
                pose.rot(LEFT_ARM, (-1.35f - Mth.sin(a) * 0.25f) * in, (0.25f - Mth.cos(a) * 0.2f) * in, 0);
                pose.rot(TORSO, 0.1f * in, 0, 0);
            }
            case INTERACT_FUSE -> {
                float push = (Mth.sin(time * 0.25f) * 0.5f + 0.5f) * 0.25f;
                pose.rot(RIGHT_ARM, (-1.55f + push) * in, -0.15f * in, 0);
                pose.rot(LEFT_ARM, -0.6f * in, 0.2f * in, 0);
            }
            case INTERACT_PICKUP -> {
                pose.rot(TORSO, 0.55f * in, 0, 0);
                pose.rot(HEAD, 0.3f * in, 0, 0);
                pose.rot(RIGHT_ARM, -0.9f * in, 0, 0);
                pose.pos(BODY, 0, 1.5f * in, 0);
            }
            case INTERACT_DOOR -> {
                pose.rot(RIGHT_ARM, -1.4f * in, -0.1f * in, 0);
                pose.rot(LEFT_ARM, -1.2f * in, 0.15f * in, 0);
                pose.rot(TORSO, 0.15f * in, 0, 0);
            }
            case INTERACT_CARD -> {
                pose.rot(RIGHT_ARM, -1.45f * in, -0.35f * in, 0);
            }
            case INTERACT_REVIVE -> {
                pose.rot(TORSO, 0.7f * in, 0, 0);
                pose.rot(RIGHT_ARM, (-1.0f + Mth.sin(time * 0.6f) * 0.15f) * in, 0, 0);
                pose.rot(LEFT_ARM, (-1.0f - Mth.sin(time * 0.6f) * 0.15f) * in, 0, 0);
                pose.pos(BODY, 0, 3f * in, 0);
            }
            default -> {
                pose.rot(RIGHT_ARM, (-1.2f + Mth.sin(time * 0.3f) * 0.08f) * in, -0.1f * in, 0);
                pose.rot(LEFT_ARM, (-1.1f - Mth.sin(time * 0.3f) * 0.08f) * in, 0.1f * in, 0);
            }
        }
    }

    /** Para que no se use Action sin importar (las ramas de arriba). */
    static boolean isNone(Action a) {
        return a == Action.NONE;
    }
}
