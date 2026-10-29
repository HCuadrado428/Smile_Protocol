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

    private static final boolean ENABLED = "1".equals(System.getenv("SMILEPROTOCOL_CLIENTTEST"));
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
        // 1. Llegada, mirando hacia el vestibulo
        shot(mc, server, "01_llegada", e, 0f, 5f, 60);
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

    private static void log(String s) {
        SmileProtocol.LOGGER.info("CLIENTTEST {}", s);
    }
}
