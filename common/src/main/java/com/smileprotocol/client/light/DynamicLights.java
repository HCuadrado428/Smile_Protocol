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
    /** Estado de cada tubo: brillo actual, si estaba encendido y cuanto le queda del arranque. */
    private static final Map<BlockPos, float[]> STATE = new HashMap<>();
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
                        .setColor(tint(pos)[0], tint(pos)[1], tint(pos)[2])
                        .setBrightness(shape(pos, lit, target, be.getLevel() == null ? 0 : be.getLevel().getGameTime()));
                LIGHTS.put(pos.immutable(), lr.addLight(data));
            } catch (Throwable t) {
                failed = true;
                SmileProtocol.LOGGER.warn("Veil: no se pudo anadir la luz de un fluorescente", t);
            }
            return;
        }
        handle.getLightData().setBrightness(shape(pos, lit, target, be.getLevel() == null ? 0 : be.getLevel().getGameTime()));
    }

    /**
     * Brillo de un tubo este tick, como un fluorescente de verdad: al encenderse arranca a golpes
     * (varios destellos antes de quedarse fijo), al apagarse se va en un instante dejando un resplandor,
     * y encendido tiene un rizado leve que cambia de un tubo a otro. Cada tubo tiene su tono (mas frio o
     * mas verde) y algunos son un poco mas flojos.
     */
    static float shape(BlockPos pos, boolean lit, float target, long time) {
        float[] s = STATE.computeIfAbsent(pos.immutable(), k -> new float[]{lit ? target : 0f, lit ? 1 : 0, 0});
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        float own = 0.85f + 0.15f * ((h >>> 40) & 0xFF) / 255f;          // cada tubo, su intensidad
        if (lit && s[1] == 0) s[2] = 10 + ((h >>> 20) & 7);                 // acaba de encenderse: arranque
        s[1] = lit ? 1 : 0;
        float want = target * own;
        if (lit && s[2] > 0) {
            s[2]--;
            boolean on = ((long) (s[2] * 7 + (h & 15)) % 5) < 2;
            want *= on ? 0.9f : 0.08f;
        } else if (lit) {
            float ripple = (float) Math.sin((time + (h & 63)) * 0.9) * 0.025f + (float) Math.sin(time * 0.13 + (h & 31)) * 0.02f;
            want *= 1f + ripple;
        }
        // apagar casi de golpe (queda un resplandor de un tick), encender ya viene del arranque
        s[0] = lit ? want : s[0] * 0.35f;
        return s[0];
    }

    /** Pequena variacion del color por tubo: unos mas verdes, otros mas azulados. */
    static float[] tint(BlockPos pos) {
        long h = pos.asLong() * 0xC2B2AE3D27D4EB4FL;
        float v = ((h >>> 33) & 0xFF) / 255f - 0.5f;
        return new float[]{R - 0.06f * Math.max(0, v), G, B + 0.08f * Math.max(0, -v) - 0.04f * Math.max(0, v)};
    }

    public static void remove(BlockPos pos) {
        STATE.remove(pos);
        LightRenderHandle<PointLightData> handle = LIGHTS.remove(pos);
        if (handle != null) handle.free();
    }

    /** Al salir del servidor o cambiar de mundo. */
    public static void clear() {
        for (LightRenderHandle<PointLightData> h : LIGHTS.values()) h.free();
        LIGHTS.clear();
        STATE.clear();
    }

    public static int count() {
        return LIGHTS.size();
    }
}
