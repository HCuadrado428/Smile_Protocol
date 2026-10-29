package com.smileprotocol.instance;

import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Todo el estado de juego de una instancia (README 6.3, punto 2): un grupo, su hueco, su zona
 * y su dificultad. Ningun sistema usa estado global de partida: circuitos, cerraduras,
 * misiones, eventos, escondites, mobs, ruido, marcadores, luces y walkie piden su estado aqui
 * con {@link #state}, a partir del jugador ({@link #of(ServerPlayer)}) o de la posicion
 * ({@link #at(ServerLevel, BlockPos)}).
 */
public final class InstanceContext {

    private static final Map<UUID, InstanceContext> CONTEXTS = new HashMap<>();

    private final UUID groupId;
    private final Map<String, Object> state = new HashMap<>();

    private InstanceContext(UUID groupId) {
        this.groupId = groupId;
    }

    /** Contexto de la partida del jugador, o null si no esta en ninguna. */
    @Nullable
    public static InstanceContext of(ServerPlayer player) {
        SPPlayerData d = SPPlayerData.of(player);
        if (!d.inSP() || d.getGroupId() == null) return null;
        return forGroup(player.server, d.getGroupId());
    }

    /** Contexto de la instancia que contiene una posicion de la dimension, o null. */
    @Nullable
    public static InstanceContext at(ServerLevel level, BlockPos pos) {
        Group g = InstanceManager.groupAt(level, pos);
        return g == null ? null : forGroup(level.getServer(), g.id());
    }

    @Nullable
    public static InstanceContext forGroup(net.minecraft.server.MinecraftServer server, UUID groupId) {
        if (RunManager.group(server, groupId) == null) {
            CONTEXTS.remove(groupId);
            return null;
        }
        return CONTEXTS.computeIfAbsent(groupId, InstanceContext::new);
    }

    /** Se olvida el estado en memoria de un grupo (al limpiar su instancia). */
    public static void discard(UUID groupId) {
        CONTEXTS.remove(groupId);
    }

    public static void clearAll() {
        CONTEXTS.clear();
    }

    public UUID groupId() {
        return groupId;
    }

    @Nullable
    public Group group(net.minecraft.server.MinecraftServer server) {
        return RunManager.group(server, groupId);
    }

    @Nullable
    public ZonePlacement zone(net.minecraft.server.MinecraftServer server) {
        Group g = group(server);
        return g == null ? null : g.current();
    }

    public DifficultyProfile difficulty(net.minecraft.server.MinecraftServer server) {
        Group g = group(server);
        return g == null ? DifficultyProfile.BASE : g.difficulty();
    }

    public BlockPos origin(net.minecraft.server.MinecraftServer server) {
        Group g = group(server);
        return g == null ? BlockPos.ZERO : InstanceManager.slotOrigin(g.slot());
    }

    /** Estado propio de un sistema en esta instancia. Se crea la primera vez que se pide. */
    @SuppressWarnings("unchecked")
    public <T> T state(String key, Supplier<T> factory) {
        return (T) state.computeIfAbsent(key, k -> factory.get());
    }

    /** Borra el estado de los sistemas (al cambiar de zona, lo de la zona anterior ya no sirve). */
    public void resetZoneState() {
        state.clear();
    }
}
