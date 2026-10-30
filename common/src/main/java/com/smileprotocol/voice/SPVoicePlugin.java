package com.smileprotocol.voice;

import com.smileprotocol.SmileProtocol;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.GroupEvent;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin de Simple Voice Chat (README 7.4): lo carga el mod de voz (entrypoint {@code voicechat}
 * en Fabric, anotacion en NeoForge). Es la unica clase que usa la API; el resto del mod habla con
 * {@link VoiceManager} a traves de {@link VoiceBridge}.
 */
@ForgeVoicechatPlugin
public class SPVoicePlugin implements VoicechatPlugin, VoiceBridge {

    @Nullable private VoicechatServerApi api;
    /** Grupos de espectadores por partida. */
    private final Map<String, Group> spectatorGroups = new ConcurrentHashMap<>();
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> encoders = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return SmileProtocol.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        SmileProtocol.LOGGER.info("Plugin de Simple Voice Chat cargado");
    }

    @Override
    public void registerEvents(EventRegistration r) {
        r.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            VoiceManager.setBridge(this);
        });
        r.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            VoiceManager.setBridge(null);
            api = null;
            spectatorGroups.clear();
            decoders.values().forEach(OpusDecoder::close);
            encoders.values().forEach(OpusEncoder::close);
            decoders.clear();
            encoders.clear();
        });
        r.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        r.registerEvent(JoinGroupEvent.class, this::onGroupChange);
        r.registerEvent(LeaveGroupEvent.class, this::onGroupChange);
        r.registerEvent(PlayerDisconnectedEvent.class, e -> {
            OpusDecoder d = decoders.remove(e.getPlayerUuid());
            if (d != null) d.close();
            OpusEncoder en = encoders.remove(e.getPlayerUuid());
            if (en != null) en.close();
        });
    }

    private void onMicrophone(MicrophonePacketEvent e) {
        VoicechatConnection sender = e.getSenderConnection();
        if (sender == null || api == null) return;
        UUID id = sender.getPlayer().getUuid();
        byte[] data = e.getPacket().getOpusEncodedData();
        if (data == null || data.length == 0) {
            OpusDecoder d = decoders.get(id);
            if (d != null) d.resetState();
            return;
        }
        OpusDecoder decoder = decoders.computeIfAbsent(id, k -> api.createDecoder());
        short[] pcm = decoder.decode(data);
        if (pcm == null || pcm.length == 0) return;
        VoiceManager.onVoice(id, decibels(pcm), e.getPacket().isWhispering());
        // Agarrado o en una secuencia: la voz sale ahogada
        if (VoiceManager.muffled(id)) {
            short[] out = muffle(pcm);
            OpusEncoder enc = encoders.computeIfAbsent(id, k -> api.createEncoder());
            e.getPacket().setOpusEncodedData(enc.encode(out));
        }
    }

    /** Nivel RMS en dBFS (0 = maximo, -127 = silencio). */
    static double decibels(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            double v = s / 32768.0;
            sum += v * v;
        }
        double rms = Math.sqrt(sum / pcm.length);
        return rms <= 1e-7 ? -127 : 20 * Math.log10(rms);
    }

    /** Pasa-bajos sencillo y menos volumen: como hablar con una mano en la boca. */
    private static short[] muffle(short[] pcm) {
        short[] out = new short[pcm.length];
        double y = 0;
        for (int i = 0; i < pcm.length; i++) {
            y += 0.18 * (pcm[i] - y);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y * 0.55));
        }
        return out;
    }

    private void onGroupChange(GroupEvent e) {
        VoicechatConnection c = e.getConnection();
        if (c == null || VoiceManager.allowGroupChange(c.getPlayer().getUuid())) return;
        if (e.isCancellable()) e.cancel();
    }

    @Override
    public void setSpectatorGroup(ServerPlayer player, @Nullable String runId) {
        if (api == null) return;
        VoicechatConnection c = api.getConnectionOf(player.getUUID());
        if (c == null) return;
        if (runId == null) {
            c.setGroup(null);
            return;
        }
        Group g = spectatorGroups.computeIfAbsent(runId, k -> api.groupBuilder()
                .setName("Espectadores " + k)
                .setHidden(true)
                .setPersistent(false)
                .setType(Group.Type.ISOLATED)
                .build());
        c.setGroup(g);
    }

    @Nullable
    @Override
    public String spectatorRun(ServerPlayer player) {
        if (api == null) return null;
        VoicechatConnection c = api.getConnectionOf(player.getUUID());
        if (c == null || !c.isInGroup()) return null;
        Group g = c.getGroup();
        if (g == null) return null;
        for (Map.Entry<String, Group> en : spectatorGroups.entrySet()) {
            if (en.getValue().getId().equals(g.getId())) return en.getKey();
        }
        return null;
    }

    @Override
    public boolean connected(ServerPlayer player) {
        if (api == null) return false;
        VoicechatConnection c = api.getConnectionOf(player.getUUID());
        return c != null && c.isConnected();
    }
}
