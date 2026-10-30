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

        // Partidas, grupos e instancias (README 6.3, 6.5, 6.7, 6.8)
        public int max_groups = 12;
        public String group_mode = "RANDOM";
        public int instance_spacing = 4096;
        public int zone_spacing = 512;
        public int instance_y = 64;
        public int intermediate_zones = 3;
        public boolean avoid_repeat_last = true;
        public boolean per_group_zones = false;
        public String start_zone = "smileprotocol:start_corridor";
        public String final_zone = "smileprotocol:smiley_lab";
        public int zone_clear_blocks_per_tick = 32768;
        public int mini_return_delay = 10;
        public int disconnect_grace = 120;
        public boolean replayable = true;
        public int max_plays_per_player = 0;
        public int lobby_reset_delay = 30;
        public boolean auto_newrun = true;

        // Movimiento y ruido (README 12)
        public int dive_cooldown = 60;
        public boolean vault_any_obstacle = true;
        public boolean look_back_toggle = false;
        public boolean lean_toggle = false;
        public float noise_crouch_walk = 3;
        /** Multiplicador del ruido de gatear dentro de un conducto de chapa (README 12.9). */
        public float noise_vent_mult = 1.6f;
        public float noise_walk = 8;
        public float noise_sprint = 16;
        public float noise_dive = 18;
        public float noise_vault = 12;
        public float noise_land = 14;
        public float noise_drop = 4;
        /** Teclas propias (nombres de InputConstants: key.keyboard.c...). No chocan con las vanilla. */
        public String key_look_back = "key.keyboard.c";
        public String key_lean_left = "key.keyboard.q";
        public String key_lean_right = "key.keyboard.e";
        public String key_drop = "key.keyboard.g";
        public String key_flashlight = "key.keyboard.f";
        /** Segundos de linterna encendida con la bateria llena (README 10). */
        public int flashlight_battery_seconds = 420;

        // Derribado, reanimacion y remates (README 9.3)
        public int bleed_out_time = 60;
        public int downed_noise_interval = 200;
        public float noise_crawl = 3.5f;
        public float noise_groan = 6;
        /** Ticks que tarda un companero en reanimar (con varios se acorta). */
        public int revive_time = 120;
        /** Vida con la que se levanta (% de la maxima). */
        public int revive_health_percent = 30;
        /** Ticks de proteccion contra remates tras levantarse. */
        public int revive_protection = 60;
        public float noise_revive = 7;
        /** Ticks de interaccion mantenida para liberar a un companero agarrado (README 18). */
        public int qte_help_time = 40;

        // Objetos (README 10.1, 12.7)
        /** Ruido de una bengala encendida, cada 2 s. */
        public float noise_flare = 10;
        /** Espera entre lanzamientos (ticks). */
        public int throw_cooldown = 15;

        // Voz (Simple Voice Chat, README 7.4 y 17.2)
        /** Por debajo de este volumen (dBFS) la voz no hace ruido. */
        public float voice_silence_db = -50;
        /** Volumen de hablar normal y de gritar (dBFS); entre medias, el radio crece. */
        public float voice_talk_db = -32;
        public float voice_shout_db = -12;
        /** Radio de ruido al susurrar, al hablar y al gritar. */
        public float noise_voice_whisper = 3;
        public float noise_voice = 9;
        public float noise_voice_shout = 22;
        /** Ticks minimos entre dos ruidos de voz del mismo jugador. */
        public int voice_noise_interval = 10;

        // Ambientacion (README 21)
        /** Shader de tension (aberracion, distorsion, grano) con mobs cerca, QTE o derribado. */
        public boolean tension_shader = true;
        /** Multiplicador de su intensidad (0 a 1,5). */
        public float tension_strength = 1f;
        /** Zumbido de ambiente con silencios, crujidos y goteos. */
        public boolean ambience = true;

        // Walkies (README 17.3) y marcadores (8.4)
        public boolean walkie_enabled = true;
        /** Ruido del crujido del walkie al recibir, alrededor del que lo lleva. */
        public float noise_walkie_receive = 5;
        /** Distancia a un mob con interferencias que distorsiona el walkie. */
        public float walkie_interference_radius = 12;
        public String key_walkie = "key.keyboard.b";
        public String key_marker = "key.keyboard.r";
        public int marker_range = 64;
        public int marker_duration = 400;
        public int marker_max = 3;
        public int marker_cooldown = 10;

        // Eventos de laboratorio (README 14.7)
        /** Segundos minimos entre dos sustos para el mismo jugador. */
        public int jumpscare_cooldown = 20;

        // Game over (README 6.7)
        /** A quien pueden mirar los eliminados: ALL_RUNS (cualquier partida en curso) u OWN_RUN. */
        public String spectate_scope = "ALL_RUNS";

        // Mobs (README 20)
        /** Berserkers por planta segun la dificultad de la zona (0, 1, 2, 3, 4...), antes de escalar por el grupo. */
        // El Paciente (docs/SMILERS.md)
        /** Plantas donde aparece (uno por planta, en los pasillos). */
        public java.util.List<String> paciente_zones = new java.util.ArrayList<>(java.util.List.of("lab_morgue", "final_room"));
        /** Bloques de camino por salto y ticks entre saltos mientras nadie lo mira. */
        public int paciente_step = 4;
        public int paciente_jump_ticks = 5;
        /** Medio angulo de vision con el que se le considera mirado (grados). */
        public float paciente_view_angle = 50;
        /** Si su victima esta mas lejos que esto y hay otra mas cerca, cambia. */
        public float paciente_retarget_distance = 24;
        // Apagon: plantas y bateria que se come por tick al apuntarle con la linterna
        public java.util.List<String> apagon_zones = new java.util.ArrayList<>(java.util.List.of("lab_generators", "final_room"));
        public float apagon_battery_drain = 0.004f;
        // El Ahorcado: plantas y cuantos por planta (colgados en los pasillos)
        public java.util.List<String> ahorcado_zones = new java.util.ArrayList<>(java.util.List.of("lab_greenhouse", "lab_morgue", "lab_generators"));
        public int ahorcado_count = 2;
        // El Rastreador
        public java.util.List<String> rastreador_zones = new java.util.ArrayList<>(java.util.List.of("lab_offices", "lab_storage"));
        /** Ticks que aguanta corriendo antes de cansarse. */
        public int rastreador_stamina = 160;
        // Smiley, el jefe (docs/SMILERS.md)
        /** Se le ve una vez en cada planta, de lejos, entre estos segundos tras llegar. */
        public boolean smiley_cameos = true;
        public int smiley_cameo_min = 60;
        public int smiley_cameo_max = 200;
        /** Linternas a la vez que le hacen retroceder (o tantas como jugadores queden). */
        public int smiley_retreat_lights = 2;
        /** Velocidad en cada fase (multiplica la base). */
        public java.util.List<Double> smiley_speed = new java.util.ArrayList<>(java.util.List.of(0.55, 0.8, 1.15));
        /** Ticks persiguiendo sin ganar terreno antes de irse por la pared a por otro. */
        public int smiley_chase_ticks = 300;
        public int smiley_scream_cooldown = 700;
        public int smiley_summon_cooldown = 1100;
        /** Smilers invocados vivos a la vez, como mucho. */
        public int smiley_summon_max = 3;
        public int smiley_lights_cooldown = 600;
        /** Ticks que tardan en volver las luces que apaga (fases 2; en la 3 no vuelven). */
        public int smiley_dark_ticks = 300;
        /** Duracion del minijuego del agarre en las fases 1 y 2 (en la 3 mata al instante). */
        public java.util.List<Integer> smiley_qte_ticks = new java.util.ArrayList<>(java.util.List.of(140, 100));
        public java.util.List<Integer> berserkers_by_difficulty = new java.util.ArrayList<>(java.util.List.of(1, 2, 2, 3, 3));
        /** Los mobs no se mueven hasta que el grupo llega a su planta. */
        public boolean mobs_wait_for_players = true;

        // Ascensores de zona (README 6.5)
        public int elevator_grace = 15;
        public int elevator_ride_time = 4;
        public String elevator_strip = "HURT_OR_REVIVED";

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
            bleed_out_time = Math.max(5, bleed_out_time);
            revive_time = Math.max(1, revive_time);
            revive_health_percent = Math.max(1, Math.min(100, revive_health_percent));
            revive_protection = Math.max(0, revive_protection);
            qte_help_time = Math.max(1, qte_help_time);
            if (berserkers_by_difficulty == null || berserkers_by_difficulty.isEmpty()) {
                berserkers_by_difficulty = new java.util.ArrayList<>(java.util.List.of(1, 2, 2, 3, 3));
            }
            max_groups = Math.max(1, max_groups);
            if (group_mode == null) group_mode = "RANDOM";
            instance_spacing = Math.max(1024, instance_spacing);
            zone_spacing = Math.max(64, Math.min(instance_spacing / 2, zone_spacing));
            instance_y = Math.max(-32, Math.min(200, instance_y));
            intermediate_zones = Math.max(0, intermediate_zones);
            if (start_zone == null) start_zone = "";
            if (final_zone == null) final_zone = "";
            // la sala final era final_room hasta que llego el Laboratorio Cero (ahora es intermedia)
            if (final_zone.equals("smileprotocol:final_room")) final_zone = "smileprotocol:smiley_lab";
            if (smiley_speed == null || smiley_speed.isEmpty()) smiley_speed = new java.util.ArrayList<>(java.util.List.of(0.55, 0.8, 1.15));
            if (smiley_qte_ticks == null || smiley_qte_ticks.isEmpty()) smiley_qte_ticks = new java.util.ArrayList<>(java.util.List.of(140, 100));
            smiley_chase_ticks = Math.max(40, smiley_chase_ticks);
            zone_clear_blocks_per_tick = Math.max(1024, zone_clear_blocks_per_tick);
            mini_return_delay = Math.max(0, mini_return_delay);
            disconnect_grace = Math.max(0, disconnect_grace);
            max_plays_per_player = Math.max(0, max_plays_per_player);
            lobby_reset_delay = Math.max(0, lobby_reset_delay);
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
