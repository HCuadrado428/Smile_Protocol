package com.smileprotocol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S -> C: contador «X/Y listos» del ascensor del lobby en el que esta el jugador (README 6.2).
 * {@code show == false} oculta el contador.
 */
public record SyncReadyPacket(boolean show, Component title, int ready, int total, int countdown, Component status) {

    public static SyncReadyPacket hidden() {
        return new SyncReadyPacket(false, Component.empty(), 0, 0, -1, Component.empty());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(show);
        if (!show) return;
        buf.writeComponent(title);
        buf.writeVarInt(ready);
        buf.writeVarInt(total);
        buf.writeVarInt(countdown + 1);
        buf.writeComponent(status);
    }

    public static SyncReadyPacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return hidden();
        return new SyncReadyPacket(true, buf.readComponent(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt() - 1, buf.readComponent());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.onSyncReady(this));
    }
}
