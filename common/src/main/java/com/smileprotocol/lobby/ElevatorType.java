package com.smileprotocol.lobby;

/** Tipo de ascensor del lobby (README 6.2). */
public enum ElevatorType {
    /** Un grupo de 2 a 6 jugadores, con boton de listo por jugador. Modo libre. */
    MINI,
    /** Todo el lobby a la vez. Modo evento. */
    MEGA,
    /** La planta de entrenamiento: de 1 a 6 jugadores, sin monstruos. Abierto en todos los modos. */
    TUTORIAL
}
