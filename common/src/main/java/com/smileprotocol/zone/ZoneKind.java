package com.smileprotocol.zone;

/** Papel de una zona en la secuencia (README 6.5). */
public enum ZoneKind {
    /** Pasillo inicial (fijo). */
    START,
    /** Zona intermedia: cambia entre partidas. */
    INTERMEDIATE,
    /** Sala final de criogenizacion y experimentos (fija). */
    FINAL,
    /** Planta de entrenamiento: solo la usa el ascensor TUTORIAL. */
    TUTORIAL
}
