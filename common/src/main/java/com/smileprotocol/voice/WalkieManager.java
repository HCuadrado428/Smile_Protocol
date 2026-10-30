package com.smileprotocol.voice;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModItems;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walkies (README 17.3). Solo con Simple Voice Chat: sin el no se entregan.
 * <ul>
 *   <li>Se transmite manteniendo B con el walkie en el hueco activo; se recibe con el walkie en
 *   cualquier hueco y encendido (se enciende y apaga con el click derecho mantenido).</li>
 *   <li>El canal es el grupo: los companeros con walkie encendido y los muertos del grupo (que
 *   escuchan como espectadores).</li>
 *   <li>Transmitir hace el ruido de la voz; al recibir, el walkie cruje alrededor del que lo lleva.</li>
 *   <li>Cerca de un mob con {@code causes_interference}, el audio se distorsiona.</li>
 * </ul>
 * El audio lo reparte el plugin de voz con {@link #routes}, que se recalcula en el hilo del servidor.
 */
public final class WalkieManager {

    /** Un receptor del walkie de alguien. */
    public record Rx(UUID player, boolean interference) {}

    private static final Set<UUID> OFF = new HashSet<>();
    private static final Set<UUID> TX = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<Rx>> ROUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, Payloads.SyncWalkie> SYNCED = new HashMap<>();
    private static int txTicks;

    private WalkieManager() {}

    public static boolean enabled() {
        return SPConfig.get().walkie_enabled && VoiceManager.available();
    }

    public static boolean hasWalkie(ServerPlayer p) {
        return SPInventoryOps.find(p, WalkieManager::isWalkie) >= 0;
    }

    public static boolean isWalkie(ItemStack st) {
        return st.is(ModItems.WALKIE.get());
    }

    public static boolean isOn(ServerPlayer p) {
        return !OFF.contains(p.getUUID());
    }

    public static boolean transmitting(UUID player) {
        return TX.contains(player);
    }

    /** Receptores del walkie de {@code sender} (null o vacio si no transmite). Hilo de la voz. */
    @Nullable
    public static List<Rx> routes(UUID sender) {
        return TX.contains(sender) ? ROUTES.get(sender) : null;
    }

    /** Equipo inicial: un walkie por jugador si hay voz (README 6.2). */
    public static void giveStarter(ServerPlayer p) {
        if (!enabled() || hasWalkie(p)) return;
        SPInventoryOps.give(p, new ItemStack(ModItems.WALKIE.get()));
        OFF.remove(p.getUUID());
    }

    /** Encender o apagar (click derecho mantenido, README 11.1). */
    public static void toggle(ServerPlayer p) {
        if (!OFF.remove(p.getUUID())) {
            OFF.add(p.getUUID());
            stopTx(p);
        }
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                isOn(p) ? "smileprotocol.walkie.on" : "smileprotocol.walkie.off"), true);
        sync(p, true);
    }

    public static void onInput(Payloads.WalkieInput in, ServerPlayer p) {
        if (in.talk()) {
            if (TX.contains(p.getUUID()) || !canTransmit(p)) return;
            TX.add(p.getUUID());
            p.serverLevel().playSound(null, p.blockPosition(), ModSounds.WALKIE_CLICK.get(), SoundSource.PLAYERS, 0.35f, 1f);
            updateRoutes(p);
            SPDebug.log(p.getGameProfile().getName() + " transmite por el walkie");
        } else {
            stopTx(p);
        }
        syncGroup(p.server, RunManager.groupOf(p));
    }

    private static void stopTx(ServerPlayer p) {
        if (!TX.remove(p.getUUID())) return;
        ROUTES.remove(p.getUUID());
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.WALKIE_CLICK.get(), SoundSource.PLAYERS, 0.3f, 0.8f);
        VoiceBridge b = VoiceManager.bridge();
        if (b != null) b.walkieEnd(p.getUUID());
    }

    /** Con el walkie en el hueco activo y encendido; derribado tambien (para pedir ayuda). */
    public static boolean canTransmit(ServerPlayer p) {
        if (!enabled() || !SPPlayerData.inSP(p) || p.isSpectator() || DeathManager.isDead(p) || !isOn(p)) return false;
        return isWalkie(SPPlayerData.of(p).getSPInventory().getSelectedStack());
    }

    private static void updateRoutes(ServerPlayer sender) {
        Group g = RunManager.groupOf(sender);
        List<Rx> out = new ArrayList<>();
        if (g != null) {
            boolean senderNoise = interference(sender);
            for (UUID id : g.members().keySet()) {
                if (id.equals(sender.getUUID())) continue;
                ServerPlayer o = sender.server.getPlayerList().getPlayer(id);
                if (o == null || !SPPlayerData.inSP(o)) continue;
                boolean dead = DeathManager.isDead(o);
                if (!dead && (!hasWalkie(o) || !isOn(o))) continue;
                out.add(new Rx(id, senderNoise || (!dead && interference(o))));
            }
        }
        ROUTES.put(sender.getUUID(), List.copyOf(out));
    }

    /** Hay un mob con interferencias cerca. */
    public static boolean interference(ServerPlayer p) {
        float r = SPConfig.get().walkie_interference_radius;
        return !p.serverLevel().getEntitiesOfClass(SPMonster.class, p.getBoundingBox().inflate(r),
                m -> m.isAlive() && m.def().causesInterference()).isEmpty();
    }

    public static void tick(MinecraftServer server) {
        if (TX.isEmpty() && SYNCED.isEmpty()) return;
        txTicks++;
        for (UUID id : new ArrayList<>(TX)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                TX.remove(id);
                ROUTES.remove(id);
                continue;
            }
            if (!canTransmit(p)) {
                stopTx(p);
                syncGroup(server, RunManager.groupOf(p));
                continue;
            }
            if (txTicks % 5 == 0) updateRoutes(p);
            // el walkie del que recibe cruje: lo oyen los de alrededor (y los mobs)
            if (txTicks % 30 == 0) {
                for (Rx rx : ROUTES.getOrDefault(id, List.of())) {
                    ServerPlayer o = server.getPlayerList().getPlayer(rx.player());
                    if (o == null || o.isSpectator() || DeathManager.isDead(o)) continue;
                    o.serverLevel().playSound(null, o.blockPosition(), ModSounds.WALKIE_CRACKLE.get(), SoundSource.PLAYERS, 0.5f,
                            0.9f + o.getRandom().nextFloat() * 0.2f);
                    NoiseSystem.emit(o.serverLevel(), o.position(), SPConfig.get().noise_walkie_receive, NoiseType.WALKIE, o);
                }
            }
        }
        if (txTicks % 10 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p, false);
        }
    }

    private static void syncGroup(MinecraftServer server, @Nullable Group g) {
        if (g == null) return;
        for (UUID id : g.members().keySet()) {
            ServerPlayer o = server.getPlayerList().getPlayer(id);
            if (o != null) sync(o, false);
        }
    }

    /** Estado del walkie para la HUD del jugador. */
    public static void sync(ServerPlayer p, boolean force) {
        Payloads.SyncWalkie s = Payloads.SyncWalkie.NONE;
        if (enabled() && SPPlayerData.inSP(p)) {
            boolean has = hasWalkie(p);
            String rx = "";
            boolean noise = false;
            Group g = RunManager.groupOf(p);
            if (g != null && (DeathManager.isDead(p) || (has && isOn(p)))) {
                for (UUID id : TX) {
                    if (id.equals(p.getUUID()) || !g.isMember(id)) continue;
                    for (Rx r : ROUTES.getOrDefault(id, List.of())) {
                        if (r.player().equals(p.getUUID())) {
                            rx = rx.isEmpty() ? g.name(id) : rx + ", " + g.name(id);
                            noise |= r.interference();
                        }
                    }
                }
            }
            s = new Payloads.SyncWalkie(has, isOn(p), TX.contains(p.getUUID()), rx, noise || (TX.contains(p.getUUID()) && interference(p)));
        }
        Payloads.SyncWalkie old = SYNCED.get(p.getUUID());
        if (!force && s.equals(old)) return;
        if (s.equals(Payloads.SyncWalkie.NONE) && old == null && !force) return;
        SYNCED.put(p.getUUID(), s);
        SPNetwork.sendTo(p, s);
    }

    public static void onLogout(ServerPlayer p) {
        TX.remove(p.getUUID());
        ROUTES.remove(p.getUUID());
        SYNCED.remove(p.getUUID());
    }
}
