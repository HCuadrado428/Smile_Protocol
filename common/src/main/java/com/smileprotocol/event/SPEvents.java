package com.smileprotocol.event;

import com.smileprotocol.ServerRef;
import com.smileprotocol.compat.ModCheck;
import com.smileprotocol.compat.ModIsolation;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.lobby.LobbyManager;
import com.smileprotocol.lobby.LobbyProtection;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.session.SPHeal;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Centro de eventos comun. Los loaders llaman a los de ciclo de vida; los mixins comunes
 * llaman a los {@code cancel...}. Todo lo que restringe solo actua dentro de la partida o en
 * la region del lobby (README 1, regla de oro; README 4.2).
 */
public final class SPEvents {

    private SPEvents() {}

    // Ciclo de vida -------------------------------------------------------------------

    public static void onServerStarting(MinecraftServer server) {
        ServerRef.set(server);
        SPConfig.load();
    }

    public static void onServerStopped(MinecraftServer server) {
        ServerRef.set(null);
    }

    public static void onLogin(ServerPlayer player) {
        ModCheck.onLogin(player);
        SPSessionManager.repairOnLogin(player);
        if (!SPPlayerData.inSP(player) && SPConfig.get().teleport_to_lobby_on_join
                && !player.isCreative() && !player.isSpectator()
                && LobbyData.get(player.server).getSpawn() != null) {
            SPSessionManager.sendToLobby(player);
        }
        LobbyManager.onLogin(player);
    }

    public static void onLogout(ServerPlayer player) {
        // El estado ya esta en los datos del jugador: no se pierde nada
        ModCheck.onLogout(player);
        LobbyManager.onLogout(player);
    }

    public static void onChangedDimension(ServerPlayer player, ResourceKey<Level> to) {
        if (SPSessionManager.isTeleporting(player)) return;
        boolean inSP = SPPlayerData.inSP(player);
        if (inSP && to != SPDimension.SP_DIM) {
            // Ha salido por otra via: restaurar alli donde este
            SPSessionManager.leave(player, SPSessionManager.Destination.STAY);
        } else if (!inSP && to == SPDimension.SP_DIM && !player.isCreative() && !player.isSpectator()) {
            // Nadie entra en las instancias sin partida
            SPSessionManager.sendToLobby(player);
        }
    }

    public static void onRespawn(ServerPlayer player) {
        // Hasta la fase 12 (derribado y muerte) morir en la partida devuelve al jugador al lobby
        if (SPPlayerData.inSP(player) && !SPDimension.isSP(player.level())) {
            SPSessionManager.leave(player, SPSessionManager.Destination.LOBBY);
        } else {
            SPSessionManager.sync(player);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (SPPlayerData.inSP(p)) SPSessionManager.feed(p);
            if (p.tickCount % 20 == 0) ModCheck.tickSecond(p);
        }
        LobbyManager.tick(server);
        if (server.getTickCount() % 20 == 0) SPDebug.tick(server);
    }

    // Bloqueos (mixins comunes) -----------------------------------------------------------

    private static boolean restricted(Player p) {
        return SPPlayerData.inSP(p) && !p.isCreative();
    }

    /** Romper bloques: nunca dentro de la partida ni en el lobby. */
    public static boolean cancelBreak(ServerPlayer player, BlockPos pos) {
        return restricted(player) || LobbyProtection.protects(player, pos);
    }

    /**
     * Clic derecho sobre un bloque (incluye colocar). Dentro de la partida y en el lobby solo
     * se interactua con bloques del mod. Devuelve un resultado para cortar o null para seguir.
     */
    @Nullable
    public static InteractionResult useItemOn(ServerPlayer player, ItemStack stack, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        boolean ownBlock = ModIsolation.isOwn(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()));
        if (restricted(player) && !ownBlock) return InteractionResult.PASS;
        if (LobbyProtection.blocksUse(player, pos)) return InteractionResult.PASS;
        return null;
    }

    /** Usar un item en el aire: dentro solo los del mod. */
    public static boolean cancelUseItem(ServerPlayer player, ItemStack stack) {
        return SPPlayerData.inSP(player) && !stack.isEmpty()
                && !ModIsolation.isOwn(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Atacar: nunca dentro (README 8.3); en el lobby tampoco. */
    public static boolean cancelAttack(Player player, Entity target) {
        return SPPlayerData.inSP(player) || LobbyProtection.protects(player, target.blockPosition());
    }

    /** Interactuar con entidades (marcos, soportes...). */
    public static boolean cancelInteract(Player player, Entity target) {
        return SPPlayerData.inSP(player) || LobbyProtection.protects(player, target.blockPosition());
    }

    /** Sin dano en el lobby. */
    public static boolean cancelHurt(LivingEntity entity) {
        return entity instanceof Player p && !p.level().isClientSide && LobbyProtection.blocksDamage(p);
    }

    /** Sin regeneracion natural dentro (README 9.2). */
    public static boolean cancelHeal(LivingEntity entity) {
        return entity instanceof Player p && !p.level().isClientSide && SPPlayerData.inSP(p) && !SPHeal.isAllowed();
    }

    /** Sin recogida automatica (README 10). */
    public static boolean cancelPickup(Player player) {
        return !player.level().isClientSide && SPPlayerData.inSP(player);
    }

    /** Entidades de otros mods en las instancias y mobs en el lobby. */
    public static boolean cancelSpawn(ServerLevel level, Entity entity) {
        return ModIsolation.blocksEntity(level, entity) || LobbyProtection.blocksSpawn(level, entity);
    }

    public static boolean cancelCommand(CommandSourceStack source, @Nullable String root) {
        return ModIsolation.blocksCommand(source, root);
    }
}
