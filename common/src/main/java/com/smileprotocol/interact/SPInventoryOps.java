package com.smileprotocol.interact;

import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Operaciones sobre los 4 huecos de la partida (README 10). */
public final class SPInventoryOps {

    private SPInventoryOps() {}

    public static SPInventory inv(ServerPlayer p) {
        return SPPlayerData.of(p).getSPInventory();
    }

    /** Hueco con un objeto que cumple la condicion: primero el activo, luego el resto. -1 si no hay. */
    public static int find(ServerPlayer p, Predicate<ItemStack> test) {
        SPInventory inv = inv(p);
        if (test.test(inv.getSelectedStack())) return inv.getSelected();
        for (int i = 0; i < SPInventory.SIZE; i++) if (test.test(inv.get(i))) return i;
        return -1;
    }

    public static int find(net.minecraft.world.entity.player.Player p, Predicate<ItemStack> test, boolean clientSide) {
        if (!clientSide && p instanceof ServerPlayer sp) return find(sp, test);
        // En el cliente se mira lo sincronizado
        for (int i = 0; i < SPInventory.SIZE; i++) {
            if (test.test(com.smileprotocol.interact.ClientInventoryView.slot(i))) return i;
        }
        return -1;
    }

    /** Mete un objeto en el primer hueco libre. False si esta lleno. */
    public static boolean give(ServerPlayer p, ItemStack stack) {
        SPInventory inv = inv(p);
        int slot = inv.getSelectedStack().isEmpty() ? inv.getSelected() : inv.firstEmpty();
        if (slot < 0) return false;
        inv.set(slot, stack);
        SPSessionManager.sync(p);
        return true;
    }

    public static ItemStack take(ServerPlayer p, int slot) {
        SPInventory inv = inv(p);
        ItemStack s = inv.get(slot);
        inv.set(slot, ItemStack.EMPTY);
        SPSessionManager.sync(p);
        return s;
    }

    public static boolean isFull(ServerPlayer p) {
        return inv(p).firstEmpty() < 0;
    }
}
