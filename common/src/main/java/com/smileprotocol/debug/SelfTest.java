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
            // Reglas de la partida: texto de ida y vuelta y efecto sobre el perfil del grupo
            var rules = com.smileprotocol.difficulty.RunRules.decode("NIGHTMARE|no_revive,fast_mobs,inventado");
            var base = DifficultyScaler.forGroup(server, 4);
            var hard = rules.apply(base);
            var easy = com.smileprotocol.difficulty.RunRules.decode("EASY").apply(base);
            boolean rulesOk = rules.encode().equals("NIGHTMARE|fast_mobs,no_revive")
                    && com.smileprotocol.difficulty.RunRules.decode(rules.encode()).equals(rules)
                    && hard.mobs() > base.mobs() && hard.qteTime() < base.qteTime() && easy.mobs() < base.mobs()
                    && Math.abs(rules.speedFactor() - 1.15 * 1.2) < 1e-6 && rules.scoreFactor() > 1.5
                    && com.smileprotocol.difficulty.RunRules.decode("").isDefault();
            log((rulesOk ? "reglas" : "FALLO reglas") + ": " + rules.encode() + " " + hard.describe() + " / facil " + easy.describe());
            if (!rulesOk) failures++;
            // Puntuacion y tabla de records
            var normal = com.smileprotocol.difficulty.RunRules.DEFAULT;
            int sEsc = com.smileprotocol.score.ScoreManager.score(6, true, 1200, 3, normal);
            int sFast = com.smileprotocol.score.ScoreManager.score(6, true, 600, 3, normal);
            int sDead = com.smileprotocol.score.ScoreManager.score(3, false, 600, 0, normal);
            int sHard = com.smileprotocol.score.ScoreManager.score(6, true, 1200, 3, rules);
            var table = new com.smileprotocol.score.ScoreData();
            for (int k = 0; k < 12; k++) table.add(new com.smileprotocol.score.ScoreData.Entry(k * 100, List.of("p" + k), 1, 6, 60, "NORMAL", false, 0));
            int place = table.add(new com.smileprotocol.score.ScoreData.Entry(550, List.of("x"), 1, 6, 60, "NORMAL", false, 0));
            boolean scoreOk = sFast > sEsc && sEsc > sDead && sHard > sEsc && table.top().size() == 10
                    && table.top().get(0).score() == 1100 && place == 7
                    && table.add(new com.smileprotocol.score.ScoreData.Entry(1, List.of("y"), 1, 6, 60, "NORMAL", false, 0)) == 0;
            log((scoreOk ? "puntuacion" : "FALLO puntuacion") + ": escapar=" + sEsc + " rapido=" + sFast + " eliminados=" + sDead
                    + " pesadilla=" + sHard + " puesto=" + place);
            if (!scoreOk) failures++;
            List<ResourceLocation> seq = ZoneSequence.generate(server, 1234L, List.of());
            log("secuencia " + seq);
            if (seq.size() < 2) failures++;
            // Voz (fase 18): silencio sin ruido, susurro < hablar < gritar
            float silent = com.smileprotocol.voice.VoiceManager.radiusFor(-80, false);
            float whisper = com.smileprotocol.voice.VoiceManager.radiusFor(-30, true);
            float talk = com.smileprotocol.voice.VoiceManager.radiusFor(-30, false);
            float shout = com.smileprotocol.voice.VoiceManager.radiusFor(-6, false);
            boolean voiceOk = silent == 0 && whisper > 0 && whisper < talk && talk < shout;
            log((voiceOk ? "voz" : "FALLO voz") + ": radios silencio=" + silent + " susurro=" + whisper + " hablar=" + talk
                    + " gritar=" + shout + " (Simple Voice Chat " + (com.smileprotocol.voice.VoiceManager.available() ? "si" : "no") + ")");
            if (!voiceOk) failures++;

            ServerLevel level = server.getLevel(SPDimension.SP_DIM);
            if (level == null) {
                log("FALLO: no existe la dimension de instancias");
                failures++;
            } else {
                int slot = 900;
                for (ResourceLocation id : ZoneCatalog.all(server)) {
                    failures += testZone(server, level, id, slot++);
                }
                failures += loadTest(server, level);
                // Limpieza de lo colocado (en este mismo tick)
                for (int i = 0; i < 400; i++) InstanceManager.tick(server);
            }
        } catch (Throwable t) {
            failures++;
            SmileProtocol.LOGGER.error("SELFTEST excepcion", t);
        }
        log(failures == 0 ? "OK" : "FALLOS: " + failures);
    }

    /**
     * Prueba de carga (fase 23): varias partidas a la vez. Coloca la planta mas grande para 6 grupos
     * seguidos y comprueba que no se solapan, cuanto tarda cada una y cuantos mobs y luces quedan.
     */
    private static int loadTest(MinecraftServer server, ServerLevel level) {
        ResourceLocation id = ResourceLocation.tryParse("smileprotocol:start_corridor");
        List<Group> groups = new java.util.ArrayList<>();
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new java.util.ArrayList<>();
        long t0 = System.nanoTime();
        int mobs = 0, lights = 0, fails = 0;
        for (int i = 0; i < 6; i++) {
            DifficultyProfile diff = DifficultyScaler.forGroup(server, 2 + i % 4);
            Group g = new Group(UUID.randomUUID(), "LOAD", 950 + i, List.of(id), diff, 950 + i);
            ZonePlacement z = InstanceManager.place(level, g, 0, 0);
            if (z == null) {
                fails++;
                continue;
            }
            groups.add(g);
            for (var b : boxes) {
                if (b.intersects(z.box())) {
                    log("FALLO carga: dos instancias se solapan");
                    fails++;
                }
            }
            boxes.add(z.box());
            mobs += level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class, AABB.of(z.box())).size();
            int[] l = {0};
            CircuitManager.forEachBlockEntity(level, z.box(), be -> {
                if (be instanceof com.smileprotocol.blockentity.FluorescentLightBlockEntity) l[0]++;
            });
            lights += l[0];
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log((fails == 0 ? "carga" : "FALLO carga") + ": " + groups.size() + " instancias a la vez en " + ms + " ms ("
                + (groups.isEmpty() ? 0 : ms / groups.size()) + " ms cada una), mobs=" + mobs + " luces=" + lights);
        for (Group g : groups) InstanceManager.release(server, g);
        return fails;
    }

    private static int testZone(MinecraftServer server, ServerLevel level, ResourceLocation id, int slot) {
        DifficultyProfile diff = DifficultyScaler.forGroup(server, 2);
        Group g = new Group(UUID.randomUUID(), "TEST", slot, List.of(id), diff, slot);
        ZonePlacement z = InstanceManager.place(level, g, 0, 0);
        if (z == null) {
            log("FALLO zona " + id + ": no se coloco");
            return 1;
        }
        int[] boxes = {0}, need = {0}, doors = {0}, valves = {0}, grilles = {0}, ventFails = {0};
        CircuitManager.forEachBlockEntity(level, z.box(), be -> {
            if (be instanceof FuseBoxBlockEntity fb) {
                boxes[0]++;
                need[0] += fb.required();
            } else if (be instanceof LabDoorBlockEntity) {
                doors[0]++;
            } else if (be instanceof ValveBlockEntity) {
                valves[0]++;
            } else if (be instanceof com.smileprotocol.blockentity.VentBlockEntity vb) {
                // Conductos (fase 17): la rejilla sellada se abre con corriente; el ventilador con freno se para
                var st = be.getBlockState();
                if (st.getBlock() instanceof com.smileprotocol.block.VentGrilleBlock) {
                    grilles[0]++;
                    if (st.getValue(com.smileprotocol.block.VentGrilleBlock.SEALED)) {
                        vb.onCircuit(level, true);
                        if (level.getBlockState(be.getBlockPos()).getValue(com.smileprotocol.block.VentGrilleBlock.SEALED)) ventFails[0]++;
                    }
                } else if (st.getBlock() instanceof com.smileprotocol.block.VentFanBlock) {
                    if (!st.getValue(com.smileprotocol.block.VentFanBlock.RUNNING)) ventFails[0]++;
                    vb.onCircuit(level, true);
                    if (level.getBlockState(be.getBlockPos()).getValue(com.smileprotocol.block.VentFanBlock.RUNNING)) ventFails[0]++;
                }
            }
        });
        int items = level.getEntitiesOfClass(ItemEntity.class, AABB.of(z.box())).size();
        boolean exit = z.exitCabin() != null;
        log("zona " + id + " caja=" + z.box().getXSpan() + "x" + z.box().getZSpan()
                + " marcadores=" + z.markers().keySet() + " cajas=" + boxes[0] + " fusibles necesarios=" + need[0]
                + " fusibles en el suelo=" + z.fuses().size() + " objetos=" + items + " puertas=" + doors[0]
                + " valvulas=" + valves[0] + " rejillas=" + grilles[0] + " salida=" + exit + " puertas salida=" + z.marker("exit_door").size()
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
        if (grilles[0] < 2 || ventFails[0] > 0) {
            log("FALLO zona " + id + ": conductos (" + grilles[0] + " rejillas, " + ventFails[0] + " fallos de circuito)");
            fails++;
        }
        InstanceManager.release(server, g);
        return fails;
    }

    private static void log(String s) {
        SmileProtocol.LOGGER.info("SELFTEST {}", s);
    }
}
