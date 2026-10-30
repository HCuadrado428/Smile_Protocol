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

    /** Mob de pruebas del agarre y el QTE (fase 11). */
    public static final Supplier<EntityType<com.smileprotocol.entity.SmilerTest>> SMILER_TEST = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "smiler_test", () -> EntityType.Builder.of(com.smileprotocol.entity.SmilerTest::new,
                    MobCategory.MONSTER).sized(0.9f, 1.95f).eyeHeight(1.75f).clientTrackingRange(10).fireImmune()
                    .build("smiler_test"));

    /** Mob de pruebas de la muerte instantanea (fase 12): mismo modelo, los dos ojos abiertos. */
    public static final Supplier<EntityType<com.smileprotocol.entity.SmilerTest.Killer>> SMILER_KILL = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "smiler_kill", () -> EntityType.Builder.of(com.smileprotocol.entity.SmilerTest.Killer::new,
                    MobCategory.MONSTER).sized(0.9f, 1.95f).eyeHeight(1.75f).clientTrackingRange(10).fireImmune()
                    .build("smiler_kill"));

    private ModEntities() {}

    public static void init() {
        Services.PLATFORM.registerAttributes(BERSERKER, Berserker::attributes);
        Services.PLATFORM.registerAttributes(SMILER_TEST, com.smileprotocol.entity.SmilerTest::attributes);
        Services.PLATFORM.registerAttributes(SMILER_KILL, com.smileprotocol.entity.SmilerTest::attributes);
    }
}
