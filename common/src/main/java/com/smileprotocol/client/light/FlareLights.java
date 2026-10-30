package com.smileprotocol.client.light;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.FlareEntity;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Luz roja de las bengalas encendidas (README 10.1, 21.1): una luz puntual de Veil que sigue a la
 * bengala, parpadea como una llama y se apaga al final.
 */
public final class FlareLights {

    private static final Map<Integer, LightRenderHandle<PointLightData>> LIGHTS = new HashMap<>();
    private static boolean failed;

    private FlareLights() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FlareEntity f)) continue;
            seen.add(f.getId());
            float k = f.intensity(0f);
            LightRenderHandle<PointLightData> h = LIGHTS.get(f.getId());
            if (h == null) {
                if (failed || k <= 0) continue;
                try {
                    PointLightData data = new PointLightData().setPosition(f.getX(), f.getY() + 0.3, f.getZ())
                            .setRadius(11f).setColor(1f, 0.22f, 0.12f).setBrightness(1.6f * k);
                    LIGHTS.put(f.getId(), VeilRenderSystem.renderer().getLightRenderer().addLight(data));
                } catch (Throwable t) {
                    failed = true;
                    SmileProtocol.LOGGER.warn("Veil: no se pudo crear la luz de una bengala", t);
                }
                continue;
            }
            h.getLightData().setPosition(f.getX(), f.getY() + 0.3, f.getZ());
            h.getLightData().setBrightness(1.6f * k);
            h.getLightData().setRadius(7f + 4f * k);
        }
        LIGHTS.entrySet().removeIf(en -> {
            if (seen.contains(en.getKey())) return false;
            en.getValue().free();
            return true;
        });
    }

    public static void clear() {
        for (LightRenderHandle<PointLightData> h : LIGHTS.values()) h.free();
        LIGHTS.clear();
    }
}
