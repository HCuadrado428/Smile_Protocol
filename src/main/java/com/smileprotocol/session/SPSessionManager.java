package com.smileprotocol.session;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.network.SyncSPStatePacket;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda y restaura el estado del jugador al entrar y salir de una partida (README 5.3).
 */
public final class SPSessionManager {

    /** Modificador de vida maxima mientras dura la partida (README 9.1). */
    private static final UUID MAX_HEALTH_ID = UUID.fromString("6f3b1c2e-5a4d-4e7f-9b21-3c5d7e9f1a02");

    /** Jugadores a los que estamos teletransportando nosotros: el cambio de dimension es legitimo. */
    private static final Set<UUID> TELEPORTING = new HashSet<>();

    private SPSessionManager() {}

    public static boolean isTeleporting(ServerPlayer player) {
        return TELEPORTING.contains(player.getUUID());
    }

    /**
     * Mete al jugador en una partida: guarda su estado, vacia el inventario y lo lleva a la
     * instancia (README 5.3, pasos 1 a 6).
     */
    public static void enter(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw, @Nullable UUID groupId) {
        SPPlayerData data = ModCapabilities.get(player);

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
        sync(player);
        SPDebug.log("enter " + player.getGameProfile().getName() + " -> " + target.dimension().location() + " " + BlockPos.containing(pos));
    }

    /**
     * Saca al jugador de la partida y restaura su estado. Si {@code toLobby}, lo deja en el
     * lobby; si no, donde estaba al bajar.
     */
    public static void leave(ServerPlayer player, boolean toLobby) {
        leave(player, toLobby ? Destination.LOBBY : Destination.ORIGIN);
    }

    /** A donde va el jugador al salir de la partida. */
    public enum Destination {
        /** Punto de aparicion del lobby. */
        LOBBY,
        /** Donde estaba al bajar (o el lobby si no se puede). */
        ORIGIN,
        /** Se queda donde esta (ha salido por otra via: /tp, muerte...). */
        STAY
    }

    public static void leave(ServerPlayer player, Destination dest) {
        SPPlayerData data = ModCapabilities.get(player);
        boolean wasIn = data.inSP();

        data.setInSP(false);
        data.setGroupId(null);
        data.getSPInventory().clear();
        removeMaxHealth(player);

        if (data.hasSaved()) {
            player.getInventory().clearContent();
            if (SPConfig.RESTORE_INVENTORY.get()) player.getInventory().load(data.getSavedInventory());
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
        SPNetwork.sendTo(player, SyncSPStatePacket.of(ModCapabilities.get(player)));
    }

    /**
     * Repara incoherencias al conectarse (README 5.3): {@code inSP} fuera de la dimension, o
     * dentro de la dimension sin {@code inSP}.
     */
    public static void repairOnLogin(ServerPlayer player) {
        SPPlayerData data = ModCapabilities.get(player);
        boolean inDim = SPDimension.isSP(player.level());
        if (data.inSP() && !inDim) {
            SmileProtocol.LOGGER.info("Restaurando a {}: inSP fuera de la dimension", player.getGameProfile().getName());
            leave(player, true);
        } else if (!data.inSP() && inDim) {
            SmileProtocol.LOGGER.info("Sacando a {}: en la dimension sin partida", player.getGameProfile().getName());
            if (data.hasSaved()) leave(player, true);
            else sendToLobby(player);
        } else if (!data.inSP() && data.hasSaved()) {
            // Estado guardado sin partida: algo se interrumpio. Devolver el inventario.
            leave(player, true);
        } else if (data.inSP()) {
            // Sigue en una partida. La reincorporacion al grupo llega en la fase 6.
            applyMaxHealth(player);
            player.sendSystemMessage(Component.translatable("smileprotocol.session.rejoined"));
        }
        sync(player);
    }

    private static void applyMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MAX_HEALTH_ID);
        double extra = SPConfig.MAX_HEALTH.get() - attr.getBaseValue();
        if (extra != 0) {
            attr.addPermanentModifier(new AttributeModifier(MAX_HEALTH_ID, "smileprotocol_max_health", extra,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void removeMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(MAX_HEALTH_ID);
    }

    public static boolean isSPDimension(ResourceKey<Level> key) {
        return key == SPDimension.SP_DIM;
    }
}
