package com.smileprotocol.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fusible (README 14.1). Puede tener tipo ({@code fuse_a}, {@code fuse_b}...) para las cajas que
 * exigen uno concreto. Los fundidos son un item aparte que no enciende nada.
 */
public class FuseItem extends Item {

    public static final String TYPE = "FuseType";
    private final boolean blown;

    public FuseItem(Properties props, boolean blown) {
        super(props);
        this.blown = blown;
    }

    public boolean isBlown() {
        return blown;
    }

    public static String type(ItemStack stack) {
        String t = SPItems.getString(stack, TYPE);
        return t.isEmpty() ? "a" : t;
    }

    @Override
    public Component getName(ItemStack stack) {
        String t = SPItems.getString(stack, TYPE);
        if (t.isEmpty() || t.equals("a")) return super.getName(stack);
        return Component.translatable(getDescriptionId(stack) + ".typed", t.toUpperCase(java.util.Locale.ROOT));
    }
}
