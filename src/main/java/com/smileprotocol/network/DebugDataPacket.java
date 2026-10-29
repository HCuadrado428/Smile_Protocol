package com.smileprotocol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S -> C: lineas del modo depuracion. Solo se envia a operadores (README 24.1). Vacio = apagado. */
public record DebugDataPacket(List<String> lines) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        for (String l : lines) buf.writeUtf(l, 256);
    }

    public static DebugDataPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(256));
        return new DebugDataPacket(lines);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.onDebug(this));
    }
}
