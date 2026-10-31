package com.smileprotocol.session;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Guarda y restaura el estado del jugador al entrar y salir de una partida (README 5.3). */
public final class SPSessionManager {

    /** Modificador de vida maxima mientras dura la partida (README 9.1). */
    private static final ResourceLocation MAX_HEALTH_ID = SmileProtocol.id("max_health");

    /** Jugadores a los que estamos teletransportando nosotros: el cambio de dimension es legitimo. */
    private static final Set<UUID> TELEPORTING = new HashSet<>();

    /** A donde va el jugador al salir de la partida. */
    public enum Destination {
        /** Punto de aparicion del lobby. */
        LOBBY,
        /** Donde estaba al bajar (o el lobby si no se puede). */
        ORIGIN,
        /** Se queda donde esta (ha salido por otra via: /tp, muerte...). */
        STAY
    }

    private SPSessionManager() {}

    public static boolean isTeleporting(ServerPlayer player) {
        return TELEPORTING.contains(player.getUUID());
    }

    /** Mete al jugador en una partida (README 5.3, pasos 1 a 6). */
    public static void enter(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw, @Nullable UUID groupId) {
        SPPlayerData data = SPPlayerData.of(player);

        if (!data.inSP()) {
            // 1. Guardar inventario completo, modo, posicion, dimension, vida y hambre
            ListTag inv = player.getInventory().save(new ListTag());
            data.saveState(inv, player.gameMode.getGameModeForPlayer().getId(), player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot(), player.getHealth(),
                    player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel());
            // 2. Vaciar el inventario real
            player.getInventory().clearContent();
            player.containerMenu.broadcastChanges();
            data.getSPInventory().clear();
        }

        // 3. Aventura como red de seguridad
        player.setGameMode(GameType.ADVENTURE);
        // 4. inSP y grupo
        data.setInSP(true);
        data.setGroupId(groupId);
        // 5. Hambre fija y vida de la partida
        applyMaxHealth(player);
        player.setHealth(player.getMaxHealth());
        feed(player);
        // 6. Teletransporte y sincronizacion
        teleport(player, target, pos, yaw, 0f);
        // Equipo inicial: con voz, un walkie (README 6.2, 17.3)
        com.smileprotocol.voice.WalkieManager.giveStarter(player);
        sync(player);
        SPDebug.log("enter " + player.getGameProfile().getName() + " -> " + target.dimension().location() + " " + BlockPos.containing(pos));
    }

    public static void leave(ServerPlayer player, Destination dest) {
        SPPlayerData data = SPPlayerData.of(player);
        boolean wasIn = data.inSP();
        boolean wasDead = wasIn && com.smileprotocol.downed.DeathManager.isDead(player);
        if (wasIn) RunManager.onPlayerLeft(player, data.getGroupId());
        com.smileprotocol.sequence.SequenceManager.clear(player);
        com.smileprotocol.voice.VoiceManager.forgetClips(player.getUUID());
        com.smileprotocol.hide.HideManager.forceExit(player, false);
        com.smileprotocol.downed.DownedManager.clear(player);
        com.smileprotocol.qte.QTEManager.clear(player);
        com.smileprotocol.interact.InteractionManager.stop(player, true);
        com.smileprotocol.item.UseManager.stop(player, true);

        data.setInSP(false);
        data.setGroupId(null);
        data.getSPInventory().clear();
        removeMaxHealth(player);

        if (data.hasSaved()) {
            player.getInventory().clearContent();
            if (SPConfig.get().restore_inventory) player.getInventory().load(data.getSavedInventory());
            player.containerMenu.broadcastChanges();
            player.setGameMode(GameType.byId(data.getSavedGameMode()));
            player.setHealth(Math.max(1f, Math.min(data.getSavedHealth(), player.getMaxHealth())));
            player.getFoodData().setFoodLevel(data.getSavedFood());
            player.getFoodData().setSaturation(data.getSavedSaturation());

            if (dest == Destination.ORIGIN) {
                ServerLevel origin = data.getSavedDimension() == null ? null : player.server.getLevel(data.getSavedDimension());
                if (origin != null && !SPDimension.isSP(origin)) {
                    teleport(player, origin, data.getSavedPos(), data.getSavedYRot(), data.getSavedXRot());
                } else {
                    dest = Destination.LOBBY;
                }
            }
            data.clearSaved();
        } else if (dest == Destination.ORIGIN) {
            dest = Destination.LOBBY;
        }

        if (wasDead) {
            // muerto en la partida (espectador): vuelve a ser jugador al salir
            if (player.isSpectator()) player.setGameMode(GameType.ADVENTURE);
            player.setCamera(player);
            com.smileprotocol.network.SPNetwork.sendTo(player, new com.smileprotocol.network.Payloads.SyncRevive(-1f, false));
        }
        if (dest == Destination.LOBBY || (dest == Destination.STAY && SPDimension.isSP(player.level()))) {
            sendToLobby(player);
        }
        sync(player);
        if (wasIn) SPDebug.log("leave " + player.getGameProfile().getName() + " -> " + dest);
    }

    /** Lleva al jugador al punto de aparicion del lobby (overworld). */
    public static void sendToLobby(ServerPlayer player) {
        MinecraftServer server = player.server;
        LobbyData lobby = LobbyData.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos spawn = lobby.getSpawn() != null ? lobby.getSpawn() : overworld.getSharedSpawnPos();
        float yaw = lobby.getSpawn() != null ? lobby.getSpawnYaw() : overworld.getSharedSpawnAngle();
        teleport(player, overworld, Vec3.atBottomCenterOf(spawn), yaw, 0f);
    }

    /** Teletransporte marcado como nuestro (no dispara la restauracion por cambio de dimension). */
    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        TELEPORTING.add(player.getUUID());
        try {
            player.stopRiding();
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
            player.fallDistance = 0;
        } finally {
            TELEPORTING.remove(player.getUUID());
        }
    }

    /** Hambre y saturacion al maximo: nunca se pierden en la partida (README 9.2). */
    public static void feed(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20f);
        player.getFoodData().setExhaustion(0f);
    }

    public static void sync(ServerPlayer player) {
        SPPlayerData data = SPPlayerData.of(player);
        SPNetwork.sendTo(player, Payloads.SyncState.of(data));
        if (data.inSP()) mirrorHand(player, data);
    }

    /**
     * El objeto del hueco activo se ve en la mano (README 13.2): se copia al hueco 0 del inventario
     * real, que dentro de la partida no tiene nada mas. Al salir se vacia y se restaura todo.
     */
    private static void mirrorHand(ServerPlayer player, SPPlayerData data) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        net.minecraft.world.item.ItemStack want = data.getSPInventory().getSelectedStack().copy();
        if (inv.selected != 0) {
            inv.selected = 0;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        }
        if (!net.minecraft.world.item.ItemStack.matches(inv.getItem(0), want)) {
            inv.setItem(0, want);
            player.containerMenu.broadcastChanges();
        }
    }

    /** Repara incoherencias al conectarse (README 5.3). */
    public static void repairOnLogin(ServerPlayer player) {
        SPPlayerData data = SPPlayerData.of(player);
        boolean inDim = SPDimension.isSP(player.level());
        if (data.inSP() && !inDim) {
            SmileProtocol.LOGGER.info("Restaurando a {}: inSP fuera de la dimension", player.getGameProfile().getName());
            leave(player, Destination.LOBBY);
        } else if (!data.inSP() && inDim) {
            SmileProtocol.LOGGER.info("Sacando a {}: en la dimension sin partida", player.getGameProfile().getName());
            if (data.hasSaved()) leave(player, Destination.LOBBY);
            else sendToLobby(player);
        } else if (!data.inSP() && data.hasSaved()) {
            // Estado guardado sin partida: algo se interrumpio. Devolver el inventario.
            leave(player, Destination.LOBBY);
        } else if (data.inSP()) {
            // Sigue en una partida: vuelve con su grupo si la partida sigue (README 6.3, 6.7)
            boolean testRoom = InstanceManager.slotAt(player.blockPosition()) == 0;
            if (RunManager.onRejoin(player)) {
                applyMaxHealth(player);
            } else if (testRoom) {
                applyMaxHealth(player);
                player.sendSystemMessage(Component.translatable("smileprotocol.session.rejoined"));
            } else {
                SmileProtocol.LOGGER.info("Sacando a {}: su partida ya no existe", player.getGameProfile().getName());
                player.sendSystemMessage(Component.translatable("smileprotocol.run.gone"));
                leave(player, Destination.LOBBY);
            }
        }
        sync(player);
    }

    private static void applyMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MAX_HEALTH_ID);
        double extra = SPConfig.get().max_health - attr.getBaseValue();
        if (extra != 0) {
            attr.addPermanentModifier(new AttributeModifier(MAX_HEALTH_ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(MAX_HEALTH_ID);
    }
}
