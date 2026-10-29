package com.smileprotocol.mechanism;

import java.util.Locale;

/** Tipos de puerta (README 14.3). */
public enum DoorType {
    /** Interaccion (lenta y sin ruido si vas agachado). */
    NORMAL,
    /** Llave con el lock_id correcto. */
    LOCKED,
    /** Tarjeta de nivel suficiente. */
    KEYCARD,
    /** Se abre sola con su circuito encendido. */
    ELECTRIC,
    /** Se abre sola al girar las valvulas de su grupo. */
    HYDRAULIC,
    /** Atascada: hacen falta dos jugadores a la vez. */
    STUCK;

    public static DoorType parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
