package com.smileprotocol.neoforge;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.platform.Platform;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Implementacion de {@link Platform} para NeoForge. */
public final class NeoForgePlatform implements Platform {

    /** Un DeferredRegister por registro vanilla usado. */
    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SmileProtocol.MODID);

    /** Datos del jugador: persistentes y copiados al morir. */
    public static final Supplier<AttachmentType<SPPlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.builder(SPPlayerData::new).serialize(SPPlayerData.CODEC).copyOnDeath().build());

    /** Conecta todos los registros al bus del mod. Se llama tras registrar el contenido. */
    static void registerAll(IEventBus modBus) {
        REGISTERS.values().forEach(r -> r.register(modBus));
        ATTACHMENTS.register(modBus);
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<String> topLevelModIds() {
        return ModList.get().getMods().stream().map(IModInfo::getModId).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory) {
        DeferredRegister<T> reg = (DeferredRegister<T>) REGISTERS.computeIfAbsent(registry.key(),
                k -> DeferredRegister.create((ResourceKey<? extends Registry<T>>) k, SmileProtocol.MODID));
        return reg.register(name, factory);
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public SPPlayerData playerData(Player player) {
        return player.getData(PLAYER_DATA);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
