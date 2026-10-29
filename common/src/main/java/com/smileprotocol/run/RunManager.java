package com.smileprotocol.run;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.difficulty.DifficultyScaler;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.session.SPSessionManager;
import com.smileprotocol.zone.ZoneSequence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crea, vigila y cierra las partidas (README 6.1, 6.3, 6.7, 6.8). Puede haber varias a la vez
 * (mini y mega) y no se afectan entre si.
 */
public final class RunManager {

    private static final RandomSource RANDOM = RandomSource.create();

    private RunManager() {}

    /** Resultado de intentar empezar una partida. */
    public record StartResult(@Nullable Run run, Component message) {
        public boolean ok() { return run != null; }
    }

    // Consultas ------------------------------------------------------------------------------

    public static List<Run> openRuns(RunData data) {
        List<Run> l = new ArrayList<>();
        for (Run r : data.runs.values()) if (!r.closed()) l.add(r);
        return l;
    }

    public static List<Run> openRuns(MinecraftServer server) {
        return openRuns(RunData.get(server));
    }

    public static List<RunData.ClearJob> clears(RunData data) {
        return data.clears;
    }

    @Nullable
    public static Run run(MinecraftServer server, String id) {
        return RunData.get(server).runs.get(id);
    }

    @Nullable
    public static Group group(MinecraftServer server, UUID groupId) {
        for (Run r : openRuns(server)) {
            Group g = r.group(groupId);
            if (g != null) return g;
        }
        return null;
    }

    @Nullable
    public static Group groupByLabel(MinecraftServer server, String label) {
        for (Run r : openRuns(server)) for (Group g : r.groups()) if (g.label().equalsIgnoreCase(label)) return g;
        return null;
    }

    @Nullable
    public static Run runOf(MinecraftServer server, Group g) {
        return run(server, g.runId());
    }

    /** Grupo en el que juega el jugador ahora mismo. */
    @Nullable
    public static Group groupOf(ServerPlayer player) {
        SPPlayerData d = SPPlayerData.of(player);
        return d.getGroupId() == null ? null : group(player.server, d.getGroupId());
    }

    /** Partida abierta que salio de un ascensor del lobby. */
    @Nullable
    public static Run activeRunFor(MinecraftServer server, String elevatorId) {
        for (Run r : openRuns(server)) if (r.elevatorId().equals(elevatorId)) return r;
        return null;
    }

    public static int activeMiniRuns(MinecraftServer server) {
        int n = 0;
        for (Run r : openRuns(server)) if (r.type() == RunType.MINI) n++;
        return n;
    }

    public static int activeGroups(MinecraftServer server) {
        int n = 0;
        for (Run r : openRuns(server)) n += r.groups().size();
        return n;
    }

    /** Huecos libres para grupos nuevos ({@code max_groups}, README 6.3). */
    public static int freeGroupSlots(MinecraftServer server) {
        return Math.max(0, SPConfig.get().max_groups - activeGroups(server));
    }

    public static boolean megaRunning(MinecraftServer server) {
        for (Run r : openRuns(server)) if (r.type() == RunType.MEGA) return true;
        return false;
    }

    /** Estado del mega ascensor aparte del modo del lobby. */
    public enum MegaStatus { OPEN, RUNNING, RESETTING, LOCKED }

    public static MegaStatus megaStatus(MinecraftServer server) {
        RunData data = RunData.get(server);
        if (megaRunning(server)) return MegaStatus.RUNNING;
        if (data.megaLocked) return MegaStatus.LOCKED;
        if (data.megaReopenAt > server.overworld().getGameTime()) return MegaStatus.RESETTING;
        return MegaStatus.OPEN;
    }

    /** Segundos hasta que se reabre el mega (0 si no espera). */
    public static int megaReopenSeconds(MinecraftServer server) {
        long left = RunData.get(server).megaReopenAt - server.overworld().getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    /** {@code max_plays_per_player} y {@code replayable} (README 6.8). */
    public static boolean canPlayMegaAgain(ServerPlayer player) {
        SPConfig.Values cfg = SPConfig.get();
        int plays = RunData.get(player.server).plays.getOrDefault(player.getUUID(), 0);
        if (!cfg.replayable && plays > 0) return false;
        return cfg.max_plays_per_player <= 0 || plays < cfg.max_plays_per_player;
    }

    /** Los eliminados y los que han terminado pueden volver al lobby con /sp lobby (README 5.1). */
    public static boolean canReturnToLobby(ServerPlayer player) {
        Group g = groupOf(player);
        return g != null && (g.state().isDone() || !g.isActive(player.getUUID()));
    }

    // Empezar ---------------------------------------------------------------------------------

    /**
     * Crea una partida y baja a los jugadores (README 6.3): forma los grupos, les asigna hueco,
     * coloca la zona de entrada de cada uno y los mete en la partida.
     */
    public static StartResult start(MinecraftServer server, RunType type, String elevatorId,
                                    List<ServerPlayer> players, boolean force) {
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return new StartResult(null, Component.translatable("smileprotocol.run.no_dimension"));
        RunData data = RunData.get(server);
        SPConfig.Values cfg = SPConfig.get();

        List<ServerPlayer> valid = new ArrayList<>();
        for (ServerPlayer p : players) if (!SPPlayerData.inSP(p)) valid.add(p);
        if (valid.isEmpty()) return new StartResult(null, Component.translatable("smileprotocol.run.nobody"));

        List<List<ServerPlayer>> groups;
        if (type == RunType.MINI) {
            groups = GroupManager.mini(valid);
        } else {
            groups = GroupManager.mega(valid, data.fixedGroups, RANDOM);
            if (groups.isEmpty() && force) groups = List.of(valid); // forzado con menos del minimo
        }
        if (groups.isEmpty()) {
            return new StartResult(null, Component.translatable("smileprotocol.elevator.need_players", cfg.min_group_size));
        }
        if (!force && type == RunType.MINI && activeMiniRuns(server) >= cfg.max_mini_runs) {
            return new StartResult(null, Component.translatable("smileprotocol.run.no_space", 1, 0));
        }
        if (!force && groups.size() > freeGroupSlots(server)) {
            return new StartResult(null, Component.translatable("smileprotocol.run.no_space", groups.size(), freeGroupSlots(server)));
        }

        long seed;
        if (type == RunType.MEGA && data.pendingMegaSeed != null) {
            seed = data.pendingMegaSeed;
            data.pendingMegaSeed = null;
        } else {
            seed = RANDOM.nextLong();
        }
        List<ResourceLocation> last = data.lastSequence.getOrDefault(elevatorId, List.of());
        List<ResourceLocation> sequence = ZoneSequence.generate(server, seed, last);
        if (sequence.isEmpty()) return new StartResult(null, Component.translatable("smileprotocol.run.no_zones"));

        data.counter++;
        String runId = "R" + data.counter;
        long now = server.overworld().getGameTime();
        Run run = new Run(runId, type, elevatorId, seed, sequence, now);

        Set<Integer> taken = new HashSet<>();
        int number = 0;
        for (List<ServerPlayer> members : groups) {
            number++;
            List<ResourceLocation> seq = sequence;
            if (type == RunType.MEGA && cfg.per_group_zones && number > 1) {
                seq = ZoneSequence.generate(server, seed + number * 104729L, sequence);
            }
            int slot = InstanceManager.allocateSlot(data, taken);
            taken.add(slot);
            DifficultyProfile difficulty = DifficultyScaler.forGroup(server, members.size());
            Group g = new Group(UUID.randomUUID(), runId, number, seq, difficulty, slot);
            for (ServerPlayer p : members) g.addMember(p.getUUID(), p.getGameProfile().getName());
            run.groups().add(g);
        }

        // Se guarda antes de colocar para que el hueco quede reservado aunque algo falle
        data.runs.put(runId, run);
        data.lastSequence.put(elevatorId, sequence);
        data.changed();

        // Primero se colocan todas las zonas de entrada; si alguna falla no baja nadie
        for (Group g : run.groups()) {
            if (InstanceManager.placeFirst(level, g) == null) {
                SmileProtocol.LOGGER.error("No se pudo colocar la zona de entrada de {}", g.label());
                for (Group other : run.groups()) InstanceManager.release(server, other);
                run.setState(RunState.ENDED);
                run.setClosed(true);
                data.runs.remove(runId);
                data.changed();
                return new StartResult(null, Component.translatable("smileprotocol.run.place_failed"));
            }
        }
        for (Group g : run.groups()) {
            ZonePlacement entry = g.current();
            for (UUID id : g.members().keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || entry == null) continue;
                SPSessionManager.enter(p, level, entry.entry(), entry.entryYaw(), g.id());
                com.smileprotocol.instance.FloorTitle.show(p, 0, entry.zoneId());
                // Equipo inicial (README 6.4): la linterna, encendida
                com.smileprotocol.interact.SPInventoryOps.give(p, new net.minecraft.world.item.ItemStack(
                        com.smileprotocol.registry.ModItems.FLASHLIGHT.get()));
                com.smileprotocol.motion.Flashlights.set(p, true);
                data.plays.merge(id, 1, Integer::sum);
                p.sendSystemMessage(Component.translatable("smileprotocol.run.joined", g.label(), g.members().size(),
                        Component.translatable("smileprotocol.run.type." + type.name().toLowerCase())).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        run.setState(RunState.RUNNING);
        data.changed();

        StringBuilder sizes = new StringBuilder();
        for (Group g : run.groups()) sizes.append(sizes.isEmpty() ? "" : "+").append(g.members().size());
        SPDebug.log("partida " + runId + " " + type + " desde " + elevatorId + " grupos=" + sizes + " semilla=" + seed
                + " zonas=" + sequence);
        return new StartResult(run, Component.translatable("smileprotocol.run.started", runId, run.groups().size(), sizes.toString()));
    }

    // Tick ---------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        InstanceManager.tick(server);
        if (server.getTickCount() % 20 != 0) return;

        RunData data = RunData.get(server);
        long now = server.overworld().getGameTime();
        SPConfig.Values cfg = SPConfig.get();

        for (Run run : openRuns(data)) {
            if (run.state() == RunState.RUNNING || run.state() == RunState.DESCENT) {
                for (Group g : run.groups()) if (!g.state().isDone()) checkGroup(server, data, g, now);
                if (run.allGroupsDone()) {
                    run.setState(RunState.ENDED);
                    run.setEndTime(now);
                    data.changed();
                    announceResults(server, run, now);
                    SPDebug.log("partida " + run.id() + " terminada");
                }
            } else if (run.state() == RunState.ENDED && !run.closed()) {
                if (now - run.endTime() >= cfg.mini_return_delay * 20L) close(server, data, run, now);
            }
        }

        // Mega: reabrir tras el plazo
        if (data.megaReopenAt >= 0 && data.megaReopenAt <= now) {
            data.megaReopenAt = -1;
            data.changed();
            SPDebug.log("mega ascensor reabierto con partida nueva");
        }
    }

    /** Desconexiones y salidas: un grupo sin nadie que juegue queda eliminado (README 6.7). */
    private static void checkGroup(MinecraftServer server, RunData data, Group g, long now) {
        boolean anyOnline = false;
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            SPPlayerData d = SPPlayerData.of(p);
            if (d.inSP() && g.id().equals(d.getGroupId())) {
                anyOnline = true;
            } else {
                // Ha salido de la partida por otra via
                g.setStatus(id, MemberStatus.LEFT);
                data.changed();
            }
        }
        if (g.activeMembers().isEmpty()) {
            finishGroup(server, g, GroupState.ELIMINATED, now);
            return;
        }
        if (anyOnline) {
            if (g.offlineSince() >= 0) {
                g.setOfflineSince(-1);
                data.changed();
            }
        } else if (g.offlineSince() < 0) {
            g.setOfflineSince(now);
            data.changed();
        } else if (now - g.offlineSince() >= SPConfig.get().disconnect_grace * 20L) {
            SPDebug.log(g.label() + ": nadie conectado durante " + SPConfig.get().disconnect_grace + " s");
            finishGroup(server, g, GroupState.ELIMINATED, now);
        }
    }

    private static void finishGroup(MinecraftServer server, Group g, GroupState state, long now) {
        if (g.state().isDone()) return;
        g.setState(state);
        g.setEndTime(now);
        RunData.get(server).changed();
        Component msg = Component.translatable("smileprotocol.run.group_" + state.name().toLowerCase(), g.label())
                .withStyle(state == GroupState.COMPLETED ? ChatFormatting.GREEN : ChatFormatting.RED);
        for (UUID id : g.members().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(msg);
        }
        SPDebug.log(g.label() + " " + state);
    }

    /** Resultados de la partida (README 6.8): zona alcanzada, tiempo y supervivientes por grupo. */
    private static void announceResults(MinecraftServer server, Run run, long now) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("smileprotocol.run.results", run.id()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Group g : run.groups()) {
            long end = g.endTime() >= 0 ? g.endTime() : now;
            long secs = Math.max(0, (end - run.startTime()) / 20);
            int survivors = g.state() == GroupState.COMPLETED ? g.activeMembers().size() : 0;
            MutableComponent line = Component.translatable("smileprotocol.run.results.group", g.number(),
                    Component.translatable("smileprotocol.run.state." + g.state().name().toLowerCase()),
                    g.zoneIndex(), g.lastIndex(), String.format("%d:%02d", secs / 60, secs % 60),
                    survivors, g.members().size());
            lines.add(line.withStyle(g.state() == GroupState.COMPLETED ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("smileprotocol.run.returning", SPConfig.get().mini_return_delay).withStyle(ChatFormatting.DARK_GRAY));
        for (Group g : run.groups()) {
            for (UUID id : g.members().keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) for (Component c : lines) p.sendSystemMessage(c);
            }
        }
    }

    /** Devuelve a todos al lobby, limpia las instancias y libera el ascensor (README 6.8). */
    private static void close(MinecraftServer server, RunData data, Run run, long now) {
        run.setClosed(true);
        for (Group g : run.groups()) {
            for (UUID id : g.members().keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                SPPlayerData d = SPPlayerData.of(p);
                if (d.inSP() && g.id().equals(d.getGroupId())) SPSessionManager.leave(p, SPSessionManager.Destination.LOBBY);
            }
            InstanceManager.release(server, g);
        }
        if (run.type() == RunType.MEGA) {
            if (SPConfig.get().auto_newrun) data.megaReopenAt = now + SPConfig.get().lobby_reset_delay * 20L;
            else data.megaLocked = true;
        }
        data.runs.remove(run.id());
        data.changed();
        SPDebug.log("partida " + run.id() + " cerrada; instancias en limpieza");
    }

    // Eventos de jugador -------------------------------------------------------------------

    /** Lo llama {@link SPSessionManager#leave} antes de borrar el grupo del jugador. */
    public static void onPlayerLeft(ServerPlayer player, @Nullable UUID groupId) {
        if (groupId == null) return;
        Group g = group(player.server, groupId);
        if (g == null || !g.isActive(player.getUUID())) return;
        g.setStatus(player.getUUID(), MemberStatus.LEFT);
        RunData.get(player.server).changed();
        if (!g.state().isDone() && g.activeMembers().isEmpty()) {
            finishGroup(player.server, g, GroupState.ELIMINATED, player.server.overworld().getGameTime());
        }
    }

    /**
     * Reincorporacion al conectarse (README 5.3, 6.7). Devuelve false si el jugador ya no tiene
     * partida a la que volver (y hay que sacarlo).
     */
    public static boolean onRejoin(ServerPlayer player) {
        SPPlayerData d = SPPlayerData.of(player);
        Group g = d.getGroupId() == null ? null : group(player.server, d.getGroupId());
        if (g == null || !g.isActive(player.getUUID())) return false;
        ZonePlacement zone = g.current();
        ServerLevel level = player.server.getLevel(SPDimension.SP_DIM);
        // Si el grupo ha cambiado de zona mientras estaba fuera, a la entrada de la actual
        if (zone != null && level != null && (player.level() != level || !zone.contains(player.position()))) {
            SPSessionManager.teleport(player, level, zone.entry(), zone.entryYaw(), 0f);
        }
        player.sendSystemMessage(Component.translatable("smileprotocol.run.rejoined", g.label(), g.stateLabel())
                .withStyle(ChatFormatting.DARK_AQUA));
        return true;
    }

    // Administracion -----------------------------------------------------------------------

    /** {@code /smileprotocol runs end <run>}. */
    public static Component endRun(MinecraftServer server, String id) {
        Run run = run(server, id);
        if (run == null || run.closed()) return Component.translatable("smileprotocol.run.unknown", id);
        long now = server.overworld().getGameTime();
        for (Group g : run.groups()) if (!g.state().isDone()) finishGroup(server, g, GroupState.ELIMINATED, now);
        return Component.translatable("smileprotocol.run.ending", id);
    }

    /** {@code /smileprotocol newrun [semilla]} (README 6.8). */
    public static Component newRun(MinecraftServer server, @Nullable Long seed) {
        RunData data = RunData.get(server);
        if (megaRunning(server)) return Component.translatable("smileprotocol.run.mega_busy");
        data.pendingMegaSeed = seed;
        data.megaLocked = false;
        data.megaReopenAt = -1;
        data.changed();
        SPDebug.log("newrun " + (seed == null ? "(semilla aleatoria)" : seed));
        return Component.translatable("smileprotocol.run.newrun", seed == null ? "-" : String.valueOf(seed));
    }

    /** {@code /smileprotocol zone <grupo> <n|next>}: mas alla de la ultima zona, el grupo termina. */
    public static Component setZone(MinecraftServer server, Group g, int index) {
        if (g.state().isDone()) return Component.translatable("smileprotocol.run.group_done", g.label());
        if (index > g.lastIndex()) {
            finishGroup(server, g, GroupState.COMPLETED, server.overworld().getGameTime());
            return Component.translatable("smileprotocol.run.group_completed", g.label());
        }
        if (!InstanceManager.advance(server, g, index)) return Component.translatable("smileprotocol.run.place_failed");
        return Component.translatable("smileprotocol.run.zone_set", g.label(), index, g.sequence().get(index).toString());
    }

    // Grupos fijados (README 6.3) ----------------------------------------------------------------

    public static List<List<UUID>> fixedGroups(MinecraftServer server) {
        return RunData.get(server).fixedGroups;
    }

    /** Fija (o sustituye) el grupo {@code n} (1..) con esos jugadores; los quita de los demas. */
    public static void setFixedGroup(MinecraftServer server, int n, List<UUID> players) {
        RunData data = RunData.get(server);
        for (List<UUID> g : data.fixedGroups) g.removeAll(players);
        while (data.fixedGroups.size() < n) data.fixedGroups.add(new ArrayList<>());
        data.fixedGroups.get(n - 1).clear();
        data.fixedGroups.get(n - 1).addAll(players);
        data.fixedGroups.removeIf(List::isEmpty);
        data.changed();
    }

    public static void moveToFixedGroup(MinecraftServer server, UUID player, int n) {
        RunData data = RunData.get(server);
        for (List<UUID> g : data.fixedGroups) g.remove(player);
        while (data.fixedGroups.size() < n) data.fixedGroups.add(new ArrayList<>());
        data.fixedGroups.get(n - 1).add(player);
        data.fixedGroups.removeIf(List::isEmpty);
        data.changed();
    }

    public static void clearFixedGroups(MinecraftServer server) {
        RunData data = RunData.get(server);
        data.fixedGroups.clear();
        data.changed();
    }

    // Depuracion ---------------------------------------------------------------------------

    public static List<String> debugLines(MinecraftServer server, ServerPlayer viewer) {
        List<String> l = new ArrayList<>();
        List<Run> runs = openRuns(server);
        l.add("partidas: " + runs.size() + " · grupos " + activeGroups(server) + "/" + SPConfig.get().max_groups
                + " · mega " + megaStatus(server));
        for (Run r : runs) {
            StringBuilder sb = new StringBuilder(" " + r.id() + " " + r.type() + " " + r.state());
            for (Group g : r.groups()) sb.append(" [").append(g.number()).append(":").append(g.stateLabel())
                    .append(" ").append(g.activeMembers().size()).append("/").append(g.members().size()).append("]");
            l.add(sb.toString());
        }
        Group mine = groupOf(viewer);
        if (mine != null) {
            l.add("mi grupo: " + mine.label() + " hueco " + mine.slot() + " · " + mine.difficulty().describe());
            ZonePlacement z = mine.current();
            if (z != null) l.add(" zona " + z.index() + "/" + mine.lastIndex() + " " + z.zoneId().getPath()
                    + " mobs " + z.mobSpawns().size() + " fusibles " + z.fuses().size()
                    + (mine.next() != null ? " · precargada " + mine.next().zoneId().getPath() : ""));
        }
        l.addAll(InstanceManager.debugLines(server));
        return l;
    }
}
