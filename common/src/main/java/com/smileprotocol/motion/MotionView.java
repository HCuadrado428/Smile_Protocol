package com.smileprotocol.motion;

/**
 * Lo que los clientes saben del estado de movimiento de un jugador (README 13.2).
 *
 * @param action   accion principal
 * @param lean     -1 izquierda, 0 nada, 1 derecha
 * @param lookBack mirando atras
 * @param detail   dato extra de la accion (tipo de interaccion, tipo de escondite...)
 * @param lowPose  pose tumbada (dive o escondite bajo): caja de 0,6 bloques
 * @param startTick tick del cliente en que empezo la accion (para animar)
 */
public record MotionView(Action action, int lean, boolean lookBack, int detail, boolean lowPose, int startTick) {
    public static final MotionView NONE = new MotionView(Action.NONE, 0, false, 0, false, 0);
}
