package com.smileprotocol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.lobby.ElevatorDef;
import com.smileprotocol.lobby.ElevatorType;
import com.smileprotocol.lobby.LobbyBuilder;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.lobby.LobbyManager;
import com.smileprotocol.lobby.LobbyMode;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.session.SPSessionManager;
import com.smileprotocol.session.TestRoom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comandos (README 5.1).
 * <ul>
 *   <li>{@code /sp} — jugadores (nivel configurable, 0 por defecto).</li>
 *   <li>{@code /smileprotocol} — administradores (nivel 2): lobby, modo, ascensores...</li>
 *   <li>{@code /spadmin} — construccion y depuracion (nivel 2).</li>
 * </ul>
 * Los subcomandos de fases posteriores responden en que fase se implementan.
 */
public final class SPCommands {

    /** Nivel de permiso de administrador (README 5.1). */
    public static final int ADMIN_LEVEL = 2;

    private static final Map<UUID, Long> LOBBY_COOLDOWN = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> ELEVATORS = (ctx, b) ->
            SharedSuggestionProvider.suggest(LobbyData.get(ctx.getSource().getServer()).getElevators()
                    .stream().map(ElevatorDef::id), b);

    private static final SuggestionProvider<CommandSourceStack> RUNS = (ctx, b) ->
            SharedSuggestionProvider.suggest(RunManager.openRuns(ctx.getSource().getServer()).stream().map(Run::id), b);

    private static final SuggestionProvider<CommandSourceStack> GROUPS = (ctx, b) ->
            SharedSuggestionProvider.suggest(RunManager.openRuns(ctx.getSource().getServer()).stream()
                    .flatMap(r -> r.groups().stream()).map(Group::label), b);

    private SPCommands() {}

    /** Lo llama cada loader al registrar los comandos. */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerPlayer(d);
        registerAdmin(d);
        registerBuild(d);
    }

    // /sp ----------------------------------------------------------------------

    private static void registerPlayer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sp")
                .then(Commands.literal("lobby")
                        .requires(s -> s.hasPermission(SPConfig.get().sp_lobby_permission))
                        .executes(SPCommands::spLobby))
                .then(Commands.literal("records").executes(c -> {
                    for (Component l : com.smileprotocol.score.ScoreManager.recordLines(c.getSource().getServer())) {
                        c.getSource().sendSuccess(() -> l, false);
                    }
                    return 1;
                }))
                .then(Commands.literal("nota").then(Commands.argument("texto", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!com.smileprotocol.debug.Playtest.active()) {
                        c.getSource().sendFailure(Component.translatable("smileprotocol.playtest.inactive"));
                        return 0;
                    }
                    com.smileprotocol.debug.Playtest.note(p, StringArgumentType.getString(c, "texto"));
                    c.getSource().sendSuccess(() -> Component.translatable("smileprotocol.playtest.noted"), false);
                    return 1;
                })))
                .then(Commands.literal("logros").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (Component l : com.smileprotocol.score.ScoreManager.achievementLines(p)) c.getSource().sendSuccess(() -> l, false);
                    return 1;
                })));
    }

    private static int spLobby(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (SPPlayerData.inSP(player)) {
            // Los eliminados y los que han terminado si pueden (README 5.1)
            if (RunManager.canReturnToLobby(player)) {
                SPSessionManager.leave(player, SPSessionManager.Destination.LOBBY);
                return 1;
            }
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.lobby.in_game"));
            return 0;
        }
        long now = player.server.getTickCount();
        long cooldown = SPConfig.get().sp_lobby_cooldown * 20L;
        Long last = LOBBY_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < cooldown && !player.hasPermissions(ADMIN_LEVEL)) {
            long secs = (cooldown - (now - last) + 19) / 20;
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.lobby.cooldown", secs));
            return 0;
        }
        LOBBY_COOLDOWN.put(player.getUUID(), now);
        SPSessionManager.sendToLobby(player);
        return 1;
    }

    // /smileprotocol -----------------------------------------------------------

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("smileprotocol")
                .requires(s -> s.hasPermission(ADMIN_LEVEL));

        root.then(Commands.literal("lobby")
                .executes(c -> toLobby(c, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(c -> toLobby(c, EntityArgument.getPlayers(c, "players")))));

        root.then(Commands.literal("leave")
                .executes(c -> leave(c, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(c -> leave(c, EntityArgument.getPlayers(c, "players")))));

        root.then(Commands.literal("mode")
                .executes(SPCommands::showMode)
                .then(Commands.literal("free").executes(c -> setMode(c, LobbyMode.FREE)))
                .then(Commands.literal("event").executes(c -> setMode(c, LobbyMode.EVENT)))
                .then(Commands.literal("closed").executes(c -> setMode(c, LobbyMode.CLOSED))));

        root.then(Commands.literal("start")
                .then(Commands.argument("elevator", StringArgumentType.word()).suggests(ELEVATORS)
                        .executes(c -> start(c, false))
                        .then(Commands.literal("force").executes(c -> start(c, true)))));

        root.then(Commands.literal("setlobby").executes(SPCommands::setLobby));

        root.then(Commands.literal("runs")
                .executes(SPCommands::listRuns)
                .then(Commands.literal("list").executes(SPCommands::listRuns))
                .then(Commands.literal("end")
                        .then(Commands.argument("run", StringArgumentType.word()).suggests(RUNS)
                                .executes(SPCommands::endRun))));

        root.then(Commands.literal("playtest")
                .then(Commands.literal("start").executes(c -> {
                    var f = com.smileprotocol.debug.Playtest.start(c.getSource().getServer(), c.getSource().getTextName());
                    c.getSource().getServer().getPlayerList().broadcastSystemMessage(
                            Component.translatable("smileprotocol.playtest.started").withStyle(ChatFormatting.AQUA), false);
                    c.getSource().sendSuccess(() -> Component.literal(f.toString()), true);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(c -> {
                    var f = com.smileprotocol.debug.Playtest.stop(c.getSource().getServer());
                    if (f == null) {
                        c.getSource().sendFailure(Component.translatable("smileprotocol.playtest.inactive"));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("smileprotocol.playtest.stopped", f.toString()), true);
                    return 1;
                })));
        root.then(Commands.literal("records").then(Commands.literal("clear").executes(c -> {
            com.smileprotocol.score.ScoreData.get(c.getSource().getServer()).clearTop();
            com.smileprotocol.score.ScoreManager.refreshBoard(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.translatable("smileprotocol.score.cleared"), true);
            return 1;
        })));
        root.then(Commands.literal("newrun")
                .executes(c -> newRun(c, null))
                .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(c -> newRun(c, LongArgumentType.getLong(c, "seed")))));

        root.then(Commands.literal("groups")
                .executes(SPCommands::listGroups)
                .then(Commands.literal("list").executes(SPCommands::listGroups))
                .then(Commands.literal("set")
                        .then(Commands.argument("group", IntegerArgumentType.integer(1, 64))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(SPCommands::setGroup))))
                .then(Commands.literal("move")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("group", IntegerArgumentType.integer(1, 64))
                                        .executes(SPCommands::moveGroup))))
                .then(Commands.literal("clear").executes(SPCommands::clearGroups)));

        root.then(stub("skipcinematic", "F16"));
        root.then(Commands.literal("zone")
                .then(Commands.argument("group", StringArgumentType.string()).suggests(GROUPS)
                        .then(Commands.literal("next").executes(c -> zone(c, -1)))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 64))
                                .executes(c -> zone(c, IntegerArgumentType.getInteger(c, "index"))))));
        root.then(stub("mission", "F14"));
        root.then(stub("spawn", "F11"));

        d.register(root);
    }

    private static int toLobby(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (SPPlayerData.inSP(p)) SPSessionManager.leave(p, SPSessionManager.Destination.LOBBY);
            else SPSessionManager.sendToLobby(p);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.lobby.sent", players.size()), true);
        return players.size();
    }

    private static int leave(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            if (SPPlayerData.inSP(p) || SPPlayerData.of(p).hasSaved()) {
                SPSessionManager.leave(p, SPSessionManager.Destination.LOBBY);
                n++;
            }
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.leave.done", count), true);
        return n;
    }

    private static int showMode(CommandContext<CommandSourceStack> ctx) {
        LobbyMode mode = LobbyData.get(ctx.getSource().getServer()).getMode();
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.mode.current",
                Component.translatable("smileprotocol.mode." + mode.name().toLowerCase())), false);
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, LobbyMode mode) {
        MinecraftServer server = ctx.getSource().getServer();
        LobbyData.get(server).setMode(mode);
        LobbyManager.onModeChanged(server);
        SPDebug.log("modo del lobby: " + mode);
        Component name = Component.translatable("smileprotocol.mode." + mode.name().toLowerCase());
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.mode.set", name), true);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("smileprotocol.mode.announce." + mode.name().toLowerCase())
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, boolean force) {
        String id = StringArgumentType.getString(ctx, "elevator");
        Component result = LobbyManager.forceStart(ctx.getSource().getServer(), id, force);
        ctx.getSource().sendSuccess(() -> result, true);
        return 1;
    }

    private static int setLobby(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (player.level() != player.server.overworld()) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.setlobby.overworld"));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        LobbyData.get(player.server).setSpawn(pos, player.getYRot());
        player.server.overworld().setDefaultSpawnPos(pos, player.getYRot());
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.setlobby.done", pos.toShortString()), true);
        return 1;
    }

    private static int listRuns(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<Run> runs = RunManager.openRuns(server);
        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.translatable("smileprotocol.command.runs.header", runs.size(),
                RunManager.activeGroups(server), SPConfig.get().max_groups,
                Component.translatable("smileprotocol.mega." + RunManager.megaStatus(server).name().toLowerCase())), false);
        long now = server.overworld().getGameTime();
        for (Run r : runs) {
            long secs = (now - r.startTime()) / 20;
            s.sendSuccess(() -> Component.literal(" " + r.id() + " · " + r.type() + " · " + r.elevatorId() + " · " + r.state()
                    + " · " + String.format("%d:%02d", secs / 60, secs % 60) + " · semilla " + r.seed())
                    .withStyle(ChatFormatting.YELLOW), false);
            for (Group g : r.groups()) {
                StringBuilder names = new StringBuilder();
                g.members().forEach((id, st) -> names.append(names.isEmpty() ? "" : ", ").append(g.name(id))
                        .append(st == com.smileprotocol.run.MemberStatus.ACTIVE ? ""
                                : st == com.smileprotocol.run.MemberStatus.DEAD ? " (muerto)" : " (fuera)"));
                s.sendSuccess(() -> Component.literal("   " + g.label() + " · " + g.stateLabel() + " · hueco " + g.slot()
                        + " · " + names), false);
            }
        }
        return runs.size();
    }

    private static int endRun(CommandContext<CommandSourceStack> ctx) {
        Component result = RunManager.endRun(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "run"));
        ctx.getSource().sendSuccess(() -> result, true);
        return 1;
    }

    private static int newRun(CommandContext<CommandSourceStack> ctx, @Nullable Long seed) {
        Component result = RunManager.newRun(ctx.getSource().getServer(), seed);
        ctx.getSource().sendSuccess(() -> result, true);
        LobbyManager.onModeChanged(ctx.getSource().getServer());
        return 1;
    }

    private static int listGroups(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<List<UUID>> fixed = RunManager.fixedGroups(server);
        CommandSourceStack s = ctx.getSource();
        if (fixed.isEmpty()) {
            s.sendSuccess(() -> Component.translatable("smileprotocol.command.groups.none"), false);
        } else {
            for (int i = 0; i < fixed.size(); i++) {
                int n = i + 1;
                StringBuilder names = new StringBuilder();
                for (UUID id : fixed.get(i)) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    names.append(names.isEmpty() ? "" : ", ").append(p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8));
                }
                s.sendSuccess(() -> Component.translatable("smileprotocol.command.groups.line", n, names.toString()), false);
            }
        }
        // Reparto que saldria ahora con los del lobby
        List<Integer> sizes = com.smileprotocol.run.GroupManager.groupSizes(lobbyPlayers(server), SPConfig.get().min_group_size);
        s.sendSuccess(() -> Component.translatable("smileprotocol.command.groups.preview", lobbyPlayers(server),
                sizes.isEmpty() ? "-" : sizes.toString()), false);
        return fixed.size();
    }

    private static int lobbyPlayers(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.overworld().players()) if (LobbyManager.canPlay(p)) n++;
        return n;
    }

    private static int setGroup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int n = IntegerArgumentType.getInteger(ctx, "group");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        List<UUID> ids = players.stream().map(ServerPlayer::getUUID).toList();
        RunManager.setFixedGroup(ctx.getSource().getServer(), n, ids);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.groups.set", n, ids.size()), true);
        if (ids.size() < SPConfig.get().min_group_size) {
            ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.groups.small",
                    SPConfig.get().min_group_size).withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private static int moveGroup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int n = IntegerArgumentType.getInteger(ctx, "group");
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RunManager.moveToFixedGroup(ctx.getSource().getServer(), p.getUUID(), n);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.groups.moved",
                p.getGameProfile().getName(), n), true);
        return 1;
    }

    private static int clearGroups(CommandContext<CommandSourceStack> ctx) {
        RunManager.clearFixedGroups(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.groups.cleared"), true);
        return 1;
    }

    private static int zone(CommandContext<CommandSourceStack> ctx, int index) {
        MinecraftServer server = ctx.getSource().getServer();
        String label = StringArgumentType.getString(ctx, "group");
        Group g = RunManager.groupByLabel(server, label);
        if (g == null) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.run.unknown_group", label));
            return 0;
        }
        int target = index < 0 ? g.zoneIndex() + 1 : index;
        Component result = RunManager.setZone(server, g, target);
        ctx.getSource().sendSuccess(() -> result, true);
        return 1;
    }

    // /spadmin -----------------------------------------------------------------

    private static void registerBuild(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spadmin")
                .requires(s -> s.hasPermission(ADMIN_LEVEL));

        root.then(Commands.literal("lobby")
                .then(Commands.literal("build")
                        .executes(c -> buildPreview(c, BlockPos.containing(c.getSource().getPosition())))
                        .then(Commands.literal("confirm")
                                .executes(c -> build(c, BlockPos.containing(c.getSource().getPosition()))))
                        .then(Commands.argument("spawn", BlockPosArgument.blockPos())
                                .executes(c -> buildPreview(c, BlockPosArgument.getLoadedBlockPos(c, "spawn")))
                                .then(Commands.literal("confirm")
                                        .executes(c -> build(c, BlockPosArgument.getLoadedBlockPos(c, "spawn"))))))
                .then(Commands.literal("region")
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .executes(SPCommands::setRegion))))
                .then(Commands.literal("elevator")
                        .then(elevatorType("mega", ElevatorType.MEGA))
                        .then(elevatorType("mini", ElevatorType.MINI))
                        .then(elevatorType("tutorial", ElevatorType.TUTORIAL))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(ELEVATORS)
                                        .executes(SPCommands::removeElevator))))
                .then(Commands.literal("info").executes(SPCommands::lobbyInfo)));

        root.then(Commands.literal("debug")
                .then(Commands.literal("on").executes(c -> debug(c, true)))
                .then(Commands.literal("off").executes(c -> debug(c, false))));

        // Provisional hasta la fase 6: entrar y salir de una partida de prueba
        root.then(Commands.literal("test")
                .then(Commands.literal("enter")
                        .executes(c -> testEnter(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> testEnter(c, EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("leave")
                        .executes(c -> leave(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> leave(c, EntityArgument.getPlayers(c, "players"))))));

        // Herramientas de construccion mirando un bloque (README 5.1, 14, 15)
        root.then(Commands.literal("lock").then(Commands.literal("set")
                .then(Commands.argument("lock_id", StringArgumentType.word())
                        .executes(c -> MechanismCommands.lockSet(c, false))
                        .then(Commands.literal("consume").executes(c -> MechanismCommands.lockSet(c, true))))));
        root.then(Commands.literal("circuit").then(Commands.literal("set")
                .then(Commands.argument("circuit_id", StringArgumentType.word())
                        .executes(c -> MechanismCommands.circuitSet(c, -1))
                        .then(Commands.argument("fuses", IntegerArgumentType.integer(1, 4))
                                .executes(c -> MechanismCommands.circuitSet(c, IntegerArgumentType.getInteger(c, "fuses")))))));
        root.then(Commands.literal("door").then(Commands.literal("set")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(MechanismCommands.DOOR_TYPES)
                        .executes(c -> MechanismCommands.doorSet(c, ""))
                        .then(Commands.argument("param", StringArgumentType.word())
                                .executes(c -> MechanismCommands.doorSet(c, StringArgumentType.getString(c, "param")))))));
        root.then(Commands.literal("valve").then(Commands.literal("set")
                .then(Commands.argument("group", StringArgumentType.word())
                        .executes(c -> MechanismCommands.valveSet(c, 0, false))
                        .then(Commands.argument("order", IntegerArgumentType.integer(0, 16))
                                .executes(c -> MechanismCommands.valveSet(c, IntegerArgumentType.getInteger(c, "order"), false))
                                .then(Commands.literal("coop")
                                        .executes(c -> MechanismCommands.valveSet(c, IntegerArgumentType.getInteger(c, "order"), true)))))));
        root.then(Commands.literal("hide").then(Commands.literal("set")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(MechanismCommands.HIDE_TYPES)
                        .executes(MechanismCommands::hideSet))));
        root.then(Commands.literal("give")
                .then(Commands.argument("item", StringArgumentType.word()).suggests(MechanismCommands.GIVE_ITEMS)
                        .executes(c -> MechanismCommands.give(c, ""))
                        .then(Commands.argument("param", StringArgumentType.word())
                                .executes(c -> MechanismCommands.give(c, StringArgumentType.getString(c, "param"))))));
        root.then(Commands.literal("noise").then(Commands.literal("test")
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.5f, 64f))
                        .executes(MechanismCommands::noiseTest))));
        // Mobs (fase 11): aparecer uno, quitar los de alrededor y derribarse para probar
        root.then(Commands.literal("mob")
                .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"berserker", "smiler_test", "smiler_kill"}, b))
                        .executes(MechanismCommands::mobSpawn)))
                .then(Commands.literal("clear").executes(MechanismCommands::mobClear))
                .then(Commands.literal("down").executes(MechanismCommands::selfDown))
                // remate y muerte instantanea del mob mas cercano (fase 12)
                .then(Commands.literal("execute").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(c -> MechanismCommands.mobSequence(c, true))))
                .then(Commands.literal("kill").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(c -> MechanismCommands.mobSequence(c, false)))));
        // Derribado, reanimacion y muerte de un jugador (README 24.1)
        root.then(Commands.literal("player").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                .then(Commands.literal("down").executes(c -> MechanismCommands.playerState(c, "down")))
                .then(Commands.literal("revive").executes(c -> MechanismCommands.playerState(c, "revive")))
                .then(Commands.literal("kill").executes(c -> MechanismCommands.playerState(c, "kill")))));

        d.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> elevatorType(String literal, ElevatorType type) {
        return Commands.literal(literal)
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .executes(c -> addElevator(c, type)))));
    }

    private static int buildPreview(CommandContext<CommandSourceStack> ctx, BlockPos spawn) {
        BoundingBox box = LobbyBuilder.regionFor(LobbyBuilder.originFor(spawn));
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.build.preview",
                box.getXSpan(), box.getYSpan(), box.getZSpan(),
                new BlockPos(box.minX(), box.minY(), box.minZ()).toShortString(),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ()).toShortString()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, BlockPos spawn) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        if (ctx.getSource().getLevel() != level) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.setlobby.overworld"));
            return 0;
        }
        Component result = LobbyBuilder.build(level, spawn);
        ctx.getSource().sendSuccess(() -> result, true);
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) SPSessionManager.sendToLobby(p);
        return 1;
    }

    private static int setRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "pos1");
        BlockPos b = BlockPosArgument.getLoadedBlockPos(ctx, "pos2");
        LobbyData.get(ctx.getSource().getServer()).setRegion(BoundingBox.fromCorners(a, b));
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.region.set",
                a.toShortString(), b.toShortString()), true);
        return 1;
    }

    private static int addElevator(CommandContext<CommandSourceStack> ctx, ElevatorType type) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "pos1");
        BlockPos b = BlockPosArgument.getLoadedBlockPos(ctx, "pos2");
        ElevatorDef def = LobbyManager.registerElevator(ctx.getSource().getServer().overworld(), type, id, id,
                BoundingBox.fromCorners(a, b));
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.elevator.set", id,
                def.doors().size(), def.button() != null ? "✔" : "✘", def.board() != null ? "✔" : "✘"), true);
        return 1;
    }

    private static int removeElevator(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        LobbyData.get(ctx.getSource().getServer()).removeElevator(id);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.elevator.removed", id), true);
        return 1;
    }

    private static int lobbyInfo(CommandContext<CommandSourceStack> ctx) {
        LobbyData data = LobbyData.get(ctx.getSource().getServer());
        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.literal("Modo: " + data.getMode()
                + " · aparición: " + (data.getSpawn() == null ? "-" : data.getSpawn().toShortString())
                + " · región: " + (data.getRegion() == null ? "-" : "sí")), false);
        for (ElevatorDef e : data.getElevators()) {
            s.sendSuccess(() -> Component.literal(" " + e.id() + " (" + e.type() + ") «" + e.name() + "» puertas="
                    + e.doors().size() + " botón=" + (e.button() != null) + " pantalla=" + (e.board() != null)), false);
        }
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        SPDebug.setEnabled(p, on);
        ctx.getSource().sendSuccess(() -> Component.translatable(on ? "smileprotocol.command.debug.on" : "smileprotocol.command.debug.off"), false);
        return 1;
    }

    private static int testEnter(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        ServerLevel level = TestRoom.level(ctx.getSource().getServer());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("No existe la dimensión smileprotocol:smile_protocol"));
            return 0;
        }
        TestRoom.ensureBuilt(level);
        UUID group = UUID.randomUUID();
        for (ServerPlayer p : players) SPSessionManager.enter(p, level, TestRoom.spawnPos(), 180f, group);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.test.enter", players.size()), true);
        return players.size();
    }

    // Utilidades ---------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> stub(String name, String phase) {
        return Commands.literal(name).executes(c -> notYet(c, phase));
    }

    private static int notYet(CommandContext<CommandSourceStack> ctx, String phase) {
        ctx.getSource().sendFailure(Component.translatable("commands.smileprotocol.not_implemented", phase));
        return 0;
    }
}
