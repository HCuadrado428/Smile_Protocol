package com.smileprotocol;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * Servidor en marcha. En 1.21 guardar y leer items necesita el acceso a los registros; los
 * datos del jugador se guardan y cargan siempre con el servidor arrancado.
 */
public final class ServerRef {

    @Nullable private static MinecraftServer server;

    private ServerRef() {}

    public static void set(@Nullable MinecraftServer s) {
        server = s;
    }

    @Nullable
    public static MinecraftServer get() {
        return server;
    }

    public static HolderLookup.Provider registries() {
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }
}
