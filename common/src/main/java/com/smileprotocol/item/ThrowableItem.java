package com.smileprotocol.item;

import net.minecraft.world.item.Item;

/**
 * Lanzable para distraer (README 12.7): click derecho mantenido para apuntar y click izquierdo para
 * lanzar. El ruido al caer y si se rompe estan en {@code data/smileprotocol/throwables/*.json}.
 */
public class ThrowableItem extends Item {
    public ThrowableItem(Properties props) {
        super(props);
    }
}
