package com.smileprotocol.voice;

import com.smileprotocol.SmileProtocol;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
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
        return SmileProtocol.MODID;
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
        // Walkie (README 17.3): la voz, con filtro de radio, a los receptores del grupo
        java.util.List<WalkieManager.Rx> rx = WalkieManager.routes(id);
        if (rx != null && !rx.isEmpty()) sendRadio(id, pcm, rx);
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

    private final Map<String, StaticAudioChannel> radioChannels = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> radioNoisy = new ConcurrentHashMap<>();
    private final java.util.Random rng = new java.util.Random();

    private void sendRadio(UUID sender, short[] pcm, java.util.List<WalkieManager.Rx> rx) {
        if (api == null) return;
        net.minecraft.server.MinecraftServer server = com.smileprotocol.ServerRef.get();
        if (server == null) return;
        byte[] clean = null, noisy = null;
        for (WalkieManager.Rx r : rx) {
            VoicechatConnection c = api.getConnectionOf(r.player());
            ServerPlayer target = server.getPlayerList().getPlayer(r.player());
            if (c == null || target == null) continue;
            if (r.interference()) {
                if (noisy == null) noisy = radioNoisy.computeIfAbsent(sender, k -> api.createEncoder()).encode(radio(pcm, true));
            } else if (clean == null) {
                clean = encoders.computeIfAbsent(sender, k -> api.createEncoder()).encode(radio(pcm, false));
            }
            String key = sender + ">" + r.player();
            StaticAudioChannel ch = radioChannels.get(key);
            if (ch == null || ch.isClosed()) {
                ch = api.createStaticAudioChannel(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        api.fromServerLevel(target.serverLevel()), c);
                if (ch == null) continue;
                radioChannels.put(key, ch);
            }
            ch.send(r.interference() ? noisy : clean);
        }
    }

    /** Filtro de radio: banda de 300 a 3000 Hz, saturacion, siseo; con interferencias, cortes y estatica. */
    private short[] radio(short[] pcm, boolean interference) {
        short[] out = new short[pcm.length];
        double lp = 0, hpIn = 0, hpOut = 0;
        boolean drop = false;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i] / 32768.0;
            lp += 0.33 * (x - lp);                       // ~3 kHz a 48 kHz
            hpOut = 0.96 * (hpOut + lp - hpIn);          // ~300 Hz
            hpIn = lp;
            double y = Math.tanh(hpOut * 2.6) * 0.7;
            y += (rng.nextDouble() - 0.5) * 0.012;
            if (interference) {
                if (i % 240 == 0) drop = rng.nextFloat() < 0.22f;
                y = drop ? (rng.nextDouble() - 0.5) * 0.25 : Math.round(y * 12) / 12.0 + (rng.nextDouble() - 0.5) * 0.08;
            }
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y * 32767));
        }
        return out;
    }

    @Override
    public void walkieEnd(UUID sender) {
        String prefix = sender + ">";
        radioChannels.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            e.getValue().flush();
            return true;
        });
        OpusEncoder a = encoders.get(sender);
        if (a != null) a.resetState();
        OpusEncoder b = radioNoisy.remove(sender);
        if (b != null) b.close();
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
