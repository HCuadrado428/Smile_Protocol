package com.smileprotocol.sequence;

import net.minecraft.util.Mth;

/**
 * Secuencias de remate y de muerte instantanea (README 9.3). Cada una tiene su animacion del mob
 * (GeckoLib, por nombre) y su version del jugador ({@code PoseCalculator}), su duracion, donde
 * queda la victima respecto al mob y los ticks de los golpes (sonido, sacudida de camara y
 * destello en la victima). Todo lo decide el servidor; los clientes solo lo reproducen.
 *
 * @param anim        animacion del mob
 * @param ticks       duracion; al acabar la victima muere
 * @param prone       la victima esta tumbada (remates de derribados)
 * @param distance    distancia de la victima delante del mob (bloques)
 * @param facesMob    la victima mira al mob (si no, le da la espalda: huia arrastrandose)
 * @param impacts     ticks de los golpes
 * @param execution   remate (si no, muerte instantanea)
 */
public enum SequenceKind {
    /**
     * El Berserker levanta los brazos y aplasta al derribado tres veces, alternando los brazos cada
     * vez mas rapido; luego lo agarra, lo levanta del suelo, lo desgarra y lo estampa por ultima vez.
     */
    BERSERKER_EXECUTION("execute", 78, true, 1.4, false, new int[]{18, 28, 37, 60}, new int[]{50}, -1,
            new int[]{37, 72}, 2, true),
    /**
     * El smiler de pruebas se agacha sobre el derribado, le levanta la cabeza, se la retuerce del
     * todo y se la arranca; se queda mirandola con la cabeza en la mano mientras el cuello chorrea.
     */
    SMILER_EXECUTION("execute_smiler", 74, true, 1.45, true, new int[]{22, 34, 44}, new int[]{44}, 44,
            new int[]{44, 70}, 0, true),
    /**
     * Muerte instantanea: agarra a la victima, la levanta del suelo, le muerde la cara dos veces y le
     * arranca la garganta de un tiron; la deja caer desangrandose.
     */
    SMILER_KILL("kill", 72, false, 1.25, true, new int[]{30, 40, 50}, new int[]{50}, -1,
            new int[]{30, 66}, 1, false),
    /**
     * El Paciente se dobla sobre la victima, le coge la cabeza con sus dedos larguisimos, la sube
     * hasta su cara, le parte el cuello de un giro, se lo retuerce del todo y la deja caer.
     */
    PACIENTE_KILL("kill_paciente", 50, false, 1.05, true, new int[]{22, 34}, new int[]{34}, -1,
            new int[]{34, 48}, 0, false);

    /** Donde sale el chorro de sangre: cuello, cara o espalda de la victima. */
    public static final int NECK = 0, FACE = 1, BACK = 2;

    private static final SequenceKind[] VALUES = values();

    public final String anim;
    public final int ticks;
    public final boolean prone;
    public final double distance;
    public final boolean facesMob;
    public final int[] impacts;
    /** Desgarros (sonido propio y mucha mas sangre). */
    public final int[] tears;
    /** Tick en que la cabeza de la victima desaparece (arrancada), o -1. */
    public final int decap;
    /** Ticks [desde, hasta] del chorro de sangre continuo. */
    public final int[] fountain;
    public final int fountainAt;
    public final boolean execution;

    SequenceKind(String anim, int ticks, boolean prone, double distance, boolean facesMob, int[] impacts, int[] tears,
                 int decap, int[] fountain, int fountainAt, boolean execution) {
        this.anim = anim;
        this.ticks = ticks;
        this.prone = prone;
        this.distance = distance;
        this.facesMob = facesMob;
        this.impacts = impacts;
        this.tears = tears;
        this.decap = decap;
        this.fountain = fountain;
        this.fountainAt = fountainAt;
        this.execution = execution;
    }

    public static SequenceKind of(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BERSERKER_EXECUTION;
    }

    /** Altura extra de la victima: el Berserker la levanta antes del ultimo golpe; el smiler, para morderla. */
    public double lift(float age) {
        if (this == BERSERKER_EXECUTION) {
            float up = Mth.clamp((age - 40) / 6f, 0f, 1f);
            float down = Mth.clamp((age - 57) / 3f, 0f, 1f);
            up = up * up * (3 - 2 * up);
            return 0.75 * up * (1 - down);
        }
        if (this == PACIENTE_KILL) {
            float up = Mth.clamp((age - 6) / 12f, 0f, 1f);
            float down = Mth.clamp((age - 40) / 4f, 0f, 1f);
            up = up * up * (3 - 2 * up);
            return 1.25 * up * (1 - down);
        }
        if (this != SMILER_KILL) return 0;
        float up = Mth.clamp((age - 6) / 12f, 0f, 1f);
        float down = Mth.clamp((age - 58) / 5f, 0f, 1f);
        up = up * up * (3 - 2 * up);
        return 0.6 * up * (1 - down);
    }

    public boolean isTear(int age) {
        for (int t : tears) if (t == age) return true;
        return false;
    }

    /** Cuanto hace del ultimo golpe (ticks), o -1 si no ha habido ninguno. */
    public float sinceImpact(float age) {
        float best = -1;
        for (int t : impacts) if (age >= t) best = age - t;
        return best;
    }

    /** La cabeza ya no esta (0-1, para la animacion). */
    public boolean headless(float age) {
        return decap >= 0 && age >= decap;
    }
}
