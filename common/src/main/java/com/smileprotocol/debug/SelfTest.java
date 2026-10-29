package com.smileprotocol.debug;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.difficulty.DifficultyScaler;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.hide.HideTypes;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.GroupManager;
import com.smileprotocol.zone.ZoneCatalog;
import com.smileprotocol.zone.ZoneSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * Prueba automatica en un servidor sin jugadores (se activa con la variable de entorno
 * {@code SMILEPROTOCOL_SELFTEST=1}; la usa la CI). Coloca cada zona en un hueco de pruebas,
 * comprueba sus marcadores y mecanismos, la limpia y apaga el servidor. Escribe lineas
 * {@code SELFTEST} en el log.
 */
public final class SelfTest {

    private SelfTest() {}

    public static boolean enabled() {
        return "1".equals(System.getenv("SMILEPROTOCOL_SELFTEST")) || Boolean.getBoolean("smileprotocol.selftest");
    }

    public static void run(MinecraftServer server) {
        int failures = 0;
        log("inicio");
        try {
            for (int n : new int[]{4, 7, 9, 11, 19, 23}) log("grupos " + n + " -> " + GroupManager.groupSizes(n, 4));
            for (int n = 2; n <= 6; n++) log("dificultad " + DifficultyScaler.forGroup(server, n).describe());
            for (String h : new String[]{"locker", "wardrobe", "gurney", "curtain", "container"}) {
                log("escondite " + HideTypes.get(server, h));
            }
            List<ResourceLocation> seq = ZoneSequence.generate(server, 1234L, List.of());
            log("secuencia " + seq);
            if (seq.size() < 2) failures++;

            ServerLevel level = server.getLevel(SPDimension.SP_DIM);
            if (level == null) {
                log("FALLO: no existe la dimension de instancias");
                failures++;
            } else {
                int slot = 900;
                for (ResourceLocation id : ZoneCatalog.all(server)) {
                    failures += testZone(server, level, id, slot++);
                }
                // Limpieza de lo colocado (en este mismo tick)
                for (int i = 0; i < 400; i++) InstanceManager.tick(server);
            }
        } catch (Throwable t) {
            failures++;
            SmileProtocol.LOGGER.error("SELFTEST excepcion", t);
        }
        log(failures == 0 ? "OK" : "FALLOS: " + failures);
    }

    private static int testZone(MinecraftServer server, ServerLevel level, ResourceLocation id, int slot) {
        DifficultyProfile diff = DifficultyScaler.forGroup(server, 2);
        Group g = new Group(UUID.randomUUID(), "TEST", slot, List.of(id), diff, slot);
        ZonePlacement z = InstanceManager.place(level, g, 0, 0);
        if (z == null) {
            log("FALLO zona " + id + ": no se coloco");
            return 1;
        }
        int[] boxes = {0}, need = {0}, doors = {0}, valves = {0};
        CircuitManager.forEachBlockEntity(level, z.box(), be -> {
            if (be instanceof FuseBoxBlockEntity fb) {
                boxes[0]++;
                need[0] += fb.required();
            } else if (be instanceof LabDoorBlockEntity) {
                doors[0]++;
            } else if (be instanceof ValveBlockEntity) {
                valves[0]++;
            }
        });
        int items = level.getEntitiesOfClass(ItemEntity.class, AABB.of(z.box())).size();
        boolean exit = z.exitCabin() != null;
        log("zona " + id + " caja=" + z.box().getXSpan() + "x" + z.box().getZSpan()
                + " marcadores=" + z.markers().keySet() + " cajas=" + boxes[0] + " fusibles necesarios=" + need[0]
                + " fusibles en el suelo=" + z.fuses().size() + " objetos=" + items + " puertas=" + doors[0]
                + " valvulas=" + valves[0] + " salida=" + exit + " puertas salida=" + z.marker("exit_door").size()
                + " circuitos=" + CircuitManager.describe(level, z.box()));
        int fails = 0;
        if (z.marker("entry").isEmpty()) {
            log("FALLO zona " + id + ": sin marcador de entrada");
            fails++;
        }
        if (!exit) {
            log("FALLO zona " + id + ": sin cabina de salida");
            fails++;
        }
        if (boxes[0] > 0 && z.fuses().size() < need[0]) {
            log("FALLO zona " + id + ": faltan sitios para fusibles (" + z.fuses().size() + " < " + need[0] + ")");
            fails++;
        }
        InstanceManager.release(server, g);
        return fails;
    }

    private static void log(String s) {
        SmileProtocol.LOGGER.info("SELFTEST {}", s);
    }
}
