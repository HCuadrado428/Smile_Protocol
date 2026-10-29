package com.smileprotocol.fabric;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.platform.Platform;
import com.smileprotocol.player.SPPlayerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Implementacion de {@link Platform} para Fabric. */
public final class FabricPlatform implements Platform {

    /** Datos del jugador: persistentes y copiados al morir. */
    public static final AttachmentType<SPPlayerData> PLAYER_DATA = AttachmentRegistry.<SPPlayerData>builder()
            .persistent(SPPlayerData.CODEC)
            .copyOnDeath()
            .initializer(SPPlayerData::new)
            .buildAndRegister(SmileProtocol.id("player_data"));

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<String> topLevelModIds() {
        // Las librerias que otro mod lleva dentro (jar-in-jar) no cuentan
        return FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> m.getContainingMod().isEmpty())
                .map(m -> m.getMetadata().getId())
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory) {
        T value = Registry.register((Registry<T>) registry, SmileProtocol.id(name), factory.get());
        return () -> value;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    }

    @Override
    public SPPlayerData playerData(Player player) {
        return player.getAttachedOrCreate(PLAYER_DATA);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
