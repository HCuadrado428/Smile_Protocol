package com.smileprotocol.client;

import com.smileprotocol.network.Payloads;
import com.smileprotocol.sequence.SequenceKind;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Remate o muerte instantanea vista por la victima (README 9.3): la camara se clava en la cara
 * del mob, tiembla con cada golpe y la pantalla se tine de rojo y se funde a negro al final. Las
 * animaciones del jugador y del mob van aparte (estado sincronizado); esto es solo la camara y el HUD.
 */
public final class ClientSequence {

    private static int kind = -1;
    private static int mobId = -1;
    private static int ticks;
    private static int age;
    private static float startYaw, startPitch;

    /** Sangre en la pantalla: posicion (0-1), tamano (pixeles de GUI), variante, edad y caida. */
    public record Splat(float x, float y, float size, int variant, int born, float drip) {}

    private static final java.util.List<Splat> SPLATS = new java.util.ArrayList<>();
    private static final java.util.Random RNG = new java.util.Random();

    private ClientSequence() {}

    public static void start(Payloads.StartSequence p) {
        if (p.ticks() <= 0) {
            reset();
            return;
        }
        kind = p.kind();
        mobId = p.mobId();
        ticks = p.ticks();
        age = 0;
        SPLATS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            startYaw = mc.player.getYRot();
            startPitch = mc.player.getXRot();
        }
    }

    public static void reset() {
        kind = -1;
        mobId = -1;
        SPLATS.clear();
    }

    public static java.util.List<Splat> splats() {
        return SPLATS;
    }

    public static int ageTicks() {
        return age;
    }

    /** Cada golpe salpica la pantalla; un desgarro la llena. */
    private static void splash(int n, float sizeMin, float sizeMax) {
        for (int i = 0; i < n; i++) {
            float x = 0.12f + RNG.nextFloat() * 0.76f, y = 0.08f + RNG.nextFloat() * 0.6f;
            SPLATS.add(new Splat(x, y, sizeMin + RNG.nextFloat() * (sizeMax - sizeMin), RNG.nextInt(4), age,
                    0.0006f + RNG.nextFloat() * 0.0016f));
        }
    }

    public static void tick(Minecraft mc) {
        if (kind < 0) return;
        age++;
        SequenceKind k = kind();
        for (int t : k.impacts) if (age == t) splash(3 + RNG.nextInt(3), 50, 120);
        if (k.isTear(age)) splash(8, 80, 190);
        // por si se pierde el final (el servidor manda la muerte al acabar)
        if (age > ticks + 40 || mc.level == null) reset();
    }

    public static boolean active() {
        return kind >= 0;
    }

    public static SequenceKind kind() {
        return SequenceKind.of(kind);
    }

    public static float age(float pt) {
        return age + pt;
    }

    public static float progress(float pt) {
        return ticks <= 0 ? 0f : Mth.clamp(age(pt) / ticks, 0f, 1f);
    }

    /** Golpe reciente (0-1) para la sacudida y el destello rojo. */
    public static float impact(float pt) {
        float since = kind().sinceImpact(age(pt));
        return since < 0 || since > 8 ? 0f : (float) Math.exp(-since * 0.45f);
    }

    @Nullable
    private static Entity mob() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mobId < 0 ? null : mc.level.getEntity(mobId);
    }

    /**
     * Cara del mob: el hueso {@code head} de GeckoLib en el mundo (un poco por encima del pivote,
     * que esta en el cuello); si aun no se ha dibujado, una altura aproximada.
     */
    private static Vec3 headPosition(Entity mob, float pt) {
        Vec3 base = mob.getPosition(pt);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher().getRenderer(mob) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> r) {
            var bone = r.getGeoModel().getBone("head").orElse(null);
            if (bone != null) {
                bone.setTrackingMatrices(true);
                org.joml.Vector3d w = bone.getWorldPosition();
                Vec3 v = new Vec3(w.x, w.y + 0.25, w.z);
                if (v.distanceToSqr(base) < 16) return v;
            }
        }
        return base.add(0, 2.3, 0);
    }

    /**
     * Giro de la camara (yaw, pitch): mirando a la cabeza del mob, con temblor y sacudidas. Null si
     * no hay secuencia o no se ve el mob.
     */
    @Nullable
    public static float[] camera(Vec3 camPos, float pt) {
        if (!active()) return null;
        Entity mob = mob();
        if (mob == null) return null;
        Vec3 head = headPosition(mob, pt);
        double dx = head.x - camPos.x, dy = head.y - camPos.y, dz = head.z - camPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        // entra suave desde donde miraba
        float k = Mth.clamp(age(pt) / 7f, 0f, 1f);
        k = k * k * (3 - 2 * k);
        yaw = startYaw + Mth.wrapDegrees(yaw - startYaw) * k;
        pitch = Mth.lerp(k, startPitch, pitch);
        float t = age(pt);
        float hit = impact(pt);
        float tremble = 0.6f + 5.5f * hit;
        yaw += (Mth.sin(t * 2.3f) + 0.6f * Mth.sin(t * 4.1f + 1f)) * tremble;
        pitch += (Mth.cos(t * 2.9f) + 0.5f * Mth.sin(t * 3.7f)) * tremble * 0.8f;
        // al acabar, la cabeza (camara) cae
        float end = Mth.clamp((t - ticks * 0.82f) / (ticks * 0.18f), 0f, 1f);
        pitch = Mth.lerp(end * 0.7f, pitch, kind().prone ? 10f : 55f);
        // la cara del mob queda a un palmo: la camara se echa un poco atras para no meterse dentro
        float back = kind() == SequenceKind.SMILER_KILL ? 0.45f : 0f;
        return new float[]{yaw, Mth.clamp(pitch, -89f, 89f), back};
    }
}
