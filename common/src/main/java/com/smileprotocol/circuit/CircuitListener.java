package com.smileprotocol.circuit;

import net.minecraft.server.level.ServerLevel;

/** Entidad de bloque que reacciona a un circuito (luces, puertas electricas, ascensores... README 14.1). */
public interface CircuitListener {

    /** Circuito al que esta ligado ("" = ninguno). */
    String circuitId();

    void onCircuit(ServerLevel level, boolean powered);
}
