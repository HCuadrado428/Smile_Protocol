package com.smileprotocol.noise;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fuente central de ruido (README 17.1). Cada accion emite un {@link NoiseEvent} en la
 * instancia donde ocurre; los mobs (fase 11) se suscriben con {@link #listen} o consultan los
 * ultimos ruidos con {@link #recent}. Los operadores con la depuracion activada ven un anillo
 * de particulas con el radio de cada ruido.
 */
public final class NoiseSystem {

    /** Cuanto tiempo se recuerdan los ruidos en una instancia (ticks). */
    private static final int MEMORY = 100;
    private static final String KEY = "noise";
    private static final List<Consumer<NoiseEvent>> LISTENERS = new ArrayList<>();

    private NoiseSystem() {}

    /** Registro de un oyente global (los mobs filtran por su instancia y su oido). */
    public static void listen(Consumer<NoiseEvent> listener) {
        LISTENERS.add(listener);
    }

    public static void emit(ServerLevel level, Vec3 pos, float radius, NoiseType type, @Nullable Entity source) {
        if (radius <= 0 || !SPDimension.isSP(level)) return;
        NoiseEvent e = new NoiseEvent(pos, radius, type, source == null ? null : source.getUUID(), level.getGameTime());
        InstanceContext ctx = InstanceContext.at(level, BlockPos.containing(pos));
        if (ctx != null) {
            Deque<NoiseEvent> q = ctx.state(KEY, ArrayDeque::new);
            q.addLast(e);
            while (!q.isEmpty() && q.peekFirst().gameTime() < e.gameTime() - MEMORY) q.removeFirst();
            while (q.size() > 64) q.removeFirst();
        }
        for (Consumer<NoiseEvent> l : LISTENERS) l.accept(e);
        showDebug(level, e);
    }

    /** Ruidos recientes en la instancia de esa posicion. */
    public static List<NoiseEvent> recent(ServerLevel level, BlockPos pos, int maxAgeTicks) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        if (ctx == null) return List.of();
        Deque<NoiseEvent> q = ctx.state(KEY, ArrayDeque::new);
        long now = level.getGameTime();
        List<NoiseEvent> out = new ArrayList<>();
        for (NoiseEvent e : q) if (now - e.gameTime() <= maxAgeTicks) out.add(e);
        return out;
    }

    /** Anillo de particulas (solo para operadores con la depuracion activa, README 17.1). */
    private static void showDebug(ServerLevel level, NoiseEvent e) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (SPDebug.isEnabled(p) && p.position().distanceToSqr(e.pos()) < 64 * 64) viewers.add(p);
        }
        if (viewers.isEmpty()) return;
        float hue = Math.min(1f, e.radius() / 18f);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 1f - hue, 0.15f), 1.2f);
        int points = Math.max(12, (int) (e.radius() * 6));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = e.pos().x + Math.cos(a) * e.radius();
            double z = e.pos().z + Math.sin(a) * e.radius();
            for (ServerPlayer p : viewers) {
                level.sendParticles(p, dust, true, x, e.pos().y + 0.2, z, 1, 0, 0, 0, 0);
            }
        }
    }
}
