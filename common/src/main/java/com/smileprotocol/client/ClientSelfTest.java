package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.HideBlock;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.HideBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.hide.HideTypes;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.run.RunType;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Prueba automatica en el cliente para la CI ({@code SMILEPROTOCOL_CLIENTTEST=1}): entra en un
 * mundo de un jugador, baja a una partida y hace capturas dentro de la planta (pasillo, caja de
 * fusibles, taquilla, puerta, escondido...). Luego cierra el juego. Las capturas van a
 * {@code screenshots/}.
 */
public final class ClientSelfTest {

    /** Grabacion de animaciones en tercera persona (SMILEPROTOCOL_ANIMTEST=1): fotogramas para hacer GIF. */
    private static final boolean ANIM = "1".equals(System.getenv("SMILEPROTOCOL_ANIMTEST"));
    private static final boolean ENABLED = ANIM || "1".equals(System.getenv("SMILEPROTOCOL_CLIENTTEST"));
    private static int ticks;
    private static int step;
    private static int wait;
    private static final List<Runnable> SHOTS = new ArrayList<>();

    private ClientSelfTest() {}

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        ticks++;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            // Abre el mundo de prueba que deja la prueba del servidor
            if (ticks == 200 && mc.level == null) {
                log("abriendo el mundo smoke");
                mc.createWorldOpenFlows().openWorld("smoke", () -> log("FALLO: no se pudo abrir el mundo"));
            }
            if (ticks % 40 == 0 && mc.screen != null) {
                log("pantalla " + mc.screen.getClass().getName());
                accept(mc.screen);
            }
            if (ticks > 20 * 600) mc.stop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        switch (step) {
            case 0 -> {
                log("en el mundo, bajando a una partida");
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    RunManager.StartResult r = RunManager.start(server, RunType.MINI, "test", List.of(sp), true);
                    log("partida: " + r.message().getString());
                });
                wait = 200;
                step++;
            }
            case 1 -> {
                plan(mc, server);
                step++;
            }
            default -> {
                int i = step - 2;
                if (i >= SHOTS.size()) {
                    log("fin");
                    mc.stop();
                    return;
                }
                SHOTS.get(i).run();
                step++;
            }
        }
    }

    private static void plan(Minecraft mc, MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        Group g = RunManager.groupOf(sp);
        if (g == null || g.current() == null) {
            log("FALLO: sin grupo");
            mc.stop();
            return;
        }
        ZonePlacement z = g.current();
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        Vec3 e = z.entry();
        if (ANIM) {
            planAnimations(mc, server, sp, level, e);
            return;
        }
        // 1. Llegada, mirando hacia el vestibulo
        shot(mc, server, "01_llegada", e, 0f, 5f, 60);
        SHOTS.add(() -> {
            var m = ClientState.mission;
            log((m != null ? "OK" : "FALLO") + " objetivo en el HUD: " + (m == null ? "-" : m.text().getString()
                    + " " + m.progress() + "/" + m.total() + " paso " + (m.step() + 1) + "/" + m.steps()));
        });
        // 2. Pasillo principal
        shot(mc, server, "02_pasillo", e.add(0, 0, 30), 0f, 5f, 40);
        shot(mc, server, "03_pasillo_transversal", e.add(0, 0, 33), -90f, 5f, 40);
        // 3. Mecanismos y escondites
        lookAt(mc, server, level, z, "04_caja_fusibles", be -> be instanceof FuseBoxBlockEntity);
        lookAt(mc, server, level, z, "05_taquilla", be -> be instanceof HideBlockEntity h && h.modelName().equals("locker"));
        lookAt(mc, server, level, z, "06_puerta", be -> be instanceof LabDoorBlockEntity);
        lookAt(mc, server, level, z, "07_puerta_seguridad", be -> be instanceof LabDoorBlockEntity
                && be.getBlockState().getBlock() == com.smileprotocol.registry.ModBlocks.SECURITY_DOOR.get());
        lookAt(mc, server, level, z, "08_camilla", be -> be instanceof HideBlockEntity h && h.modelName().equals("gurney"));
        lookAt(mc, server, level, z, "09_armario", be -> be instanceof HideBlockEntity h && !h.modelName().equals("locker")
                && !h.modelName().equals("gurney"));
        lookAt(mc, server, level, z, "10_valvula", be -> be instanceof ValveBlockEntity);
        // 4. Abrir una puerta y esconderse
        SHOTS.add(() -> server.execute(() -> {
            BlockEntity door = find(level, z, be -> be instanceof LabDoorBlockEntity);
            if (door != null) com.smileprotocol.block.LabDoorBlock.setOpenAt(level, door.getBlockPos(), true);
        }));
        lookAt(mc, server, level, z, "11_puerta_abierta", be -> be instanceof LabDoorBlockEntity
                && be.getBlockState().getValue(com.smileprotocol.block.LabDoorBlock.OPEN));
        SHOTS.add(() -> server.execute(() -> {
            BlockEntity locker = find(level, z, be -> be instanceof HideBlockEntity h && h.modelName().equals("locker"));
            if (locker != null && locker.getBlockState().getBlock() instanceof HideBlock hb) {
                HideManager.enter(sp, locker.getBlockPos(), HideTypes.get(server, "locker"), hb);
            }
        }));
        SHOTS.add(() -> wait = 40);
        SHOTS.add(() -> grab(mc, "12_escondido"));
        SHOTS.add(() -> server.execute(() -> HideManager.forceExit(sp, true)));
        // 5. Con corriente: enciende el circuito de salida
        SHOTS.add(() -> server.execute(() -> CircuitManager.notify(level, BlockPos.containing(e), "exit", true)));
        shot(mc, server, "13_con_corriente", e.add(0, 0, 30), 0f, 5f, 40);
        // 6. HUD con poca vida (venas, pulso acelerado) y una sala con sangre
        SHOTS.add(() -> server.execute(() -> sp.setHealth(4f)));
        lookAt(mc, server, level, z, "14_vida_baja_camilla", be -> be instanceof HideBlockEntity h && h.modelName().equals("gurney"));
        SHOTS.add(() -> server.execute(() -> sp.setHealth(sp.getMaxHealth())));
        // 7. Berserker (fase 11): de frente, en diagonal, golpeando donde sono un ruido y el derribo
        Vec3 cam = e.add(0, 0, 30);
        Vec3 mobAt = cam.add(0, 0, 4);
        com.smileprotocol.entity.Berserker[] mob = {null};
        com.smileprotocol.entity.SmilerTest[] killer = {null};
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.entity.Berserker b = com.smileprotocol.entity.MobManager.spawn(level,
                    com.smileprotocol.registry.ModEntities.BERSERKER.get(), BlockPos.containing(mobAt), level.random);
            if (b != null) {
                b.setNoAi(true);
                b.setYRot(180f);
                b.setYBodyRot(180f);
                b.setYHeadRot(180f);
                mob[0] = b;
            }
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
        }));
        SHOTS.add(() -> wait = 40);
        SHOTS.add(() -> grab(mc, "15_berserker"));
        // de lado: se gira el mob (el pasillo es estrecho para mover la camara)
        SHOTS.add(() -> server.execute(() -> {
            if (mob[0] != null) {
                mob[0].setYRot(90f);
                mob[0].setYBodyRot(90f);
                mob[0].setYHeadRot(90f);
            }
        }));
        SHOTS.add(() -> wait = 20);
        SHOTS.add(() -> grab(mc, "16_berserker_lado"));
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            if (mob[0] != null) mob[0].setNoAi(false);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> server.execute(() ->
                com.smileprotocol.noise.NoiseSystem.emit(level, mobAt.add(0, 0, -2), 12f, com.smileprotocol.noise.NoiseType.TEST, null)));
        SHOTS.add(() -> wait = 14);
        SHOTS.add(() -> grab(mc, "17_berserker_golpe"));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> grab(mc, "18_derribado"));
        // 8. Agarre y QTE con el mob de pruebas
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.downed.DownedManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(40))) m.discard();
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_TEST.get(),
                    BlockPos.containing(cam.add(0, 0, 4)), level.random);
        }));
        SHOTS.add(() -> wait = 50);
        SHOTS.add(() -> grab(mc, "19_agarre_qte"));
        // 8b. Fase 13: botiquin, bengala, lata lanzada y jeringa derribado
        SHOTS.add(() -> server.execute(() -> {
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(40))) m.discard();
            com.smileprotocol.qte.QTEManager.clear(sp);
            com.smileprotocol.downed.DownedManager.clear(sp);
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            sp.setHealth(8f);
            use(sp, com.smileprotocol.registry.ModItems.MEDKIT.get(), com.smileprotocol.network.Payloads.UseItem.START);
        }));
        SHOTS.add(() -> wait = 20);
        SHOTS.add(() -> grab(mc, "23_botiquin"));
        SHOTS.add(() -> wait = 45);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = sp.getHealth() > 15f && com.smileprotocol.interact.SPInventoryOps.find(sp,
                    st -> st.is(com.smileprotocol.registry.ModItems.MEDKIT.get())) < 0;
            log((ok ? "OK" : "FALLO") + " botiquin: vida " + sp.getHealth());
            use(sp, com.smileprotocol.registry.ModItems.FLARE.get(), com.smileprotocol.network.Payloads.UseItem.START);
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = !level.getEntitiesOfClass(com.smileprotocol.entity.FlareEntity.class,
                    sp.getBoundingBox().inflate(4)).isEmpty();
            log((ok ? "OK" : "FALLO") + " bengala encendida");
            com.smileprotocol.motion.Flashlights.set(sp, false);
            SPSessionManager.teleport(sp, level, sp.position().add(sp.getLookAngle().multiply(-1.5, 0, -1.5)), sp.getYRot(), 35f);
        }));
        SHOTS.add(() -> wait = 15);
        SHOTS.add(() -> grab(mc, "24_bengala"));
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, -10f);
            use(sp, com.smileprotocol.registry.ModItems.CAN.get(), com.smileprotocol.network.Payloads.UseItem.AIM);
        }));
        SHOTS.add(() -> wait = 6);
        SHOTS.add(() -> server.execute(() -> {
            int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, st -> st.is(com.smileprotocol.registry.ModItems.CAN.get()));
            com.smileprotocol.item.UseManager.onInput(new com.smileprotocol.network.Payloads.UseItem(
                    com.smileprotocol.network.Payloads.UseItem.THROW, slot, sp.getYRot(), sp.getXRot()), sp);
            log((com.smileprotocol.interact.SPInventoryOps.find(sp, st -> st.is(com.smileprotocol.registry.ModItems.CAN.get())) < 0
                    ? "OK" : "FALLO") + " lata lanzada");
        }));
        SHOTS.add(() -> wait = 40);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(30),
                    ie -> ie.getItem().is(com.smileprotocol.registry.ModItems.CAN.get())).size() > 0;
            log((ok ? "OK" : "FALLO") + " la lata cae y se puede recoger");
            com.smileprotocol.downed.DownedManager.down(sp, "prueba");
            use(sp, com.smileprotocol.registry.ModItems.ADRENALINE.get(), com.smileprotocol.network.Payloads.UseItem.START);
        }));
        SHOTS.add(() -> wait = 95);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = !com.smileprotocol.downed.DownedManager.isDowned(sp) && sp.getHealth() <= sp.getMaxHealth() * 0.25f;
            log((ok ? "OK" : "FALLO") + " jeringa: se levanta solo con vida " + sp.getHealth());
            sp.setHealth(sp.getMaxHealth());
        }));
        SHOTS.add(() -> wait = 30);
        // 9. Fase 12: levantarse tras la reanimacion, muerte instantanea en primera persona y muerte final
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.clear(sp);
            com.smileprotocol.sequence.SequenceManager.clear(sp);
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(40))) m.discard();
            com.smileprotocol.downed.DownedManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            com.smileprotocol.downed.DownedManager.down(sp, "prueba");
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.downed.DownedManager.revive(sp, null);
            boolean ok = !com.smileprotocol.downed.DownedManager.isDowned(sp)
                    && com.smileprotocol.motion.MotionManager.action(sp) == com.smileprotocol.motion.Action.GETUP
                    && Math.abs(sp.getHealth() - sp.getMaxHealth() * 0.3f) < 1.01f
                    && com.smileprotocol.downed.DownedManager.isProtected(sp);
            log((ok ? "OK" : "FALLO") + " reanimacion: vida " + sp.getHealth() + "/" + sp.getMaxHealth() + ", protegido");
        }));
        SHOTS.add(() -> wait = 10);
        SHOTS.add(() -> grab(mc, "20_levantandose"));
        SHOTS.add(() -> server.execute(() -> {
            // derribado y protegido: no se puede rematar
            com.smileprotocol.downed.DownedManager.down(sp, "prueba");
            log((com.smileprotocol.downed.DownedManager.canExecute(sp) ? "FALLO" : "OK") + " proteccion contra remates");
            com.smileprotocol.downed.DownedManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            sp.hurt(sp.damageSources().fall(), 1000f);
            log((com.smileprotocol.downed.DownedManager.isDowned(sp) && sp.isAlive() ? "OK" : "FALLO") + " dano letal = derribo");
            com.smileprotocol.downed.DownedManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            killer[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_KILL.get(),
                    BlockPos.containing(cam.add(0, 0, 2)), level.random);
            if (killer[0] != null) {
                killer[0].setNoAi(true);
                boolean started = com.smileprotocol.sequence.SequenceManager.start(killer[0], sp,
                        com.smileprotocol.sequence.SequenceKind.SMILER_KILL);
                log((started ? "OK" : "FALLO") + " muerte instantanea empieza");
            }
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> grab(mc, "21_muerte_instantanea"));
        SHOTS.add(() -> server.execute(() -> log((sp.hurt(sp.damageSources().fall(), 1000f) ? "FALLO" : "OK")
                + " invulnerable durante la secuencia")));
        SHOTS.add(() -> wait = 50);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = com.smileprotocol.downed.DeathManager.isDead(sp) && sp.isSpectator() && sp.isAlive();
            log((ok ? "OK" : "FALLO") + " muerte final: espectador del grupo");
        }));
        SHOTS.add(() -> wait = 5);
        SHOTS.add(() -> grab(mc, "22_muerto"));
    }

    // Grabacion de animaciones -------------------------------------------------------------------

    /** Camara fija de la grabacion (la lee CameraMixin). */
    public static volatile Vec3 TEST_CAM_POS;
    public static volatile float TEST_CAM_YAW, TEST_CAM_PITCH;

    /** Seguir a una entidad: -1 nadie, -2 el jugador local, otro numero el id del mob. */
    public static volatile int TEST_FOLLOW_ID = -1;
    public static volatile Vec3 TEST_FOLLOW_OFFSET = Vec3.ZERO;
    public static volatile double TEST_FOLLOW_LOOK_Y;

    public static net.minecraft.world.entity.Entity followed() {
        Minecraft mc = Minecraft.getInstance();
        if (TEST_FOLLOW_ID == -2) return mc.player;
        if (TEST_FOLLOW_ID < 0 || mc.level == null) return null;
        return mc.level.getEntity(TEST_FOLLOW_ID);
    }

    private static void follow(int id, Vec3 offset, double lookY) {
        TEST_FOLLOW_OFFSET = offset;
        TEST_FOLLOW_LOOK_Y = lookY;
        TEST_FOLLOW_ID = id;
        TEST_CAM_POS = Vec3.ZERO;
    }

    public static boolean recording() {
        return TEST_CAM_POS != null;
    }

    /**
     * Estudio flotante encima de la planta, con luz y una pared de fondo. La camara es un soporte
     * de armaduras invisible: el jugador se ve en tercera persona con sus poses reales (PlayerAnimator)
     * y los mobs con sus animaciones de GeckoLib.
     */
    private static void planAnimations(Minecraft mc, MinecraftServer server, ServerPlayer sp, ServerLevel level, Vec3 e) {
        BlockPos base = BlockPos.containing(e).offset(0, 30, 0);
        Vec3 P = Vec3.atBottomCenterOf(base.above());
        SHOTS.add(() -> server.execute(() -> {
            net.minecraft.world.level.block.state.BlockState floor = com.smileprotocol.registry.ModBlocks.building("lab_tile_floor").defaultBlockState();
            net.minecraft.world.level.block.state.BlockState wall = com.smileprotocol.registry.ModBlocks.building("lab_plaster").defaultBlockState();
            net.minecraft.world.level.block.state.BlockState low = com.smileprotocol.registry.ModBlocks.building("lab_wall_tiles").defaultBlockState();
            net.minecraft.world.level.block.state.BlockState light = net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -5; dz <= 8; dz++) level.setBlockAndUpdate(base.offset(dx, 0, dz), floor);
                for (int dy = 1; dy <= 6; dy++) level.setBlockAndUpdate(base.offset(dx, dy, -5), dy <= 2 ? low : wall);
            }
            for (int dx = -8; dx <= 8; dx += 4) {
                for (int dz = -3; dz <= 7; dz += 5) level.setBlockAndUpdate(base.offset(dx, 4, dz), light);
            }
            com.smileprotocol.motion.Flashlights.set(sp, false);
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class, new net.minecraft.world.phys.AABB(P, P).inflate(60))) m.discard();
            SPSessionManager.teleport(sp, level, P, -90f, 0f);
        }));
        SHOTS.add(() -> wait = 20);
        SHOTS.add(() -> {
            mc.options.hideGui = true;
            com.smileprotocol.config.SPConfig.get().force_first_person = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        Vec3 chest = P.add(0, 0.95, 0);
        Vec3 side = P.add(0, 1.0, 5.2);
        Vec3 threeQ = P.add(2.2, 1.1, 3.4);
        var o = mc.options;

        // Jugador ---------------------------------------------------------------------------------
        Vec3 fQ = new Vec3(1.5, 1.3, 2.5), fSide = new Vec3(0, 1.05, 3.3);
        SHOTS.add(() -> follow(-2, fQ, 0.95));
        scene(server, level, sp, null, chest, P, -90f, null);
        record(mc, "jugador_quieto", 40, null);

        SHOTS.add(() -> follow(-2, fSide, 0.9));
        scene(server, level, sp, null, chest, P.add(-4.5, 0, 0), -90f, null);
        record(mc, "jugador_andar", 45, k -> o.keyUp.setDown(true));
        SHOTS.add(() -> o.keyUp.setDown(false));

        scene(server, level, sp, null, chest, P.add(-5, 0, 0), -90f, null);
        record(mc, "jugador_correr", 30, k -> {
            o.keyUp.setDown(true);
            o.keySprint.setDown(true);
            if (mc.player != null) mc.player.setSprinting(true);
        });
        SHOTS.add(() -> { o.keyUp.setDown(false); o.keySprint.setDown(false); });

        scene(server, level, sp, null, chest, P.add(-3.5, 0, 0), -90f, null);
        record(mc, "jugador_agachado", 45, k -> { o.keyUp.setDown(true); o.keyShift.setDown(true); });
        SHOTS.add(() -> { o.keyUp.setDown(false); o.keyShift.setDown(false); });

        // Conducto de un bloque de alto: al llegar se tumba a gatear (vanilla) y lo atraviesa.
        // El lado que da a la camara es de cristal para verlo dentro.
        BlockPos duct = base.above().offset(1, 0, 0);
        SHOTS.add(() -> server.execute(() -> {
            net.minecraft.world.level.block.state.BlockState wall = com.smileprotocol.registry.ModBlocks.building("lab_plaster").defaultBlockState();
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = -2; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        boolean hole = dz == 0 && dy == 0;
                        boolean glass = dz == 1 && dy <= 1;
                        level.setBlockAndUpdate(duct.offset(dx, dy, dz), hole ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                                : glass ? net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState() : wall);
                    }
                }
            }
        }));
        scene(server, level, sp, P.add(1.0, 1.3, 4.6), P.add(1.5, 0.5, 0), P.add(-2.5, 0, 0), -90f, null);
        record(mc, "jugador_conducto", 80, k -> o.keyUp.setDown(true));
        SHOTS.add(() -> {
            o.keyUp.setDown(false);
            follow(-2, fSide, 0.9);
            server.execute(() -> {
                for (int dx = 0; dx < 3; dx++)
                    for (int dz = -2; dz <= 1; dz++)
                        for (int dy = 0; dy <= 2; dy++)
                            level.setBlockAndUpdate(duct.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            });
        });

        scene(server, level, sp, null, chest, P.add(-3.5, 0, 0), -90f, () -> sp.setHealth(4f));
        record(mc, "jugador_poca_vida", 60, k -> o.keyUp.setDown(true));
        SHOTS.add(() -> { o.keyUp.setDown(false); server.execute(() -> sp.setHealth(sp.getMaxHealth())); });

        scene(server, level, sp, null, chest, P.add(-6, 0, 0), -90f, null);
        record(mc, "jugador_dive", 40, k -> {
            o.keyUp.setDown(k < 34);
            o.keySprint.setDown(k < 12);
            if (k < 12 && mc.player != null) mc.player.setSprinting(true);
            o.keyShift.setDown(k == 12);
        });
        SHOTS.add(() -> { o.keyUp.setDown(false); o.keySprint.setDown(false); o.keyShift.setDown(false); });

        SHOTS.add(() -> server.execute(() -> level.setBlockAndUpdate(BlockPos.containing(P), com.smileprotocol.registry.ModBlocks.building("lab_crate").defaultBlockState())));
        scene(server, level, sp, null, chest, P.add(-3, 0, 0), -90f, null);
        boolean[] jumped = {false};
        record(mc, "jugador_vault", 36, k -> {
            if (k == 0) jumped[0] = false;
            o.keyUp.setDown(k < 30);
            boolean near = mc.player != null && mc.player.getX() > P.x - 1.7;
            o.keyJump.setDown(near && !jumped[0]);
            if (near && !jumped[0] && k > 0) jumped[0] = mc.player.getX() > P.x - 1.5;
        });
        SHOTS.add(() -> {
            o.keyUp.setDown(false);
            o.keyJump.setDown(false);
            server.execute(() -> level.setBlockAndUpdate(BlockPos.containing(P), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
        });

        scene(server, level, sp, null, chest, P.add(-3.5, 0, 0), -90f, null);
        record(mc, "jugador_mirar_atras", 45, k -> {
            o.keyUp.setDown(true);
            if (k == 8) ClientMotion.TEST_KEYS.add(com.smileprotocol.config.SPConfig.get().key_look_back);
        });
        SHOTS.add(() -> { o.keyUp.setDown(false); ClientMotion.TEST_KEYS.clear(); });

        SHOTS.add(() -> follow(-2, fQ, 0.95));
        scene(server, level, sp, null, chest, P, -90f, null);
        record(mc, "jugador_asomarse", 56, k -> {
            ClientMotion.TEST_KEYS.clear();
            if (k >= 4 && k < 28) ClientMotion.TEST_KEYS.add(com.smileprotocol.config.SPConfig.get().key_lean_left);
            if (k >= 32 && k < 54) ClientMotion.TEST_KEYS.add(com.smileprotocol.config.SPConfig.get().key_lean_right);
        });
        SHOTS.add(ClientMotion.TEST_KEYS::clear);

        String[] names = {"valvula", "fusible", "puerta", "recoger", "tarjeta"};
        int[] kinds = {com.smileprotocol.client.anim.PoseCalculator.INTERACT_VALVE, com.smileprotocol.client.anim.PoseCalculator.INTERACT_FUSE,
                com.smileprotocol.client.anim.PoseCalculator.INTERACT_DOOR, com.smileprotocol.client.anim.PoseCalculator.INTERACT_PICKUP,
                com.smileprotocol.client.anim.PoseCalculator.INTERACT_CARD};
        for (int i = 0; i < names.length; i++) {
            int kind = kinds[i];
            scene(server, level, sp, null, chest, P, -90f, () -> com.smileprotocol.motion.MotionManager.setAction(sp,
                    com.smileprotocol.motion.Action.INTERACT, kind, false));
            record(mc, "jugador_interactuar_" + names[i], 40, null);
            SHOTS.add(() -> server.execute(() -> com.smileprotocol.motion.MotionManager.clearAction(sp, com.smileprotocol.motion.Action.INTERACT)));
        }

        SHOTS.add(() -> follow(-2, new Vec3(1.4, 1.5, 3.0), 0.3));
        scene(server, level, sp, null, chest, P.add(-2.5, 0, 0), -90f, () -> com.smileprotocol.downed.DownedManager.down(sp, "grabacion"));
        record(mc, "jugador_derribado", 70, k -> o.keyUp.setDown(true));
        SHOTS.add(() -> {
            o.keyUp.setDown(false);
            server.execute(() -> com.smileprotocol.downed.DownedManager.clear(sp));
        });

        // Agarre con el mob de pruebas (jugador y mob a la vez)
        com.smileprotocol.entity.SmilerTest[] tester = {null};
        scene(server, level, sp, P.add(1.6, 1.5, 4.0), P.add(1.4, 1.1, 0), P, -90f, () -> {
            tester[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_TEST.get(),
                    BlockPos.containing(P.add(4, 0, 0)), level.random);
            if (tester[0] != null) tester[0].moveTo(P.x + 4, P.y, P.z, 90f, 0f);
        });
        record(mc, "agarre_qte", 55, null);
        // de lado y cerca: las manos tienen que sujetar al jugador por los hombros
        SHOTS.add(() -> placeCamera(P.add(0.6, 1.1, 3.2), P.add(0.6, 1.1, 0)));
        record(mc, "agarre_lado", 20, k -> { if (k % 5 == 0 && tester[0] != null) probe("GRAB t=" + k, tester[0].getId(), sp.getId()); });
        SHOTS.add(() -> { if (tester[0] != null) follow(tester[0].getId(), new Vec3(1.8, 1.7, 3.2), 1.2); });
        record(mc, "smiler_aturdido", 40, k -> {
            if (k == 0) server.execute(() -> {
                com.smileprotocol.qte.QTEManager.clear(sp);
                com.smileprotocol.downed.DownedManager.clear(sp);
                if (tester[0] != null) tester[0].debugForce(com.smileprotocol.entity.SmilerTest.State.STUNNED);
                SPSessionManager.teleport(sp, level, P.add(-3, 0, 2), -90f, 0f);
            });
        });
        SHOTS.add(() -> server.execute(() -> { if (tester[0] != null) tester[0].discard(); }));

        // Berserker -----------------------------------------------------------------------------------
        com.smileprotocol.entity.Berserker[] bz = {null};
        Vec3 bChest = P.add(0, 1.3, 0);
        Vec3 bQ = P.add(3.0, 1.6, 5.0);
        Vec3 bSide = P.add(0, 1.4, 7.5);
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, P.add(-9, 0, 7), -90f, 0f);
            bz[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.BERSERKER.get(),
                    BlockPos.containing(P), level.random);
        }));
        SHOTS.add(() -> wait = 6);
        Vec3 fbQ = new Vec3(2.0, 1.8, 3.4), fbSide = new Vec3(0, 1.6, 4.4);
        SHOTS.add(() -> { if (bz[0] != null) follow(bz[0].getId(), fbQ, 1.25); });
        mobScene(server, level, bz, null, bChest, P, com.smileprotocol.entity.Berserker.State.PATROL, false, null, 0);
        record(mc, "berserker_quieto", 64, null);
        SHOTS.add(() -> { if (bz[0] != null) follow(bz[0].getId(), fbSide, 1.2); });
        mobScene(server, level, bz, null, bChest, P.add(-5, 0, 0), com.smileprotocol.entity.Berserker.State.PATROL, false, P.add(6, 0, 0), 0.55);
        record(mc, "berserker_andar", 70, null);
        mobScene(server, level, bz, null, bChest, P.add(-6, 0, 0), com.smileprotocol.entity.Berserker.State.INVESTIGATE, true, P.add(7, 0, 0), 1.35);
        record(mc, "berserker_correr", 34, null);
        SHOTS.add(() -> { if (bz[0] != null) follow(bz[0].getId(), fbQ, 1.25); });
        mobScene(server, level, bz, null, bChest, P, com.smileprotocol.entity.Berserker.State.STRIKE, false, null, 0);
        record(mc, "berserker_golpe", 64, k -> {
            if (k == 22) server.execute(() -> { if (bz[0] != null) bz[0].debugForce(com.smileprotocol.entity.Berserker.State.RECOVER, false, null, 0); });
        });
        mobScene(server, level, bz, null, bChest, P, com.smileprotocol.entity.Berserker.State.SEARCH, false, null, 0);
        record(mc, "berserker_escuchar", 80, null);
        SHOTS.add(() -> server.execute(() -> { if (bz[0] != null) bz[0].discard(); }));

        // Fase 12: levantarse, reanimar, ayudar, remates y muerte instantanea ------------------------
        // (en la grabacion las secuencias no matan: el jugador tiene que seguir para las demas)
        SHOTS.add(() -> com.smileprotocol.sequence.SequenceManager.TEST_NO_KILL = true);
        SHOTS.add(() -> follow(-2, new Vec3(1.6, 1.3, 2.6), 0.6));
        scene(server, level, sp, null, chest, P, -90f, () -> com.smileprotocol.downed.DownedManager.down(sp, "grabacion"));
        SHOTS.add(() -> wait = 20);
        record(mc, "jugador_levantarse_ayudado", 40, k -> {
            if (k == 2) server.execute(() -> com.smileprotocol.downed.DownedManager.revive(sp, sp));
        });
        scene(server, level, sp, null, chest, P, -90f, () -> com.smileprotocol.downed.DownedManager.down(sp, "grabacion"));
        SHOTS.add(() -> wait = 20);
        record(mc, "jugador_levantarse_solo", 40, k -> {
            if (k == 2) server.execute(() -> com.smileprotocol.downed.DownedManager.revive(sp, null));
        });
        SHOTS.add(() -> follow(-2, new Vec3(1.8, 1.4, 2.8), 0.7));
        scene(server, level, sp, null, chest, P, -90f, () -> com.smileprotocol.motion.MotionManager.setAction(sp,
                com.smileprotocol.motion.Action.INTERACT, com.smileprotocol.client.anim.PoseCalculator.INTERACT_REVIVE, false));
        record(mc, "jugador_reanimar", 70, null);
        SHOTS.add(() -> follow(-2, new Vec3(-0.6, 1.0, 3.6), 0.55));
        record(mc, "jugador_reanimar_lado", 70, null);
        SHOTS.add(() -> follow(-2, new Vec3(1.8, 1.4, 2.8), 0.7));
        scene(server, level, sp, null, chest, P, -90f, () -> com.smileprotocol.motion.MotionManager.setAction(sp,
                com.smileprotocol.motion.Action.INTERACT, com.smileprotocol.client.anim.PoseCalculator.INTERACT_PULL, false));
        record(mc, "jugador_ayudar_agarre", 40, null);
        SHOTS.add(() -> server.execute(() -> com.smileprotocol.motion.MotionManager.setAction(sp, com.smileprotocol.motion.Action.NONE, 0, false)));

        // Remates y muerte instantanea: el mob en P mirando al este; la victima queda delante
        com.smileprotocol.entity.SPMonster[] ex = {null};
        String[] seqNames = {"remate_berserker", "remate_smiler", "muerte_instantanea"};
        com.smileprotocol.sequence.SequenceKind[] seqKinds = {com.smileprotocol.sequence.SequenceKind.BERSERKER_EXECUTION,
                com.smileprotocol.sequence.SequenceKind.SMILER_EXECUTION, com.smileprotocol.sequence.SequenceKind.SMILER_KILL};
        for (int i = 0; i < seqKinds.length; i++) {
            com.smileprotocol.sequence.SequenceKind kind = seqKinds[i];
            int idx = i;
            SHOTS.add(() -> server.execute(() -> {
                com.smileprotocol.downed.DownedManager.clear(sp);
                com.smileprotocol.motion.MotionManager.setAction(sp, com.smileprotocol.motion.Action.NONE, 0, false);
                sp.setHealth(sp.getMaxHealth());
                SPSessionManager.teleport(sp, level, P.add(1.8, 0, 0), 90f, 0f);
                var type = idx == 0 ? com.smileprotocol.registry.ModEntities.BERSERKER.get()
                        : idx == 1 ? com.smileprotocol.registry.ModEntities.SMILER_TEST.get() : com.smileprotocol.registry.ModEntities.SMILER_KILL.get();
                ex[0] = com.smileprotocol.entity.MobManager.spawn(level, type, BlockPos.containing(P), level.random);
                if (ex[0] != null) {
                    ex[0].setNoAi(true);
                    ex[0].moveTo(P.x, P.y, P.z, -90f, 0f);
                    ex[0].setYBodyRot(-90f);
                    ex[0].setYHeadRot(-90f);
                }
                if (kind.execution) com.smileprotocol.downed.DownedManager.down(sp, "grabacion");
            }));
            SHOTS.add(() -> wait = 12);
            SHOTS.add(() -> placeCamera(P.add(1.2, 2.0, 6.4), P.add(1.2, 1.0, 0)));
            record(mc, seqNames[i], kind.ticks + 8, k -> {
                if (k == 0) server.execute(() -> { if (ex[0] != null) com.smileprotocol.sequence.SequenceManager.start(ex[0], sp, kind); });
                // donde quedan las manos y la cabeza del mob respecto a la victima (para ajustar distancias)
                if (k % 4 == 0 && ex[0] != null) probe(kind.name() + " t=" + k, ex[0].getId(), sp.getId());
            });
            // de frente / en diagonal, la misma secuencia otra vez
            SHOTS.add(() -> placeCamera(P.add(5.0, 2.0, 3.6), P.add(1.0, 0.9, 0)));
            record(mc, seqNames[i] + "_diagonal", kind.ticks + 8, k -> {
                if (k == 0) server.execute(() -> {
                    if (kind.execution) com.smileprotocol.downed.DownedManager.down(sp, "grabacion");
                    if (ex[0] != null) com.smileprotocol.sequence.SequenceManager.start(ex[0], sp, kind);
                });
            });
            SHOTS.add(() -> server.execute(() -> { if (ex[0] != null) ex[0].discard(); }));
        }
        // La muerte instantanea vista por la victima (camara y HUD de la secuencia)
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.downed.DownedManager.clear(sp);
            com.smileprotocol.motion.MotionManager.setAction(sp, com.smileprotocol.motion.Action.NONE, 0, false);
            SPSessionManager.teleport(sp, level, P.add(1.8, 0, 0), 90f, 0f);
            ex[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_KILL.get(),
                    BlockPos.containing(P), level.random);
            if (ex[0] != null) {
                ex[0].setNoAi(true);
                ex[0].moveTo(P.x, P.y, P.z, -90f, 0f);
            }
        }));
        SHOTS.add(() -> {
            TEST_CAM_POS = null;
            TEST_FOLLOW_ID = -1;
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });
        SHOTS.add(() -> wait = 10);
        record(mc, "muerte_instantanea_pov", 72, k -> {
            if (k == 0) server.execute(() -> {
                if (ex[0] != null) com.smileprotocol.sequence.SequenceManager.start(ex[0], sp, com.smileprotocol.sequence.SequenceKind.SMILER_KILL);
            });
        });
        SHOTS.add(() -> server.execute(() -> { if (ex[0] != null) ex[0].discard(); }));
        SHOTS.add(() -> com.smileprotocol.sequence.SequenceManager.TEST_NO_KILL = false);

        // Fase 13: usar consumibles, apuntar y lanzar ------------------------------------------------
        SHOTS.add(() -> {
            mc.options.hideGui = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        SHOTS.add(() -> follow(-2, new Vec3(1.8, 1.4, 2.8), 0.8));
        String[] useNames = {"jugador_botiquin", "jugador_bateria", "jugador_bengala"};
        net.minecraft.world.item.Item[] useItems = {com.smileprotocol.registry.ModItems.MEDKIT.get(),
                com.smileprotocol.registry.ModItems.BATTERY.get(), com.smileprotocol.registry.ModItems.FLARE.get()};
        for (int i = 0; i < useNames.length; i++) {
            net.minecraft.world.item.Item it = useItems[i];
            scene(server, level, sp, null, chest, P, -90f, () -> {
                sp.setHealth(6f);
                com.smileprotocol.motion.Flashlights.recharge(sp, -0.6f);
                use(sp, it, com.smileprotocol.network.Payloads.UseItem.START);
            });
            record(mc, useNames[i], 50, null);
        }
        record(mc, "bengala_suelo", 40, null);
        SHOTS.add(() -> follow(-2, new Vec3(-1.6, 1.4, 2.6), 0.9));
        scene(server, level, sp, null, chest, P.add(-3, 0, 0), -90f, () ->
                use(sp, com.smileprotocol.registry.ModItems.BOTTLE.get(), com.smileprotocol.network.Payloads.UseItem.AIM));
        record(mc, "jugador_apuntar_lanzar", 45, k -> {
            if (k == 25) server.execute(() -> {
                int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, st -> st.is(com.smileprotocol.registry.ModItems.BOTTLE.get()));
                com.smileprotocol.item.UseManager.onInput(new com.smileprotocol.network.Payloads.UseItem(
                        com.smileprotocol.network.Payloads.UseItem.THROW, slot, sp.getYRot(), -15f), sp);
            });
        });
        SHOTS.add(() -> follow(-2, new Vec3(1.6, 1.3, 2.6), 0.4));
        scene(server, level, sp, null, chest, P, -90f, () -> {
            com.smileprotocol.downed.DownedManager.down(sp, "grabacion");
            use(sp, com.smileprotocol.registry.ModItems.ADRENALINE.get(), com.smileprotocol.network.Payloads.UseItem.START);
        });
        record(mc, "jugador_jeringa_derribado", 110, null);
        SHOTS.add(() -> {
            TEST_CAM_POS = null;
            TEST_FOLLOW_ID = -1;
            mc.options.hideGui = false;
        });
    }

    /** Da el objeto, lo pone en la mano y empieza a usarlo (o a apuntarlo). */
    private static void use(ServerPlayer sp, net.minecraft.world.item.Item item, int kind) {
        com.smileprotocol.interact.SPInventoryOps.give(sp, new net.minecraft.world.item.ItemStack(item));
        int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, st -> st.is(item));
        if (slot < 0) {
            log("FALLO: no cabe " + item);
            return;
        }
        com.smileprotocol.player.SPPlayerData.of(sp).getSPInventory().setSelected(slot);
        SPSessionManager.sync(sp);
        com.smileprotocol.item.UseManager.onInput(new com.smileprotocol.network.Payloads.UseItem(kind, slot, sp.getYRot(), sp.getXRot()), sp);
    }

    /**
     * Registra donde estan las manos y la cabeza del mob (huesos de GeckoLib en el mundo) respecto
     * al mob y a la victima: distancia hacia delante y altura, en bloques.
     */
    private static void probe(String label, int mobId, int victimId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.entity.Entity e = mc.level.getEntity(mobId);
        net.minecraft.world.entity.Entity v = mc.level.getEntity(victimId);
        if (!(e instanceof com.smileprotocol.entity.SPMonster m) || v == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(m) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> r)) return;
        float yaw = m.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        double fx = -net.minecraft.util.Mth.sin(yaw), fz = net.minecraft.util.Mth.cos(yaw);
        StringBuilder sb = new StringBuilder("HUESOS " + label + String.format(" victima=%.2f/%.2f", 
                (v.getX() - m.getX()) * fx + (v.getZ() - m.getZ()) * fz, v.getY() - m.getY()));
        for (String b : new String[]{"hand_r", "hand_l", "head"}) {
            r.getGeoModel().getBone(b).ifPresent(bone -> {
                bone.setTrackingMatrices(true);
                org.joml.Vector3d w = bone.getWorldPosition();
                double dx = w.x - m.getX(), dz = w.z - m.getZ();
                sb.append(String.format(" %s=%.2f/%.2f", b, dx * fx + dz * fz, w.y - m.getY()));
            });
        }
        log(sb.toString());
    }

    /** Coloca la camara (mirando a {@code look}) y al jugador; {@code extra} corre en el servidor. */
    private static void scene(MinecraftServer server, ServerLevel level, ServerPlayer sp, Vec3 cam, Vec3 look, Vec3 at, float yaw,
                              Runnable extra) {
        SHOTS.add(() -> {
            placeCamera(cam, look);
            server.execute(() -> {
                SPSessionManager.teleport(sp, level, at, yaw, 0f);
                if (extra != null) extra.run();
            });
        });
        SHOTS.add(() -> wait = 8);
    }

    private static void mobScene(MinecraftServer server, ServerLevel level, com.smileprotocol.entity.Berserker[] bz, Vec3 cam, Vec3 look,
                                 Vec3 at, com.smileprotocol.entity.Berserker.State st, boolean fast, Vec3 to, double speed) {
        SHOTS.add(() -> placeCamera(cam, look));
        SHOTS.add(() -> server.execute(() -> {
            if (bz[0] == null) return;
            bz[0].moveTo(at.x, at.y, at.z, -90f, 0f);
            bz[0].setYBodyRot(-90f);
            bz[0].setYHeadRot(-90f);
            bz[0].debugForce(st, fast, to, speed);
        }));
        SHOTS.add(() -> wait = 10);
    }

    private static void placeCamera(Vec3 cam, Vec3 look) {
        if (cam == null) return; // la escena usa camara que sigue
        TEST_FOLLOW_ID = -1;
        double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
        TEST_CAM_YAW = (float) Math.toDegrees(Math.atan2(-dx, dz));
        TEST_CAM_PITCH = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        TEST_CAM_POS = cam;
    }

    private static void record(Minecraft mc, String name, int frames, java.util.function.IntConsumer perTick) {
        for (int i = 0; i < frames; i++) {
            int k = i;
            SHOTS.add(() -> {
                if (perTick != null) perTick.accept(k);
                Screenshot.grab(mc.gameDirectory, String.format("anim-%s-%03d.png", name, k), mc.getMainRenderTarget(), c -> {});
            });
        }
        SHOTS.add(() -> log("grabada " + name + " (" + frames + " fotogramas)"));
    }

    private static void shot(Minecraft mc, MinecraftServer server, String name, Vec3 pos, float yaw, float pitch, int delay) {
        SHOTS.add(() -> server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.getLevel(SPDimension.SP_DIM);
            SPSessionManager.teleport(sp, level, pos, yaw, pitch);
        }));
        SHOTS.add(() -> wait = delay);
        SHOTS.add(() -> grab(mc, name));
    }

    /** Busca la entidad de bloque mas cercana a la entrada que cumpla la condicion y se pone delante. */
    private static void lookAt(Minecraft mc, MinecraftServer server, ServerLevel level, ZonePlacement z, String name,
                               Predicate<BlockEntity> test) {
        SHOTS.add(() -> server.execute(() -> {
            BlockEntity be = find(level, z, test);
            if (be == null) {
                log("no encontrado para " + name);
                return;
            }
            BlockState st = be.getBlockState();
            Direction f = st.hasProperty(HideBlock.FACING) ? st.getValue(HideBlock.FACING) : Direction.SOUTH;
            BlockPos p = be.getBlockPos();
            Vec3 eye = Vec3.atBottomCenterOf(p.relative(f, 3));
            float yaw = f.getOpposite().toYRot();
            if (be instanceof HideBlockEntity hb && hb.modelName().equals("gurney")) {
                // la camilla mide dos bloques: se mira desde un lado, a la altura del centro
                Direction side = f.getClockWise();
                if (!level.getBlockState(p.relative(side)).isAir()) side = side.getOpposite();
                eye = Vec3.atBottomCenterOf(p.relative(side, 2)).add(Vec3.atLowerCornerOf(f.getNormal()).scale(0.5));
                yaw = side.getOpposite().toYRot();
            }
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            SPSessionManager.teleport(sp, level, eye, yaw, 12f);
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> grab(mc, name));
    }

    private static BlockEntity find(ServerLevel level, ZonePlacement z, Predicate<BlockEntity> test) {
        BlockEntity[] best = {null};
        double[] bd = {Double.MAX_VALUE};
        Vec3 e = z.entry();
        CircuitManager.forEachBlockEntity(level, z.box(), be -> {
            if (!test.test(be)) return;
            double d = Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(e);
            if (d < bd[0]) {
                bd[0] = d;
                best[0] = be;
            }
        });
        return best[0];
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("captura " + name));
    }

    /** Acepta las pantallas de confirmacion al abrir el mundo (copia de seguridad, avisos...). */
    private static void accept(Object screen) {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(screen);
                    if (v instanceof it.unimi.dsi.fastutil.booleans.BooleanConsumer bc) {
                        log("acepto " + c.getSimpleName());
                        bc.accept(true);
                        return;
                    }
                    if (v != null && f.getType().isInterface()) {
                        for (java.lang.reflect.Method m : f.getType().getMethods()) {
                            if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == boolean.class
                                    && m.getParameterTypes()[1] == boolean.class) {
                                log("continuo " + c.getSimpleName());
                                m.setAccessible(true);
                                m.invoke(v, false, false);
                                return;
                            }
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // siguiente campo
                }
            }
        }
    }

    private static void log(String s) {
        SmileProtocol.LOGGER.info("CLIENTTEST {}", s);
    }
}
