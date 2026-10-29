package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bloques del mod. Se iran anadiendo por fases: ascensores (F05), cajas de fusibles,
 * puertas y valvulas (F09), escondites (F10), rejillas (F17)...
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SmileProtocol.MODID);

    private ModBlocks() {}
}
