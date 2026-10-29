package com.smileprotocol;

import com.mojang.logging.LogUtils;
import com.smileprotocol.command.SPCommands;
import com.smileprotocol.registry.ModBlocks;
import com.smileprotocol.registry.ModEntities;
import com.smileprotocol.registry.ModItems;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Punto de entrada del mod Smile Protocol.
 * <p>
 * Regla de oro (README 1): las restricciones y mecanicas de terror solo se aplican
 * mientras el jugador esta en una partida ({@code inSP}). Esta clase solo conecta
 * los registros y los buses de eventos; la logica vive en sus paquetes.
 */
@Mod(SmileProtocol.MODID)
public final class SmileProtocol {

    public static final String MODID = "smileprotocol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmileProtocol() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.addListener(SPCommands::onRegisterCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Smile Protocol: configuracion comun lista");
    }

    /** Atajo para crear identificadores del mod: {@code smileprotocol:<path>}. */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
