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
            INTERACT_DOOR = 4, INTERACT_CARD = 5, INTERACT_REVIVE = 6, INTERACT_PULL = 7;

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
            case DOWNED -> downed(pose, time, speed, phase, age);
            case GETUP -> getup(pose, m.detail() == 1, age);
            case SEQUENCE -> sequence(pose, com.smileprotocol.sequence.SequenceKind.of(m.detail()), age, time);
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

    /**
     * Derribado (tumbado boca abajo): tira de un brazo y luego del otro, las piernas se arrastran.
     * Al caer, los primeros ticks, los brazos se abren para frenar el golpe.
     */
    private static void downed(Pose pose, float time, float speed, float phase, float age) {
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
        float fall = 1f - Mth.clamp(age / 10f, 0f, 1f);
        if (fall > 0) {
            pose.rot(RIGHT_ARM, 1.1f * fall, 0, 0.7f * fall);
            pose.rot(LEFT_ARM, 1.1f * fall, 0, -0.7f * fall);
            pose.rot(HEAD, -0.5f * fall, 0, 0);
            pose.bend(RIGHT_LEG, 0, 0.8f * fall);
            pose.bend(LEFT_LEG, 0, 0.6f * fall);
        }
    }

    // Levantarse ---------------------------------------------------------------------------------

    /** Postura absoluta de una parte: rotacion, posicion del pivote y doblez. */
    private record Key(float rx, float ry, float rz, float px, float py, float pz, float bend) {
        Key lerp(Key o, float k) {
            return new Key(Mth.lerp(k, rx, o.rx), Mth.lerp(k, ry, o.ry), Mth.lerp(k, rz, o.rz), Mth.lerp(k, px, o.px),
                    Mth.lerp(k, py, o.py), Mth.lerp(k, pz, o.pz), Mth.lerp(k, bend, o.bend));
        }

        void apply(Pose pose, String part) {
            pose.setRot(part, rx, ry, rz);
            pose.setPos(part, px, py, pz);
            pose.bend(part, 0, bend);
        }
    }

    private static final String[] PARTS = {TORSO, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG};

    /** Tumbado (en el marco de pie: la pose de vanilla ya no esta tumbada al levantarse). */
    private static final Key[] PRONE = {
            new Key(PI / 2, 0, 0, 0, 21f, -2f, 0), new Key(1.15f, 0, 0, 0, 20.6f, -2f, 0),
            new Key(-1.6f, 0, 0.2f, -5f, 21f, -0.5f, -0.5f), new Key(-1.6f, 0, -0.2f, 5f, 21f, -0.5f, -0.5f),
            new Key(1.5f, 0, 0.05f, -1.9f, 21.5f, 10f, 0), new Key(1.5f, 0, -0.05f, 1.9f, 21.5f, 10f, 0)};
    /** A cuatro patas, apoyandose en las manos. */
    private static final Key[] FOURS = {
            new Key(PI / 2 - 0.15f, 0, 0, 0, 14.5f, 0, 0), new Key(-0.1f, 0, 0, 0, 16.4f, -1.6f, 0),
            new Key(-0.45f, 0, 0.12f, -5f, 15f, 0.5f, -0.35f), new Key(-0.45f, 0, -0.12f, 5f, 15f, 0.5f, -0.35f),
            new Key(-0.05f, 0, 0.06f, -1.9f, 17.1f, 10.6f, 1.5f), new Key(-0.05f, 0, -0.06f, 1.9f, 17.1f, 10.6f, 1.5f)};

    /** De rodillas (rodilla derecha en el suelo, pie izquierdo delante); con ayuda alza la mano. */
    private static Key[] kneel(boolean helped) {
        return new Key[]{
                new Key(0.3f, 0, 0, 0, 6.5f, -3.5f, 0), new Key(-0.25f, helped ? 0.2f : 0, 0, 0, 6.5f, -3.5f, 0),
                helped ? new Key(-2.3f, 0.1f, 0.15f, -5f, 8.4f, -2.9f, -0.2f) : new Key(-0.35f, 0, 0.15f, -5f, 8.4f, -2.9f, -0.4f),
                new Key(-0.75f, 0, -0.1f, 5f, 8.4f, -2.9f, -0.6f),
                new Key(0.05f, 0, 0.05f, -1.9f, 18f, 0, 1.55f), new Key(-1.45f, 0, -0.05f, 1.9f, 18f, 0, 1.45f)};
    }

    private static final Key[] STAND = {
            new Key(0, 0, 0, 0, 0, 0, 0), new Key(0, 0, 0, 0, 0, 0, 0),
            new Key(0, 0, 0, -5f, 2f, 0, 0), new Key(0, 0, 0, 5f, 2f, 0, 0),
            new Key(0, 0, 0, -1.9f, 12f, 0, 0), new Key(0, 0, 0, 1.9f, 12f, 0, 0)};

    /**
     * Levantarse tras la reanimacion (README 9.3): de tumbado a cuatro patas, de rodillas y de pie.
     * Con ayuda alza la mano hacia el companero que tira de el.
     */
    private static void getup(Pose pose, boolean helped, float age) {
        float t = Mth.clamp(age / MotionManager.GETUP_TICKS, 0f, 1f);
        Key[] a, b;
        float k;
        if (t < 0.2f) { a = PRONE; b = FOURS; k = t / 0.2f; }
        else if (t < 0.55f) { a = FOURS; b = kneel(helped); k = (t - 0.2f) / 0.35f; }
        else { a = kneel(helped); b = STAND; k = (t - 0.55f) / 0.45f; }
        k = k * k * (3 - 2 * k);
        for (int i = 0; i < PARTS.length; i++) a[i].lerp(b[i], k).apply(pose, PARTS[i]);
    }

    /**
     * Reanimar a un companero (README 9.3), postura absoluta: de rodillas junto a el, inclinado
     * encima con los brazos rectos sobre su espalda. Ciclo de 3 s: compresiones (todo el peso del
     * cuerpo baja y sube), y luego lo zarandea por los hombros mirando por encima del hombro por si
     * viene algo. Entra desde de pie en 8 ticks.
     */
    private static void reviveRescuer(Pose pose, float time, float age) {
        float k = Mth.clamp(age / 8f, 0f, 1f);
        k = k * k * (3 - 2 * k);
        float cyc = (age % 60f) / 60f;
        // 0..1: cuanto de "zarandear" (frente a compresiones), con fundidos suaves
        float shake = Mth.clamp((cyc - 0.66f) / 0.06f, 0f, 1f) * (1 - Mth.clamp((cyc - 0.94f) / 0.06f, 0f, 1f));
        float pump = 1 - shake;
        float press = Math.max(0f, Mth.sin(time * 0.62f)) * pump;          // bajada de cada compresion
        float rock = Mth.sin(time * 0.45f) * shake;                          // vaiven al zarandear
        float glance = shake * Mth.clamp(Mth.sin((cyc - 0.66f) / 0.34f * PI) * 1.6f, 0f, 1f);
        float breath = Mth.sin(time * 0.15f);

        // Caderas sobre las rodillas (rodillas en el suelo, espinillas hacia atras)
        float hipY = 16.6f + 0.5f * press, hipZ = 0.5f;
        float lean = 0.92f + 0.10f * press + 0.04f * breath * shake;
        float topY = hipY - 12f * Mth.cos(lean), topZ = hipZ - 12f * Mth.sin(lean);
        Key[] kneel = {
                new Key(lean, 0.22f * glance, 0.08f * rock, 0.6f * rock, topY, topZ, 0),
                new Key(0.65f - 0.75f * glance + 0.12f * press, 1.05f * glance, 0.1f * rock, 0.6f * rock, topY + 0.3f, topZ - 0.3f, 0),
                // brazos rectos hacia abajo y algo adelante; al zarandear, a los hombros y doblados
                new Key(-0.32f - 0.25f * shake + 0.06f * rock, -0.1f + 0.2f * glance, 0.12f + 0.1f * shake, -4.6f + 0.6f * rock,
                        topY + 2f * Mth.cos(lean), topZ + 2f * Mth.sin(lean), -0.15f - 0.55f * shake + 0.2f * (1 - press) * pump),
                new Key(-0.32f - 0.25f * shake - 0.06f * rock, 0.1f, -0.12f - 0.1f * shake, 4.6f + 0.6f * rock,
                        topY + 2f * Mth.cos(lean), topZ + 2f * Mth.sin(lean), -0.15f - 0.55f * shake + 0.2f * (1 - press) * pump),
                new Key(-0.22f, 0, 0.1f, -2.1f, hipY, hipZ, 1.78f),
                new Key(-0.18f, 0, -0.1f, 2.1f, hipY, hipZ, 1.74f)};
        for (int i = 0; i < PARTS.length; i++) STAND[i].lerp(kneel[i], k).apply(pose, PARTS[i]);
    }

    // Remates y muertes (README 9.3) -------------------------------------------------------------

    private static float pulse(float since, float len) {
        return since < 0 || since > len ? 0f : Mth.sin(since / len * PI);
    }

    private static void sequence(Pose pose, com.smileprotocol.sequence.SequenceKind kind, float age, float time) {
        float hit = pulse(kind.sinceImpact(age), 7f);
        switch (kind) {
            case BERSERKER_EXECUTION -> {
                // Tumbado: intenta huir arrastrandose, cada golpe lo sacude; tras el ultimo queda inerte
                float limp = Mth.clamp((age - kind.impacts[kind.impacts.length - 1] - 2) / 6f, 0f, 1f);
                float panic = Mth.clamp(1f - (age - 20f) / 30f, 0.25f, 1f) * (1 - limp);
                float cyc = time * 0.55f;
                float r = Mth.sin(cyc), l = Mth.sin(cyc + PI);
                pose.setRot(RIGHT_ARM, Mth.lerp(limp, -2.4f - 0.6f * r * panic, -1.7f), 0.15f, Mth.lerp(limp, 0.35f, 0.95f) + 0.7f * hit);
                pose.setRot(LEFT_ARM, Mth.lerp(limp, -2.4f - 0.6f * l * panic, -1.6f), -0.15f, Mth.lerp(limp, -0.35f, -0.9f) - 0.7f * hit);
                pose.bend(RIGHT_ARM, 0, (-0.4f - 0.8f * Math.max(0, -r) * panic) * (1 - limp));
                pose.bend(LEFT_ARM, 0, (-0.4f - 0.8f * Math.max(0, -l) * panic) * (1 - limp));
                pose.setRot(HEAD, Mth.lerp(limp, -0.75f, -0.35f) - 0.6f * hit, Mth.lerp(limp, 0.15f * r * panic, 0.9f), 0);
                pose.setRot(RIGHT_LEG, 0.05f - 0.5f * hit + 0.15f * r * panic, 0, 0.08f + 0.1f * limp);
                pose.setRot(LEFT_LEG, 0.05f - 0.35f * hit - 0.15f * r * panic, 0, -0.08f - 0.1f * limp);
                pose.bend(RIGHT_LEG, 0, 0.9f * hit);
                pose.bend(LEFT_LEG, 0, 0.6f * hit);
                pose.rot(TORSO, -0.25f * hit, 0.1f * r * panic, 0.08f * r * panic);
            }
            case SMILER_EXECUTION -> {
                // Tumbado de cara al mob: le agarra la cabeza y tira hacia arriba; forcejea; el
                // segundo golpe le parte el cuello (la cabeza queda girada) y se desploma
                float pull = Mth.clamp((age - 12f) / 8f, 0f, 1f);
                float snap = Mth.clamp((age - kind.impacts[1]) / 2f, 0f, 1f);
                float lift = pull * (1 - Mth.clamp((age - kind.impacts[1] - 4) / 6f, 0f, 1f));
                float fight = pull * (1 - snap);
                float a = Mth.sin(time * 1.1f), b = Mth.sin(time * 0.8f + 1f);
                pose.setRot(HEAD, Mth.lerp(lift, -0.75f, -1.75f) + 0.15f * hit, 1.45f * snap + 0.12f * a * fight, 0.4f * snap);
                pose.rot(TORSO, -0.45f * lift, 0.1f * b * fight, 0);
                // los brazos van atras, a las manos que le sujetan la cabeza
                pose.setRot(RIGHT_ARM, Mth.lerp(pull, -2.3f + 0.4f * Mth.sin(time * 0.5f), -3.0f + 0.3f * a * fight) * (1 - snap) + -0.25f * snap,
                        0.2f, Mth.lerp(snap, 0.45f, 0.35f) + 0.2f * hit);
                pose.setRot(LEFT_ARM, Mth.lerp(pull, -2.3f - 0.4f * Mth.sin(time * 0.5f), -3.0f - 0.3f * a * fight) * (1 - snap) + -0.25f * snap,
                        -0.2f, Mth.lerp(snap, -0.45f, -0.35f) - 0.2f * hit);
                pose.bend(RIGHT_ARM, 0, -0.9f * fight);
                pose.bend(LEFT_ARM, 0, -0.9f * fight);
                pose.setRot(RIGHT_LEG, 0.05f - 0.45f * Math.max(0, b) * fight, 0, 0.08f);
                pose.setRot(LEFT_LEG, 0.05f - 0.45f * Math.max(0, -b) * fight, 0, -0.08f);
                pose.bend(RIGHT_LEG, 0, 1.1f * Math.max(0, b) * fight);
                pose.bend(LEFT_LEG, 0, 1.1f * Math.max(0, -b) * fight);
            }
            case SMILER_KILL -> {
                // Levantado del suelo: patalea y empuja la cara del mob; el mordisco le echa la cabeza
                // atras, convulsiona y queda colgando; al soltarlo se desploma
                float up = Mth.clamp((age - 6f) / 10f, 0f, 1f);
                float limp = Mth.clamp((age - 44f) / 6f, 0f, 1f);
                float drop = Mth.clamp((age - 53f) / 4f, 0f, 1f);
                float fight = 1 - limp;
                float bite = Math.max(pulse(age - kind.impacts[0], 9f), pulse(age - kind.impacts[1], 8f));
                float shake = age > kind.impacts[0] && limp < 1 ? Mth.sin(time * 2.6f) * 0.12f * fight : 0f;
                float a = Mth.sin(time * 1.25f), b = Mth.sin(time * 0.95f + 0.6f);
                pose.setRot(RIGHT_ARM, Mth.lerp(limp, -1.55f + 0.3f * a, 0.15f) + shake, Mth.lerp(limp, 0.3f, 0), Mth.lerp(limp, 0.2f, 0.08f));
                pose.setRot(LEFT_ARM, Mth.lerp(limp, -1.55f - 0.3f * a, 0.1f) - shake, Mth.lerp(limp, -0.3f, 0), Mth.lerp(limp, -0.2f, -0.08f));
                pose.bend(RIGHT_ARM, 0, (-0.8f - 0.4f * Math.max(0, a)) * fight);
                pose.bend(LEFT_ARM, 0, (-0.8f - 0.4f * Math.max(0, -a)) * fight);
                pose.setRot(HEAD, Mth.lerp(limp, -0.2f - 0.7f * bite, 0.75f) + shake, Mth.lerp(limp, -0.55f + 0.2f * b, 0.35f), Mth.lerp(limp, 0.1f * a, 0.3f));
                pose.setRot(TORSO, -0.12f * bite + Mth.lerp(limp, 0, 0.12f) + 0.55f * drop, 0.15f * b * fight, 0);
                // piernas colgando que patalean
                float kick = up * fight;
                pose.setRot(RIGHT_LEG, 0.15f * up - 0.55f * Math.max(0, b) * kick + 0.04f * Mth.sin(time * 0.2f) * limp - 1.1f * drop, 0, 0.06f);
                pose.setRot(LEFT_LEG, 0.15f * up - 0.55f * Math.max(0, -b) * kick + 0.04f * Mth.sin(time * 0.2f + 1) * limp - 0.6f * drop, 0, -0.06f);
                pose.bend(RIGHT_LEG, 0, 0.3f * up + 0.9f * Math.max(0, b) * kick + 1.6f * drop);
                pose.bend(LEFT_LEG, 0, 0.3f * up + 0.9f * Math.max(0, -b) * kick + 1.2f * drop);
                bodyPx(pose, 0, 6f * drop, -2f * drop);
            }
        }
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
            case INTERACT_REVIVE -> reviveRescuer(pose, time, age);
            case INTERACT_PULL -> {
                // Tira del companero agarrado: brazos estirados, echado hacia atras, pies clavados
                float tug = Mth.sin(time * 0.55f) * 0.5f + 0.5f;
                pose.rot(RIGHT_ARM, (-1.45f + 0.15f * tug) * in, -0.15f * in, 0);
                pose.rot(LEFT_ARM, (-1.4f + 0.15f * tug) * in, 0.15f * in, 0);
                pose.bend(RIGHT_ARM, 0, (-0.2f - 0.5f * tug) * in);
                pose.bend(LEFT_ARM, 0, (-0.2f - 0.5f * tug) * in);
                pose.rot(TORSO, (-0.2f - 0.12f * tug) * in, 0.08f * Mth.sin(time * 0.3f) * in, 0);
                pose.rot(HEAD, (0.15f + 0.1f * tug) * in, 0, 0);
                pose.rot(RIGHT_LEG, -0.45f * in, 0, 0.05f * in);
                pose.rot(LEFT_LEG, 0.35f * in, 0, -0.05f * in);
                pose.bend(RIGHT_LEG, 0, 0.6f * in);
                bodyPx(pose, 0, 1.2f * in, 1.0f * tug * in);
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
