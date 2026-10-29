package com.smileprotocol.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S -> C: lo que muestra cada pantalla de ascensor del lobby (README 5.2, 23). */
public record SyncLobbyBoardsPacket(List<Board> boards) {

    /** Una pantalla: dos lineas de texto y el color de la segunda (estado). */
    public record Board(BlockPos pos, Component title, Component status, int color) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(boards.size());
        for (Board b : boards) {
            buf.writeBlockPos(b.pos());
            buf.writeComponent(b.title());
            buf.writeComponent(b.status());
            buf.writeInt(b.color());
        }
    }

    public static SyncLobbyBoardsPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<Board> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Board(buf.readBlockPos(), buf.readComponent(), buf.readComponent(), buf.readInt()));
        }
        return new SyncLobbyBoardsPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.onSyncBoards(this));
    }
}
