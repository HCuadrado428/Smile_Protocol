package com.smileprotocol.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Llave de una cerradura concreta ({@code lock_id}, README 14.2). */
public class KeyItem extends Item {

    public static final String LOCK = "Lock";

    public KeyItem(Properties props) {
        super(props);
    }

    public static String lock(ItemStack stack) {
        return SPItems.getString(stack, LOCK);
    }

    public static Component lockName(String lock) {
        return Component.translatableWithFallback("smileprotocol.lock." + lock, lock);
    }

    @Override
    public Component getName(ItemStack stack) {
        String lock = lock(stack);
        if (lock.isEmpty()) return super.getName(stack);
        return Component.translatable("item.smileprotocol.key.named", lockName(lock));
    }
}
