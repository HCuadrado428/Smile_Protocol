package com.smileprotocol.sequence;

import com.smileprotocol.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sangre de los remates y las muertes (README 9.3): chorros y salpicaduras de particulas (trozos
 * que salen volando y caen, y niebla roja), y charcos y manchas que se quedan en el suelo y en las
 * paredes cercanas (desaparecen con la limpieza de la zona).
 */
public final class Gore {

    private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.42f, 0.0f, 0.01f), 1.8f);
    private static final DustParticleOptions MIST_DARK = new DustParticleOptions(new Vector3f(0.18f, 0.0f, 0.0f), 2.4f);
    private static final BlockParticleOption BLOOD = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
    private static final BlockParticleOption FRESH = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption DRIP = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState());

    private Gore() {}

    /** Salpicadura de un golpe: trozos que salen en abanico hacia {@code dir} y arriba, y niebla. */
    public static void burst(ServerLevel level, Vec3 at, Vec3 dir, float power) {
        RandomSource r = level.random;
        int n = (int) (26 * power);
        for (int i = 0; i < n; i++) {
            double vx = dir.x * 0.5 + r.nextGaussian() * 0.45, vz = dir.z * 0.5 + r.nextGaussian() * 0.45;
            double vy = 0.35 + r.nextDouble() * 0.6;
            // con count 0 el "spread" es la velocidad
            level.sendParticles(i % 3 == 0 ? FRESH : BLOOD, at.x, at.y, at.z, 0, vx, vy, vz, 0.35 * power);
        }
        level.sendParticles(MIST, at.x, at.y + 0.1, at.z, (int) (20 * power), 0.35, 0.25, 0.35, 0.02);
        level.sendParticles(MIST_DARK, at.x, at.y, at.z, (int) (10 * power), 0.25, 0.15, 0.25, 0.01);
    }

    /** Chorro continuo (un cuello, una cara): llamarlo cada tick mientras dure. */
    public static void fountain(ServerLevel level, Vec3 at, Vec3 dir, float power) {
        RandomSource r = level.random;
        for (int i = 0; i < 3; i++) {
            double vx = dir.x * 0.25 + r.nextGaussian() * 0.12, vz = dir.z * 0.25 + r.nextGaussian() * 0.12;
            level.sendParticles(BLOOD, at.x, at.y, at.z, 0, vx, 0.55 + r.nextDouble() * 0.3, vz, 0.4 * power);
        }
        level.sendParticles(MIST, at.x, at.y, at.z, 2, 0.06, 0.06, 0.06, 0.01);
        if (r.nextInt(3) == 0) level.sendParticles(DRIP, at.x, at.y, at.z, 2, 0.15, 0.05, 0.15, 0);
    }

    /**
     * Charco: manchas de sangre en el suelo alrededor (solo sobre suelo firme y en aire libre) y en
     * las paredes que haya a uno o dos bloques.
     */
    public static void pool(ServerLevel level, Vec3 center, int count, int radius) {
        RandomSource r = level.random;
        BlockPos base = BlockPos.containing(center.x, center.y + 0.2, center.z);
        for (int i = 0; i < count; i++) {
            BlockPos p = i == 0 ? base : base.offset(r.nextInt(radius * 2 + 1) - radius, 0, r.nextInt(radius * 2 + 1) - radius);
            BlockState here = level.getBlockState(p);
            if (!here.isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            level.setBlock(p, ModBlocks.BLOOD_FLOOR.get().defaultBlockState()
                    .setValue(com.smileprotocol.block.DecalBlock.VARIANT, r.nextInt(6)), 3);
        }
        // salpicaduras en las paredes de alrededor
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 2; k++) {
                BlockPos wall = base.relative(d, k);
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos w = wall.above(dy), in = w.relative(d.getOpposite());
                    if (!level.getBlockState(w).isFaceSturdy(level, w, d.getOpposite())) continue;
                    if (!level.getBlockState(in).isAir() || r.nextInt(3) == 0) continue;
                    level.setBlock(in, ModBlocks.WALL_DECAL.get().defaultBlockState()
                            .setValue(com.smileprotocol.block.WallDecalBlock.FACING, d.getOpposite())
                            .setValue(com.smileprotocol.block.WallDecalBlock.VARIANT, r.nextInt(8)), 3);
                    break;
                }
            }
        }
    }
}
