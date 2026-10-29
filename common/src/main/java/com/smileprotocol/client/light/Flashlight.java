package com.smileprotocol.client.light;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.client.ClientState;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Haz de las linternas con luces de area de Veil (README 21.1): el del jugador local sigue a la
 * camara en cada fotograma; los de los demas, a su cabeza. Un poco de luz de relleno alrededor
 * para ver lo que hay a un palmo.
 */
public final class Flashlight {

    private static final float R = 1.0f, G = 0.94f, B = 0.82f;
    private static final Set<Integer> ON = new HashSet<>();
    private static final Map<Integer, LightRenderHandle<AreaLightData>> BEAMS = new HashMap<>();
    private static final Map<Integer, LightRenderHandle<PointLightData>> FILL = new HashMap<>();
    private static boolean failed;

    private Flashlight() {}

    public static void set(int entityId, boolean on) {
        if (on) ON.add(entityId);
        else ON.remove(entityId);
    }

    public static boolean isOn(int entityId) {
        return ON.contains(entityId);
    }

    /** Cada fotograma, desde la camara. */
    public static void update(Camera camera, float pt) {
        if (failed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientState.inSmileProtocol()) {
            clear();
            return;
        }
        try {
            Set<Integer> alive = new HashSet<>();
            for (int id : ON) {
                Entity e = mc.level.getEntity(id);
                if (!(e instanceof Player p) || p.isInvisible()) continue;
                alive.add(id);
                LightRenderHandle<AreaLightData> beam = BEAMS.computeIfAbsent(id, k -> VeilRenderSystem.renderer().getLightRenderer()
                        .addLight(new AreaLightData().setSize(0.08, 0.08).setAngle(0.55f).setDistance(22f)
                                .setColor(R, G, B).setBrightness(2.2f)));
                LightRenderHandle<PointLightData> fill = FILL.computeIfAbsent(id, k -> VeilRenderSystem.renderer().getLightRenderer()
                        .addLight(new PointLightData().setRadius(3.2f).setColor(R, G, B).setBrightness(0.35f)));
                AreaLightData d = beam.getLightData();
                if (p == mc.player && camera.getEntity() == p) {
                    d.setTo(camera);
                } else {
                    float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
                    d.getOrientationMutable().set(new Quaternionf().rotationYXZ(Mth.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0f));
                    d.getPositionMutable().set(p.getX(), p.getEyeY() - 0.15, p.getZ());
                    d.markDirty();
                }
                fill.getLightData().setPosition(p.getX(), p.getEyeY(), p.getZ());
            }
            for (int id : Set.copyOf(BEAMS.keySet())) {
                if (!alive.contains(id)) {
                    BEAMS.remove(id).free();
                    LightRenderHandle<PointLightData> f = FILL.remove(id);
                    if (f != null) f.free();
                }
            }
        } catch (Throwable t) {
            failed = true;
            SmileProtocol.LOGGER.warn("Veil: no se pudo crear la luz de la linterna", t);
        }
    }

    public static void clear() {
        BEAMS.values().forEach(LightRenderHandle::free);
        FILL.values().forEach(LightRenderHandle::free);
        BEAMS.clear();
        FILL.clear();
    }

    public static void reset() {
        clear();
        ON.clear();
    }
}
