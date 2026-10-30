package com.smileprotocol.qte;

import java.util.Locale;

/** Minijuegos de escape (README 18). */
public enum QTEType {
    /** Machacar la barra espaciadora. */
    MASH,
    /** Pulsar cuando el cursor pasa por la zona (3 aciertos, 2 fallos y fuera). */
    TIMING,
    /** Pulsar W A S D en el orden que se muestra. */
    SEQUENCE,
    /** Mantener la aguja dentro de la zona con la barra espaciadora. */
    HOLD;

    public static QTEType parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return MASH;
        }
    }

    // Reglas compartidas por cliente y servidor (el servidor decide; el cliente solo dibuja)

    /** Ticks que tarda el cursor de TIMING en ir y volver. */
    public static final int TIMING_PERIOD = 28;
    public static final int TIMING_HITS = 3;
    public static final int TIMING_MISSES = 2;
    public static final int SEQUENCE_LENGTH = 6;
    public static final float HOLD_ZONE = 0.22f;

    /** Posicion del cursor de TIMING (0 a 1, ida y vuelta) en el tick {@code t}. */
    public static float timingCursor(float t) {
        float p = (t % TIMING_PERIOD) / TIMING_PERIOD;
        return p < 0.5f ? p * 2 : 2 - p * 2;
    }

    /** Centro de la zona de TIMING de la ronda {@code round}. */
    public static float timingZone(long seed, int round) {
        long h = (seed * 6364136223846793005L + round * 1442695040888963407L) >>> 33;
        return 0.2f + (h % 1000) / 1000f * 0.6f;
    }

    public static float timingWidth(int difficulty) {
        return Math.max(0.08f, 0.18f - difficulty * 0.02f);
    }

    /** Tecla n de la secuencia (0 W, 1 A, 2 S, 3 D). */
    public static int sequenceKey(long seed, int n) {
        long h = (seed * 2862933555777941757L + n * 3037000493L) >>> 40;
        return (int) (h % 4);
    }

    /** Centro de la zona de HOLD, que se mueve despacio. */
    public static float holdZone(long seed, float t) {
        double ph = (seed % 628) / 100.0;
        return (float) (0.5 + 0.28 * Math.sin(t * 0.07 + ph) + 0.1 * Math.sin(t * 0.19 + ph * 2));
    }
}
