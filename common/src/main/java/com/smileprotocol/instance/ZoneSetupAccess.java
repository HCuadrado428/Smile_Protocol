package com.smileprotocol.instance;

import net.minecraft.world.item.ItemStack;

/** Acceso publico a la tabla de objetos de los marcadores {@code sp:item} (para /spadmin give). */
public final class ZoneSetupAccess {

    private ZoneSetupAccess() {}

    public static ItemStack item(String spec) {
        return ZoneSetup.itemFor(spec);
    }
}
