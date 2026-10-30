package com.smileprotocol;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.registry.ModBlockEntities;
import com.smileprotocol.registry.ModBlocks;
import com.smileprotocol.registry.ModCreativeTabs;
import com.smileprotocol.registry.ModItems;
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

    /** Configuracion y registros. Los bloques van antes que los items y las entidades de bloque. */
    public static void init() {
        SPConfig.load();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        com.smileprotocol.registry.ModEntities.init();
        com.smileprotocol.entity.MobManager.init();
        ModCreativeTabs.init();
        LOGGER.info("Smile Protocol: iniciado");
    }

    /** Identificador del mod: {@code smileprotocol:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
