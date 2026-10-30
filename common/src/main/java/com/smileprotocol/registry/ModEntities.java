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

    /** Bengala encendida (fase 13). */
    public static final Supplier<EntityType<com.smileprotocol.entity.FlareEntity>> FLARE = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "flare", () -> EntityType.Builder.<com.smileprotocol.entity.FlareEntity>of(
                    com.smileprotocol.entity.FlareEntity::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(10)
                    .updateInterval(4).fireImmune().build("flare"));

    /** Lata o botella lanzada (fase 13). */
    public static final Supplier<EntityType<com.smileprotocol.entity.ThrownSPItem>> THROWN_ITEM = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "thrown_item", () -> EntityType.Builder.<com.smileprotocol.entity.ThrownSPItem>of(
                    com.smileprotocol.entity.ThrownSPItem::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(8)
                    .updateInterval(10).build("thrown_item"));

    private ModEntities() {}

    public static void init() {
        Services.PLATFORM.registerAttributes(BERSERKER, Berserker::attributes);
        Services.PLATFORM.registerAttributes(SMILER_TEST, com.smileprotocol.entity.SmilerTest::attributes);
        Services.PLATFORM.registerAttributes(SMILER_KILL, com.smileprotocol.entity.SmilerTest::attributes);
    }
}
