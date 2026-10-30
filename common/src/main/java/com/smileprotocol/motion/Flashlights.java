package com.smileprotocol.motion;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Linterna (README 10, 21.1): va siempre equipada dentro de la partida, sin ocupar hueco, y se
 * enciende con F. Gasta bateria mientras esta encendida ({@code flashlight_battery_seconds}); sin
 * bateria se apaga. El servidor envia el estado a los cercanos para que vean el haz (Veil).
 */
public final class Flashlights {

    private static final Set<UUID> ON = new HashSet<>();
    private static final Map<UUID, Float> BATTERY = new HashMap<>();

    private Flashlights() {}

    public static boolean isOn(ServerPlayer p) {
        return ON.contains(p.getUUID());
    }

    public static float battery(ServerPlayer p) {
        return BATTERY.getOrDefault(p.getUUID(), 1f);
    }

    /** Gasta bateria de golpe (Apagon se la come cuando le apuntas). */
    public static void drain(ServerPlayer p, float amount) {
        float b = Math.max(0f, battery(p) - amount);
        BATTERY.put(p.getUUID(), b);
        if (b <= 0f && isOn(p)) set(p, false);
    }

    /** Recarga (al empezar la partida; las baterias como consumible llegan en la fase 13). */
    public static void recharge(ServerPlayer p, float amount) {
        BATTERY.put(p.getUUID(), Math.min(1f, battery(p) + amount));
        broadcast(p, isOn(p));
    }

    public static void onToggle(Payloads.ToggleFlashlight in, ServerPlayer p) {
        if (!SPPlayerData.inSP(p)) return;
        set(p, in.on() && battery(p) > 0f);
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
        Payloads.SyncFlashlight packet = new Payloads.SyncFlashlight(p.getId(), on, battery(p));
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p || o.distanceToSqr(p) < 96 * 96) SPNetwork.sendTo(o, packet);
        }
    }

    /** Cada segundo: gasta bateria, se apaga al agotarse o al salir de la partida. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        float drain = 1f / Math.max(10, SPConfig.get().flashlight_battery_seconds);
        for (UUID id : Set.copyOf(ON)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                ON.remove(id);
                continue;
            }
            if (!SPPlayerData.inSP(p)) {
                set(p, false);
                continue;
            }
            float b = Math.max(0f, battery(p) - drain);
            BATTERY.put(id, b);
            if (b <= 0f) {
                p.displayClientMessage(Component.translatable("smileprotocol.flashlight.empty").withStyle(ChatFormatting.RED), true);
                set(p, false);
            } else {
                broadcast(p, true);
            }
        }
    }
}
