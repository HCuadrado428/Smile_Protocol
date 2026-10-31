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
    private static final Map<Integer, Float> BATTERY = new HashMap<>();
    /** Momento (ms) del ultimo cambio de la linterna local, para mostrar la bateria un rato. */
    private static long lastToggle;
    private static final Map<Integer, LightRenderHandle<AreaLightData>> BEAMS = new HashMap<>();
    private static final Map<Integer, LightRenderHandle<PointLightData>> FILL = new HashMap<>();
    private static boolean failed;

    /** Direccion del haz local con retraso (la mano sigue a la mirada con algo de inercia). */
    private static float lagYaw = Float.NaN, lagPitch;

    private Flashlight() {}

    public static void set(int entityId, boolean on) {
        if (on) ON.add(entityId);
        else ON.remove(entityId);
    }

    public static boolean isOn(int entityId) {
        return ON.contains(entityId);
    }

    public static void sync(int entityId, boolean on, float battery) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId && on != ON.contains(entityId)) {
            lastToggle = net.minecraft.Util.getMillis();
        }
        set(entityId, on);
        BATTERY.put(entityId, battery);
    }

    public static float battery(int entityId) {
        return BATTERY.getOrDefault(entityId, 1f);
    }

    /** Milisegundos desde el ultimo cambio de la linterna local. */
    public static long sinceToggle() {
        return net.minecraft.Util.getMillis() - lastToggle;
    }

    /** Intensidad segun la bateria: baja al final y parpadea por debajo del 10 %. */
    private static float intensity(int id) {
        float b = battery(id);
        float k = b < 0.25f ? 0.45f + 0.55f * (b / 0.25f) : 1f;
        if (b < 0.10f) {
            long t = net.minecraft.Util.getMillis() / 60 + id * 13L;
            if ((t % 37) < 3 || (t % 53) < 2) k *= 0.1f;
        }
        return k;
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
                float k = intensity(id);
                d.setBrightness(2.2f * k);
                fill.getLightData().setBrightness(0.35f * k);
                if (p == mc.player && camera.getEntity() == p) {
                    // en la mano: el haz va un poco por detras de la mirada y se balancea al andar
                    float yaw = camera.getYRot(), pitch = camera.getXRot();
                    if (Float.isNaN(lagYaw)) {
                        lagYaw = yaw;
                        lagPitch = pitch;
                    }
                    lagYaw += Mth.wrapDegrees(yaw - lagYaw) * 0.35f;
                    lagPitch += (pitch - lagPitch) * 0.35f;
                    float walk = p.walkDist * 6.2f;
                    float amp = Math.min(1f, (float) p.getDeltaMovement().horizontalDistance() * 9f);
                    float sy = lagYaw + Mth.sin(walk) * 0.9f * amp, sp = lagPitch + Math.abs(Mth.cos(walk)) * 0.8f * amp;
                    d.getOrientationMutable().set(new Quaternionf().rotationYXZ(Mth.PI - sy * Mth.DEG_TO_RAD, -sp * Mth.DEG_TO_RAD, 0f));
                    var cp = camera.getPosition();
                    var right = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw + 90);
                    d.getPositionMutable().set(cp.x + right.x * 0.18, cp.y - 0.12, cp.z + right.z * 0.18);
                    d.markDirty();
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
        BATTERY.clear();
    }
}
