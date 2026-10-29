package com.smileprotocol.neoforge;

import com.smileprotocol.SmileProtocol;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Punto de entrada en NeoForge. */
@Mod(SmileProtocol.MODID)
public final class SmileProtocolNeoForge {

    public SmileProtocolNeoForge(IEventBus modBus) {
        SmileProtocol.init();
    }
}
