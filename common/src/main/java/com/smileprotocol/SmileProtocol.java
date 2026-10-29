package com.smileprotocol;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Codigo comun de Smile Protocol (Minecraft 1.21.1, Fabric y NeoForge).
 * <p>
 * Regla de oro (README 1): las restricciones y mecanicas de terror solo se aplican
 * mientras el jugador esta en una partida ({@code inSP}). Cada loader llama a
 * {@link #init()} al arrancar.
 */
public final class SmileProtocol {

    public static final String MODID = "smileprotocol";
    public static final Logger LOGGER = LoggerFactory.getLogger("Smile Protocol");

    private SmileProtocol() {}

    public static void init() {
        LOGGER.info("Smile Protocol: iniciando");
    }

    /** Identificador del mod: {@code smileprotocol:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
