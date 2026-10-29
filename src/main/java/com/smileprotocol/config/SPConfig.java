package com.smileprotocol.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Configuracion comun ({@code config/smileprotocol-common.toml}, README 24).
 * Se iran anadiendo valores por fases.
 */
public final class SPConfig {

    public static final ForgeConfigSpec SPEC;

    // Sesion (README 5)
    public static final ForgeConfigSpec.BooleanValue RESTORE_INVENTORY;
    public static final ForgeConfigSpec.IntValue SP_LOBBY_PERMISSION;
    public static final ForgeConfigSpec.IntValue SP_LOBBY_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue TELEPORT_TO_LOBBY_ON_JOIN;

    // Partida (README 8, 9)
    public static final ForgeConfigSpec.IntValue MAX_HEALTH;
    public static final ForgeConfigSpec.BooleanValue FORCE_FIRST_PERSON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_COMMANDS;

    // Aislamiento de mods (README 7)
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQUIRED_MODS;
    public static final ForgeConfigSpec.IntValue HANDSHAKE_TIMEOUT;

    // Lobby (README 4.2, 6.2)
    public static final ForgeConfigSpec.ConfigValue<String> LOBBY_GAMEMODE;
    public static final ForgeConfigSpec.IntValue MINI_MIN_PLAYERS;
    public static final ForgeConfigSpec.IntValue MINI_MAX_PLAYERS;
    public static final ForgeConfigSpec.IntValue MINI_READY_TIMEOUT;
    public static final ForgeConfigSpec.IntValue MAX_MINI_RUNS;
    public static final ForgeConfigSpec.IntValue MIN_GROUP_SIZE;
    public static final ForgeConfigSpec.IntValue DESCENT_COUNTDOWN;
    public static final ForgeConfigSpec.DoubleValue REQUIRED_READY_RATIO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Sesion: guardado y restauracion del jugador (README 5)").push("session");
        RESTORE_INVENTORY = b.comment("Devolver el inventario original al salir de una partida")
                .define("restore_inventory", true);
        SP_LOBBY_PERMISSION = b.comment("Nivel de permiso necesario para /sp lobby")
                .defineInRange("sp_lobby_permission", 0, 0, 4);
        SP_LOBBY_COOLDOWN = b.comment("Segundos de espera entre usos de /sp lobby")
                .defineInRange("sp_lobby_cooldown", 10, 0, 3600);
        TELEPORT_TO_LOBBY_ON_JOIN = b.comment("Llevar al lobby a quien se conecta fuera de una partida")
                .define("teleport_to_lobby_on_join", true);
        b.pop();

        b.comment("Dentro de la partida (README 8, 9)").push("game");
        MAX_HEALTH = b.comment("Vida maxima dentro de la partida")
                .defineInRange("max_health", 20, 1, 1024);
        FORCE_FIRST_PERSON = b.comment("Forzar primera persona dentro de la partida")
                .define("force_first_person", true);
        PLAYER_COMMANDS = b.comment("Comandos que un jugador sin permisos puede usar dentro de la partida")
                .defineListAllowEmpty("player_commands", List.of("sp", "msg", "tell", "w", "me", "help"), o -> o instanceof String);
        b.pop();

        b.comment("Aislamiento de mods (README 7)").push("isolation");
        ALLOWED_MODS = b.comment("Lista blanca de mods en el cliente")
                .defineListAllowEmpty("allowed_mods", List.of(
                        "minecraft", "forge", "smileprotocol", "geckolib", "veil",
                        "playeranimator", "voicechat", "sound_physics_remastered"), o -> o instanceof String);
        REQUIRED_MODS = b.comment("Mods que el cliente debe tener para poder bajar")
                .defineListAllowEmpty("required_mods", List.of("veil", "sound_physics_remastered"), o -> o instanceof String);
        HANDSHAKE_TIMEOUT = b.comment("Segundos para que el cliente envie su lista de mods al conectarse")
                .defineInRange("handshake_timeout", 15, 1, 300);
        b.pop();

        b.comment("Lobby y ascensores (README 4.2, 6.2)").push("lobby");
        LOBBY_GAMEMODE = b.comment("Modo de juego en el lobby: adventure o survival")
                .define("lobby_gamemode", "adventure");
        MINI_MIN_PLAYERS = b.defineInRange("mini_min_players", 2, 1, 16);
        MINI_MAX_PLAYERS = b.defineInRange("mini_max_players", 6, 1, 16);
        MINI_READY_TIMEOUT = b.comment("Segundos antes de expulsar a un AFK de un mini ascensor")
                .defineInRange("mini_ready_timeout", 30, 5, 600);
        MAX_MINI_RUNS = b.defineInRange("max_mini_runs", 4, 0, 64);
        MIN_GROUP_SIZE = b.comment("Minimo de jugadores para el mega ascensor")
                .defineInRange("min_group_size", 4, 1, 64);
        DESCENT_COUNTDOWN = b.comment("Segundos de cuenta atras antes de bajar")
                .defineInRange("descent_countdown", 5, 0, 60);
        REQUIRED_READY_RATIO = b.comment("Proporcion de jugadores del lobby que deben estar en el mega ascensor")
                .defineInRange("required_ready_ratio", 1.0, 0.1, 1.0);
        b.pop();

        SPEC = b.build();
    }

    private SPConfig() {}
}
