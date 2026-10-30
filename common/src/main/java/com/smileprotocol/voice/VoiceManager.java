package com.smileprotocol.voice;

import com.smileprotocol.ServerRef;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voz de proximidad dentro de la partida (README 7.4, 17.2), sin depender de Simple Voice Chat:
 * <ul>
 *   <li>Hablar hace ruido: el volumen de cada paquete del microfono (dB) se traduce en un radio,
 *   de susurro a grito ({@code noise_voice_*}). Como mucho un ruido cada {@code voice_noise_interval}.</li>
 *   <li>Los muertos van a un grupo de voz aislado de espectadores de su partida: ni oyen a los
 *   vivos ni los vivos a ellos. Al volver en el ascensor salen del grupo.</li>
 *   <li>Dentro de la partida no se puede entrar ni salir de grupos de voz a mano (seria hablar a
 *   distancia sin walkie).</li>
 * </ul>
 */
public final class VoiceManager {

    @Nullable private static VoiceBridge bridge;
    /** Ultimo tick con ruido de voz de cada jugador; lo escribe el hilo de la voz. */
    private static final Map<UUID, Integer> LAST_NOISE = new ConcurrentHashMap<>();
    /** Volumen del ultimo paquete (para el HUD y las pruebas). */
    private static final Map<UUID, Float> LAST_DB = new ConcurrentHashMap<>();
    private static boolean internalChange;

    private VoiceManager() {}

    public static void setBridge(@Nullable VoiceBridge b) {
        bridge = b;
        SPDebug.log("Simple Voice Chat " + (b != null ? "detectado" : "no disponible"));
    }

    @Nullable
    static VoiceBridge bridge() {
        return bridge;
    }

    public static boolean available() {
        return bridge != null;
    }

    public static boolean connected(ServerPlayer p) {
        return bridge != null && bridge.connected(p);
    }

    /** Radio de ruido para un paquete de voz de {@code db} decibelios (0 = no hace ruido). */
    public static float radiusFor(double db, boolean whisper) {
        SPConfig.Values c = SPConfig.get();
        if (db < c.voice_silence_db) return 0f;
        if (whisper) return c.noise_voice_whisper;
        float t = (float) Mth.clamp((db - c.voice_talk_db) / (c.voice_shout_db - c.voice_talk_db), 0, 1);
        return Mth.lerp(t, c.noise_voice, c.noise_voice_shout);
    }

    /**
     * Un paquete del microfono (desde el hilo de la voz). Hace el ruido en el hilo del servidor.
     * @return true si el ruido se ha emitido (o se va a emitir).
     */
    public static boolean onVoice(UUID player, double db, boolean whisper) {
        LAST_DB.put(player, (float) db);
        MinecraftServer server = ServerRef.get();
        if (server == null) return false;
        float r = radiusFor(db, whisper);
        if (r <= 0) return false;
        int now = server.getTickCount();
        Integer last = LAST_NOISE.get(player);
        if (last != null && now - last < SPConfig.get().voice_noise_interval && now >= last) return false;
        LAST_NOISE.put(player, now);
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(player);
            if (p == null || !SPPlayerData.inSP(p) || p.isSpectator() || DeathManager.isDead(p)) return;
            NoiseSystem.emit(p.serverLevel(), p.getEyePosition(), r, NoiseType.VOICE, p);
        });
        return true;
    }

    /** Volumen del ultimo paquete de voz del jugador (dB; -127 si no ha hablado). */
    public static float lastDb(UUID player) {
        return LAST_DB.getOrDefault(player, -127f);
    }

    /** Si se deja al jugador cambiar de grupo de voz por su cuenta. */
    public static boolean allowGroupChange(UUID player) {
        if (internalChange) return true;
        MinecraftServer server = ServerRef.get();
        if (server == null) return true;
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        return p == null || !SPPlayerData.inSP(p);
    }

    /** Cada segundo: los muertos de una partida, a su grupo de espectadores; los demas, fuera. */
    public static void tick(MinecraftServer server) {
        WalkieManager.tick(server);
        if (bridge == null || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String want = null;
            if (SPPlayerData.inSP(p) && DeathManager.isDead(p)) {
                Group g = RunManager.groupOf(p);
                if (g != null) want = g.runId();
            }
            String have = bridge.spectatorRun(p);
            if (java.util.Objects.equals(want, have) || (want == null && have == null)) continue;
            internalChange = true;
            try {
                bridge.setSpectatorGroup(p, want);
            } finally {
                internalChange = false;
            }
            SPDebug.log("voz: " + p.getGameProfile().getName() + (want != null ? " al grupo de espectadores " + want : " sale del grupo de espectadores"));
        }
    }

    /** Partida del grupo de voz de espectadores en el que esta (null si no esta en ninguno). */
    @Nullable
    public static String spectatorRun(ServerPlayer p) {
        return bridge != null ? bridge.spectatorRun(p) : null;
    }

    public static void onLogout(ServerPlayer p) {
        LAST_NOISE.remove(p.getUUID());
        LAST_DB.remove(p.getUUID());
        WalkieManager.onLogout(p);
    }
}
