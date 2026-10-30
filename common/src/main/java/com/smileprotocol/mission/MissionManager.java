package com.smileprotocol.mission;

import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Misiones (README 19): cada planta tiene una lista de pasos, globales para el grupo y
 * cooperativos. Solo se muestra el objetivo actual. Los sistemas avisan de lo que pasa (puertas
 * abiertas, valvulas completas, circuitos encendidos, objetos recogidos) y aqui se guarda como
 * "hechos" de la planta, aunque no sea el paso actual: si el grupo hace las cosas en otro orden,
 * los pasos que ya estan hechos se saltan. El ascensor de salida no arranca hasta que solo queda
 * escapar. El estado vive en la instancia (se reinicia al cambiar de planta).
 */
public final class MissionManager {

    private static final String KEY = "missions";
    private static final FileToIdConverter FILES = FileToIdConverter.json("missions");
    @Nullable private static ResourceManager loadedFrom;
    private static Map<String, MissionDef> defs = Map.of();

    /** Estado de la mision de una planta. */
    static final class State {
        MissionDef def;
        int zoneIndex = -1;
        int step;
        int timer;
        final Set<String> facts = new HashSet<>();
        Payloads.SyncMission synced;
    }

    private MissionManager() {}

    // Definiciones -------------------------------------------------------------------------------

    private static MissionDef def(MinecraftServer server, ResourceLocation zone) {
        ResourceManager rm = server.getResourceManager();
        if (rm != loadedFrom) {
            loadedFrom = rm;
            Map<String, MissionDef> m = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(rm).entrySet()) {
                String name = FILES.fileToId(e.getKey()).getPath();
                try (Reader r = e.getValue().openAsReader()) {
                    m.put(name, MissionDef.parse(name, JsonParser.parseReader(r).getAsJsonObject()));
                } catch (Exception ex) {
                    SmileProtocol.LOGGER.error("Mision {} no valida: {}", name, ex.toString());
                }
            }
            defs = m;
        }
        String id = zone.getPath();
        return defs.getOrDefault(id, MissionDef.fallback(id));
    }

    @Nullable
    private static State state(MinecraftServer server, Group g) {
        ZonePlacement z = g.current();
        if (z == null) return null;
        InstanceContext ctx = InstanceContext.forGroup(server, g.id());
        if (ctx == null) return null;
        State s = ctx.state(KEY, State::new);
        if (s.zoneIndex != g.zoneIndex() || s.def == null) {
            s.zoneIndex = g.zoneIndex();
            s.def = def(server, z.zoneId());
            s.step = 0;
            s.timer = 0;
            s.facts.clear();
            s.synced = null;
        }
        return s;
    }

    // Avisos de los sistemas -----------------------------------------------------------------------

    /** Lo llama {@code LabEvents.fire}: puertas, valvulas, objetos... */
    public static void onEvent(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, String trigger, String arg) {
        String fact = switch (trigger) {
            case "door_opened" -> "door:" + arg;
            case "door_forced" -> "door:forced";
            case "valve_group_complete" -> "valve:" + arg;
            case "item_picked" -> "item:" + arg;
            default -> null;
        };
        if (fact != null) addFact(level, pos, fact);
    }

    /** Lo llama {@code CircuitManager.notify} al encenderse o apagarse un circuito. */
    public static void onCircuit(ServerLevel level, BlockPos pos, String circuit, boolean powered) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        if (ctx == null) return;
        Group g = ctx.group(level.getServer());
        if (g == null) return;
        State s = state(level.getServer(), g);
        if (s == null) return;
        if (powered) s.facts.add("circuit:" + circuit);
        else s.facts.remove("circuit:" + circuit);
    }

    private static void addFact(ServerLevel level, BlockPos pos, String fact) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        if (ctx == null) return;
        Group g = ctx.group(level.getServer());
        if (g == null) return;
        State s = state(level.getServer(), g);
        if (s != null) s.facts.add(fact);
    }

    /** El ascensor de salida solo arranca cuando el unico paso que queda es escapar. */
    public static boolean readyToLeave(MinecraftServer server, Group g) {
        State s = state(server, g);
        if (s == null) return true;
        for (int i = s.step; i < s.def.steps().size(); i++) {
            if (!"ESCAPE".equals(s.def.steps().get(i).type())) return false;
        }
        return true;
    }

    /** Al partir el ascensor: el paso de escapar se cumple. */
    public static void onDepart(MinecraftServer server, Group g) {
        State s = state(server, g);
        if (s != null) s.facts.add("escaped");
    }

    // Tick -------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return;
        for (Run run : RunManager.openRuns(server)) {
            for (Group g : run.groups()) {
                if (g.state().isDone() || g.current() == null) continue;
                State s = state(server, g);
                if (s == null) continue;
                List<ServerPlayer> players = players(server, g);
                // avanza todos los pasos que ya esten hechos (orden libre)
                int guard = 0;
                while (s.step < s.def.steps().size() && guard++ < 16) {
                    MissionDef.Step st = s.def.steps().get(s.step);
                    int[] pr = progress(level, g, s, st, players);
                    if (pr[0] < pr[1]) break;
                    complete(level, g, s, st, players);
                    s.step++;
                    s.timer = 0;
                }
                sync(level, g, s, players);
            }
        }
    }

    /** {progreso, total} del paso. */
    private static int[] progress(ServerLevel level, Group g, State s, MissionDef.Step st, List<ServerPlayer> players) {
        ZonePlacement z = g.current();
        return switch (st.type()) {
            case "FIND_ITEM" -> {
                int have = 0;
                ResourceLocation id = ResourceLocation.tryParse(st.arg());
                for (ServerPlayer p : players) {
                    for (int i = 0; i < com.smileprotocol.player.SPInventory.SIZE; i++) {
                        ItemStack it = SPPlayerData.of(p).getSPInventory().get(i);
                        if (!it.isEmpty() && id != null && BuiltInRegistries.ITEM.getKey(it.getItem()).equals(id)) have++;
                    }
                }
                if (s.facts.contains("item:" + st.arg())) have = Math.max(have, st.count());
                yield new int[]{Math.min(have, st.count()), st.count()};
            }
            case "OPEN_DOOR" -> new int[]{s.facts.contains("door:" + st.arg()) ? 1 : 0, 1};
            case "TURN_VALVES" -> new int[]{s.facts.contains("valve:" + st.arg()) ? 1 : 0, 1};
            case "POWER" -> new int[]{s.facts.contains("circuit:" + st.arg())
                    || CircuitManager.isPowered(level, z.box().getCenter(), st.arg()) ? 1 : 0, 1};
            case "INSERT_FUSES" -> fuses(level, z, st.arg());
            case "REACH" -> {
                boolean in = false;
                for (BlockPos m : z.marker(st.arg())) {
                    for (ServerPlayer p : players) if (p.blockPosition().closerThan(m, 3.5)) in = true;
                }
                yield new int[]{in || s.facts.contains("reach:" + st.arg()) ? 1 : 0, 1};
            }
            case "SURVIVE" -> {
                s.timer += 5;
                yield new int[]{Math.min(st.count(), s.timer / 20), st.count()};
            }
            default -> new int[]{s.facts.contains("escaped") ? 1 : 0, 1};   // ESCAPE
        };
    }

    /** Fusibles puestos y necesarios en las cajas del circuito (toda la planta). */
    private static int[] fuses(ServerLevel level, ZonePlacement z, String circuit) {
        int[] r = {0, 0};
        CircuitManager.forEachBlockEntity(level, z.box(), be -> {
            if (be instanceof FuseBoxBlockEntity fb && fb.circuit().equals(circuit)) {
                int good = 0;
                for (String f : fb.fuses()) if (!FuseBoxBlockEntity.BLOWN.equals(f)) good++;
                r[0] += Math.min(good, fb.required());
                r[1] += fb.required();
            }
        });
        if (r[1] == 0) return new int[]{1, 1};
        return r;
    }

    private static void complete(ServerLevel level, Group g, State s, MissionDef.Step st, List<ServerPlayer> players) {
        boolean last = s.step == s.def.steps().size() - 1;
        for (ServerPlayer p : players) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.6f, 0.7f);
        }
        ZonePlacement z = g.current();
        for (MissionDef.Event ev : st.events()) {
            switch (ev.type()) {
                case "message" -> {
                    Component c = Component.translatable(ev.arg()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
                    for (ServerPlayer p : players) p.displayClientMessage(c, false);
                }
                case "sound" -> {
                    ResourceLocation id = ResourceLocation.tryParse(ev.arg());
                    SoundEvent snd = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
                    if (snd != null) for (ServerPlayer p : players) {
                        p.serverLevel().playSound(null, p.blockPosition(), snd, SoundSource.HOSTILE, 1.2f, 0.8f);
                    }
                }
                case "spawn_mob" -> {
                    List<BlockPos> spawns = new ArrayList<>(z.mobSpawns());
                    if (!spawns.isEmpty()) {
                        BlockPos at = farthest(spawns, players);
                        var type = "smiler_test".equals(ev.arg()) ? com.smileprotocol.registry.ModEntities.SMILER_TEST.get()
                                : com.smileprotocol.registry.ModEntities.BERSERKER.get();
                        com.smileprotocol.entity.MobManager.spawn(level, type, at, level.random);
                    }
                }
                case "lights_off", "lights_on" -> CircuitManager.forEachBlockEntity(level, z.box(), be -> {
                    if (be instanceof com.smileprotocol.blockentity.FluorescentLightBlockEntity fl
                            && fl.getBlockState().hasProperty(com.smileprotocol.block.FluorescentLightBlock.LIT)) {
                        level.setBlock(fl.getBlockPos(), fl.getBlockState().setValue(com.smileprotocol.block.FluorescentLightBlock.LIT,
                                "lights_on".equals(ev.type())), 3);
                    }
                });
                case "power" -> CircuitManager.notify(level, z.box().getCenter(), ev.arg(), true);
                default -> { }
            }
        }
        if (!players.isEmpty()) com.smileprotocol.mechanism.LabEvents.fire(level, players.get(0).blockPosition(), players.get(0),
                "mission_objective_completed", s.def.id() + ":" + s.step);
        SPDebug.log(g.label() + ": objetivo cumplido " + st.type() + (st.arg().isEmpty() ? "" : " " + st.arg())
                + (last ? " (mision completa)" : ""));
    }

    private static BlockPos farthest(List<BlockPos> spots, List<ServerPlayer> players) {
        BlockPos best = spots.get(0);
        double bd = -1;
        for (BlockPos b : spots) {
            double d = Double.MAX_VALUE;
            for (ServerPlayer p : players) d = Math.min(d, p.blockPosition().distSqr(b));
            if (d > bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private static void sync(ServerLevel level, Group g, State s, List<ServerPlayer> players) {
        Payloads.SyncMission pkt;
        if (s.step >= s.def.steps().size()) {
            pkt = new Payloads.SyncMission(false, Component.empty(), 0, 0, s.step, s.def.steps().size());
        } else {
            MissionDef.Step st = s.def.steps().get(s.step);
            int[] pr = progress(level, g, s, st, players);
            if ("SURVIVE".equals(st.type())) s.timer -= 5;          // progress() ya ha contado este tick
            pkt = new Payloads.SyncMission(true, text(st), pr[0], pr[1], s.step, s.def.steps().size());
        }
        boolean changed = !pkt.equals(s.synced);
        s.synced = pkt;
        for (ServerPlayer p : players) {
            if (changed || p.tickCount % 100 < 5) SPNetwork.sendTo(p, pkt);
        }
    }

    /** Texto del objetivo: el de la ficha o uno por defecto segun el tipo. */
    private static Component text(MissionDef.Step st) {
        if (!st.text().isEmpty()) return Component.translatable(st.text());
        return switch (st.type()) {
            case "FIND_ITEM" -> {
                ResourceLocation id = ResourceLocation.tryParse(st.arg());
                Component name = id == null ? Component.literal(st.arg()) : BuiltInRegistries.ITEM.get(id).getDescription();
                yield Component.translatable("smileprotocol.mission.find_item", name);
            }
            case "OPEN_DOOR" -> Component.translatable("forced".equals(st.arg()) ? "smileprotocol.mission.force_door"
                    : "smileprotocol.mission.open_door");
            case "INSERT_FUSES" -> Component.translatable("smileprotocol.mission.insert_fuses");
            case "TURN_VALVES" -> Component.translatable("smileprotocol.mission.turn_valves");
            case "POWER" -> Component.translatable("smileprotocol.mission.power");
            case "REACH" -> Component.translatable("smileprotocol.mission.reach");
            case "SURVIVE" -> Component.translatable("smileprotocol.mission.survive");
            default -> Component.translatable("smileprotocol.mission.escape");
        };
    }

    private static List<ServerPlayer> players(MinecraftServer server, Group g) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p) && g.id().equals(SPPlayerData.of(p).getGroupId())) out.add(p);
        }
        return out;
    }

    /** Para el panel de depuracion. */
    public static List<String> debugLines(MinecraftServer server, Group g) {
        State s = state(server, g);
        if (s == null) return List.of();
        List<String> l = new ArrayList<>();
        l.add("mision " + s.def.id() + " paso " + (s.step + 1) + "/" + s.def.steps().size() + " hechos=" + s.facts);
        return l;
    }
}
