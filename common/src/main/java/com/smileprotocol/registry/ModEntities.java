package com.smileprotocol.registry;

import com.smileprotocol.entity.Berserker;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

/** Entidades del mod: los mobs de la partida (README 20). */
public final class ModEntities {

    /** Mide 2,5 de alto encorvado; la caja es de 1,95 para pasar por las puertas (se agacha). */
    public static final Supplier<EntityType<Berserker>> BERSERKER = Services.PLATFORM.register(BuiltInRegistries.ENTITY_TYPE,
            "berserker", () -> EntityType.Builder.of(Berserker::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.95f).eyeHeight(1.75f).clientTrackingRange(10).fireImmune()
                    .build("berserker"));

    private ModEntities() {}

    public static void init() {
        Services.PLATFORM.registerAttributes(BERSERKER, Berserker::attributes);
    }
}
