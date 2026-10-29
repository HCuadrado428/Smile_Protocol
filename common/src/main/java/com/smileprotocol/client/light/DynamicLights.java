package com.smileprotocol.client.light;

import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import net.minecraft.core.BlockPos;

/**
 * Luces dinamicas de Veil (README 21.1). Provisional: Veil 4 cambio la API de luces
 * ({@code PointLightData}, {@code LightRenderHandle}); se conecta en cuanto se confirmen sus firmas.
 */
public final class DynamicLights {

    private DynamicLights() {}

    public static void tickFluorescent(FluorescentLightBlockEntity be) {}

    public static void remove(BlockPos pos) {}

    public static void clear() {}

    public static int count() {
        return 0;
    }
}
