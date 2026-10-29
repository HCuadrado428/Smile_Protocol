package com.smileprotocol.run;

/**
 * Situacion de cada miembro dentro de su grupo. Las fases 12 y 16 anaden derribado, muerto y
 * espectador.
 */
public enum MemberStatus {
    /** Juega (aunque este desconectado, dentro del plazo de {@code disconnect_grace}). */
    ACTIVE,
    /** Ha salido de la partida (leave, muerte hasta la fase 12, o plazo de desconexion agotado). */
    LEFT
}
