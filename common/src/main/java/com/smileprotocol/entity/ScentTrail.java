package com.smileprotocol.entity;

import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Rastro de cada jugador (docs/SMILERS.md, el Rastreador): por donde ha pasado en el ultimo minuto y
 * medio, un punto cada bloque y medio. Si esta herido sangra: algunos puntos dejan una mancha en el
 * suelo, y el Rastreador sigue ese rastro mas deprisa.
 */
public final class ScentTrail {

    /** Un punto del rastro. */
    public record Crumb(Vec3 pos, long tick, boolean blood) {
    }

    /** Lo que dura el rastro (ticks). */
    public static final int LIFE = 1800;
    private static final double SPACING = 1.5;
    private static final int MAX = 240;

    private static final Map<UUID, ArrayDeque<Crumb>> TRAILS = new HashMap<>();
    private static long lastRecorded = -1;

    private ScentTrail() {
    }

    /** Apunta por donde va cada jugador (una vez por tick del mundo, lo llame quien lo llame). */
    public static void record(ServerLevel level) {
        long now = level.getGameTime();
        if (now == lastRecorded || now % 5 != 0) return;
        lastRecorded = now;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || !SPPlayerData.inSP(p) || DeathManager.isDead(p) || !p.onGround()) continue;
            ArrayDeque<Crumb> t = TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
            Crumb last = t.peekLast();
            if (last != null && last.pos().distanceToSqr(p.position()) < SPACING * SPACING) continue;
            boolean bleeding = p.getHealth() <= p.getMaxHealth() * 0.6f;
            boolean blood = bleeding && t.size() % 3 == 0;
            t.addLast(new Crumb(p.position(), now, bleeding));
            if (blood) stain(level, p.blockPosition(), level.random.nextInt(6));
            while (t.size() > MAX || (!t.isEmpty() && now - t.peekFirst().tick() > LIFE)) t.pollFirst();
        }
    }

    private static void stain(ServerLevel level, BlockPos at, int variant) {
        BlockState here = level.getBlockState(at);
        if (!here.isAir() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) return;
        level.setBlock(at, ModBlocks.BLOOD_FLOOR.get().defaultBlockState()
                .setValue(com.smileprotocol.block.DecalBlock.VARIANT, variant), 3);
    }

    /**
     * El punto de rastro mas reciente (de cualquier jugador) a menos de {@code radius} bloques de
     * {@code from} y con menos de {@code maxAge} ticks.
     */
    @Nullable
    public static Crumb freshNear(ServerLevel level, Vec3 from, double radius, int maxAge) {
        long now = level.getGameTime();
        Crumb best = null;
        for (ArrayDeque<Crumb> t : TRAILS.values()) {
            for (Iterator<Crumb> it = t.descendingIterator(); it.hasNext(); ) {
                Crumb c = it.next();
                if (now - c.tick() > maxAge) break;
                if (c.pos().distanceToSqr(from) <= radius * radius && (best == null || c.tick() > best.tick())) best = c;
            }
        }
        return best;
    }

    /** El siguiente punto del mismo rastro despues de {@code c} (o null si es el ultimo). */
    @Nullable
    public static Crumb next(Crumb c) {
        for (ArrayDeque<Crumb> t : TRAILS.values()) {
            boolean found = false;
            for (Crumb x : t) {
                if (found) return x;
                if (x == c) found = true;
            }
            if (found) return null;
        }
        return null;
    }

    /** Pruebas y fin de partida. */
    public static void clear(UUID player) {
        TRAILS.remove(player);
    }

    public static int size(UUID player) {
        ArrayDeque<Crumb> t = TRAILS.get(player);
        return t == null ? 0 : t.size();
    }
}
