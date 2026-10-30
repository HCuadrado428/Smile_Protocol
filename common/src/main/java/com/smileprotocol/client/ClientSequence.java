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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            startYaw = mc.player.getYRot();
            startPitch = mc.player.getXRot();
        }
    }

    public static void reset() {
        kind = -1;
        mobId = -1;
    }

    public static void tick(Minecraft mc) {
        if (kind < 0) return;
        age++;
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
     * Giro de la camara (yaw, pitch): mirando a la cabeza del mob, con temblor y sacudidas. Null si
     * no hay secuencia o no se ve el mob.
     */
    @Nullable
    public static float[] camera(Vec3 camPos, float pt) {
        if (!active()) return null;
        Entity mob = mob();
        if (mob == null) return null;
        Vec3 head = mob.getPosition(pt).add(0, mob.getBbHeight() * 0.92, 0);
        if (kind() == SequenceKind.SMILER_KILL) head = head.add(0, -0.1, 0);
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
        return new float[]{yaw, Mth.clamp(pitch, -89f, 89f)};
    }
}
