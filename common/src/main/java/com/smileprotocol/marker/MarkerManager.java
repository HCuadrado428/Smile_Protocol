package com.smileprotocol.marker;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marcadores de equipo (README 8.4): sin ruido y solo para los del grupo. El servidor valida
 * (cooldown, maximo por jugador, ni en QTE ni en secuencias), mira a donde apunta el jugador y los
 * reparte al grupo. Derribado solo se puede pedir ayuda, y el marcador va sobre el jugador.
 */
public final class MarkerManager {

    public static final int ATTENTION = 0, DANGER = 1, GO = 2, HELP = 3, CLEAR = 4;
    public static final String[] NAMES = {"attention", "danger", "go", "help", "clear"};
    /** Colores de los jugadores del grupo (por orden de llegada). */
    public static final int[] COLORS = {0xE8C547, 0x5FB8E8, 0xE86A5F, 0x8FD16A, 0xC07AE8, 0xF0A040, 0x6AE0C8, 0xE8E8E8};

    private record Marker(int id, UUID owner, String name, int kind, Vec3 pos, long created, long expires,
                          @Nullable UUID onPlayer, int color) {}

    private static final Map<UUID, List<Marker>> BY_GROUP = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static int nextId = 1;

    private MarkerManager() {}

    public static void onInput(Payloads.PlaceMarker in, ServerPlayer p) {
        int kind = in.kind();
        if (kind < 0 || kind >= NAMES.length) return;
        if (!SPPlayerData.inSP(p) || p.isSpectator() || DeathManager.isDead(p)) return;
        if (QTEManager.isGrabbed(p) || SequenceManager.inSequence(p)) return;
        Group g = RunManager.groupOf(p);
        if (g == null) return;
        SPConfig.Values cfg = SPConfig.get();
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < cfg.marker_cooldown) return;
        LAST.put(p.getUUID(), now);

        Vec3 pos;
        UUID onPlayer = null;
        if (DownedManager.isDowned(p)) {
            kind = HELP;
            onPlayer = p.getUUID();
            pos = p.position().add(0, 1.4, 0);
        } else {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getLookAngle().scale(cfg.marker_range));
            BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
            pos = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().add(eye.subtract(hit.getLocation()).normalize().scale(0.25));
        }
        List<Marker> list = BY_GROUP.computeIfAbsent(g.id(), k -> new ArrayList<>());
        int k = kind;
        list.removeIf(m -> m.owner.equals(p.getUUID()) && m.kind == k);
        List<Marker> mine = list.stream().filter(m -> m.owner.equals(p.getUUID())).toList();
        if (mine.size() >= cfg.marker_max) list.remove(mine.get(0));
        list.add(new Marker(nextId++, p.getUUID(), p.getGameProfile().getName(), kind, pos, now, now + cfg.marker_duration,
                onPlayer, color(g, p.getUUID())));
        SPDebug.log(p.getGameProfile().getName() + " marca " + NAMES[kind] + " en " + net.minecraft.core.BlockPos.containing(pos).toShortString());
        sync(p.server, g);
    }

    /** Color fijo de cada jugador dentro de su grupo. */
    public static int color(Group g, UUID player) {
        int i = 0;
        for (UUID id : g.members().keySet()) {
            if (id.equals(player)) return COLORS[i % COLORS.length];
            i++;
        }
        return COLORS[0];
    }

    public static void tick(MinecraftServer server) {
        if (BY_GROUP.isEmpty() || server.getTickCount() % 5 != 0) return;
        for (Iterator<Map.Entry<UUID, List<Marker>>> it = BY_GROUP.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Marker>> e = it.next();
            Group g = RunManager.group(server, e.getKey());
            if (g == null) {
                it.remove();
                continue;
            }
            boolean changed = false;
            List<Marker> list = e.getValue();
            for (int i = list.size() - 1; i >= 0; i--) {
                Marker m = list.get(i);
                ServerPlayer owner = server.getPlayerList().getPlayer(m.owner);
                long now = owner != null ? owner.serverLevel().getGameTime() : server.overworld().getGameTime();
                boolean gone = now >= m.expires;
                if (m.onPlayer != null) {
                    // la ayuda sigue al derribado y se quita al levantarse
                    if (owner == null || !DownedManager.isDowned(owner)) gone = true;
                    else if (owner.position().add(0, 1.4, 0).distanceToSqr(m.pos) > 0.25) {
                        list.set(i, new Marker(m.id, m.owner, m.name, m.kind, owner.position().add(0, 1.4, 0), m.created,
                                m.expires, m.onPlayer, m.color));
                        changed = true;
                    }
                }
                if (gone) {
                    list.remove(i);
                    changed = true;
                }
            }
            if (changed) sync(server, g);
            if (list.isEmpty()) it.remove();
        }
    }

    private static void sync(MinecraftServer server, Group g) {
        List<Marker> list = BY_GROUP.getOrDefault(g.id(), List.of());
        for (UUID id : g.members().keySet()) {
            ServerPlayer o = server.getPlayerList().getPlayer(id);
            if (o != null && SPPlayerData.inSP(o)) SPNetwork.sendTo(o, new Payloads.SyncMarkers(write(list, o.serverLevel().getGameTime())));
        }
    }

    private static CompoundTag write(List<Marker> list, long now) {
        ListTag l = new ListTag();
        for (Marker m : list) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", m.id);
            t.putInt("k", m.kind);
            t.putDouble("x", m.pos.x);
            t.putDouble("y", m.pos.y);
            t.putDouble("z", m.pos.z);
            t.putInt("c", m.color);
            t.putString("n", m.name);
            t.putUUID("o", m.owner);
            t.putInt("left", (int) Math.max(0, m.expires - now));
            t.putInt("dur", (int) (m.expires - m.created));
            l.add(t);
        }
        CompoundTag root = new CompoundTag();
        root.put("m", l);
        return root;
    }

    /** Marcadores vivos del grupo (para las pruebas). */
    public static int count(Group g) {
        return BY_GROUP.getOrDefault(g.id(), List.of()).size();
    }

    public static void onLogout(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }
}
