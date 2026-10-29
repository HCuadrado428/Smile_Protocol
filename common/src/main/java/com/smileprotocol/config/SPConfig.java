package com.smileprotocol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuracion comun ({@code config/smileprotocol.json}, README 24). Igual en Fabric y
 * NeoForge. Si falta algun valor se usa el de por defecto y se reescribe el fichero.
 */
public final class SPConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Values values = new Values();

    private SPConfig() {}

    public static Values get() {
        return values;
    }

    /** Valores y sus defectos. Los nombres coinciden con los del README. */
    public static final class Values {
        // Sesion (README 5)
        public boolean restore_inventory = true;
        public int sp_lobby_permission = 0;
        public int sp_lobby_cooldown = 10;
        public boolean teleport_to_lobby_on_join = true;

        // Dentro de la partida (README 8, 9)
        public int max_health = 20;
        public boolean force_first_person = true;
        public List<String> player_commands = new ArrayList<>(List.of("sp", "msg", "tell", "w", "me", "help"));

        // Aislamiento de mods (README 7)
        public List<String> allowed_mods = new ArrayList<>(List.of(
                "minecraft", "java", "neoforge", "fabricloader", "fabric-api", "mixinextras",
                "smileprotocol", "geckolib", "veil", "playeranimator", "player-animator",
                "voicechat", "sound_physics_remastered"));
        /** Prefijos permitidos: los modulos de Fabric API aparecen como mods sueltos. */
        public List<String> allowed_mod_prefixes = new ArrayList<>(List.of("fabric-"));
        public List<String> required_mods = new ArrayList<>(List.of("veil", "sound_physics_remastered"));
        public int handshake_timeout = 15;

        // Lobby (README 4.2, 6.2)
        public String lobby_gamemode = "adventure";
        public int mini_min_players = 2;
        public int mini_max_players = 6;
        public int mini_ready_timeout = 30;
        public int max_mini_runs = 4;
        public int min_group_size = 4;
        public int descent_countdown = 5;
        public double required_ready_ratio = 1.0;

        // Ambientacion (README 21.1)
        public int max_dynamic_lights = 64;

        void sanitize() {
            sp_lobby_cooldown = Math.max(0, sp_lobby_cooldown);
            max_health = Math.max(1, max_health);
            handshake_timeout = Math.max(1, handshake_timeout);
            mini_min_players = Math.max(1, mini_min_players);
            mini_max_players = Math.max(mini_min_players, mini_max_players);
            mini_ready_timeout = Math.max(5, mini_ready_timeout);
            max_mini_runs = Math.max(0, max_mini_runs);
            min_group_size = Math.max(1, min_group_size);
            descent_countdown = Math.max(0, descent_countdown);
            required_ready_ratio = Math.min(1.0, Math.max(0.1, required_ready_ratio));
            max_dynamic_lights = Math.max(0, max_dynamic_lights);
            if (player_commands == null) player_commands = new ArrayList<>();
            if (allowed_mods == null) allowed_mods = new ArrayList<>();
            if (allowed_mod_prefixes == null) allowed_mod_prefixes = new ArrayList<>();
            if (required_mods == null) required_mods = new ArrayList<>();
        }
    }

    public static void load() {
        Path file = Services.PLATFORM.configDir().resolve("smileprotocol.json");
        Values loaded = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, Values.class);
            } catch (IOException | JsonParseException e) {
                SmileProtocol.LOGGER.error("No se pudo leer {}; se usan los valores por defecto", file, e);
            }
        }
        values = loaded != null ? loaded : new Values();
        values.sanitize();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(values, w);
            }
        } catch (IOException e) {
            SmileProtocol.LOGGER.warn("No se pudo escribir {}", file, e);
        }
    }

    /** El mod esta permitido si esta en la lista o empieza por un prefijo permitido. */
    public static boolean isModAllowed(String modId) {
        if (values.allowed_mods.contains(modId)) return true;
        for (String p : values.allowed_mod_prefixes) if (modId.startsWith(p)) return true;
        return false;
    }
}
