package com.smileprotocol.command;

import com.mojang.brigadier.CommandDispatcher;
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
import com.smileprotocol.registry.ModCapabilities;
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
import net.minecraftforge.event.RegisterCommandsEvent;

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

    private SPCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        registerPlayer(d);
        registerAdmin(d);
        registerBuild(d);
    }

    // /sp ----------------------------------------------------------------------

    private static void registerPlayer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sp")
                .then(Commands.literal("lobby")
                        .requires(s -> s.hasPermission(SPConfig.SP_LOBBY_PERMISSION.get()))
                        .executes(SPCommands::spLobby)));
    }

    private static int spLobby(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (ModCapabilities.inSP(player)) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.lobby.in_game"));
            return 0;
        }
        long now = player.server.getTickCount();
        long cooldown = SPConfig.SP_LOBBY_COOLDOWN.get() * 20L;
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
                .then(Commands.literal("event").executes(c -> setMode(c, LobbyMode.EVENT))));

        root.then(Commands.literal("start")
                .then(Commands.argument("elevator", StringArgumentType.word()).suggests(ELEVATORS)
                        .executes(c -> start(c, false))
                        .then(Commands.literal("force").executes(c -> start(c, true)))));

        root.then(Commands.literal("setlobby").executes(SPCommands::setLobby));

        root.then(stub("runs", "F06"));
        root.then(stub("newrun", "F06"));
        root.then(stub("groups", "F06"));
        root.then(stub("skipcinematic", "F16"));
        root.then(stub("zone", "F16"));
        root.then(stub("mission", "F14"));
        root.then(stub("spawn", "F11"));

        d.register(root);
    }

    private static int toLobby(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (ModCapabilities.inSP(p)) SPSessionManager.leave(p, SPSessionManager.Destination.LOBBY);
            else SPSessionManager.sendToLobby(p);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.lobby.sent", players.size()), true);
        return players.size();
    }

    private static int leave(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            if (ModCapabilities.inSP(p) || ModCapabilities.get(p).hasSaved()) {
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

        root.then(stub("lock", "F09"));
        root.then(stub("circuit", "F09"));
        root.then(stub("hide", "F10"));
        root.then(Commands.literal("noise").then(stub("test", "F11")));

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
