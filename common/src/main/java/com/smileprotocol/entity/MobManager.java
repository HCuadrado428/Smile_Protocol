package com.smileprotocol.entity;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.noise.NoiseEvent;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mobs de las zonas (README 20, 6.3): los reparte al colocar cada planta y les lleva los ruidos
 * del {@link NoiseSystem}. Los quita la limpieza de la zona, como al resto de entidades.
 */
public final class MobManager {

    private static boolean listening;

    private MobManager() {}

    public static void init() {
        if (listening) return;
        listening = true;
        NoiseSystem.listen(MobManager::onNoise);
    }

    private static void onNoise(ServerLevel level, NoiseEvent e) {
        double reach = Math.max(8, e.radius() * 2.0);
        for (SPMonster m : level.getEntitiesOfClass(SPMonster.class, new AABB(e.pos(), e.pos()).inflate(reach))) {
            m.hear(level, e);
        }
    }

    /** Berserkers de una planta: segun su dificultad, escalados por el grupo y lejos de la llegada. */
    public static int spawnForZone(ServerLevel level, List<BlockPos> points, Vec3 entry, int zoneDifficulty,
                                   DifficultyProfile difficulty, RandomSource rnd) {
        List<Integer> table = SPConfig.get().berserkers_by_difficulty;
        int base = table.get(Math.max(0, Math.min(table.size() - 1, zoneDifficulty)));
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 24) far.add(p);
        if (far.isEmpty()) return 0;
        int n = difficulty.scaleMobs(base, far.size());
        // repartidos: los mas alejados entre si primero
        List<BlockPos> chosen = new ArrayList<>();
        List<BlockPos> pool = new ArrayList<>(far);
        chosen.add(pool.remove(rnd.nextInt(pool.size())));
        while (chosen.size() < n && !pool.isEmpty()) {
            pool.sort(Comparator.comparingDouble(p -> -chosen.stream().mapToDouble(c -> c.distSqr(p)).min().orElse(0)));
            chosen.add(pool.remove(0));
        }
        int k = 0;
        for (BlockPos p : chosen) {
            if (spawn(level, ModEntities.BERSERKER.get(), p, rnd) != null) k++;
        }
        SPDebug.log("mobs: " + k + " berserkers (base " + base + ", dificultad " + zoneDifficulty + ")");
        return k;
    }

    /**
     * El Paciente (docs/SMILERS.md): uno en las plantas de {@code paciente_zones}, en un punto lejos
     * de la llegada desde el que pueda andar hasta ella (en los pasillos: no cabe por las puertas).
     */
    /** El Imitador: en las plantas de {@code imitador_zones}, lejos de la llegada. */
    public static void spawnImitador(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                     Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().imitador_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 30) far.add(p);
        if (far.isEmpty()) return;
        BlockPos p = far.get(rnd.nextInt(far.size()));
        if (spawn(level, ModEntities.IMITADOR.get(), p, rnd) != null) SPDebug.log("mobs: imitador en " + p.toShortString());
    }

    /** El Gemelo: en las plantas de {@code gemelo_zones}, lejos de la llegada. */
    public static void spawnGemelo(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                   Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().gemelo_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 30) far.add(p);
        if (far.isEmpty()) return;
        BlockPos p = far.get(rnd.nextInt(far.size()));
        if (spawn(level, ModEntities.GEMELO.get(), p, rnd) != null) SPDebug.log("mobs: gemelo en " + p.toShortString());
    }

    public static void spawnPaciente(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                     Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().paciente_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 30) far.add(p);
        java.util.Collections.shuffle(far, new java.util.Random(rnd.nextLong()));
        for (BlockPos p : far) {
            Paciente m = spawn(level, ModEntities.PACIENTE.get(), p, rnd);
            if (m == null) continue;
            var path = m.getNavigation().createPath(BlockPos.containing(entry), 2);
            if (level.noCollision(m) && path != null && path.getNodeCount() > 8) {
                SPDebug.log("mobs: paciente en " + p.toShortString());
                return;
            }
            m.discard();
        }
        SPDebug.log("mobs: sin sitio para el paciente en " + zoneId);
    }

    /** El Rastreador: uno en las plantas de {@code rastreador_zones}, lejos de la llegada. */
    public static void spawnRastreador(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                       Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().rastreador_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 36) far.add(p);
        if (far.isEmpty()) return;
        BlockPos p = far.get(rnd.nextInt(far.size()));
        if (spawn(level, ModEntities.RASTREADOR.get(), p, rnd) != null) SPDebug.log("mobs: rastreador en " + p.toShortString());
    }

    /** Ahorcados: colgados del techo de los pasillos en las plantas de {@code ahorcado_zones}. */
    public static void spawnAhorcados(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                      Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().ahorcado_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 20) far.add(p);
        java.util.Collections.shuffle(far, new java.util.Random(rnd.nextLong()));
        int k = 0;
        for (BlockPos p : far) {
            if (k >= SPConfig.get().ahorcado_count) break;
            // hace falta techo a 4 bloques del suelo (pasillos y salas del laboratorio)
            if (!level.getBlockState(p.above(3)).isAir() || level.getBlockState(p.above(4)).isAir()) continue;
            Ahorcado a = spawn(level, ModEntities.AHORCADO.get(), p, rnd);
            if (a == null) continue;
            a.hangAt(Vec3.atBottomCenterOf(p).add(0, 1.75, 0));
            k++;
        }
        SPDebug.log("mobs: " + k + " ahorcados en " + zoneId);
    }

    /** Apagon: uno en las plantas de {@code apagon_zones}, lejos de la llegada. */
    public static void spawnApagon(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, List<BlockPos> points,
                                   Vec3 entry, RandomSource rnd) {
        if (!SPConfig.get().apagon_zones.contains(zoneId.getPath())) return;
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos p : points) if (Vec3.atBottomCenterOf(p).distanceTo(entry) > 36) far.add(p);
        if (far.isEmpty()) return;
        BlockPos p = far.get(rnd.nextInt(far.size()));
        if (spawn(level, ModEntities.APAGON.get(), p, rnd) != null) SPDebug.log("mobs: apagon en " + p.toShortString());
    }

    private static final net.minecraft.resources.ResourceLocation RULES_SPEED =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("smileprotocol", "rules_speed");

    /** Reglas de la partida (Pesadilla, «monstruos rapidos»): mas velocidad. */
    private static void applyRules(ServerLevel level, SPMonster mob, BlockPos pos) {
        var ctx = com.smileprotocol.instance.InstanceContext.at(level, pos);
        var g = ctx == null ? null : ctx.group(level.getServer());
        if (g == null) return;
        double f = g.rules().speedFactor();
        var speed = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null || Math.abs(f - 1) < 1e-3) return;
        speed.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RULES_SPEED, f - 1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static <T extends SPMonster> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos, RandomSource rnd) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        applyRules(level, mob, pos);
        level.addFreshEntity(mob);
        return mob;
    }
}
