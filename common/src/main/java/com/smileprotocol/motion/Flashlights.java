package com.smileprotocol.motion;

import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Linterna (README 10, 21.1): se lleva en un hueco y se enciende con F. El servidor guarda quien
 * la tiene encendida y lo envia a los cercanos para que vean su haz (luz de Veil). La bateria
 * llega en la fase 20.
 */
public final class Flashlights {

    private static final Set<UUID> ON = new HashSet<>();

    private Flashlights() {}

    public static boolean hasFlashlight(ServerPlayer p) {
        return SPInventoryOps.find(p, s -> s.getItem() == ModItems.FLASHLIGHT.get()) >= 0;
    }

    public static boolean isOn(ServerPlayer p) {
        return ON.contains(p.getUUID());
    }

    public static void onToggle(Payloads.ToggleFlashlight in, ServerPlayer p) {
        if (!SPPlayerData.inSP(p)) return;
        boolean on = in.on() && hasFlashlight(p);
        set(p, on);
    }

    public static void set(ServerPlayer p, boolean on) {
        boolean was = ON.contains(p.getUUID());
        if (on) ON.add(p.getUUID());
        else ON.remove(p.getUUID());
        if (was != on) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.35f, on ? 1.8f : 1.4f);
            if (on) LabEvents.fire(p.serverLevel(), p.blockPosition(), p, "flashlight_on", "");
        }
        broadcast(p, on);
    }

    private static void broadcast(ServerPlayer p, boolean on) {
        Payloads.SyncFlashlight packet = new Payloads.SyncFlashlight(p.getId(), on);
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p || o.distanceToSqr(p) < 96 * 96) SPNetwork.sendTo(o, packet);
        }
    }

    /** Cada segundo: se apaga si ya no la lleva o salio de la partida; los que llegan ven las encendidas. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (UUID id : Set.copyOf(ON)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                ON.remove(id);
            } else if (!SPPlayerData.inSP(p) || !hasFlashlight(p)) {
                set(p, false);
            } else {
                broadcast(p, true);
            }
        }
    }
}
