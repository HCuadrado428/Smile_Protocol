package com.smileprotocol.event;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.compat.ModCheck;
import com.smileprotocol.compat.ModIsolation;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.lobby.LobbyManager;
import com.smileprotocol.lobby.LobbyProtection;
import com.smileprotocol.registry.ModCapabilities;
import com.smileprotocol.session.SPHeal;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos comunes (bus de Forge). Cada regla de la partida se aplica solo si el jugador esta
 * en una partida ({@code inSP}, regla de oro del README 1).
 */
@Mod.EventBusSubscriber(modid = SmileProtocol.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {

    private CommonEvents() {}

    // Sesion (README 5.3) -------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ModCheck.onLogin(player);
        SPSessionManager.repairOnLogin(player);
        if (!ModCapabilities.inSP(player) && SPConfig.TELEPORT_TO_LOBBY_ON_JOIN.get()
                && !player.isCreative() && !player.isSpectator()
                && LobbyData.get(player.server).getSpawn() != null) {
            SPSessionManager.sendToLobby(player);
        }
        LobbyManager.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // El estado ya esta en los datos del jugador: no se pierde nada.
        ModCheck.onLogout(player);
        LobbyManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SPSessionManager.isTeleporting(player)) return;
        boolean inSP = ModCapabilities.inSP(player);
        if (inSP && event.getTo() != SPDimension.SP_DIM) {
            // Ha salido por otra via: restaurar alli donde este
            SPSessionManager.leave(player, SPSessionManager.Destination.STAY);
        } else if (!inSP && event.getTo() == SPDimension.SP_DIM && !player.isCreative() && !player.isSpectator()) {
            // Nadie entra en las instancias sin partida
            SPSessionManager.sendToLobby(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Hasta la fase 12 (derribado y muerte) morir en la partida devuelve al jugador al lobby.
        if (ModCapabilities.inSP(player) && !SPDimension.isSP(player.level())) {
            SPSessionManager.leave(player, SPSessionManager.Destination.LOBBY);
        } else {
            SPSessionManager.sync(player);
        }
    }

    // Ticks --------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (ModCapabilities.inSP(player)) SPSessionManager.feed(player);
        if (player.tickCount % 20 == 0) ModCheck.tickSecond(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LobbyManager.tick(event.getServer());
        if (event.getServer().getTickCount() % 20 == 0) SPDebug.tick(event.getServer());
    }

    // Vida y hambre (README 9) ---------------------------------------------------

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player p && ModCapabilities.inSP(p) && !SPHeal.isAllowed()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player p && ModCapabilities.inSP(p) && event.getItem().isEdible()) {
            event.setCanceled(true);
        }
    }

    // Sin atacar, romper ni colocar (README 1, 8.3) -------------------------------

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (ModCapabilities.inSP(p) && !p.isCreative()) event.setCanceled(true);
        else LobbyProtection.onBreak(event);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && ModCapabilities.inSP(p) && !p.isCreative()) event.setCanceled(true);
        else LobbyProtection.onPlace(event);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (ModCapabilities.inSP(event.getEntity())) event.setCanceled(true);
        else LobbyProtection.onAttack(event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player p = event.getEntity();
        if (ModCapabilities.inSP(p) && !p.isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player p = event.getEntity();
        if (ModCapabilities.inSP(p) && !p.isCreative()) {
            // Solo los bloques e items del mod son interactuables dentro (README 7.3)
            boolean ownBlock = ModIsolation.isOwn(BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()));
            if (!ownBlock) {
                event.setUseBlock(Event.Result.DENY);
                if (!isOwnItem(event.getItemStack())) event.setCanceled(true);
            }
        } else {
            LobbyProtection.onRightClickBlock(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ModCapabilities.inSP(event.getEntity()) && !isOwnItem(event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (ModCapabilities.inSP(event.getEntity())) event.setCanceled(true);
        else LobbyProtection.onEntityInteract(event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (ModCapabilities.inSP(event.getEntity())) event.setCanceled(true);
        else LobbyProtection.onEntityInteractSpecific(event);
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        // Sin recogida automatica: se recoge mirando e interactuando (README 10)
        if (ModCapabilities.inSP(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LobbyProtection.onLivingAttack(event);
    }

    // Aislamiento (README 7.3) ----------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (ModIsolation.blockEntityJoin(event)) {
            event.setCanceled(true);
            return;
        }
        LobbyProtection.onEntityJoin(event);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ModIsolation.onCommand(event);
    }

    private static boolean isOwnItem(ItemStack stack) {
        return stack.isEmpty() || ModIsolation.isOwn(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
