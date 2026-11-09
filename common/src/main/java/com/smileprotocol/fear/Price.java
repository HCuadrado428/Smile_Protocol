package com.smileprotocol.fear;

import com.smileprotocol.mechanism.MonsterLights;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cada objetivo tiene un precio (docs/MIEDO.md): meter un fusible chisporrotea y se oye lejos, girar
 * una valvula chirria, y lo que de verdad cumple un objetivo (encender un circuito, abrir una
 * compuerta) dispara una alarma de unos segundos que atrae a todo lo que haya en la planta. El
 * momento de cumplirlo es el momento de peligro.
 */
public final class Price {

    private record Alarm(ResourceKey<Level> dim, BlockPos pos, long start, int ticks) {}

    private static final List<Alarm> ALARMS = new ArrayList<>();
    public static final int ALARM_TICKS = 20 * 9;

    private Price() {}

    /** Fusible metido: chispazo y un zumbido que se oye a 26 bloques. */
    public static void fuse(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        level.playSound(null, pos, ModSounds.EVENT_SURGE.get(), SoundSource.BLOCKS, 1.3f, 1.1f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.4);
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 26f, NoiseType.FUSE, player);
    }

    /** Valvula girada: chirrido de metal que se oye lejos. */
    public static void valve(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 0.5f);
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 26f, NoiseType.VALVE, player);
    }

    /** Objetivo cumplido: alarma de {@code ticks}, con las luces fallando y un ruido que llega a toda la planta. */
    public static void alarm(ServerLevel level, BlockPos pos, int ticks) {
        for (Alarm a : ALARMS) {
            if (a.dim().equals(level.dimension()) && a.pos().distSqr(pos) < 64) return;
        }
        ALARMS.add(new Alarm(level.dimension(), pos.immutable(), level.getServer().getTickCount(), ticks));
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 64f, NoiseType.EVENT, null);
        MonsterLights.near(level, pos.above(2));
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Alarm> it = ALARMS.iterator(); it.hasNext(); ) {
            Alarm a = it.next();
            ServerLevel level = server.getLevel(a.dim());
            int age = (int) (now - a.start());
            if (level == null || age > a.ticks()) {
                it.remove();
                continue;
            }
            if (age % 48 == 0) level.playSound(null, a.pos(), ModSounds.EVENT_ALARM.get(), SoundSource.BLOCKS, 3f, 1f);
            if (age == a.ticks() / 2) NoiseSystem.emit(level, Vec3.atCenterOf(a.pos()), 64f, NoiseType.EVENT, null);
        }
    }

    /** Pruebas. */
    public static int alarms() {
        return ALARMS.size();
    }

    public static void clearAll() {
        ALARMS.clear();
    }
}
