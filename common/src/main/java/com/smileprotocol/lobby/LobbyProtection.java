package com.smileprotocol.lobby;

import com.smileprotocol.compat.ModIsolation;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Protecciones de la region del lobby (README 4.2). Para jugadores que no esten en creativo:
 * no se rompe, no se coloca, solo se interactua con bloques del mod, no hay dano ni PvP,
 * no aparecen mobs y no hay hambre. Al entrar se pasa a modo aventura.
 */
public final class LobbyProtection {

    private LobbyProtection() {}

    @Nullable
    private static LobbyData lobbyFor(Level level) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return null;
        LobbyData data = LobbyData.get(sl.getServer());
        return data.getRegion() == null ? null : data;
    }

    /** True si el jugador esta sujeto a las protecciones en esa posicion. */
    public static boolean protects(Player p, BlockPos pos) {
        if (p.isCreative() || SPPlayerData.inSP(p)) return false;
        LobbyData data = lobbyFor(p.level());
        return data != null && data.inRegion(pos);
    }

    /** Clic derecho sobre un bloque: solo los del mod. */
    public static boolean blocksUse(Player p, BlockPos pos) {
        if (!protects(p, pos)) return false;
        return !ModIsolation.isOwn(BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()));
    }

    /** Sin dano ni PvP dentro del lobby. */
    public static boolean blocksDamage(Player p) {
        if (SPPlayerData.inSP(p)) return false;
        LobbyData data = lobbyFor(p.level());
        return data != null && data.inRegion(p.position());
    }

    /** Sin mobs en el lobby (salvo los del propio mod). */
    public static boolean blocksSpawn(ServerLevel level, Entity e) {
        if (!(e instanceof Mob)) return false;
        LobbyData data = lobbyFor(level);
        if (data == null || !data.inRegion(e.blockPosition())) return false;
        return !ModIsolation.isOwn(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /** Cada segundo: modo de juego del lobby y sin hambre para quien esta dentro. */
    public static void tickPlayers(MinecraftServer server, LobbyData data) {
        if (data.getRegion() == null) return;
        GameType mode = "survival".equalsIgnoreCase(SPConfig.get().lobby_gamemode) ? GameType.SURVIVAL : GameType.ADVENTURE;
        for (ServerPlayer p : server.overworld().players()) {
            if (SPPlayerData.inSP(p) || !data.inRegion(p.position())) continue;
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
            SPSessionManager.feed(p);
        }
    }
}
