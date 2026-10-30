package com.smileprotocol.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Familia de los smilers (README 20.1): todos parten de Smiley. Silueta negra, pelo largo, manos
 * grandes de dedos afilados y la sonrisa roja, que es lo unico que brilla (capa emisiva de
 * GeckoLib, {@code *_glowmask.png}). Cada smiler varia el ojo y las proporciones; los modelos
 * comparten nombres de huesos para reutilizar animaciones.
 */
public abstract class SmilerBase extends SPMonster {

    protected SmilerBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }
}
