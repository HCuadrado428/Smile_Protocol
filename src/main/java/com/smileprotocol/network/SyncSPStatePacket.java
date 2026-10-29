package com.smileprotocol.network;

import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S -> C: si el jugador esta en una partida y su inventario de 4 huecos (README 23). */
public record SyncSPStatePacket(boolean inSP, ItemStack[] slots, int selected) {

    public static SyncSPStatePacket of(SPPlayerData data) {
        SPInventory inv = data.getSPInventory();
        ItemStack[] slots = new ItemStack[SPInventory.SIZE];
        for (int i = 0; i < SPInventory.SIZE; i++) slots[i] = inv.get(i).copy();
        return new SyncSPStatePacket(data.inSP(), slots, inv.getSelected());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inSP);
        for (ItemStack s : slots) buf.writeItem(s);
        buf.writeByte(selected);
    }

    public static SyncSPStatePacket decode(FriendlyByteBuf buf) {
        boolean inSP = buf.readBoolean();
        ItemStack[] slots = new ItemStack[SPInventory.SIZE];
        for (int i = 0; i < SPInventory.SIZE; i++) slots[i] = buf.readItem();
        return new SyncSPStatePacket(inSP, slots, buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.onSyncState(this));
    }
}
