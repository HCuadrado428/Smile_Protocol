package com.smileprotocol.dimension;

import com.smileprotocol.SmileProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Dimension de instancias (README 4.1). Se define por datapack dentro del mod:
 * {@code data/smileprotocol/dimension_type/smile_protocol.json} y
 * {@code data/smileprotocol/dimension/smile_protocol.json} (generador de vacio).
 * Cada grupo juega en su propio hueco de esta dimension (README 6.3).
 */
public final class SPDimension {

    public static final ResourceKey<Level> SP_DIM =
            ResourceKey.create(Registries.DIMENSION, SmileProtocol.id("smile_protocol"));

    public static final ResourceKey<DimensionType> SP_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, SmileProtocol.id("smile_protocol"));

    private SPDimension() {}

    /** True si el nivel es la dimension de instancias. */
    public static boolean isSP(Level level) {
        return level != null && level.dimension() == SP_DIM;
    }
}
