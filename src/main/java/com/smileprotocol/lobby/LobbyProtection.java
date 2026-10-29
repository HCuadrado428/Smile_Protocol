package com.smileprotocol.lobby;

import com.smileprotocol.compat.ModIsolation;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.registry.ModCapabilities;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Protecciones de la region del lobby (README 4.2). Para jugadores que no esten en creativo:
 * no se rompe, no se coloca, solo se interactua con bloques del mod, no hay dano ni PvP,
 * no aparecen mobs y no hay hambre. Al entrar se pasa a modo aventura.
 */
public final class LobbyProtection {

    private LobbyProtection() {}

    @Nullable
    private static LobbyData lobbyFor(LevelAccessor level) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return null;
        LobbyData data = LobbyData.get(sl.getServer());
        return data.getRegion() == null ? null : data;
    }

    private static boolean protects(Player p, LevelAccessor level, BlockPos pos) {
        if (p.isCreative()) return false;
        LobbyData data = lobbyFor(level);
        return data != null && data.inRegion(pos);
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (protects(event.getPlayer(), event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && protects(p, event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!protects(event.getEntity(), event.getLevel(), event.getPos())) return;
        boolean own = ModIsolation.isOwn(BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()));
        if (!own) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (protects(event.getEntity(), event.getLevel(), event.getTarget().blockPosition())) event.setCanceled(true);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (protects(event.getEntity(), event.getLevel(), event.getTarget().blockPosition())) event.setCanceled(true);
    }

    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (protects(p, p.level(), event.getTarget().blockPosition())) event.setCanceled(true);
    }

    /** Sin dano ni PvP dentro del lobby. */
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        LobbyData data = lobbyFor(p.level());
        if (data != null && data.inRegion(p.position()) && !ModCapabilities.inSP(p)) event.setCanceled(true);
    }

    /** Sin mobs en el lobby (salvo los del propio mod). */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof Mob)) return;
        LobbyData data = lobbyFor(event.getLevel());
        if (data == null || !data.inRegion(e.blockPosition())) return;
        if (!ModIsolation.isOwn(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) event.setCanceled(true);
    }

    /** Cada segundo: modo de juego del lobby y sin hambre para quien esta dentro. */
    public static void tickPlayers(MinecraftServer server, LobbyData data) {
        if (data.getRegion() == null) return;
        GameType mode = "survival".equalsIgnoreCase(SPConfig.LOBBY_GAMEMODE.get()) ? GameType.SURVIVAL : GameType.ADVENTURE;
        for (ServerPlayer p : server.overworld().players()) {
            if (ModCapabilities.inSP(p) || !data.inRegion(p.position())) continue;
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
            SPSessionManager.feed(p);
        }
    }
}
