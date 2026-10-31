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
    private static BlockPos LOBBY = BlockPos.ZERO;

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
                // El lobby (README 5.2): se construye en el cielo del mundo de prueba y se mira
                log("en el mundo, construyendo el lobby");
                com.smileprotocol.config.SPConfig.get().scare_warning = false;
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    BlockPos spawn = BlockPos.containing(sp.getX(), 180, sp.getZ());
                    com.smileprotocol.lobby.LobbyBuilder.build(server.overworld(), spawn);
                    SPSessionManager.teleport(sp, server.overworld(), Vec3.atBottomCenterOf(spawn), 180f, 0f);
                    LOBBY = spawn;
                });
                wait = 80;
                step++;
            }
            case 1 -> {
                Screenshot.grab(mc.gameDirectory, "00_lobby.png", mc.getMainRenderTarget(), c -> log("captura 00_lobby"));
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    var lv = com.smileprotocol.lobby.LobbyData.get(server);
                    log((lv.inRegion(sp.position()) ? "OK" : "FALLO") + " lobby: el punto de aparicion esta dentro de la region");
                    // hacia la recepcion y el logotipo
                    SPSessionManager.teleport(sp, server.overworld(), Vec3.atBottomCenterOf(LOBBY).add(-6, 0, -6), 100f, 5f);
                });
                wait = 30;
                step++;
            }
            case 2 -> {
                Screenshot.grab(mc.gameDirectory, "00_lobby_recepcion.png", mc.getMainRenderTarget(), c -> log("captura 00_lobby_recepcion"));
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    // los ascensores, desde el centro
                    SPSessionManager.teleport(sp, server.overworld(), Vec3.atBottomCenterOf(LOBBY).add(0, 0, -30), 180f, -5f);
                });
                wait = 30;
                step++;
            }
            case 3 -> {
                Screenshot.grab(mc.gameDirectory, "00_lobby_ascensores.png", mc.getMainRenderTarget(), c -> log("captura 00_lobby_ascensores"));
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    var lv = com.smileprotocol.lobby.LobbyData.get(server);
                    var tut = lv.getElevator("tutorial");
                    boolean ok = tut != null && tut.type() == com.smileprotocol.lobby.ElevatorType.TUTORIAL
                            && tut.doors().size() == 2 && tut.button() != null && tut.board() != null;
                    log((ok ? "OK" : "FALLO") + " lobby: ascensor TUTORIAL registrado"
                            + (tut == null ? "" : " (puertas=" + tut.doors().size() + " boton=" + (tut.button() != null)
                            + " pantalla=" + (tut.board() != null) + ")"));
                    // Modo «closed»: todo cerrado menos el tutorial
                    var before = lv.getMode();
                    lv.setMode(com.smileprotocol.lobby.LobbyMode.CLOSED);
                    boolean closedOk = com.smileprotocol.lobby.LobbyManager.isOpen(server, "tutorial")
                            && !com.smileprotocol.lobby.LobbyManager.isOpen(server, "mini1")
                            && !com.smileprotocol.lobby.LobbyManager.isOpen(server, "mega");
                    lv.setMode(com.smileprotocol.lobby.LobbyMode.FREE);
                    boolean freeOk = com.smileprotocol.lobby.LobbyManager.isOpen(server, "tutorial")
                            && com.smileprotocol.lobby.LobbyManager.isOpen(server, "mini1");
                    lv.setMode(before);
                    log((closedOk && freeOk ? "OK" : "FALLO") + " modo closed: solo queda abierto el tutorial");
                    var board = com.smileprotocol.score.ScoreData.get(server).board();
                    boolean signs = board.size() == 10 && board.stream().allMatch(b ->
                            server.overworld().getBlockEntity(b) instanceof net.minecraft.world.level.block.entity.SignBlockEntity);
                    log((signs ? "OK" : "FALLO") + " lobby: tablon de records con " + board.size() + " carteles");
                    // Reglas: dentro de la cabina del ascensor 1 se eligen con F7 (aqui, el paquete directamente)
                    var mini = lv.getElevator("mini1");
                    if (mini != null) {
                        SPSessionManager.teleport(sp, server.overworld(), mini.cabinAABB().getCenter().with(net.minecraft.core.Direction.Axis.Y,
                                mini.cabin().minY()), 0f, 0f);
                        com.smileprotocol.lobby.LobbyManager.onSetRules(new com.smileprotocol.network.Payloads.SetRules("NIGHTMARE|weak_battery"), sp);
                        var got = com.smileprotocol.lobby.LobbyManager.rulesOf("mini1");
                        log((got.encode().equals("NIGHTMARE|weak_battery") ? "OK" : "FALLO") + " reglas elegidas en la cabina: " + got.encode());
                        com.smileprotocol.lobby.LobbyManager.onSetRules(new com.smileprotocol.network.Payloads.SetRules("NORMAL"), sp);
                    }
                    // la puerta del tutorial, en la pared oeste
                    SPSessionManager.teleport(sp, server.overworld(), Vec3.atBottomCenterOf(LOBBY).add(-13, 0, -33), 90f, 0f);
                });
                wait = 30;
                step++;
            }
            case 4 -> {
                Screenshot.grab(mc.gameDirectory, "00_lobby_tutorial.png", mc.getMainRenderTarget(), c -> log("captura 00_lobby_tutorial"));
                wait = 10;
                step++;
            }
            case 5 -> {
                log("en el mundo, bajando a una partida");
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    RunManager.StartResult r = RunManager.start(server, RunType.MINI, "test", List.of(sp), true);
                    log("partida: " + r.message().getString());
                });
                wait = 200;
                step++;
            }
            case 6 -> {
                plan(mc, server);
                step++;
            }
            default -> {
                int i = step - 7;
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
        // Smiley no se deja ver solo durante las pruebas (su aparicion se fuerza en la 8k)
        com.smileprotocol.config.SPConfig.get().smiley_cameos = false;
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
        SHOTS.add(() -> server.execute(() -> {
            // Fase 19: con voz se entrega un walkie; se guarda para no ocupar hueco hasta su prueba
            int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, com.smileprotocol.voice.WalkieManager::isWalkie);
            if (com.smileprotocol.voice.WalkieManager.enabled()) log((slot >= 0 ? "OK" : "FALLO") + " walkie en el equipo inicial");
            if (slot >= 0) com.smileprotocol.interact.SPInventoryOps.take(sp, slot);
        }));
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
        SHOTS.add(() -> log((ClientTension.level(1f) > 0.3f && ClientTension.rendered() && !ClientTension.failed() ? "OK" : "FALLO")
                + " shader de tension con el Berserker cerca (dibujado " + ClientTension.rendered() + ", error " + ClientTension.failed() + ", "
                + String.format(java.util.Locale.ROOT, "%.2f", ClientTension.level(1f)) + ")"));
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
        // 8c. Fase 15: recoger una tarjeta dispara la alarma (parpadeo, ruido, radio) y un susto
        SHOTS.add(() -> server.execute(() -> com.smileprotocol.mechanism.LabEvents.fire(level, sp.blockPosition(), sp,
                "item_picked", "smileprotocol:keycard_1")));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> server.execute(() -> log((com.smileprotocol.labevent.LabEventManager.running() > 0 ? "OK" : "FALLO")
                + " evento de laboratorio: alarma de la tarjeta (" + com.smileprotocol.labevent.LabEventManager.running() + " efectos)")));
        SHOTS.add(() -> grab(mc, "25_alarma"));
        SHOTS.add(() -> server.execute(() -> com.smileprotocol.labevent.LabActions.scare(java.util.List.of(sp),
                com.smileprotocol.labevent.LabActions.SCARE_FACE, 1f)));
        SHOTS.add(() -> wait = 3);
        SHOTS.add(() -> grab(mc, "26_susto"));
        SHOTS.add(() -> wait = 100);
        // 8d. Fase 17: retirar una rejilla del suelo y gatear por el conducto
        BlockPos[] grille = {null};
        Direction[] ductDir = {Direction.NORTH};
        SHOTS.add(() -> server.execute(() -> {
            BlockEntity ventBe = find(level, z, vb -> vb instanceof com.smileprotocol.blockentity.VentBlockEntity
                    && vb.getBlockState().getBlock() instanceof com.smileprotocol.block.VentGrilleBlock
                    && !vb.getBlockState().getValue(com.smileprotocol.block.VentGrilleBlock.SEALED));
            if (ventBe == null) {
                log("FALLO rejilla de ventilacion: no hay ninguna en la zona");
                return;
            }
            grille[0] = ventBe.getBlockPos();
            for (Direction vd : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(grille[0].below().relative(vd)).isAir()) ductDir[0] = vd;
            }
            Direction ventBack = ductDir[0].getOpposite();
            SPSessionManager.teleport(sp, level, Vec3.atBottomCenterOf(grille[0].above().relative(ventBack, 2)), ductDir[0].toYRot(), 45f);
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> grab(mc, "30_rejilla"));
        SHOTS.add(() -> server.execute(() -> {
            if (grille[0] == null) return;
            BlockState vst = level.getBlockState(grille[0]);
            if (vst.getBlock() instanceof com.smileprotocol.block.VentGrilleBlock vg) vg.complete(level, grille[0], vst, sp);
            boolean ventOk = level.getBlockState(grille[0]).getValue(com.smileprotocol.block.VentGrilleBlock.OPEN);
            log((ventOk ? "OK" : "FALLO") + " rejilla de ventilacion retirada");
        }));
        SHOTS.add(() -> wait = 10);
        SHOTS.add(() -> grab(mc, "31_rejilla_abierta"));
        SHOTS.add(() -> server.execute(() -> {
            if (grille[0] == null) return;
            SPSessionManager.teleport(sp, level, Vec3.atBottomCenterOf(grille[0].below().relative(ductDir[0])), ductDir[0].toYRot(), 0f);
        }));
        SHOTS.add(() -> wait = 30);
        SHOTS.add(() -> server.execute(() -> {
            if (grille[0] == null) return;
            boolean ventOk = sp.isVisuallyCrawling() && com.smileprotocol.motion.MotionManager.inDuct(sp);
            log((ventOk ? "OK" : "FALLO") + " gateando dentro del conducto (pose " + sp.getPose() + ")");
        }));
        SHOTS.add(() -> grab(mc, "32_conducto"));
        // 8e. Fase 18: la voz hace ruido (simulando un paquete del microfono) y Simple Voice Chat
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            com.smileprotocol.voice.VoiceManager.onVoice(sp.getUUID(), -14, false);
        }));
        SHOTS.add(() -> wait = 5);
        SHOTS.add(() -> server.execute(() -> {
            boolean heard = com.smileprotocol.noise.NoiseSystem.recent(level, sp.blockPosition(), 40).stream()
                    .anyMatch(n -> n.type() == com.smileprotocol.noise.NoiseType.VOICE && n.radius() > 15);
            log((heard ? "OK" : "FALLO") + " la voz hace ruido (grito)");
            log("INFO Simple Voice Chat " + (com.smileprotocol.voice.VoiceManager.available() ? "cargado (plugin activo)" : "no instalado"));
        }));
        // 8f. Fase 19: marcador de peligro y walkie
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 8f);
        }));
        SHOTS.add(() -> wait = 12);
        SHOTS.add(() -> server.execute(() -> com.smileprotocol.marker.MarkerManager.onInput(
                new com.smileprotocol.network.Payloads.PlaceMarker(com.smileprotocol.marker.MarkerManager.DANGER), sp)));
        SHOTS.add(() -> wait = 8);
        SHOTS.add(() -> log((ClientMarkers.markers().stream().anyMatch(m -> m.kind() == com.smileprotocol.marker.MarkerManager.DANGER)
                ? "OK" : "FALLO") + " marcador de peligro visible para el grupo (" + ClientMarkers.markers().size() + ")"));
        SHOTS.add(() -> grab(mc, "33_marcador"));
        SHOTS.add(() -> server.execute(() -> {
            if (!com.smileprotocol.voice.WalkieManager.enabled()) {
                log("INFO walkie: sin Simple Voice Chat no se entrega");
                return;
            }
            com.smileprotocol.interact.SPInventoryOps.give(sp, new net.minecraft.world.item.ItemStack(com.smileprotocol.registry.ModItems.WALKIE.get()));
            int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, com.smileprotocol.voice.WalkieManager::isWalkie);
            if (slot < 0) {
                log("FALLO walkie: no cabe");
                return;
            }
            com.smileprotocol.player.SPPlayerData.of(sp).getSPInventory().setSelected(slot);
            SPSessionManager.sync(sp);
            com.smileprotocol.voice.WalkieManager.onInput(new com.smileprotocol.network.Payloads.WalkieInput(true), sp);
            log((com.smileprotocol.voice.WalkieManager.transmitting(sp.getUUID()) ? "OK" : "FALLO") + " walkie: transmite con B");
        }));
        SHOTS.add(() -> wait = 12);
        SHOTS.add(() -> grab(mc, "34_walkie"));
        SHOTS.add(() -> server.execute(() -> {
            if (!com.smileprotocol.voice.WalkieManager.enabled()) return;
            com.smileprotocol.voice.WalkieManager.onInput(new com.smileprotocol.network.Payloads.WalkieInput(false), sp);
            com.smileprotocol.voice.WalkieManager.toggle(sp);
            boolean off = !com.smileprotocol.voice.WalkieManager.isOn(sp);
            com.smileprotocol.voice.WalkieManager.onInput(new com.smileprotocol.network.Payloads.WalkieInput(true), sp);
            log((off && !com.smileprotocol.voice.WalkieManager.transmitting(sp.getUUID()) ? "OK" : "FALLO")
                    + " walkie apagado: no transmite");
            com.smileprotocol.voice.WalkieManager.toggle(sp);
            // que no ocupe hueco en las pruebas siguientes
            int slot = com.smileprotocol.interact.SPInventoryOps.find(sp, com.smileprotocol.voice.WalkieManager::isWalkie);
            if (slot >= 0) com.smileprotocol.interact.SPInventoryOps.take(sp, slot);
        }));
        // 8g. Fase 21: el Paciente se congela mientras lo miras y salta cuando no
        com.smileprotocol.entity.Paciente[] pac = {null};
        double[] pacDist = {0};
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, -8f);
            pac[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.PACIENTE.get(),
                    BlockPos.containing(cam.add(0, 0, 8)), level.random);
            if (pac[0] != null) {
                pac[0].setYRot(180f);
                pac[0].setYBodyRot(180f);
                pac[0].setYHeadRot(180f);
            }
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = pac[0] != null && pac[0].jumps() == 0 && pac[0].watched();
            log((ok ? "OK" : "FALLO") + " paciente congelado mientras lo miras (saltos " + (pac[0] == null ? -1 : pac[0].jumps()) + ")");
            if (pac[0] != null) pacDist[0] = pac[0].distanceTo(sp);
        }));
        SHOTS.add(() -> grab(mc, "35_paciente"));
        SHOTS.add(() -> server.execute(() -> SPSessionManager.teleport(sp, level, cam, 180f, 0f)));
        SHOTS.add(() -> wait = 9);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = pac[0] != null && pac[0].jumps() > 0 && pac[0].distanceTo(sp) < pacDist[0] - 1;
            log((ok ? "OK" : "FALLO") + " paciente avanza cuando no lo miras (saltos " + (pac[0] == null ? -1 : pac[0].jumps())
                    + ", distancia " + String.format(java.util.Locale.ROOT, "%.1f -> %.1f", pacDist[0], pac[0] == null ? 0 : pac[0].distanceTo(sp)) + ")");
            if (pac[0] != null) {
                // de cara a el a 2,5 bloques, mirandole a la sonrisa
                pac[0].debugForce(0);
                Vec3 m = pac[0].position();
                Vec3 at = m.add(0, 0, -2.5);
                if (!level.noCollision(sp, sp.getBoundingBox().move(at.subtract(sp.position())))) at = m.add(0, 0, 2.5);
                Vec3 face = m.add(0, 3.35, 0);
                double dx = face.x - at.x, dz = face.z - at.z, dy = face.y - (at.y + sp.getEyeHeight());
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                pac[0].setYRot(yaw + 180f);
                pac[0].setYBodyRot(yaw + 180f);
                pac[0].setYHeadRot(yaw + 180f);
                SPSessionManager.teleport(sp, level, at, yaw, pitch);
            }
        }));
        SHOTS.add(() -> wait = 15);
        SHOTS.add(() -> grab(mc, "36_paciente_cerca"));
        SHOTS.add(() -> server.execute(() -> { if (pac[0] != null) pac[0].discard(); }));
        // 8h. Fase 21: el Rastreador no ve por la espalda; de frente te ve y persigue a cuatro patas
        com.smileprotocol.entity.Rastreador[] ras = {null};
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            ras[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.RASTREADOR.get(),
                    BlockPos.containing(cam.add(0, 0, 9)), level.random);
            if (ras[0] != null) ras[0].debugForce(com.smileprotocol.entity.Rastreador.State.PATROL);
        }));
        SHOTS.add(() -> wait = 5);
        SHOTS.add(() -> server.execute(() -> {
            if (ras[0] == null) {
                log("FALLO rastreador: no aparece");
                return;
            }
            for (float y : new float[]{0f, 180f}) {
                ras[0].setYRot(y);
                ras[0].setYBodyRot(y);
                ras[0].setYHeadRot(y);
                boolean sees = ras[0].wouldSee(sp);
                boolean ok = y == 0f ? !sees : sees;
                log((ok ? "OK" : "FALLO") + (y == 0f ? " rastreador no te ve por la espalda" : " rastreador te ve de frente"));
            }
            // con la IA: de frente, persigue
            ras[0].debugRelease();
        }));
        SHOTS.add(() -> wait = 12);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = ras[0] != null && ras[0].state() == com.smileprotocol.entity.Rastreador.State.CHASE;
            log((ok ? "OK" : "FALLO") + " rastreador te ve y persigue (" + (ras[0] == null ? "-" : ras[0].state()) + ")");
        }));
        SHOTS.add(() -> grab(mc, "37_rastreador"));
        // de cerca, buscando con la palma del ojo en alto
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.clear(sp);
            if (ras[0] != null) {
                ras[0].debugForce(com.smileprotocol.entity.Rastreador.State.SEARCH);
                ras[0].moveTo(cam.x, cam.y, cam.z + 3.2, 180f, 0f);
                ras[0].setYBodyRot(180f);
                ras[0].setYHeadRot(180f);
            }
            SPSessionManager.teleport(sp, level, cam, 0f, -10f);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "38_rastreador_cerca"));
        SHOTS.add(() -> server.execute(() -> { if (ras[0] != null) ras[0].discard(); }));
        // 8i. Fase 21: el Ahorcado cae sobre quien pasa debajo, salvo si le apuntan con la linterna
        com.smileprotocol.entity.Ahorcado[] ah = {null};
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.clear(sp);
            SPSessionManager.teleport(sp, level, cam, 0f, -32f);
            ah[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.AHORCADO.get(),
                    BlockPos.containing(cam.add(0, 0, 3)), level.random);
            if (ah[0] != null) {
                ah[0].setYRot(180f);
                ah[0].setYBodyRot(180f);
                ah[0].hangAt(Vec3.atBottomCenterOf(BlockPos.containing(cam.add(0, 0, 3))).add(0, 1.75, 0));
            }
            com.smileprotocol.motion.Flashlights.set(sp, true);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "39_ahorcado"));
        SHOTS.add(() -> server.execute(() -> {
            // pasa por debajo mirando arriba con la linterna: no cae
            SPSessionManager.teleport(sp, level, cam.add(0, 0, 3), 0f, -80f);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = ah[0] != null && ah[0].state() == com.smileprotocol.entity.Ahorcado.State.HANG;
            log((ok ? "OK" : "FALLO") + " ahorcado iluminado no cae (" + (ah[0] == null ? "-" : ah[0].state()) + ")");
            com.smileprotocol.motion.Flashlights.set(sp, false);
            SPSessionManager.teleport(sp, level, cam.add(0, 0, 3), 0f, 10f);
        }));
        SHOTS.add(() -> wait = 55);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = ah[0] != null && (ah[0].state() == com.smileprotocol.entity.Ahorcado.State.GRAB
                    || com.smileprotocol.qte.QTEManager.isGrabbed(sp));
            log((ok ? "OK" : "FALLO") + " ahorcado cae y agarra al que pasa debajo sin mirar (" + (ah[0] == null ? "-" : ah[0].state()) + ")");
        }));
        SHOTS.add(() -> grab(mc, "40_ahorcado_agarre"));
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.clear(sp);
            com.smileprotocol.downed.DownedManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            if (ah[0] != null) ah[0].discard();
        }));
        // 8j. Fase 21: Apagon retrocede ante la linterna y se come la bateria
        com.smileprotocol.entity.Apagon[] apg = {null};
        float[] bat = {0};
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            com.smileprotocol.motion.Flashlights.recharge(sp, 1f);
            com.smileprotocol.motion.Flashlights.set(sp, false);
            apg[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.APAGON.get(),
                    BlockPos.containing(cam.add(0, 0, 6)), level.random);
        }));
        SHOTS.add(() -> wait = 15);
        SHOTS.add(() -> grab(mc, "41_apagon"));
        SHOTS.add(() -> server.execute(() -> {
            bat[0] = com.smileprotocol.motion.Flashlights.battery(sp);
            com.smileprotocol.motion.Flashlights.set(sp, true);
        }));
        SHOTS.add(() -> wait = 15);
        SHOTS.add(() -> server.execute(() -> {
            boolean recoil = apg[0] != null && apg[0].state() == com.smileprotocol.entity.Apagon.State.RECOIL;
            float now = com.smileprotocol.motion.Flashlights.battery(sp);
            log((recoil ? "OK" : "FALLO") + " apagon retrocede ante la linterna (" + (apg[0] == null ? "-" : apg[0].state()) + ")");
            log((now < bat[0] - 0.02f ? "OK" : "FALLO") + " apagon se come la bateria ("
                    + String.format(java.util.Locale.ROOT, "%.2f -> %.2f", bat[0], now) + ")");
        }));
        SHOTS.add(() -> grab(mc, "42_apagon_linterna"));
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.motion.Flashlights.set(sp, false);
            com.smileprotocol.qte.QTEManager.clear(sp);
            if (apg[0] != null) apg[0].discard();
        }));
        // 8k. Fase 21: Smiley (el jefe). Modelo, intro, reglas del combate, linterna, remate y arrastre
        com.smileprotocol.entity.Smiley[] sm = {null};
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.clear(sp);
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(40))) m.discard();
            SPSessionManager.teleport(sp, level, cam, 0f, -12f);
            com.smileprotocol.motion.Flashlights.set(sp, false);
            sm[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILEY.get(),
                    BlockPos.containing(cam.add(0, 0, 6)), level.random);
            if (sm[0] != null) {
                sm[0].debugForce(com.smileprotocol.entity.Smiley.State.STALK, 1);
                sm[0].moveTo(cam.x, cam.y, cam.z + 6, 180f, 0f);
                sm[0].setYBodyRot(180f);
                sm[0].setYHeadRot(180f);
            }
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "43_smiley"));
        // la intro: se le enciende el ojo y se le abre la sonrisa
        SHOTS.add(() -> server.execute(() -> {
            if (sm[0] != null) sm[0].debugForce(com.smileprotocol.entity.Smiley.State.INTRO, 1);
            SPSessionManager.teleport(sp, level, cam.add(0, 0, 2.5), 0f, -38f);
        }));
        SHOTS.add(() -> wait = 105);
        SHOTS.add(() -> grab(mc, "44_smiley_intro"));
        SHOTS.add(() -> log((ClientBossMusic.playing() == 1 ? "OK" : "FALLO") + " musica del jefe: suena la fase 1 ("
                + ClientBossMusic.playing() + ")"));
        SHOTS.add(() -> server.execute(() -> {
            boolean phases = com.smileprotocol.entity.SmileyFight.phaseFor(0) == 1 && com.smileprotocol.entity.SmileyFight.phaseFor(1) == 2
                    && com.smileprotocol.entity.SmileyFight.phaseFor(2) == 3 && com.smileprotocol.entity.SmileyFight.phaseFor(3) == 3;
            log((phases ? "OK" : "FALLO") + " smiley: una fase mas por generador (1, 2, 3, 3)");
            log((com.smileprotocol.entity.SmileyFight.phase(level, sp.blockPosition()) == 1 ? "OK" : "FALLO")
                    + " smiley: sin generadores, fase 1");
            if (sm[0] != null) {
                var q1 = sm[0].nextQte();
                sm[0].debugGrabbed();
                var q2 = sm[0].nextQte();
                log((q1 != q2 ? "OK" : "FALLO") + " smiley: minijuego distinto en cada agarre (" + q1 + " -> " + q2 + ")");
                var k = sm[0].killSequenceFor(sp);
                log((k == com.smileprotocol.sequence.SequenceKind.SMILEY_DRAG ? "OK" : "FALLO")
                        + " smiley: al ultimo en pie se lo lleva a rastras (" + k + ")");
                log((sm[0].neededLights(level) == 1 ? "OK" : "FALLO") + " smiley: solo, basta una linterna para hacerle retroceder");
            }
            boolean zone = com.smileprotocol.config.SPConfig.get().final_zone.equals("smileprotocol:smiley_lab");
            var tpl = server.getStructureManager().get(SmileProtocol.id("zones/smiley_lab"));
            int[] gens = {0};
            java.util.Set<String> marks = new java.util.HashSet<>();
            tpl.ifPresent(t -> {
                var set = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
                for (var info : t.filterBlocks(BlockPos.ZERO, set, com.smileprotocol.registry.ModBlocks.FUSE_BOX.get())) {
                    if (info.nbt() != null && info.nbt().getString("Circuit").startsWith("gen")) gens[0]++;
                }
                for (var info : t.filterBlocks(BlockPos.ZERO, set, net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK)) {
                    if (info.nbt() != null) marks.add(info.nbt().getString("metadata").split(" ")[0]);
                }
            });
            boolean ok = zone && gens[0] == 3 && marks.containsAll(java.util.List.of("sp:smiley", "sp:cell", "sp:summon", "sp:exit_cabin", "sp:entry"));
            log((ok ? "OK" : "FALLO") + " laboratorio cero: sala final con 3 generadores y sus marcadores (" + gens[0] + " " + marks + ")");
            // linterna: le apunta y retrocede
            if (sm[0] != null) {
                sm[0].debugForce(com.smileprotocol.entity.Smiley.State.STALK, 1);
                sm[0].debugRelease();
            }
            SPSessionManager.teleport(sp, level, cam, 0f, -8f);
            com.smileprotocol.motion.Flashlights.recharge(sp, 1f);
            com.smileprotocol.motion.Flashlights.set(sp, true);
        }));
        SHOTS.add(() -> wait = 10);
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = sm[0] != null && sm[0].state() == com.smileprotocol.entity.Smiley.State.RETREAT;
            log((ok ? "OK" : "FALLO") + " smiley retrocede ante la linterna (" + (sm[0] == null ? "-" : sm[0].state()) + ")");
        }));
        SHOTS.add(() -> grab(mc, "45_smiley_linterna"));
        // remate (fase 1 y 2) y arrastre del ultimo, sin morir
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.motion.Flashlights.set(sp, false);
            com.smileprotocol.qte.QTEManager.clear(sp);
            com.smileprotocol.sequence.SequenceManager.TEST_NO_KILL = true;
            if (sm[0] != null) {
                sm[0].debugForce(com.smileprotocol.entity.Smiley.State.STALK, 2);
                sm[0].moveTo(cam.x, cam.y, cam.z + 2, 180f, 0f);
                SPSessionManager.teleport(sp, level, cam, 0f, 0f);
                boolean ok = com.smileprotocol.sequence.SequenceManager.start(sm[0], sp, com.smileprotocol.sequence.SequenceKind.SMILEY_KILL);
                log((ok ? "OK" : "FALLO") + " smiley: remate en marcha");
            }
        }));
        SHOTS.add(() -> wait = 36);
        SHOTS.add(() -> grab(mc, "46_smiley_remate"));
        SHOTS.add(() -> wait = 50);
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.sequence.SequenceManager.clear(sp);
            sp.setHealth(sp.getMaxHealth());
            if (sm[0] != null) {
                sm[0].moveTo(cam.x, cam.y, cam.z + 2, 180f, 0f);
                SPSessionManager.teleport(sp, level, cam, 0f, 0f);
                boolean ok = com.smileprotocol.sequence.SequenceManager.start(sm[0], sp, com.smileprotocol.sequence.SequenceKind.SMILEY_DRAG);
                log((ok ? "OK" : "FALLO") + " smiley: arrastre en marcha");
            }
        }));
        SHOTS.add(() -> wait = 50);
        SHOTS.add(() -> grab(mc, "47_smiley_arrastre"));
        SHOTS.add(() -> server.execute(() -> {
            boolean ok = com.smileprotocol.sequence.SequenceManager.inSequence(sp);
            log((ok ? "OK" : "FALLO") + " smiley: la victima sigue en el arrastre");
            com.smileprotocol.sequence.SequenceManager.clear(sp);
            com.smileprotocol.sequence.SequenceManager.TEST_NO_KILL = false;
            if (sm[0] != null) sm[0].discard();
            SPSessionManager.teleport(sp, level, e, 0f, 0f);
            boolean seen = com.smileprotocol.entity.SmileyFight.cameoNow(level, sp);
            log("INFO smiley se deja ver en la planta: " + seen);
        }));
        SHOTS.add(() -> wait = 20);
        SHOTS.add(() -> grab(mc, "48_smiley_aparicion"));
        SHOTS.add(() -> server.execute(() -> {
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.Smiley.class,
                    new net.minecraft.world.phys.AABB(e, e).inflate(60))) m.discard();
            sp.setHealth(sp.getMaxHealth());
        }));
        // 8l. Fase 24: muebles que se registran (se abren y sueltan botin de la planta, una vez)
        BlockPos[] furn = {null};
        SHOTS.add(() -> server.execute(() -> {
            var box = z.box();
            double best = Double.MAX_VALUE;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int zz = box.minZ(); zz <= box.maxZ(); zz++) {
                    m.set(x, BlockPos.containing(e).getY(), zz);
                    if (level.getBlockState(m).getBlock() instanceof com.smileprotocol.block.SearchableFurnitureBlock) {
                        double d = m.distSqr(BlockPos.containing(e));
                        if (d < best) {
                            best = d;
                            furn[0] = m.immutable();
                        }
                    }
                }
            }
            log((furn[0] != null ? "OK" : "FALLO") + " muebles: hay muebles que se registran en la planta");
            if (furn[0] != null) {
                var st = level.getBlockState(furn[0]);
                Direction f = st.getValue(com.smileprotocol.block.FurnitureBlock.FACING);
                Vec3 at = Vec3.atBottomCenterOf(furn[0]).add(f.getStepX() * 2.2, 0, f.getStepZ() * 2.2);
                SPSessionManager.teleport(sp, level, at, f.getOpposite().toYRot(), 35f);
            }
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "49_mueble"));
        SHOTS.add(() -> server.execute(() -> {
            if (furn[0] == null) return;
            var st = level.getBlockState(furn[0]);
            if (st.getBlock() instanceof com.smileprotocol.block.SearchableFurnitureBlock b) {
                boolean had = b.spec(level, furn[0], st, sp) != null;
                b.complete(level, furn[0], st, sp);
                var after = level.getBlockState(furn[0]);
                boolean open = after.getValue(com.smileprotocol.block.SearchableFurnitureBlock.OPEN);
                boolean gone = b.spec(level, furn[0], after, sp) == null;
                log((had && open && gone ? "OK" : "FALLO") + " muebles: se registra una vez y queda abierto ("
                        + level.getBlockState(furn[0]).getBlock().getDescriptionId() + ")");
            }
        }));
        SHOTS.add(() -> wait = 15);
        SHOTS.add(() -> grab(mc, "50_mueble_abierto"));
        // 8m. Fase 22: accesibilidad (minijuego asistido validado en el servidor, colores de daltonismo)
        SHOTS.add(() -> server.execute(() -> {
            com.smileprotocol.qte.QTEManager.setAssist(sp, true);
            boolean on = com.smileprotocol.qte.QTEManager.assisted(sp);
            com.smileprotocol.qte.QTEManager.setAssist(sp, false);
            boolean off = !com.smileprotocol.qte.QTEManager.assisted(sp);
            log((on && off ? "OK" : "FALLO") + " accesibilidad: minijuego asistido segun el jugador");
        }));
        SHOTS.add(() -> {
            var cfg = com.smileprotocol.config.SPConfig.get();
            int c0 = com.smileprotocol.marker.MarkerManager.COLORS[1];
            cfg.colorblind = "DEUTERANOPIA";
            int c1 = ClientMarkers.display(c0);
            cfg.colorblind = "OFF";
            log((c1 != c0 && ClientMarkers.display(c0) == c0 ? "OK" : "FALLO") + " accesibilidad: paleta para daltonismo");
        });
        // 8n. Planta de entrenamiento (ascensor TUTORIAL): sin monstruos, con sus estaciones
        ZonePlacement[] tut = {null};
        Group[] tutGroup = {null};
        SHOTS.add(() -> server.execute(() -> {
            var id = net.minecraft.resources.ResourceLocation.tryParse(com.smileprotocol.config.SPConfig.get().tutorial_zone);
            tutGroup[0] = new Group(java.util.UUID.randomUUID(), "TUT", 980, List.of(id),
                    com.smileprotocol.difficulty.DifficultyScaler.forGroup(server, 1), 980);
            tut[0] = com.smileprotocol.instance.InstanceManager.place(level, tutGroup[0], 0, 0);
            if (tut[0] == null) {
                log("FALLO tutorial: no se coloco la planta");
                return;
            }
            int mobs = level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class, net.minecraft.world.phys.AABB.of(tut[0].box())).size();
            boolean marks = !tut[0].marker("tut_crouch").isEmpty() && !tut[0].marker("tut_vent").isEmpty()
                    && tut[0].marker("mob_spawn").isEmpty();
            log((mobs == 0 && marks ? "OK" : "FALLO") + " tutorial: sin monstruos (" + mobs + ") y con sus marcadores "
                    + tut[0].markers().keySet());
            com.smileprotocol.motion.Flashlights.set(sp, true);
            SPSessionManager.teleport(sp, level, tut[0].entry().add(0, 0, 9), 0f, 5f);
        }));
        SHOTS.add(() -> wait = 40);
        SHOTS.add(() -> grab(mc, "51_tutorial_entrada"));
        SHOTS.add(() -> server.execute(() -> {
            if (tut[0] != null) SPSessionManager.teleport(sp, level, tut[0].entry().add(1, 0, 33), 0f, 5f);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "52_tutorial_escondites"));
        SHOTS.add(() -> server.execute(() -> {
            if (tut[0] != null) SPSessionManager.teleport(sp, level, tut[0].entry().add(-1, 0, 64), 0f, 20f);
        }));
        SHOTS.add(() -> wait = 25);
        SHOTS.add(() -> grab(mc, "53_tutorial_conducto"));
        SHOTS.add(() -> server.execute(() -> {
            if (tutGroup[0] != null) com.smileprotocol.instance.InstanceManager.release(server, tutGroup[0]);
        }));
        // 8o. Pantalla de reglas (F7 dentro de un ascensor pequeno)
        SHOTS.add(() -> mc.setScreen(new RulesScreen(com.smileprotocol.difficulty.RunRules.decode("NIGHTMARE|fast_mobs,no_revive"))));
        SHOTS.add(() -> wait = 10);
        SHOTS.add(() -> grab(mc, "54_reglas"));
        SHOTS.add(() -> mc.setScreen(null));
        // 8p. Senuelo (despertador) y aturdidor
        com.smileprotocol.entity.SPMonster[] stunMob = {null};
        SHOTS.add(() -> server.execute(() -> {
            for (var m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(40))) m.discard();
            SPSessionManager.teleport(sp, level, cam, 0f, 25f);
            var d = com.smileprotocol.item.Decoys.place(level, cam.add(0, 0, 2.2), 200, 30f);
            log((d != null && com.smileprotocol.item.Decoys.active() > 0 ? "OK" : "FALLO") + " senuelo colocado y sonando");
        }));
        SHOTS.add(() -> wait = 35);
        SHOTS.add(() -> grab(mc, "55_senuelo"));
        SHOTS.add(() -> server.execute(() -> {
            SPSessionManager.teleport(sp, level, cam, 0f, 0f);
            stunMob[0] = com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.BERSERKER.get(),
                    BlockPos.containing(cam.add(0, 0, 3)), level.random);
        }));
        SHOTS.add(() -> wait = 5);
        SHOTS.add(() -> server.execute(() -> {
            var m = stunMob[0];
            if (m == null) {
                log("FALLO aturdidor: no aparecio el berserker");
                return;
            }
            m.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, sp.getEyePosition());
            var target = com.smileprotocol.item.UseManager.stunTarget(sp);
            boolean ok = target == m && m.stun(60) && m.isStunned();
            log((ok ? "OK" : "FALLO") + " aturdidor: apunta al smiler de delante y lo deja quieto");
        }));
        SHOTS.add(() -> wait = 20);
        SHOTS.add(() -> grab(mc, "56_aturdido"));
        SHOTS.add(() -> server.execute(() -> {
            if (stunMob[0] != null) stunMob[0].discard();
            for (var e2 : level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
                    new net.minecraft.world.phys.AABB(cam, cam).inflate(10))) e2.discard();
        }));
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
        // grupo de uno: con el unico jugador muerto es game over (README 6.7)
        SHOTS.add(() -> wait = 50);
        SHOTS.add(() -> log((mc.screen instanceof GroupEndScreen ? "OK" : "FALLO") + " pantalla de GAME OVER"));
        SHOTS.add(() -> server.execute(() -> {
            if (!com.smileprotocol.voice.VoiceManager.available()) return;
            if (!com.smileprotocol.voice.VoiceManager.connected(sp)) {
                log("INFO voz: el cliente no tiene conexion de voz (sin audio en CI); grupo de espectadores sin probar");
                return;
            }
            String run = com.smileprotocol.voice.VoiceManager.spectatorRun(sp);
            log((run != null ? "OK" : "FALLO") + " voz: el muerto pasa al grupo de espectadores (" + run + ")");
        }));
        SHOTS.add(() -> grab(mc, "27_game_over"));
        SHOTS.add(() -> server.execute(() -> {
            var data = com.smileprotocol.score.ScoreData.get(server);
            boolean rec = !data.top().isEmpty() && data.stats(sp.getUUID()).runs > 0;
            log((rec ? "OK" : "FALLO") + " puntuacion: la partida queda en la tabla (" + data.top().size() + ") y en las estadisticas");
            boolean first = com.smileprotocol.score.ScoreManager.unlock(sp, "graduate");
            boolean again = com.smileprotocol.score.ScoreManager.unlock(sp, "graduate");
            log((first && !again ? "OK" : "FALLO") + " logros: se desbloquean una sola vez");
            // el tablon del lobby ya muestra la partida
            var b = data.board();
            if (!b.isEmpty()) {
                server.overworld().getChunkAt(b.get(0));
                com.smileprotocol.score.ScoreManager.refreshBoard(server);
            }
            String l0 = b.isEmpty() || !(server.overworld().getBlockEntity(b.get(0)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s)
                    ? "" : s.getFrontText().getMessage(0, false).getString();
            log((l0.contains("pts") ? "OK" : "FALLO") + " tablon de records actualizado: «" + l0 + "»");
        }));
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
