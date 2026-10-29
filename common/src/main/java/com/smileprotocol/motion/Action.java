package com.smileprotocol.motion;

/**
 * Accion principal del jugador (README 13.2, {@code PlayerActionState}). Se sincroniza a los
 * jugadores cercanos para las animaciones. Asomarse y mirar atras van aparte porque se
 * combinan con andar.
 */
public enum Action {
    NONE, DIVE, VAULT, HIDDEN, INTERACT;

    private static final Action[] VALUES = values();

    public static Action of(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
