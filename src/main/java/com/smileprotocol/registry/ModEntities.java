package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Entidades del mod. El primer smiler es el Berserker (F11, README 20.2).
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SmileProtocol.MODID);

    private ModEntities() {}
}
