package com.smileprotocol.compat;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capa 2 del aislamiento (README 7.1): el cliente envia su lista de mods al conectarse.
 * Quien tenga mods fuera de la lista blanca, o le falte alguno obligatorio, no cuenta como
 * listo en ningun ascensor. Un cliente modificado puede mentir; esto cubre el caso normal.
 */
public final class ModCheck {

    public enum Status { PENDING, OK, DISALLOWED, MISSING, TIMEOUT }

    public record Result(Status status, List<String> disallowed, List<String> missing) {}

    private static final Map<UUID, Result> RESULTS = new HashMap<>();
    private static final Map<UUID, Integer> WAITING_TICKS = new HashMap<>();

    private ModCheck() {}

    public static void onLogin(ServerPlayer player) {
        RESULTS.put(player.getUUID(), new Result(Status.PENDING, List.of(), List.of()));
        WAITING_TICKS.put(player.getUUID(), 0);
    }

    public static void onLogout(ServerPlayer player) {
        RESULTS.remove(player.getUUID());
        WAITING_TICKS.remove(player.getUUID());
    }

    public static void onHandshake(ServerPlayer player, List<String> mods) {
        List<String> disallowed = new ArrayList<>();
        for (String m : mods) if (!SPConfig.isModAllowed(m)) disallowed.add(m);
        List<String> missing = new ArrayList<>();
        for (String r : SPConfig.get().required_mods) if (!mods.contains(r)) missing.add(r);

        Status status = !disallowed.isEmpty() ? Status.DISALLOWED : !missing.isEmpty() ? Status.MISSING : Status.OK;
        RESULTS.put(player.getUUID(), new Result(status, List.copyOf(disallowed), List.copyOf(missing)));
        WAITING_TICKS.remove(player.getUUID());

        if (status != Status.OK) {
            player.sendSystemMessage(reason(player).copy().withStyle(ChatFormatting.RED));
            SPDebug.log("handshake " + player.getGameProfile().getName() + ": " + status + " " + disallowed + " " + missing);
        }
    }

    /** Cada segundo: marca como fuera de plazo a quien no ha enviado su lista. */
    public static void tickSecond(ServerPlayer player) {
        Integer t = WAITING_TICKS.get(player.getUUID());
        if (t == null) return;
        t += 20;
        if (t >= SPConfig.get().handshake_timeout * 20) {
            WAITING_TICKS.remove(player.getUUID());
            RESULTS.put(player.getUUID(), new Result(Status.TIMEOUT, List.of(), List.of()));
            player.sendSystemMessage(reason(player).copy().withStyle(ChatFormatting.RED));
        } else {
            WAITING_TICKS.put(player.getUUID(), t);
        }
    }

    public static boolean isOk(ServerPlayer player) {
        Result r = RESULTS.get(player.getUUID());
        return r != null && r.status() == Status.OK;
    }

    public static Result get(ServerPlayer player) {
        return RESULTS.getOrDefault(player.getUUID(), new Result(Status.PENDING, List.of(), List.of()));
    }

    /** Motivo por el que el jugador no puede bajar, para mostrarselo. */
    public static Component reason(ServerPlayer player) {
        Result r = get(player);
        return switch (r.status()) {
            case OK -> Component.empty();
            case PENDING -> Component.translatable("smileprotocol.modcheck.pending");
            case TIMEOUT -> Component.translatable("smileprotocol.modcheck.timeout");
            case DISALLOWED -> Component.translatable("smileprotocol.modcheck.disallowed", String.join(", ", r.disallowed()));
            case MISSING -> Component.translatable("smileprotocol.modcheck.missing", String.join(", ", r.missing()));
        };
    }

    public static String describe(ServerPlayer player) {
        Result r = get(player);
        return r.status() + (r.disallowed().isEmpty() ? "" : " " + r.disallowed()) + (r.missing().isEmpty() ? "" : " falta " + r.missing());
    }
}
