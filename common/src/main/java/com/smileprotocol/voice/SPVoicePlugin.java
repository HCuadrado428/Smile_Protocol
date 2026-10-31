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
        VoiceManager.recordClip(id, data, decibels(pcm));
        int muffle = com.smileprotocol.hide.HideManager.muffle(id);
        VoiceManager.onVoice(id, decibels(pcm) - (muffle == 2 ? 10 : muffle == 1 ? 4 : 0), e.getPacket().isWhispering());
        // Escondido: los de fuera le oyen ahogado (filtro de paso bajo y menos volumen) desde el escondite
        if (muffle > 0 && sendMuffled(e, id, pcm, muffle)) e.cancel();
        // Walkie (README 17.3): la voz, con filtro de radio, a los receptores del grupo
        java.util.List<WalkieManager.Rx> rx = WalkieManager.routes(id);
        if (rx != null && !rx.isEmpty()) sendRadio(id, pcm, rx);
    }

    private final Map<UUID, de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel> muffledChannels = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> muffledEncoders = new ConcurrentHashMap<>();

    /** Reenvia la voz ahogada como audio posicional en el sitio del jugador. False si no se puede (se deja la normal). */
    private boolean sendMuffled(MicrophonePacketEvent e, UUID id, short[] pcm, int muffle) {
        if (api == null) return false;
        net.minecraft.server.MinecraftServer server = com.smileprotocol.ServerRef.get();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p == null) return false;
        de.maxhenkel.voicechat.api.Position pos = api.createPosition(p.getX(), p.getEyeY(), p.getZ());
        var ch = muffledChannels.get(id);
        if (ch == null || ch.isClosed()) {
            ch = api.createLocationalAudioChannel(UUID.nameUUIDFromBytes(("muffle" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    api.fromServerLevel(p.serverLevel()), pos);
            if (ch == null) return false;
            muffledChannels.put(id, ch);
        }
        ch.updateLocation(pos);
        float range = (float) api.getVoiceChatDistance();
        ch.setDistance(range * (muffle == 2 ? 0.5f : 0.75f));
        // el que habla no debe oirse a si mismo
        ch.setFilter(player -> !player.getUuid().equals(id));
        ch.send(muffledEncoders.computeIfAbsent(id, k -> api.createEncoder()).encode(muffle(pcm, muffle)));
        return true;
    }

    private static final java.util.concurrent.ScheduledExecutorService MIMIC_EXEC =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SmileProtocol-Imitador");
                t.setDaemon(true);
                return t;
            });

    @Override
    public boolean playMimic(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos, java.util.List<byte[]> clip,
                             float distance) {
        if (api == null || clip.isEmpty()) return false;
        OpusDecoder dec = api.createDecoder();
        OpusEncoder enc = api.createEncoder();
        try {
            // decodificar todo, deformar y volver a partir en paquetes de 20 ms (960 muestras a 48 kHz)
            java.util.List<short[]> parts = new java.util.ArrayList<>();
            int total = 0;
            for (byte[] b : clip) {
                short[] s = dec.decode(b);
                if (s == null) continue;
                parts.add(s);
                total += s.length;
            }
            short[] all = new short[total];
            int k = 0;
            for (short[] s : parts) {
                System.arraycopy(s, 0, all, k, s.length);
                k += s.length;
            }
            short[] warped = warp(all);
            java.util.List<byte[]> frames = new java.util.ArrayList<>();
            for (int i = 0; i + 960 <= warped.length; i += 960) {
                frames.add(enc.encode(java.util.Arrays.copyOfRange(warped, i, i + 960)));
            }
            var ch = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(level), api.createPosition(pos.x, pos.y, pos.z));
            if (ch == null || frames.isEmpty()) return false;
            ch.setDistance(distance);
            int[] i = {0};
            java.util.concurrent.ScheduledFuture<?>[] task = {null};
            task[0] = MIMIC_EXEC.scheduleAtFixedRate(() -> {
                if (i[0] >= frames.size()) {
                    ch.flush();
                    task[0].cancel(false);
                    return;
                }
                ch.send(frames.get(i[0]++));
            }, 0, 20, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            SmileProtocol.LOGGER.warn("Imitador: no se pudo reproducir la voz: {}", e.toString());
            return false;
        } finally {
            dec.close();
            enc.close();
        }
    }

    /** Voz «casi» igual: un 7 % mas grave, con un tartamudeo en medio y un poco de aspereza. */
    static short[] warp(short[] in) {
        double rate = 0.93;
        int n = (int) (in.length / rate);
        short[] slow = new short[n];
        for (int i = 0; i < n; i++) {
            double src = i * rate;
            int a = (int) src;
            double f = src - a;
            double v = a + 1 < in.length ? in[a] * (1 - f) + in[a + 1] * f : in[Math.min(a, in.length - 1)];
            double x = v / 32768.0;
            x = Math.tanh(x * 1.6) * 0.8;
            slow[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, x * 32767));
        }
        // tartamudeo: repite un trozo de 120 ms hacia la mitad
        int cut = n / 2, len = Math.min(5760, n / 4);
        if (len < 960) return slow;
        short[] out = new short[n + len];
        System.arraycopy(slow, 0, out, 0, cut);
        System.arraycopy(slow, cut - len, out, cut, len);
        System.arraycopy(slow, cut, out, cut + len, n - cut);
        return out;
    }

    /** Paso bajo (dos polos) y menos volumen: suena «a traves de la puerta». */
    static short[] muffle(short[] pcm, int level) {
        double a = level == 2 ? 0.08 : 0.2;          // ~650 Hz o ~1,8 kHz a 48 kHz
        double gain = level == 2 ? 0.55 : 0.8;
        short[] out = new short[pcm.length];
        double y1 = 0, y2 = 0;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i] / 32768.0;
            y1 += a * (x - y1);
            y2 += a * (y1 - y2);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y2 * gain * 1.4 * 32767));
        }
        return out;
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
