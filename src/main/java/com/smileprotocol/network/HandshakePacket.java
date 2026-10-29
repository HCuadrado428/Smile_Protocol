package com.smileprotocol.network;

import com.smileprotocol.compat.ModCheck;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C -> S: lista de mods cargados en el cliente (README 7.1, capa 2). */
public record HandshakePacket(List<String> mods) {

    private static final int MAX_MODS = 1024;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mods.size());
        for (String m : mods) buf.writeUtf(m, 64);
    }

    public static HandshakePacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_MODS);
        List<String> mods = new ArrayList<>(n);
        for (int i = 0; i < n; i++) mods.add(buf.readUtf(64));
        return new HandshakePacket(mods);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) ModCheck.onHandshake(player, mods);
    }
}
