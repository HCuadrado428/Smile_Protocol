package com.smileprotocol.run;

/**
 * Situacion de cada miembro dentro de su grupo. El derribado no es un estado del grupo (es
 * temporal, {@code DownedManager}); los muertos siguen en el grupo como espectadores.
 */
public enum MemberStatus {
    /** Juega (aunque este desconectado, dentro del plazo de {@code disconnect_grace}). */
    ACTIVE,
    /** Ha muerto en la partida: especta a sus companeros vivos hasta que el grupo sube (README 9.3, 6.5). */
    DEAD,
    /** Ha salido de la partida (leave, o plazo de desconexion agotado). */
    LEFT
}
