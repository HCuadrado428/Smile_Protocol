package com.smileprotocol.client.light;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.config.SPConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Luces dinamicas de Veil 4 (README 21.1). Cada fluorescente encendido tiene una luz puntual
 * justo debajo; al parpadear se apaga y enciende con el bloque. Hay un maximo de luces
 * simultaneas ({@code max_dynamic_lights}) para cuidar el rendimiento.
 */
public final class DynamicLights {

    private static final float RADIUS = 9f;
    private static final float BRIGHTNESS = 1.15f;
    /** Blanco ligeramente verdoso, como un tubo fluorescente viejo. */
    private static final float R = 0.86f, G = 1.0f, B = 0.92f;

    private static final Map<BlockPos, LightRenderHandle<PointLightData>> LIGHTS = new HashMap<>();
    private static boolean failed;

    /** Dentro de las instancias las luces son mas cortas y tenues: es un laboratorio a oscuras. */
    private static final float RADIUS_SP = 6f;
    private static final float BRIGHTNESS_SP = 0.7f;

    private DynamicLights() {}

    private static boolean inInstance(FluorescentLightBlockEntity be) {
        return be.getLevel() != null && com.smileprotocol.dimension.SPDimension.isSP(be.getLevel());
    }

    @Nullable
    private static LightRenderer renderer() {
        if (failed) return null;
        try {
            return VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            failed = true;
            SmileProtocol.LOGGER.warn("Veil: no se pudieron crear luces dinamicas", t);
            return null;
        }
    }

    public static void tickFluorescent(FluorescentLightBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        boolean lit = state.getBlock() instanceof FluorescentLightBlock && state.getValue(FluorescentLightBlock.LIT);
        float target = lit ? (inInstance(be) ? BRIGHTNESS_SP : BRIGHTNESS) : 0f;

        LightRenderHandle<PointLightData> handle = LIGHTS.get(pos);
        if (handle == null) {
            if (LIGHTS.size() >= SPConfig.get().max_dynamic_lights) return;
            LightRenderer lr = renderer();
            if (lr == null) return;
            try {
                PointLightData data = new PointLightData()
                        .setPosition(pos.getX() + 0.5, pos.getY() - 0.35, pos.getZ() + 0.5)
                        .setRadius(inInstance(be) ? RADIUS_SP : RADIUS)
                        .setColor(R, G, B)
                        .setBrightness(target);
                LIGHTS.put(pos.immutable(), lr.addLight(data));
            } catch (Throwable t) {
                failed = true;
                SmileProtocol.LOGGER.warn("Veil: no se pudo anadir la luz de un fluorescente", t);
            }
            return;
        }
        handle.getLightData().setBrightness(target);
    }

    public static void remove(BlockPos pos) {
        LightRenderHandle<PointLightData> handle = LIGHTS.remove(pos);
        if (handle != null) handle.free();
    }

    /** Al salir del servidor o cambiar de mundo. */
    public static void clear() {
        for (LightRenderHandle<PointLightData> h : LIGHTS.values()) h.free();
        LIGHTS.clear();
    }

    public static int count() {
        return LIGHTS.size();
    }
}
