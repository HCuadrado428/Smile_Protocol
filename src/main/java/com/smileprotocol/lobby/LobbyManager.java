package com.smileprotocol.lobby;

import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.block.ElevatorButtonBlock;
import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.compat.ModCheck;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.network.ScreenFadePacket;
import com.smileprotocol.network.SyncLobbyBoardsPacket;
import com.smileprotocol.network.SyncReadyPacket;
import com.smileprotocol.registry.ModCapabilities;
import com.smileprotocol.session.SPSessionManager;
import com.smileprotocol.session.TestRoom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ascensores del lobby (README 6.2): cuenta quien esta en cada cabina, gestiona el «listo»,
 * la cuenta atras, las puertas, las pantallas y el contador del HUD.
 * <p>
 * Provisional hasta la fase 6: al bajar, el grupo va a la sala de pruebas
 * ({@link TestRoom}) en vez de a una instancia real con su {@code Run}.
 */
public final class LobbyManager {

    private static final int UPDATE_EVERY = 5;

    enum State { IDLE, COUNTDOWN, DESCENDING, RUNNING }

    /** Estado en memoria de un ascensor. Se reinicia con el servidor. */
    static final class Runtime {
        State state = State.IDLE;
        final Set<UUID> ready = new HashSet<>();
        Set<UUID> inside = new LinkedHashSet<>();
        int countdown;
        int afkTicks;
        int descentTimer;
        final List<UUID> members = new ArrayList<>();
        @Nullable Boolean doorsOpen;
        int shownReady;
        int shownTotal;
    }

    private static final Map<String, Runtime> RUNTIME = new HashMap<>();
    private static final Set<UUID> SHOWING_HUD = new HashSet<>();
    private static List<SyncLobbyBoardsPacket.Board> lastBoards = List.of();

    private LobbyManager() {}

    private static Runtime runtime(String id) {
        return RUNTIME.computeIfAbsent(id, k -> new Runtime());
    }

    // Tick ---------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % UPDATE_EVERY != 0) return;

        LobbyData data = LobbyData.get(server);
        RUNTIME.keySet().removeIf(id -> data.getElevator(id) == null);

        Set<UUID> hud = new HashSet<>();
        for (ElevatorDef def : data.getElevators()) {
            update(server, data, def, runtime(def.id()), hud);
        }
        // Quien ya no esta en ninguna cabina deja de ver el contador
        for (UUID id : SHOWING_HUD) {
            if (!hud.contains(id)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) SPNetwork.sendTo(p, SyncReadyPacket.hidden());
            }
        }
        SHOWING_HUD.clear();
        SHOWING_HUD.addAll(hud);

        if (t % 10 == 0) syncBoards(server, false);
        if (t % 20 == 0) LobbyProtection.tickPlayers(server, data);
    }

    private static void update(MinecraftServer server, LobbyData data, ElevatorDef def, Runtime rt, Set<UUID> hud) {
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> occupants = occupants(server, def);

        switch (rt.state) {
            case IDLE, COUNTDOWN -> {
                boolean closed = isClosed(data, def, rt);
                setDoors(overworld, def, rt, !closed);
                if (closed) {
                    for (ServerPlayer p : occupants) pushOut(p, def, "smileprotocol.elevator.closed");
                    rt.ready.clear();
                    rt.inside.clear();
                    rt.state = State.IDLE;
                    rt.afkTicks = 0;
                    return;
                }
                if (def.type() == ElevatorType.MINI) updateMini(def, rt, occupants);
                else updateMega(server, data, def, rt, occupants);
            }
            case DESCENDING -> {
                rt.descentTimer -= UPDATE_EVERY;
                if (rt.descentTimer <= 0) descend(server, def, rt);
            }
            case RUNNING -> {
                setDoors(overworld, def, rt, false);
                boolean anyInGame = false;
                for (UUID id : rt.members) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null && ModCapabilities.inSP(p)) {
                        anyInGame = true;
                        break;
                    }
                }
                if (!anyInGame) {
                    SPDebug.log("ascensor " + def.id() + " libre (partida terminada)");
                    rt.members.clear();
                    rt.ready.clear();
                    rt.state = State.IDLE;
                }
            }
        }

        // Contador del HUD para quien esta dentro
        if (rt.state == State.IDLE || rt.state == State.COUNTDOWN) {
            SyncReadyPacket hudPacket = readyPacket(def, rt);
            for (ServerPlayer p : occupants(server, def)) {
                SPNetwork.sendTo(p, hudPacket);
                hud.add(p.getUUID());
            }
        }
    }

    private static void updateMini(ElevatorDef def, Runtime rt, List<ServerPlayer> occupants) {
        int max = SPConfig.MINI_MAX_PLAYERS.get();
        int min = SPConfig.MINI_MIN_PLAYERS.get();

        // Cabina llena: no entra nadie mas
        if (occupants.size() > max) {
            List<ServerPlayer> keep = new ArrayList<>();
            for (ServerPlayer p : occupants) {
                if (rt.inside.contains(p.getUUID()) && keep.size() < max) keep.add(p);
            }
            for (ServerPlayer p : occupants) {
                if (!keep.contains(p)) {
                    if (keep.size() < max) keep.add(p);
                    else pushOut(p, def, "smileprotocol.elevator.full");
                }
            }
            occupants = keep;
        }

        rt.inside = ids(occupants);
        rt.ready.retainAll(rt.inside);
        rt.shownReady = rt.ready.size();
        rt.shownTotal = occupants.size();

        boolean enough = occupants.size() >= min;
        boolean allReady = enough && rt.ready.size() == occupants.size();

        if (allReady) {
            rt.afkTicks = 0;
            tickCountdown(def, rt, occupants);
            return;
        }
        cancelCountdown(rt, occupants);

        // AFK dentro de la cabina: todos listos menos uno durante demasiado tiempo
        if (enough && rt.ready.size() == occupants.size() - 1) {
            rt.afkTicks += UPDATE_EVERY;
            if (rt.afkTicks >= SPConfig.MINI_READY_TIMEOUT.get() * 20) {
                for (ServerPlayer p : occupants) {
                    if (!rt.ready.contains(p.getUUID())) pushOut(p, def, "smileprotocol.elevator.afk");
                }
                rt.afkTicks = 0;
            }
        } else {
            rt.afkTicks = 0;
        }
    }

    private static void updateMega(MinecraftServer server, LobbyData data, ElevatorDef def, Runtime rt,
                                   List<ServerPlayer> occupants) {
        List<ServerPlayer> ready = new ArrayList<>();
        for (ServerPlayer p : occupants) if (ModCheck.isOk(p)) ready.add(p);
        int total = lobbyEligibleCount(server, data);

        rt.inside = ids(occupants);
        rt.ready.clear();
        rt.ready.addAll(ids(ready));
        rt.shownReady = ready.size();
        rt.shownTotal = total;

        int min = SPConfig.MIN_GROUP_SIZE.get();
        int needed = (int) Math.ceil(total * SPConfig.REQUIRED_READY_RATIO.get());
        boolean go = ready.size() >= min && ready.size() >= needed;
        if (go) tickCountdown(def, rt, ready);
        else cancelCountdown(rt, occupants);
    }

    private static void tickCountdown(ElevatorDef def, Runtime rt, List<ServerPlayer> group) {
        if (rt.state == State.IDLE) {
            rt.state = State.COUNTDOWN;
            rt.countdown = SPConfig.DESCENT_COUNTDOWN.get() * 20;
            SPDebug.log("ascensor " + def.id() + ": cuenta atras con " + group.size() + " jugadores");
        } else {
            rt.countdown -= UPDATE_EVERY;
        }
        if (rt.countdown <= 0) startDescent(def, rt, group);
    }

    private static void cancelCountdown(Runtime rt, List<ServerPlayer> occupants) {
        if (rt.state == State.COUNTDOWN) {
            rt.state = State.IDLE;
            for (ServerPlayer p : occupants) {
                p.displayClientMessage(Component.translatable("smileprotocol.elevator.cancelled").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    // Bajada -------------------------------------------------------------------

    private static void startDescent(ElevatorDef def, Runtime rt, List<ServerPlayer> group) {
        if (group.isEmpty()) {
            rt.state = State.IDLE;
            return;
        }
        ServerLevel overworld = group.get(0).server.overworld();
        rt.state = State.DESCENDING;
        rt.descentTimer = 20;
        rt.members.clear();
        rt.members.addAll(ids(group));
        rt.ready.clear();

        // Se pulsa el boton, se cierran las puertas y la pantalla se funde a negro (README 6.2)
        if (def.button() != null) {
            overworld.playSound(null, def.button(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.8f);
        }
        setDoors(overworld, def, rt, false);
        for (ServerPlayer p : group) {
            SPNetwork.sendTo(p, new ScreenFadePacket(15, 40, 20));
            SPNetwork.sendTo(p, SyncReadyPacket.hidden());
        }
        SPDebug.log("ascensor " + def.id() + ": baja con " + group.size() + " jugadores");
    }

    private static void descend(MinecraftServer server, ElevatorDef def, Runtime rt) {
        ServerLevel instances = TestRoom.level(server);
        if (instances == null) {
            SPDebug.log("ascensor " + def.id() + ": no existe la dimension de instancias");
            rt.state = State.IDLE;
            rt.members.clear();
            return;
        }
        TestRoom.ensureBuilt(instances);
        // Provisional (fase 6): un grupo por bajada, todos a la sala de pruebas
        UUID group = UUID.randomUUID();
        for (UUID id : rt.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || ModCapabilities.inSP(p)) continue;
            SPSessionManager.enter(p, instances, TestRoom.spawnPos(), 180f, group);
        }
        server.overworld().playSound(null, def.exit(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1f, 0.5f);
        rt.state = State.RUNNING;
    }

    /** {@code /smileprotocol start <ascensor> [force]}. Devuelve un mensaje para el admin. */
    public static Component forceStart(MinecraftServer server, String id, boolean force) {
        LobbyData data = LobbyData.get(server);
        ElevatorDef def = data.getElevator(id);
        if (def == null) return Component.translatable("smileprotocol.elevator.unknown", id);
        Runtime rt = runtime(id);
        if (rt.state != State.IDLE && rt.state != State.COUNTDOWN) {
            return Component.translatable("smileprotocol.elevator.busy", def.name());
        }
        if (!force && isClosed(data, def, rt)) return Component.translatable("smileprotocol.elevator.closed_admin", def.name());
        List<ServerPlayer> group = new ArrayList<>();
        for (ServerPlayer p : occupants(server, def)) if (force || ModCheck.isOk(p)) group.add(p);
        if (group.isEmpty()) return Component.translatable("smileprotocol.elevator.empty", def.name());
        startDescent(def, rt, group);
        return Component.translatable("smileprotocol.elevator.started", def.name(), group.size());
    }

    // Boton de listo -------------------------------------------------------------

    public static void onButtonPressed(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        if (player.level() != server.overworld()) return;
        LobbyData data = LobbyData.get(server);
        ElevatorDef def = elevatorForButton(data, pos);
        if (def == null) {
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.unlinked"), true);
            return;
        }
        Runtime rt = runtime(def.id());
        if (!def.contains(player.position())) {
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.enter_first"), true);
            return;
        }
        if (def.type() == ElevatorType.MEGA) {
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.mega_auto"), true);
            return;
        }
        if (rt.state != State.IDLE && rt.state != State.COUNTDOWN) return;
        if (isClosed(data, def, rt)) {
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.closed"), true);
            return;
        }
        if (!canPlay(player)) {
            player.displayClientMessage(cannotPlayReason(player).copy().withStyle(ChatFormatting.RED), false);
            return;
        }
        UUID id = player.getUUID();
        if (rt.ready.remove(id)) {
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.not_ready").withStyle(ChatFormatting.GRAY), true);
        } else {
            rt.ready.add(id);
            player.displayClientMessage(Component.translatable("smileprotocol.elevator.ready").withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Nullable
    private static ElevatorDef elevatorForButton(LobbyData data, BlockPos pos) {
        for (ElevatorDef def : data.getElevators()) {
            if (pos.equals(def.button()) || def.cabin().inflatedBy(1).isInside(pos)) return def;
        }
        return null;
    }

    // Reglas ---------------------------------------------------------------------

    /** Un jugador cuenta para los ascensores si juega (no creativo ni espectador) y su cliente es valido. */
    public static boolean canPlay(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator() && !ModCapabilities.inSP(p) && ModCheck.isOk(p);
    }

    private static Component cannotPlayReason(ServerPlayer p) {
        if (!ModCheck.isOk(p)) return ModCheck.reason(p);
        return Component.translatable("smileprotocol.elevator.cannot_play");
    }

    private static boolean isClosed(LobbyData data, ElevatorDef def, Runtime rt) {
        if (def.type() == ElevatorType.MEGA) return data.getMode() == LobbyMode.FREE;
        if (data.getMode() == LobbyMode.EVENT) return true;
        return rt.state == State.IDLE && rt.inside.isEmpty() && runningMinis(data) >= SPConfig.MAX_MINI_RUNS.get();
    }

    /** Partidas mini en marcha (README 6.3, {@code max_mini_runs}). */
    private static int runningMinis(LobbyData data) {
        int n = 0;
        for (ElevatorDef def : data.getElevators()) {
            if (def.type() != ElevatorType.MINI) continue;
            State s = runtime(def.id()).state;
            if (s == State.DESCENDING || s == State.RUNNING) n++;
        }
        return n;
    }

    /** Jugadores dentro de la cabina que cuentan (no creativo, no espectador, no en partida). */
    private static List<ServerPlayer> occupants(MinecraftServer server, ElevatorDef def) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.overworld().players()) {
            if (p.isSpectator() || p.isCreative() || ModCapabilities.inSP(p)) continue;
            if (def.contains(p.position())) list.add(p);
        }
        return list;
    }

    /** Y del mega: jugadores en supervivencia o aventura que pueden jugar, en todo el lobby. */
    private static int lobbyEligibleCount(MinecraftServer server, LobbyData data) {
        int n = 0;
        for (ServerPlayer p : server.overworld().players()) {
            if (canPlay(p) && (data.getRegion() == null || data.inRegion(p.position()))) n++;
        }
        return n;
    }

    private static Set<UUID> ids(List<ServerPlayer> players) {
        Set<UUID> s = new LinkedHashSet<>();
        for (ServerPlayer p : players) s.add(p.getUUID());
        return s;
    }

    private static void pushOut(ServerPlayer p, ElevatorDef def, String messageKey) {
        float yaw = p.getYRot();
        SPSessionManager.teleport(p, p.server.overworld(), Vec3.atBottomCenterOf(def.exit()), yaw, p.getXRot());
        p.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD), true);
    }

    private static void setDoors(ServerLevel level, ElevatorDef def, Runtime rt, boolean open) {
        if (rt.doorsOpen != null && rt.doorsOpen == open) return;
        rt.doorsOpen = open;
        for (BlockPos d : def.doors()) ElevatorDoorBlock.setOpen(level, d, open);
        if (!def.doors().isEmpty()) {
            level.playSound(null, def.doors().get(0), open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 0.8f, 0.7f);
        }
    }

    // HUD y pantallas --------------------------------------------------------------

    private static SyncReadyPacket readyPacket(ElevatorDef def, Runtime rt) {
        int min = def.type() == ElevatorType.MINI ? SPConfig.MINI_MIN_PLAYERS.get() : SPConfig.MIN_GROUP_SIZE.get();
        Component status;
        int countdown = -1;
        if (rt.state == State.COUNTDOWN) {
            countdown = Math.max(0, (rt.countdown + 19) / 20);
            status = Component.translatable("smileprotocol.elevator.countdown", countdown);
        } else if (def.type() == ElevatorType.MINI && rt.shownTotal < min) {
            status = Component.translatable("smileprotocol.elevator.need_players", min);
        } else if (def.type() == ElevatorType.MEGA && rt.shownReady < min) {
            status = Component.translatable("smileprotocol.elevator.need_players", min);
        } else if (def.type() == ElevatorType.MEGA) {
            status = Component.translatable("smileprotocol.elevator.waiting_all");
        } else {
            status = Component.translatable("smileprotocol.elevator.press_ready");
        }
        return new SyncReadyPacket(true, Component.literal(def.name()), rt.shownReady, rt.shownTotal, countdown, status);
    }

    private static SyncLobbyBoardsPacket.Board board(LobbyData data, ElevatorDef def) {
        Runtime rt = runtime(def.id());
        Component status;
        int color;
        switch (rt.state) {
            case COUNTDOWN -> {
                status = Component.translatable("smileprotocol.board.countdown", Math.max(0, (rt.countdown + 19) / 20));
                color = 0xFFB020;
            }
            case DESCENDING -> {
                status = Component.translatable("smileprotocol.board.closing");
                color = 0xFFB020;
            }
            case RUNNING -> {
                status = Component.translatable("smileprotocol.board.running");
                color = 0xE04040;
            }
            default -> {
                if (isClosed(data, def, rt)) {
                    status = Component.translatable("smileprotocol.board.closed");
                    color = 0x9A9A9A;
                } else if (rt.shownTotal == 0) {
                    status = Component.translatable("smileprotocol.board.free");
                    color = 0x40D070;
                } else {
                    status = Component.translatable("smileprotocol.board.ready", rt.shownReady, rt.shownTotal);
                    color = 0xF0E040;
                }
            }
        }
        return new SyncLobbyBoardsPacket.Board(def.board(), Component.literal(def.name()), status, color);
    }

    private static SyncLobbyBoardsPacket boardsPacket(MinecraftServer server) {
        LobbyData data = LobbyData.get(server);
        List<SyncLobbyBoardsPacket.Board> list = new ArrayList<>();
        for (ElevatorDef def : data.getElevators()) if (def.board() != null) list.add(board(data, def));
        return new SyncLobbyBoardsPacket(list);
    }

    private static void syncBoards(MinecraftServer server, boolean force) {
        SyncLobbyBoardsPacket packet = boardsPacket(server);
        if (!force && packet.boards().equals(lastBoards) && server.getTickCount() % 100 != 0) return;
        lastBoards = packet.boards();
        for (ServerPlayer p : server.overworld().players()) SPNetwork.sendTo(p, packet);
    }

    public static void onLogin(ServerPlayer player) {
        SPNetwork.sendTo(player, boardsPacket(player.server));
    }

    public static void onLogout(ServerPlayer player) {
        UUID id = player.getUUID();
        for (Runtime rt : RUNTIME.values()) {
            rt.ready.remove(id);
            rt.inside.remove(id);
        }
        SHOWING_HUD.remove(id);
    }

    /** Al cambiar de modo, reevaluar enseguida puertas y pantallas. */
    public static void onModeChanged(MinecraftServer server) {
        for (Runtime rt : RUNTIME.values()) rt.doorsOpen = null;
        syncBoards(server, true);
    }

    public static List<String> debugLines(MinecraftServer server) {
        List<String> l = new ArrayList<>();
        for (ElevatorDef def : LobbyData.get(server).getElevators()) {
            Runtime rt = runtime(def.id());
            l.add(" " + def.id() + " [" + def.type() + "] " + rt.state + " dentro=" + rt.inside.size()
                    + " listos=" + rt.ready.size() + (rt.members.isEmpty() ? "" : " grupo=" + rt.members.size()));
        }
        return l;
    }

    // Registro de ascensores ---------------------------------------------------------

    /**
     * Registra (o sustituye) un ascensor a partir de su cabina. Busca sus puertas a un bloque
     * de la cabina, su boton dentro y su pantalla a dos bloques (README 5.1:
     * {@code /spadmin lobby elevator}).
     */
    public static ElevatorDef registerElevator(ServerLevel level, ElevatorType type, String id, String name, BoundingBox cabin) {
        List<BlockPos> doors = new ArrayList<>();
        BlockPos button = null;
        BlockPos board = null;
        double bestBoard = Double.MAX_VALUE;
        BoundingBox search = cabin.inflatedBy(2);
        BoundingBox doorSearch = cabin.inflatedBy(1);
        Vec3 center = Vec3.atCenterOf(cabin.getCenter());

        for (BlockPos p : BlockPos.betweenClosed(search.minX(), search.minY(), search.minZ(),
                search.maxX(), search.maxY(), search.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ElevatorDoorBlock && doorSearch.isInside(p)
                    && s.getValue(ElevatorDoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                doors.add(p.immutable());
            } else if (s.getBlock() instanceof ElevatorButtonBlock && doorSearch.isInside(p) && button == null) {
                button = p.immutable();
            } else if (s.getBlock() instanceof ElevatorBoardBlock) {
                double d = Vec3.atCenterOf(p).distanceToSqr(center);
                if (d < bestBoard) {
                    bestBoard = d;
                    board = p.immutable();
                }
            }
        }

        BlockPos exit;
        if (!doors.isEmpty()) {
            Direction out = level.getBlockState(doors.get(0)).getValue(ElevatorDoorBlock.FACING);
            BlockPos first = doors.get(0);
            BlockPos last = doors.get(doors.size() - 1);
            BlockPos mid = new BlockPos((first.getX() + last.getX()) / 2, first.getY(), (first.getZ() + last.getZ()) / 2);
            exit = mid.relative(out, 2);
        } else {
            exit = new BlockPos(cabin.getCenter().getX(), cabin.minY(), cabin.maxZ() + 2);
        }

        // Recorrido de cada hoja: telescopico si hay mas de dos (GeckoLib)
        ElevatorDoorBlockEntity.assignSlides(level, doors);

        ElevatorDef def = new ElevatorDef(id, type, name, cabin, List.copyOf(doors), button, board, exit);
        LobbyData data = LobbyData.get(level.getServer());
        data.putElevator(def);
        RUNTIME.remove(id);
        syncBoards(level.getServer(), true);
        SPDebug.log("ascensor registrado " + id + " " + type + " puertas=" + doors.size()
                + " boton=" + (button != null) + " pantalla=" + (board != null));
        return def;
    }
}
