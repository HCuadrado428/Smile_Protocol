package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Items del mod (README 10): linterna, baterias, fusibles, llaves, tarjetas, botiquines,
 * bengalas, jeringas, walkies y lanzables. Se registran en sus fases.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SmileProtocol.MODID);

    private ModItems() {}
}
