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
    /** Halo ancho y tenue alrededor del haz: el cono no acaba de golpe. */
    private static final Map<Integer, LightRenderHandle<AreaLightData>> SPILL = new HashMap<>();
    private static int dustFrame;
    private static boolean failed;
    private static int failures;

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
        // el Apagon cerca se come la luz: parpadea mas cuanto mas cerca esta
        var mc = net.minecraft.client.Minecraft.getInstance();
        var e = mc.level == null ? null : mc.level.getEntity(id);
        if (e != null) {
            double best = 99;
            for (var a : mc.level.getEntitiesOfClass(com.smileprotocol.entity.Apagon.class, e.getBoundingBox().inflate(8))) {
                best = Math.min(best, a.distanceTo(e));
            }
            if (best < 8) {
                float near = (float) (1 - best / 8);
                long t = net.minecraft.Util.getMillis() / 45 + id * 7L;
                if ((t % 11) < 1 + (int) (near * 4) || (t % 29) < (int) (near * 5)) k *= 0.08f;
                else k *= 1f - 0.35f * near;
            }
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
                LightRenderHandle<AreaLightData> spill = SPILL.computeIfAbsent(id, k -> VeilRenderSystem.renderer().getLightRenderer()
                        .addLight(new AreaLightData().setSize(0.08, 0.08).setAngle(1.05f).setDistance(11f)
                                .setColor(R, G * 0.98f, B * 0.95f).setBrightness(0.45f)));
                AreaLightData d = beam.getLightData();
                float k = intensity(id);
                d.setBrightness(2.2f * k);
                fill.getLightData().setBrightness(0.35f * k);
                spill.getLightData().setBrightness(0.45f * k);
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
                    aim(d, sp, sy);
                    var cp = camera.getPosition();
                    var right = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw + 90);
                    d.getPositionMutable().set(cp.x + right.x * 0.18, cp.y - 0.12, cp.z + right.z * 0.18);
                    d.markDirty();
                    // polvo flotando en el haz
                    if (k > 0.2f && ++dustFrame % 4 == 0) {
                        var look = net.minecraft.world.phys.Vec3.directionFromRotation(sp, sy);
                        double dist = 1.2 + mc.level.random.nextDouble() * 5;
                        var at = cp.add(look.scale(dist)).add((mc.level.random.nextDouble() - 0.5) * dist * 0.35,
                                (mc.level.random.nextDouble() - 0.5) * dist * 0.35, (mc.level.random.nextDouble() - 0.5) * dist * 0.35);
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 0, -0.002, 0);
                    }
                } else {
                    float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
                    aim(d, pitch, yaw);
                    d.getPositionMutable().set(p.getX(), p.getEyeY() - 0.15, p.getZ());
                    d.markDirty();
                }
                AreaLightData sd = spill.getLightData();
                sd.getOrientationMutable().set(d.getOrientationMutable());
                sd.getPositionMutable().set(d.getPositionMutable());
                sd.markDirty();
                fill.getLightData().setPosition(p.getX(), p.getEyeY(), p.getZ());
            }
            for (int id : Set.copyOf(BEAMS.keySet())) {
                if (!alive.contains(id)) {
                    BEAMS.remove(id).free();
                    LightRenderHandle<PointLightData> f = FILL.remove(id);
                    if (f != null) f.free();
                    LightRenderHandle<AreaLightData> sl = SPILL.remove(id);
                    if (sl != null) sl.free();
                }
            }
        } catch (Throwable t) {
            // un fallo suelto no apaga la linterna para siempre: se reintenta, y solo se rinde tras varios
            if (++failures > 20) failed = true;
            clear();
            SmileProtocol.LOGGER.warn("Veil: no se pudo crear la luz de la linterna (" + failures + ")", t);
        }
    }

    /**
     * Orienta el haz hacia donde mira (grados de Minecraft). Veil guarda la rotacion de mundo a local
     * y el haz sale por +Z local: igual que su propio {@code setTo(camera)}, se usa lookAlong con la
     * direccion invertida. La rotacion por angulos de antes solo acertaba mirando a algunos lados.
     */
    private static void aim(AreaLightData d, float pitch, float yaw) {
        var look = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
        var up = net.minecraft.world.phys.Vec3.directionFromRotation(pitch - 90f, yaw);
        d.getOrientationMutable().identity().lookAlong(
                new org.joml.Vector3f((float) -look.x, (float) -look.y, (float) -look.z),
                new org.joml.Vector3f((float) up.x, (float) up.y, (float) up.z));
    }

    public static void clear() {
        BEAMS.values().forEach(LightRenderHandle::free);
        FILL.values().forEach(LightRenderHandle::free);
        SPILL.values().forEach(LightRenderHandle::free);
        BEAMS.clear();
        FILL.clear();
        SPILL.clear();
    }

    public static void reset() {
        clear();
        ON.clear();
        BATTERY.clear();
    }
}
