package com.smileprotocol.interact;

import net.minecraft.world.item.ItemStack;

import java.util.function.IntFunction;

/**
 * Vista del inventario de 4 huecos en el cliente, para los prompts calculados en codigo comun
 * (sin depender de clases de cliente). El cliente pone el proveedor al arrancar.
 */
public final class ClientInventoryView {

    private static IntFunction<ItemStack> provider = i -> ItemStack.EMPTY;

    private ClientInventoryView() {}

    public static void setProvider(IntFunction<ItemStack> p) {
        provider = p;
    }

    public static ItemStack slot(int i) {
        return provider.apply(i);
    }
}
