package com.smileprotocol.motion;

/**
 * Accion principal del jugador (README 13.2, {@code PlayerActionState}). Se sincroniza a los
 * jugadores cercanos para las animaciones. Asomarse y mirar atras van aparte porque se
 * combinan con andar.
 */
public enum Action {
    NONE, DIVE, VAULT, HIDDEN, INTERACT, DOWNED, GRABBED,
    /** Levantandose tras ser reanimado ({@code detail} 1 = con ayuda). */
    GETUP,
    /** Secuencia de remate o de muerte ({@code detail} = {@code SequenceKind}). */
    SEQUENCE,
    /** Usando un consumible ({@code detail} = gesto, {@code PoseCalculator.USE_*}). */
    USE,
    /** Apuntando un lanzable. */
    AIM,
    /** Acaba de lanzar (unos ticks). */
    THROW;

    private static final Action[] VALUES = values();

    public static Action of(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
