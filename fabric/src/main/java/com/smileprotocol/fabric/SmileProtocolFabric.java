package com.smileprotocol.fabric;

import com.smileprotocol.SmileProtocol;
import net.fabricmc.api.ModInitializer;

/** Punto de entrada en Fabric. */
public final class SmileProtocolFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SmileProtocol.init();
    }
}
