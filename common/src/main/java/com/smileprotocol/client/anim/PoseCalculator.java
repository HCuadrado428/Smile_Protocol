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
 * Animaciones del jugador (README 13), calculadas por codigo a partir de su estado sincronizado.
 * PlayerAnimator aplica el resultado (el puente esta en cada loader). Cada parte puede llevar:
 * <ul>
 *   <li>un incremento sobre la pose vanilla (rotacion en radianes y posicion en pixeles), para lo que
 *   se superpone al paso normal (respirar, girar el torso al andar, cojear...);</li>
 *   <li>o valores absolutos, para las posturas que no tienen nada que ver con la vanilla (el dive, el arrastrarse derribado);</li>
 *   <li>y un doblez de codo o rodilla (BEND de PlayerAnimator).</li>
 * </ul>
 * Coordenadas del modelo: Y hacia abajo, -Z hacia delante, el suelo esta en Y = 24. Rotacion X
 * positiva inclina el torso hacia delante; negativa levanta brazos y piernas hacia delante.
 */
public final class PoseCalculator {

    /** Nombres de las partes en PlayerAnimator. {@code body} mueve el cuerpo entero. */
    public static final String HEAD = "head", TORSO = "torso", BODY = "body",
            RIGHT_ARM = "rightArm", LEFT_ARM = "leftArm", RIGHT_LEG = "rightLeg", LEFT_LEG = "leftLeg";

    /** Tipos de interaccion para la animacion ({@code MotionView.detail}). */
    public static final int INTERACT_GENERIC = 0, INTERACT_VALVE = 1, INTERACT_FUSE = 2, INTERACT_PICKUP = 3,
            INTERACT_DOOR = 4, INTERACT_CARD = 5, INTERACT_REVIVE = 6;

    private static final float PI = Mth.PI;

    /** Pose calculada: por parte, {pitch, yaw, roll, x, y, z, ejeDoblez, doblez} y si es absoluta. */
    public static final class Pose {
        final Map<String, float[]> parts = new HashMap<>();
        final Map<String, boolean[]> absolute = new HashMap<>();
        boolean active;

        public float[] get(String part) {
            return active ? parts.get(part) : null;
        }

        /** [rotacion absoluta, posicion absoluta]. */
        public boolean[] flags(String part) {
            return absolute.get(part);
        }

        public boolean isActive() {
            return active;
        }

        void clear() {
            for (float[] v : parts.values()) java.util.Arrays.fill(v, 0f);
            for (boolean[] f : absolute.values()) java.util.Arrays.fill(f, false);
            active = false;
        }

        private float[] v(String part) {
            return parts.computeIfAbsent(part, k -> new float[8]);
        }

        private boolean[] f(String part) {
            return absolute.computeIfAbsent(part, k -> new boolean[2]);
        }

        void rot(String part, float pitch, float yaw, float roll) {
            float[] v = v(part);
            v[0] += pitch;
            v[1] += yaw;
            v[2] += roll;
            if (pitch != 0 || yaw != 0 || roll != 0) active = true;
        }

        void pos(String part, float x, float y, float z) {
            float[] v = v(part);
            v[3] += x;
            v[4] += y;
            v[5] += z;
            if (x != 0 || y != 0 || z != 0) active = true;
        }

        /** Rotacion absoluta (sustituye a la vanilla). */
        void setRot(String part, float pitch, float yaw, float roll) {
            float[] v = v(part);
            v[0] = pitch;
            v[1] = yaw;
            v[2] = roll;
            f(part)[0] = true;
            active = true;
        }

        /** Posicion absoluta del pivote de la parte (coordenadas del modelo). */
        void setPos(String part, float x, float y, float z) {
            float[] v = v(part);
            v[3] = x;
            v[4] = y;
            v[5] = z;
            f(part)[1] = true;
            active = true;
        }

        /** Codo o rodilla: eje (0 = hacia delante) y cuanto se dobla. */
        void bend(String part, float axis, float amount) {
            float[] v = v(part);
            v[6] = axis;
            v[7] += amount;
            if (amount != 0) active = true;
        }
    }

    private PoseCalculator() {}

    /**
     * Mueve el cuerpo entero en pixeles del modelo (Y hacia abajo). PlayerAnimator aplica la
     * posicion de {@code body} en bloques y con Y hacia arriba: aqui se convierte.
     */
    private static void bodyPx(Pose pose, float x, float yDown, float z) {
        pose.pos(BODY, x / 16f, -yDown / 16f, z / 16f);
    }

    public static void compute(Player player, float pt, Pose pose) {
        pose.clear();
        if (!ClientState.inSmileProtocol()) return;
        MotionView m = MotionStates.view(player);
        ClientMotion.Visual vis = ClientMotion.visual(player.getId());
        float time = player.tickCount + pt;
        float age = vis.actionAge + pt;
        float speed = Mth.clamp(player.walkAnimation.speed(pt), 0f, 1f);
        float phase = player.walkAnimation.position(pt) * 0.6662f;
        float hp = player.getMaxHealth() <= 0 ? 1f : Mth.clamp(player.getHealth() / player.getMaxHealth(), 0f, 1f);

        switch (m.action()) {
            case DIVE -> dive(pose, age, time);
            case VAULT -> vault(pose, age);
            case INTERACT -> interact(pose, m.detail(), time, age);
            case GRABBED -> grabbed(pose, time);
            case DOWNED -> downed(pose, time, speed, phase);
            case HIDDEN -> { }
            default -> {
                // gateo por conductos y agachado (shift): los de vanilla
                if (player.isVisuallyCrawling()) return;
                if (player.isCrouching()) { }
                else if (player.isSprinting()) sprint(pose, time, speed, phase);
                else locomotion(pose, time, speed, phase, hp);
            }
        }

        // Mirar atras (README 12.5): cabeza y torso girados por encima del hombro
        float look = vis.look(pt);
        if (look > 0.01f && m.action() == Action.NONE && !player.isCrouching()) {
            pose.rot(HEAD, -0.1f * look, look * 1.25f, 0);
            pose.rot(TORSO, 0, look * 0.6f, 0);
            pose.rot(RIGHT_ARM, -0.25f * look, look * 0.55f, 0.15f * look);
            pose.rot(LEFT_ARM, 0.15f * look, look * 0.55f, 0);
        }

        // Asomarse (README 12.6): el cuerpo se inclina, la mano del lado se apoya en la esquina
        float lean = vis.lean(pt);
        if (Math.abs(lean) > 0.01f && m.action() == Action.NONE) {
            float a = Math.abs(lean);
            pose.rot(BODY, 0, 0, -lean * 0.3f);
            pose.rot(HEAD, 0.05f * a, -lean * 0.1f, -lean * 0.15f);
            String arm = lean < 0 ? LEFT_ARM : RIGHT_ARM;
            float s = lean < 0 ? -1 : 1;
            pose.rot(arm, -1.25f * a, 0.25f * s * a, 0.35f * s * a);
            pose.bend(arm, 0, -0.9f * a);
            pose.rot(lean < 0 ? RIGHT_ARM : LEFT_ARM, -0.3f * a, 0, -0.15f * s * a);
            pose.rot(lean < 0 ? LEFT_LEG : RIGHT_LEG, -0.08f * a, 0, 0.12f * s * a);
        }
    }

    // De pie ------------------------------------------------------------------------------------

    /** Quieto y andando: respira, mira nervioso, gira el torso al paso; con poca vida cojea. */
    private static void locomotion(Pose pose, float time, float speed, float phase, float hp) {
        float still = 1f - speed;
        // Respiracion: el pecho sube y los hombros se mueven
        float breath = Mth.sin(time * 0.09f);
        pose.rot(TORSO, 0.02f * breath * still, 0, 0);
        pose.rot(RIGHT_ARM, -0.03f * breath * still, 0, 0.05f + 0.025f * breath);
        pose.rot(LEFT_ARM, -0.03f * breath * still, 0, -0.05f - 0.025f * breath);
        // Tension: brazos algo adelantados, codos un poco doblados
        pose.rot(RIGHT_ARM, -0.12f, 0, 0);
        pose.rot(LEFT_ARM, -0.12f, 0, 0);
        pose.bend(RIGHT_ARM, 0, -0.25f - 0.25f * speed);
        pose.bend(LEFT_ARM, 0, -0.25f - 0.25f * speed);
        // Miradas nerviosas a los lados estando quieto
        float glance = Mth.sin(time * 0.031f) * Mth.sin(time * 0.017f + 1.3f);
        pose.rot(HEAD, 0.04f * Mth.sin(time * 0.05f) * still, 0.45f * glance * glance * glance * still, 0);
        // Al andar: el torso gira contra los brazos, la cabeza compensa, el cuerpo sube y baja
        float sw = Mth.sin(phase) * speed;
        pose.rot(TORSO, 0.06f * speed, -0.1f * sw, 0.03f * sw);
        pose.rot(HEAD, -0.04f * speed, 0.08f * sw, 0);
        bodyPx(pose, 0, -0.6f * Math.abs(Mth.cos(phase)) * speed, 0);
        pose.bend(RIGHT_LEG, 0, 0.35f * Math.max(0, Mth.cos(phase)) * speed);
        pose.bend(LEFT_LEG, 0, 0.35f * Math.max(0, -Mth.cos(phase)) * speed);
        // Poca vida: cojea de la pierna derecha y se sujeta el costado
        if (hp < 0.35f) {
            float k = (0.35f - hp) / 0.35f;
            pose.rot(RIGHT_LEG, -0.6f * Mth.cos(phase) * speed * k, 0, 0.05f * k);
            pose.rot(TORSO, 0.15f * k, 0, 0.08f * k * (0.5f + 0.5f * Mth.sin(phase)) * speed);
            pose.rot(HEAD, 0.2f * k, 0, 0.06f * k);
            pose.setRot(LEFT_ARM, -0.55f * k - 0.1f, 0.45f * k, 0.25f + 0.1f * k);
            pose.bend(LEFT_ARM, 0, -1.2f * k);
        }
    }

    /** Carrera: inclinado hacia delante, brazos bombeando con los codos doblados y rodillas altas. */
    private static void sprint(Pose pose, float time, float speed, float phase) {
        float c = Mth.cos(phase);
        pose.rot(TORSO, 0.32f, -0.14f * Mth.sin(phase) * speed, 0);
        pose.rot(HEAD, -0.28f, 0.1f * Mth.sin(phase) * speed, 0);
        bodyPx(pose, 0, 0.5f - 1.0f * Math.abs(c) * speed, -0.8f);
        // los brazos de la vanilla ya oscilan; se exagera y se doblan los codos
        pose.rot(RIGHT_ARM, 0.55f * Mth.cos(phase + PI) * speed - 0.25f, 0, 0.12f);
        pose.rot(LEFT_ARM, 0.55f * Mth.cos(phase) * speed - 0.25f, 0, -0.12f);
        pose.bend(RIGHT_ARM, 0, -1.25f);
        pose.bend(LEFT_ARM, 0, -1.25f);
        // rodillas: se doblan al echar la pierna atras y suben al adelantarla
        pose.rot(RIGHT_LEG, -0.35f * Math.max(0, -c) * speed, 0, 0);
        pose.rot(LEFT_LEG, -0.35f * Math.max(0, c) * speed, 0, 0);
        pose.bend(RIGHT_LEG, 0, 1.3f * Math.max(0, c) * speed + 0.2f);
        pose.bend(LEFT_LEG, 0, 1.3f * Math.max(0, -c) * speed + 0.2f);
    }

    // Acciones ----------------------------------------------------------------------------------

    /** Dive: se lanza tumbado, brazos estirados por delante y luego se frena con las manos. */
    private static void dive(Pose pose, float age, float time) {
        float slide = Mth.clamp(age / 4f, 0f, 1f);
        float out = age < MotionManager.DIVE_SLIDE ? 0f : Mth.clamp((age - MotionManager.DIVE_SLIDE) / 8f, 0f, 1f);
        float k = slide * (1 - out);
        // Pose de nado (tumbado): brazos por encima de la cabeza = por delante
        pose.setRot(RIGHT_ARM, Mth.lerp(k, 0f, -2.85f), 0, Mth.lerp(k, 0f, 0.18f));
        pose.setRot(LEFT_ARM, Mth.lerp(k, 0f, -2.85f), 0, Mth.lerp(k, 0f, -0.18f));
        pose.bend(RIGHT_ARM, 0, -0.2f * k);
        pose.bend(LEFT_ARM, 0, -0.2f * k);
        pose.setRot(RIGHT_LEG, 0.08f * k + Mth.sin(time * 0.8f) * 0.04f * k, 0, 0.03f);
        pose.setRot(LEFT_LEG, 0.08f * k - Mth.sin(time * 0.8f) * 0.04f * k, 0, -0.03f);
        pose.setRot(HEAD, -0.9f * k, 0, 0);
    }

    /** Vault: apoya la mano derecha en el obstaculo, recoge las piernas a un lado y cae. */
    private static void vault(Pose pose, float age) {
        float p = Mth.clamp(age / MotionManager.VAULT_TICKS, 0f, 1f);
        float arc = Mth.sin(p * PI);
        float early = Mth.clamp(p * 2.2f, 0f, 1f) * (1 - Mth.clamp((p - 0.7f) / 0.3f, 0f, 1f));
        pose.rot(RIGHT_ARM, -1.45f * early, 0.2f * early, 0.25f * early);
        pose.bend(RIGHT_ARM, 0, -0.3f * early);
        pose.rot(LEFT_ARM, -0.4f * arc, 0, -0.9f * arc);
        pose.rot(TORSO, 0.35f * arc, 0, 0);
        pose.rot(BODY, 0, 0.35f * arc, 0.35f * arc);
        pose.rot(RIGHT_LEG, -1.0f * arc, 0, -0.35f * arc);
        pose.rot(LEFT_LEG, -0.75f * arc, 0, -0.5f * arc);
        pose.bend(RIGHT_LEG, 0, 1.4f * arc);
        pose.bend(LEFT_LEG, 0, 1.2f * arc);
        pose.rot(HEAD, -0.2f * arc, -0.2f * arc, 0);
    }

    /** Agarrado: empuja al mob con los brazos, gira la cara, patalea. */
    private static void grabbed(Pose pose, float time) {
        float a = Mth.sin(time * 0.95f), b = Mth.sin(time * 1.3f + 1f), c = Mth.sin(time * 0.6f);
        pose.setRot(RIGHT_ARM, -1.5f + a * 0.35f, 0.25f, 0.2f + b * 0.1f);
        pose.setRot(LEFT_ARM, -1.5f - a * 0.35f, -0.25f, -0.2f - b * 0.1f);
        pose.bend(RIGHT_ARM, 0, -0.7f - 0.4f * Math.max(0, a));
        pose.bend(LEFT_ARM, 0, -0.7f - 0.4f * Math.max(0, -a));
        pose.rot(TORSO, -0.2f, c * 0.25f, b * 0.06f);
        pose.rot(HEAD, -0.15f, -0.5f + c * 0.25f, 0.1f * a);
        pose.rot(RIGHT_LEG, -0.25f + b * 0.35f, 0, 0.05f);
        pose.rot(LEFT_LEG, -0.1f - b * 0.35f, 0, -0.05f);
        pose.bend(RIGHT_LEG, 0, 0.6f + 0.4f * Math.max(0, b));
        pose.bend(LEFT_LEG, 0, 0.6f + 0.4f * Math.max(0, -b));
        bodyPx(pose, 0, 0, 0.6f * a);
    }

    /** Derribado (tumbado boca abajo): tira de un brazo y luego del otro, las piernas se arrastran. */
    private static void downed(Pose pose, float time, float speed, float phase) {
        boolean moving = speed > 0.02f;
        float cyc = moving ? phase * 0.6f : time * 0.03f;
        float r = Mth.sin(cyc), l = Mth.sin(cyc + PI);
        float amp = moving ? 1f : 0.15f;
        // brazo que se estira por delante y luego tira hacia el pecho
        pose.setRot(RIGHT_ARM, -2.3f - 0.6f * r * amp, 0.15f, 0.35f - 0.2f * r * amp);
        pose.setRot(LEFT_ARM, -2.3f - 0.6f * l * amp, -0.15f, -0.35f + 0.2f * l * amp);
        pose.bend(RIGHT_ARM, 0, -0.4f - 0.8f * Math.max(0, -r) * amp);
        pose.bend(LEFT_ARM, 0, -0.4f - 0.8f * Math.max(0, -l) * amp);
        pose.setRot(RIGHT_LEG, 0.05f, 0, 0.08f);
        pose.setRot(LEFT_LEG, 0.05f + 0.1f * r * amp, 0, -0.04f);
        pose.bend(LEFT_LEG, 0, 0.3f + 0.2f * r * amp);
        pose.setRot(HEAD, -0.75f + 0.1f * Mth.sin(time * 0.2f), 0.15f * r * amp, 0);
        pose.rot(TORSO, 0, 0.08f * r * amp, 0.06f * r * amp);
    }

    /** Interacciones (README 11): cada una con su gesto. {@code in} entra suave en 4 ticks. */
    private static void interact(Pose pose, int kind, float time, float age) {
        float in = Mth.clamp(age / 4f, 0f, 1f);
        switch (kind) {
            case INTERACT_VALVE -> {
                // Girar la rueda con las dos manos: tirones que mueven hombros y torso
                float a = time * 0.32f;
                float tug = Mth.sin(a);
                pose.rot(RIGHT_ARM, (-1.3f + Mth.sin(a) * 0.3f) * in, (-0.35f + Mth.cos(a) * 0.25f) * in, 0.05f * in);
                pose.rot(LEFT_ARM, (-1.3f - Mth.sin(a) * 0.3f) * in, (0.35f - Mth.cos(a) * 0.25f) * in, -0.05f * in);
                pose.bend(RIGHT_ARM, 0, -0.6f * in);
                pose.bend(LEFT_ARM, 0, -0.6f * in);
                pose.rot(TORSO, 0.12f * in, 0.18f * tug * in, 0.05f * tug * in);
                pose.rot(HEAD, 0.15f * in, -0.1f * tug * in, 0);
                pose.rot(RIGHT_LEG, -0.15f * in, 0, 0.05f * in);
                pose.rot(LEFT_LEG, 0.2f * in, 0, -0.05f * in);
            }
            case INTERACT_FUSE -> {
                // Empuja el fusible con la derecha mientras se apoya con la izquierda
                float push = (Mth.sin(time * 0.28f) * 0.5f + 0.5f);
                pose.rot(RIGHT_ARM, (-1.5f + push * 0.2f) * in, -0.12f * in, 0);
                pose.bend(RIGHT_ARM, 0, (-0.9f + push * 0.7f) * in);
                pose.rot(LEFT_ARM, -1.05f * in, 0.35f * in, -0.1f * in);
                pose.bend(LEFT_ARM, 0, -0.3f * in);
                pose.rot(TORSO, 0.1f * in, -0.1f * in, 0);
                pose.rot(HEAD, 0.25f * in, 0.05f * in, 0);
            }
            case INTERACT_PICKUP -> {
                // Se agacha doblando las rodillas y alarga el brazo al suelo
                float d = Mth.sin(Mth.clamp(age / 12f, 0f, 1f) * PI);
                float k = Math.max(in, d);
                pose.rot(TORSO, 0.6f * k, 0, 0);
                pose.rot(HEAD, 0.35f * k, 0, 0);
                pose.rot(RIGHT_ARM, -0.95f * k, 0.15f * k, 0);
                pose.rot(LEFT_ARM, -0.2f * k, 0, -0.25f * k);
                pose.rot(RIGHT_LEG, -0.55f * k, 0, 0.05f * k);
                pose.rot(LEFT_LEG, -0.25f * k, 0, -0.05f * k);
                pose.bend(RIGHT_LEG, 0, 1.2f * k);
                pose.bend(LEFT_LEG, 0, 0.9f * k);
                bodyPx(pose, 0, 3.5f * k, 0);
            }
            case INTERACT_DOOR -> {
                // Hombro contra la puerta y las dos manos empujando
                float shove = Mth.sin(time * 0.22f) * 0.5f + 0.5f;
                pose.rot(RIGHT_ARM, -1.45f * in, -0.1f * in, 0);
                pose.rot(LEFT_ARM, -1.3f * in, 0.2f * in, 0);
                pose.bend(RIGHT_ARM, 0, (-0.8f + 0.5f * shove) * in);
                pose.bend(LEFT_ARM, 0, (-0.9f + 0.5f * shove) * in);
                pose.rot(TORSO, (0.15f + 0.08f * shove) * in, -0.15f * in, 0);
                pose.rot(RIGHT_LEG, 0.25f * in, 0, 0);
                pose.rot(LEFT_LEG, -0.3f * in, 0, 0);
                pose.bend(LEFT_LEG, 0, 0.5f * in);
            }
            case INTERACT_CARD -> {
                // Pasa la tarjeta por el lector: sube, desliza de lado a lado, mira la luz
                float swipe = Mth.sin(time * 0.35f);
                pose.rot(RIGHT_ARM, -1.4f * in, (-0.4f + 0.3f * swipe) * in, 0);
                pose.bend(RIGHT_ARM, 0, -0.5f * in);
                pose.rot(LEFT_ARM, -0.25f * in, 0, -0.1f * in);
                pose.rot(HEAD, 0.1f * in, (0.15f + 0.1f * swipe) * in, 0);
                pose.rot(TORSO, 0.05f * in, -0.1f * in, 0);
            }
            case INTERACT_REVIVE -> {
                // De rodillas, presionando el pecho del companero
                float press = Mth.sin(time * 0.6f) * 0.5f + 0.5f;
                pose.rot(TORSO, 0.75f * in, 0, 0);
                pose.rot(RIGHT_ARM, (-1.0f + press * 0.2f) * in, 0.2f * in, 0);
                pose.rot(LEFT_ARM, (-1.0f + press * 0.2f) * in, -0.2f * in, 0);
                pose.bend(RIGHT_ARM, 0, -0.3f * press * in);
                pose.bend(LEFT_ARM, 0, -0.3f * press * in);
                pose.rot(RIGHT_LEG, -1.4f * in, 0, 0.1f * in);
                pose.rot(LEFT_LEG, -0.3f * in, 0, -0.1f * in);
                pose.bend(RIGHT_LEG, 0, 1.5f * in);
                pose.bend(LEFT_LEG, 0, 1.4f * in);
                bodyPx(pose, 0, 5f * in, 0);
            }
            default -> {
                pose.rot(RIGHT_ARM, (-1.2f + Mth.sin(time * 0.3f) * 0.08f) * in, -0.1f * in, 0);
                pose.rot(LEFT_ARM, (-1.1f - Mth.sin(time * 0.3f) * 0.08f) * in, 0.1f * in, 0);
                pose.bend(RIGHT_ARM, 0, -0.5f * in);
                pose.bend(LEFT_ARM, 0, -0.5f * in);
            }
        }
    }
}
