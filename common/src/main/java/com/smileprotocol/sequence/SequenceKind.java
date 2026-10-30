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
    /** El Berserker levanta los brazos y aplasta tres veces al derribado contra el suelo. */
    BERSERKER_EXECUTION("execute", 64, true, 1.9, false, new int[]{20, 34, 50}, true),
    /** El smiler de pruebas se agacha sobre el derribado, le levanta la cabeza y se la retuerce. */
    SMILER_EXECUTION("execute_smiler", 60, true, 1.5, true, new int[]{22, 42}, true),
    /** Muerte instantanea: agarra a la victima, la levanta del suelo y le muerde la cara. */
    SMILER_KILL("kill", 64, false, 1.5, true, new int[]{30, 40}, false);

    private static final SequenceKind[] VALUES = values();

    public final String anim;
    public final int ticks;
    public final boolean prone;
    public final double distance;
    public final boolean facesMob;
    public final int[] impacts;
    public final boolean execution;

    SequenceKind(String anim, int ticks, boolean prone, double distance, boolean facesMob, int[] impacts, boolean execution) {
        this.anim = anim;
        this.ticks = ticks;
        this.prone = prone;
        this.distance = distance;
        this.facesMob = facesMob;
        this.impacts = impacts;
        this.execution = execution;
    }

    public static SequenceKind of(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BERSERKER_EXECUTION;
    }

    /** Altura extra de la victima (la muerte instantanea la levanta del suelo). */
    public double lift(float age) {
        if (this != SMILER_KILL) return 0;
        float up = Mth.clamp((age - 6) / 12f, 0f, 1f);
        float down = Mth.clamp((age - 52) / 5f, 0f, 1f);
        up = up * up * (3 - 2 * up);
        return 0.55 * up * (1 - down);
    }

    /** Cuanto hace del ultimo golpe (ticks), o -1 si no ha habido ninguno. */
    public float sinceImpact(float age) {
        float best = -1;
        for (int t : impacts) if (age >= t) best = age - t;
        return best;
    }
}
