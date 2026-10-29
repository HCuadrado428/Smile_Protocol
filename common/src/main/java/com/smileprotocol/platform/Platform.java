package com.smileprotocol.platform;

import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lo poco que cambia entre loaders. Cada loader lo implementa ({@code FabricPlatform},
 * {@code NeoForgePlatform}) y se carga con {@link Services}.
 */
public interface Platform {

    /** "fabric" o "neoforge". */
    String loaderName();

    boolean isClient();

    Path configDir();

    boolean isModLoaded(String modId);

    /**
     * Mods cargados de primer nivel (sin las librerias que otro mod lleva dentro), para el
     * handshake de la lista blanca (README 7.1).
     */
    List<String> topLevelModIds();

    /** Registra un objeto en un registro vanilla. En NeoForge es diferido. */
    <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory);

    /** Datos persistentes del jugador (adjuntos a la entidad, se copian al morir). */
    SPPlayerData playerData(Player player);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);
}
