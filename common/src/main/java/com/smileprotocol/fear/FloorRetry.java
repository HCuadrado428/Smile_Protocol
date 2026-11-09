package com.smileprotocol.fear;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vidas por planta (test del miedo): si te matan y te queda alguna, despiertas en el ascensor de la
 * planta sin lo que cogiste en ella. Con {@code lives_per_floor} = 2, la primera muerte de cada
 * planta te devuelve y la segunda es de verdad (como siempre). Vale tambien en la sala final.
 */
public final class FloorRetry {

    private static final class Snap {
        final UUID group;
        final int zone;
        final List<ItemStack> items = new ArrayList<>();
        int deaths;

        Snap(UUID group, int zone) {
            this.group = group;
            this.zone = zone;
        }
    }

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();

    private FloorRetry() {}

    /** Al pisar una planta nueva: se apunta lo que llevaba. */
    public static void onFloor(ServerPlayer p, UUID group, int zone) {
        Snap s = SNAPS.get(p.getUUID());
        if (s != null && s.group.equals(group) && s.zone == zone) return;
        s = new Snap(group, zone);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) s.items.add(inv.getItem(i).copy());
        SNAPS.put(p.getUUID(), s);
    }

    /** Cada segundo: se apunta lo que lleva cada jugador al pisar una planta nueva de su partida. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!com.smileprotocol.player.SPPlayerData.inSP(p) || p.isSpectator()) continue;
            Group g = RunManager.groupOf(p);
            ZonePlacement z = g == null ? null : g.current();
            if (z == null || z.box() == null || !z.box().isInside(p.blockPosition())) continue;
            onFloor(p, g.id(), z.index());
        }
    }

    /** Lo llama la muerte: true si en vez de morir vuelve al principio de la planta. */
    public static boolean tryRetry(ServerPlayer p) {
        Snap s = SNAPS.get(p.getUUID());
        Group g = RunManager.groupOf(p);
        if (s == null || g == null || !g.id().equals(s.group)) return false;
        ZonePlacement z = g.current();
        if (z == null || z.index() != s.zone) return false;
        if (s.deaths >= SPConfig.get().lives_per_floor - 1) return false;
        s.deaths++;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && i < s.items.size(); i++) inv.setItem(i, s.items.get(i).copy());
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.fallDistance = 0;
        com.smileprotocol.session.SPSessionManager.teleport(p, p.serverLevel(), z.entry(), z.entryYaw(), 0f);
        SPNetwork.sendTo(p, new Payloads.ScreenFade(0, 40, 30));
        int left = SPConfig.get().lives_per_floor - 1 - s.deaths;
        p.displayClientMessage(Component.translatable(left > 0 ? "smileprotocol.retry.woke" : "smileprotocol.retry.last")
                .withStyle(ChatFormatting.DARK_RED), false);
        FearDirector.calm(p, 20 * 30);
        com.smileprotocol.debug.SPDebug.log(p.getGameProfile().getName() + " vuelve al principio de la planta (" + s.deaths + ")");
        return true;
    }

    /** Pruebas. */
    public static int deaths(ServerPlayer p) {
        Snap s = SNAPS.get(p.getUUID());
        return s == null ? 0 : s.deaths;
    }

    public static void forget(UUID p) {
        SNAPS.remove(p);
    }

    public static void clearAll() {
        SNAPS.clear();
    }
}
