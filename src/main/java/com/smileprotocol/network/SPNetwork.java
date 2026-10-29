package com.smileprotocol.network;

import com.smileprotocol.SmileProtocol;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de red del mod (README 23). El cliente debe tener el mod con la misma version
 * de protocolo: el evento se juega con un modpack cerrado.
 */
public final class SPNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SmileProtocol.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SPNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncSPStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSPStatePacket::encode).decoder(SyncSPStatePacket::decode)
                .consumerMainThread(SyncSPStatePacket::handle).add();
        CHANNEL.messageBuilder(SelectSlotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectSlotPacket::encode).decoder(SelectSlotPacket::decode)
                .consumerMainThread(SelectSlotPacket::handle).add();
        CHANNEL.messageBuilder(HandshakePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HandshakePacket::encode).decoder(HandshakePacket::decode)
                .consumerMainThread(HandshakePacket::handle).add();
        CHANNEL.messageBuilder(SyncReadyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncReadyPacket::encode).decoder(SyncReadyPacket::decode)
                .consumerMainThread(SyncReadyPacket::handle).add();
        CHANNEL.messageBuilder(SyncLobbyBoardsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncLobbyBoardsPacket::encode).decoder(SyncLobbyBoardsPacket::decode)
                .consumerMainThread(SyncLobbyBoardsPacket::handle).add();
        CHANNEL.messageBuilder(ScreenFadePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScreenFadePacket::encode).decoder(ScreenFadePacket::decode)
                .consumerMainThread(ScreenFadePacket::handle).add();
        CHANNEL.messageBuilder(DebugDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DebugDataPacket::encode).decoder(DebugDataPacket::decode)
                .consumerMainThread(DebugDataPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
