package com.smileprotocol.item;

import net.minecraft.world.item.Item;

/** Tarjeta de acceso de nivel 1 a 3 (README 14.2). */
public class KeycardItem extends Item {

    private final int level;

    public KeycardItem(Properties props, int level) {
        super(props);
        this.level = level;
    }

    public int level() {
        return level;
    }
}
