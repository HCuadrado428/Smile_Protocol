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

    /** El Paciente (fase 21): 3,6 bloques de alto; no cabe por las puertas de las salas. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Paciente>> PACIENTE = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "paciente", () -> EntityType.Builder.of(com.smileprotocol.entity.Paciente::new,
                    MobCategory.MONSTER).sized(0.8f, 3.4f).eyeHeight(3.2f).clientTrackingRange(12).fireImmune()
                    .build("paciente"));

    /** El Rastreador (fase 21): 2,2 bloques encorvado; la caja de 2 pasa por las puertas. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Rastreador>> RASTREADOR = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "rastreador", () -> EntityType.Builder.of(com.smileprotocol.entity.Rastreador::new,
                    MobCategory.MONSTER).sized(0.7f, 1.95f).eyeHeight(1.8f).clientTrackingRange(12).fireImmune()
                    .build("rastreador"));

    /** El Ahorcado (fase 21): muneco de 1,2 bloques que cuelga del techo. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Ahorcado>> AHORCADO = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "ahorcado", () -> EntityType.Builder.of(com.smileprotocol.entity.Ahorcado::new,
                    MobCategory.MONSTER).sized(0.6f, 1.2f).eyeHeight(1.0f).clientTrackingRange(10).fireImmune()
                    .build("ahorcado"));

    /** Apagon (fase 21): humo con ojos; la caja es fija, el dibujo crece a oscuras. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Apagon>> APAGON = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "apagon", () -> EntityType.Builder.of(com.smileprotocol.entity.Apagon::new,
                    MobCategory.MONSTER).sized(0.8f, 1.95f).eyeHeight(1.7f).clientTrackingRange(12).fireImmune()
                    .build("apagon"));

    /** Smiley, el jefe (fase 21): 4 bloques de alto; la caja, algo menos para que quepa por los pasillos. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Smiley>> SMILEY = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "smiley", () -> EntityType.Builder.of(com.smileprotocol.entity.Smiley::new,
                    MobCategory.MONSTER).sized(0.9f, 3.4f).eyeHeight(3.2f).clientTrackingRange(16).fireImmune()
                    .build("smiley"));

    /** El Imitador: 2,4 bloques encorvado; la caja de 2 pasa por las puertas. */
    public static final Supplier<EntityType<com.smileprotocol.entity.Imitador>> IMITADOR = Services.PLATFORM.register(
            BuiltInRegistries.ENTITY_TYPE, "imitador", () -> EntityType.Builder.of(com.smileprotocol.entity.Imitador::new,
                    MobCategory.MONSTER).sized(0.7f, 1.95f).eyeHeight(1.8f).clientTrackingRange(12).fireImmune()
                    .build("imitador"));

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
        Services.PLATFORM.registerAttributes(PACIENTE, com.smileprotocol.entity.Paciente::attributes);
        Services.PLATFORM.registerAttributes(RASTREADOR, com.smileprotocol.entity.Rastreador::attributes);
        Services.PLATFORM.registerAttributes(AHORCADO, com.smileprotocol.entity.Ahorcado::attributes);
        Services.PLATFORM.registerAttributes(APAGON, com.smileprotocol.entity.Apagon::attributes);
        Services.PLATFORM.registerAttributes(SMILEY, com.smileprotocol.entity.Smiley::attributes);
        Services.PLATFORM.registerAttributes(IMITADOR, com.smileprotocol.entity.Imitador::attributes);
    }
}
