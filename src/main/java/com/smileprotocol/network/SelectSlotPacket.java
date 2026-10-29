package com.smileprotocol.network;

import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C -> S: hueco activo (1-4) del inventario de la partida (README 10). */
public record SelectSlotPacket(int slot) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(slot);
    }

    public static SelectSlotPacket decode(FriendlyByteBuf buf) {
        return new SelectSlotPacket(buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || slot < 0 || slot >= SPInventory.SIZE) return;
        SPPlayerData data = ModCapabilities.get(player);
        if (!data.inSP()) return;
        data.getSPInventory().setSelected(slot);
    }
}
