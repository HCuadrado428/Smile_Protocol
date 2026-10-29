package com.smileprotocol.client.light;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.deferred.VeilDeferredRenderer;
import foundry.veil.api.client.render.deferred.light.PointLight;
import foundry.veil.api.client.render.deferred.light.renderer.LightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Luces dinamicas de Veil (README 21.1). Cada fluorescente encendido tiene una luz puntual
 * justo debajo; al parpadear, la luz se apaga y enciende con el bloque. Hay un maximo de
 * luces simultaneas para cuidar el rendimiento.
 * <p>
 * Las luces solo se ven con el renderizado diferido de Veil activo: se intenta activar al
 * crear la primera luz. Si la tarjeta o un mod de renderizado no lo admite, el juego sigue
 * con la luz de bloque normal.
 */
public final class DynamicLights {

    private static final int MAX_LIGHTS = 64;
    private static final float RADIUS = 9f;
    private static final float BRIGHTNESS = 1.15f;
    /** Blanco ligeramente verdoso, como un tubo fluorescente viejo. */
    private static final float R = 0.86f, G = 1.0f, B = 0.92f;

    private static final Map<BlockPos, PointLight> LIGHTS = new HashMap<>();
    private static boolean triedEnable;
    private static boolean failed;

    private DynamicLights() {}

    @Nullable
    private static LightRenderer renderer() {
        if (failed) return null;
        try {
            VeilDeferredRenderer deferred = VeilRenderSystem.renderer().getDeferredRenderer();
            if (!triedEnable) {
                triedEnable = true;
                if (VeilDeferredRenderer.isSupported() && !deferred.isEnabled()) deferred.enable();
                SmileProtocol.LOGGER.info("Veil: renderizado diferido {}", deferred.isEnabled() ? "activo" : "no disponible");
            }
            return deferred.getLightRenderer();
        } catch (Throwable t) {
            failed = true;
            SmileProtocol.LOGGER.warn("Veil: no se pudieron crear luces dinamicas", t);
            return null;
        }
    }

    public static void tickFluorescent(FluorescentLightBlockEntity be) {
        LightRenderer lr = renderer();
        if (lr == null) return;
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        boolean lit = state.getBlock() instanceof FluorescentLightBlock && state.getValue(FluorescentLightBlock.LIT);

        PointLight light = LIGHTS.get(pos);
        if (light == null) {
            if (LIGHTS.size() >= MAX_LIGHTS) return;
            light = new PointLight();
            light.setPosition(pos.getX() + 0.5, pos.getY() - 0.35, pos.getZ() + 0.5);
            light.setRadius(RADIUS);
            light.setColor(R, G, B);
            light.setBrightness(lit ? BRIGHTNESS : 0f);
            lr.addLight(light);
            LIGHTS.put(pos.immutable(), light);
            return;
        }
        float target = lit ? BRIGHTNESS : 0f;
        if (light.getBrightness() != target) light.setBrightness(target);
    }

    public static void remove(BlockPos pos) {
        PointLight light = LIGHTS.remove(pos);
        if (light == null) return;
        LightRenderer lr = renderer();
        if (lr != null) lr.removeLight(light);
    }

    /** Al salir del servidor o cambiar de mundo. */
    public static void clear() {
        LightRenderer lr = renderer();
        if (lr != null) for (PointLight l : LIGHTS.values()) lr.removeLight(l);
        LIGHTS.clear();
    }

    public static int count() {
        return LIGHTS.size();
    }
}
