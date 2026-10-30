package com.smileprotocol.item;

import net.minecraft.world.item.Item;

/**
 * Consumible de la partida (README 10.1): se usa con el click derecho mantenido. Lo que hace, lo
 * que tarda y el ruido estan en {@code data/smileprotocol/consumables/*.json} ({@link ItemDefs}).
 */
public class ConsumableItem extends Item {
    public ConsumableItem(Properties props) {
        super(props);
    }
}
