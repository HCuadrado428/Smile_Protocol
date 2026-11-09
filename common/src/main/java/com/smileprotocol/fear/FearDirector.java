package com.smileprotocol.fear;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.FurnitureBlock;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.entity.Ahorcado;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.labevent.LabActions;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.mechanism.MonsterLights;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.run.RunType;
import com.smileprotocol.sequence.SequenceManager;
import com.smileprotocol.zone.ZoneCatalog;
import com.smileprotocol.zone.ZoneDef;
import com.smileprotocol.zone.ZoneKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Director del miedo: el ritmo de cada planta, por jugador. Va por fases (calma, tension que sube,
 * un pico y un respiro) y decide cuando pasa algo que no es un monstruo: pasos por el techo o
 * dentro de la pared, una puerta que se cierra detras de ti, una silla que estaba mirando a otro
 * lado, luces que fallan a tu espalda y, en el pico, una silueta que cruza al fondo del pasillo o
 * un ruido que atrae a un monstruo de verdad. Cuando hay un monstruo cerca no se inventa nada: el
 * miedo ya esta ahi. Y cuando un monstruo te descubre, un golpe seco de sonido (solo para ti).
 *
 * Todo lo falso lo ve y lo oye solo el jugador al que le toca. No actua en el tutorial ni en la
 * sala final, ni con el jugador escondido, derribado, agarrado o en una secuencia.
 */
public final class FearDirector {

    public enum Phase { CALM, BUILD, PEAK, RELIEF }

    /** Lo que se puede provocar a mano (pruebas y /spadmin). */
    public enum Beat { CEILING, WALL, DOOR, MOVED, LIGHTS, SHADOW, LURE, STING }

    private record Pending(long at, Vec3 pos, SoundEvent sound, float volume, float pitch) {}

    private static final class Ghost {
        final Entity mob;
        final Vec3 from, to;
        int age;
        final int life;

        Ghost(Entity mob, Vec3 from, Vec3 to, int life) {
            this.mob = mob;
            this.from = from;
            this.to = to;
            this.life = life;
        }
    }

    private static final class State {
        Phase phase = Phase.CALM;
        int left = 20 * 30;
        int beatIn = 20 * 12;
        long lastSpot = -100000;
        boolean seen;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        final List<Pending> pending = new ArrayList<>();
        @Nullable Ghost ghost;
        @Nullable UUID group;
        int zoneIndex = -1;
        /** La llegada ya fijo la calma de la planta nueva. */
        boolean calmSet;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private FearDirector() {}

    // Tick -----------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            ZonePlacement zp = zoneOf(p);
            if (zp == null) {
                if (s != null) {
                    play(p, s, now);
                    if (s.pending.isEmpty() && !s.calmSet) forget(p.getUUID());
                }
                continue;
            }
            if (s == null) {
                s = new State();
                STATES.put(p.getUUID(), s);
            }
            Group g = InstanceManager.groupAt(p.serverLevel(), p.blockPosition());
            // planta nueva: se empieza en calma (la llegada ya tiene su momento)
            if (g != null && (!g.id().equals(s.group) || s.zoneIndex != zp.index())) {
                s.group = g.id();
                s.zoneIndex = zp.index();
                if (!s.calmSet) {
                    s.phase = Phase.CALM;
                    s.left = 20 * (30 + p.getRandom().nextInt(20));
                }
                s.calmSet = false;
                s.trail.clear();
            }
            play(p, s, now);
            moveGhost(s);
            if (!eligible(p)) continue;
            if (p.tickCount % 10 == 0) {
                s.trail.addLast(p.position());
                while (s.trail.size() > 24) s.trail.removeFirst();
                watch(p, s, now);
            }
            step(p, s, zp);
        }
    }

    /** El jugador esta en una planta normal de su partida (no tutorial, no sala final). */
    @Nullable
    private static ZonePlacement zoneOf(ServerPlayer p) {
        if (!SPPlayerData.inSP(p) || p.isSpectator() || DeathManager.isDead(p)) return null;
        ServerLevel level = p.serverLevel();
        Group g = InstanceManager.groupAt(level, p.blockPosition());
        if (g == null) return null;
        Run run = RunManager.runOf(level.getServer(), g);
        if (run == null || run.type() == RunType.TUTORIAL) return null;
        ZonePlacement zp = g.current();
        if (zp == null || zp.box() == null || !zp.box().isInside(p.blockPosition())) return null;
        ZoneDef def = ZoneCatalog.get(level.getServer(), zp.zoneId());
        if (def != null && (def.kind() == ZoneKind.FINAL || def.kind() == ZoneKind.TUTORIAL)) return null;
        return zp;
    }

    private static boolean eligible(ServerPlayer p) {
        return !DownedManager.isDowned(p) && !QTEManager.isGrabbed(p) && !SequenceManager.inSequence(p);
    }

    /** Monstruos alrededor: si uno te descubre, golpe de sonido y pico; si hay alguno cerca, nada falso. */
    private static void watch(ServerPlayer p, State s, long now) {
        boolean spotted = false;
        for (SPMonster m : p.serverLevel().getEntitiesOfClass(SPMonster.class, p.getBoundingBox().inflate(20))) {
            if (!real(m)) continue;
            double d = m.distanceTo(p);
            if (d > 20 || !m.hasLineOfSight(p)) continue;
            Vec3 to = p.position().subtract(m.position());
            float face = Mth.degreesDifferenceAbs(m.getYHeadRot(), (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f);
            boolean coming = m.getNavigation().isInProgress() && m.getNavigation().getTargetPos() != null
                    && m.getNavigation().getTargetPos().distSqr(p.blockPosition()) < 25;
            if (face < 35 && (coming || d < 10)) spotted = true;
        }
        if (spotted && !s.seen && now - s.lastSpot > 20 * 25 && !HideManager.isHidden(p)) {
            LabActions.scare(List.of(p), LabActions.SCARE_AUDIO, 1f);
            s.phase = Phase.PEAK;
            s.left = 20 * 8;
        }
        if (spotted) s.lastSpot = now;
        s.seen = spotted;
    }

    private static boolean real(SPMonster m) {
        return m.isAlive() && !m.isInvisible() && !m.isNoAi() && !(m instanceof Ahorcado a && a.isDecoy());
    }

    private static boolean monsterNear(ServerPlayer p, double r) {
        for (SPMonster m : p.serverLevel().getEntitiesOfClass(SPMonster.class, p.getBoundingBox().inflate(r))) {
            if (real(m) && m.distanceTo(p) < r) return true;
        }
        return false;
    }

    private static void step(ServerPlayer p, State s, ZonePlacement zp) {
        RandomSource r = p.getRandom();
        boolean near = monsterNear(p, 18);
        if (--s.left <= 0) {
            switch (s.phase) {
                case CALM -> {
                    s.phase = Phase.BUILD;
                    s.left = 20 * (40 + r.nextInt(25));
                    s.beatIn = 20 * (6 + r.nextInt(8));
                }
                case BUILD -> {
                    s.phase = Phase.PEAK;
                    s.left = 20 * 6;
                    if (!near) peak(p, s, zp);
                }
                case PEAK -> {
                    s.phase = Phase.RELIEF;
                    s.left = 20 * (25 + r.nextInt(20));
                }
                case RELIEF -> {
                    // si sigue habiendo algo cerca, el respiro se alarga
                    if (near) {
                        s.left = 20 * 10;
                    } else {
                        s.phase = Phase.CALM;
                        s.left = 20 * (35 + r.nextInt(30));
                    }
                }
            }
        }
        if (s.phase == Phase.BUILD && !near && --s.beatIn <= 0) {
            s.beatIn = 20 * (11 + r.nextInt(10));
            Beat[] order = r.nextFloat() < 0.4f ? new Beat[]{Beat.CEILING, Beat.DOOR, Beat.MOVED, Beat.LIGHTS}
                    : r.nextFloat() < 0.5f ? new Beat[]{Beat.DOOR, Beat.MOVED, Beat.WALL, Beat.CEILING}
                    : new Beat[]{Beat.MOVED, Beat.WALL, Beat.LIGHTS, Beat.CEILING};
            for (Beat b : order) {
                if (fire(p, s, zp, b)) break;
            }
        }
        // en calma, alguna vez, algo cambia de sitio sin que lo veas (lo encontraras al volver)
        if (s.phase == Phase.CALM && !near && p.tickCount % 200 == 0 && r.nextFloat() < 0.25f) fire(p, s, zp, Beat.MOVED);
    }

    /** El momento fuerte: una silueta al fondo; si no hay sitio, algo de verdad oye un ruido y viene. */
    private static void peak(ServerPlayer p, State s, ZonePlacement zp) {
        if (p.getRandom().nextFloat() < 0.6f && fire(p, s, zp, Beat.SHADOW)) return;
        if (fire(p, s, zp, Beat.LURE)) return;
        fire(p, s, zp, Beat.CEILING);
    }

    // Sustos -----------------------------------------------------------------------------------------

    /** Provoca uno a mano (pruebas): true si se pudo. */
    public static boolean debugBeat(ServerPlayer p, Beat b) {
        ZonePlacement zp = InstanceManager.groupAt(p.serverLevel(), p.blockPosition()) == null ? null
                : InstanceManager.groupAt(p.serverLevel(), p.blockPosition()).current();
        if (zp == null) return false;
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        return fire(p, s, zp, b);
    }

    private static boolean fire(ServerPlayer p, State s, ZonePlacement zp, Beat b) {
        boolean ok = switch (b) {
            case CEILING -> ceiling(p, s, false);
            case WALL -> ceiling(p, s, true);
            case DOOR -> door(p, s, zp);
            case MOVED -> moved(p, zp);
            case LIGHTS -> lights(p);
            case SHADOW -> shadow(p, s, zp);
            case LURE -> lure(p);
            case STING -> {
                LabActions.scare(List.of(p), LabActions.SCARE_AUDIO, 1f);
                yield true;
            }
        };
        if (ok) com.smileprotocol.debug.SPDebug.log("miedo: " + b + " para " + p.getGameProfile().getName() + " (" + s.phase + ")");
        return ok;
    }

    /** Pasos que cruzan por encima (o por dentro de la pared de al lado), siguiendote un poco. */
    private static boolean ceiling(ServerPlayer p, State s, boolean wall) {
        ServerLevel level = p.serverLevel();
        RandomSource r = p.getRandom();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 start, dir;
        if (wall) {
            // la pared solida mas cercana a un lado
            Vec3 found = null;
            for (int sign : new int[]{1, -1}) {
                for (int d = 1; d <= 3 && found == null; d++) {
                    BlockPos bp = BlockPos.containing(p.position().add(side.scale(sign * d)).add(0, 1, 0));
                    if (level.getBlockState(bp).isCollisionShapeFullBlock(level, bp)) found = p.position().add(side.scale(sign * (d + 0.8)));
                }
                if (found != null) break;
            }
            if (found == null) return false;
            start = found.add(look.scale(-5)).add(0, 1.2, 0);
            dir = look;
        } else {
            start = p.position().add(look.scale(-4)).add(side.scale(r.nextFloat() * 2 - 1)).add(0, 5.6, 0);
            dir = look;
        }
        long now = level.getServer().getTickCount();
        SoundEvent step = wall ? SoundEvents.DEEPSLATE_STEP : SoundEvents.WOOD_STEP;
        int n = 6 + r.nextInt(4);
        int gap = wall ? 9 : 7;
        for (int i = 0; i < n; i++) {
            Vec3 at = start.add(dir.scale(i * 1.1)).add(side.scale((i % 2 == 0 ? 0.25 : -0.25)));
            s.pending.add(new Pending(now + 4 + (long) i * gap + r.nextInt(3), at, step, wall ? 1.3f : 1.6f, 0.55f + r.nextFloat() * 0.1f));
        }
        // a veces se para justo encima... y algo rasca
        if (!wall && r.nextFloat() < 0.5f) {
            Vec3 at = start.add(dir.scale(n * 1.1));
            s.pending.add(new Pending(now + 4 + (long) n * gap + 25, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.35f, 0.5f));
        }
        return true;
    }

    /** Una puerta normal que acabas de cruzar se cierra de golpe a tu espalda. */
    private static boolean door(ServerPlayer p, State s, ZonePlacement zp) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos c = p.blockPosition();
        BlockPos best = null;
        double bestD = 99;
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-9, -1, -9), c.offset(9, 1, 9))) {
            BlockState st = level.getBlockState(bp);
            if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || !st.getValue(DoorBlock.OPEN)) continue;
            if (!zp.box().isInside(bp)) continue;
            if (level.getBlockEntity(bp) instanceof LabDoorBlockEntity be && be.type() != DoorType.NORMAL) continue;
            Vec3 to = Vec3.atBottomCenterOf(bp).subtract(p.position());
            double d = to.horizontalDistance();
            if (d < 2.5 || d > 9) continue;
            if (to.normalize().dot(look) > -0.2) continue;   // detras de ti
            boolean passed = false;
            for (Vec3 t : s.trail) {
                if (t.distanceToSqr(Vec3.atBottomCenterOf(bp)) < 4) passed = true;
            }
            if (!passed || d >= bestD) continue;
            best = bp.immutable();
            bestD = d;
        }
        if (best == null) return false;
        BlockState st = level.getBlockState(best);
        ((DoorBlock) st.getBlock()).setOpen(null, level, st, best, false);
        level.playSound(null, best, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.6f, 0.6f);
        return true;
    }

    /** Un mueble que no estas viendo pasa a mirarte (una silla, una camilla, un carrito). */
    private static boolean moved(ServerPlayer p, ZonePlacement zp) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos c = p.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-12, -1, -12), c.offset(12, 1, 12))) {
            BlockState st = level.getBlockState(bp);
            if (!(st.getBlock() instanceof FurnitureBlock) || !zp.box().isInside(bp)) continue;
            String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
            if (!(id.contains("chair") || id.contains("trolley") || id.contains("iv_stand") || id.contains("cart") || id.contains("gurney"))) continue;
            Vec3 to = Vec3.atCenterOf(bp).subtract(p.getEyePosition());
            if (to.horizontalDistance() < 4) continue;
            if (to.multiply(1, 0, 1).normalize().dot(look) > 0.3) continue;   // fuera de la vista
            found.add(bp.immutable());
        }
        if (found.isEmpty()) return false;
        BlockPos bp = found.get(p.getRandom().nextInt(found.size()));
        BlockState st = level.getBlockState(bp);
        Vec3 to = p.position().subtract(Vec3.atCenterOf(bp));
        Direction face = Direction.getNearest(to.x, 0, to.z);
        if (st.getValue(HorizontalDirectionalBlock.FACING) == face) return false;
        level.setBlock(bp, st.setValue(HorizontalDirectionalBlock.FACING, face), 3);
        return true;
    }

    /** Las luces de detras fallan un momento, como si algo pasara por debajo. */
    private static boolean lights(ServerPlayer p) {
        Vec3 back = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(-7));
        MonsterLights.near(p.serverLevel(), BlockPos.containing(back).above(3));
        Vec3 at = back.add(0, 1, 0);
        soundTo(p, SoundEvents.CHAIN_STEP, at, 0.7f, 0.5f);
        return true;
    }

    /** Una silueta cruza el fondo del pasillo, sin ruido, y no esta cuando llegas. */
    private static boolean shadow(ServerPlayer p, State s, ZonePlacement zp) {
        if (s.ghost != null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        // dentro del alcance de la linterna (a 22 bloques no se distinguia en la oscuridad)
        for (int d = 16; d >= 9; d -= 1) {
            Vec3 mid = p.position().add(look.scale(d));
            BlockPos bp = BlockPos.containing(mid);
            if (!open(level, bp) || !zp.box().isInside(bp)) continue;
            if (level.clip(new ClipContext(p.getEyePosition(), Vec3.atCenterOf(bp.above()), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            // de pared a pared (o tres bloques a cada lado como mucho)
            int a = 0, b = 0;
            while (a < 3 && open(level, BlockPos.containing(mid.add(side.scale(-(a + 1)))))) a++;
            while (b < 3 && open(level, BlockPos.containing(mid.add(side.scale(b + 1))))) b++;
            if (a + b < 2) continue;
            Vec3 from = Vec3.atBottomCenterOf(bp).add(side.scale(-a - 0.6)), to = Vec3.atBottomCenterOf(bp).add(side.scale(b + 0.6));
            if (p.getRandom().nextBoolean()) {
                Vec3 t = from;
                from = to;
                to = t;
            }
            var mob = com.smileprotocol.registry.ModEntities.SMILER_TEST.get().create(level);
            if (mob == null) return false;
            Vec3 dir = to.subtract(from);
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
            mob.moveTo(from.x, from.y, from.z, yaw, 0);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setNoAi(true);
            mob.setSilent(true);
            level.addFreshEntity(mob);
            s.ghost = new Ghost(mob, from, to, (int) Math.max(10, dir.length() / 0.32));
            return true;
        }
        return false;
    }

    private static boolean open(ServerLevel level, BlockPos bp) {
        return level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()
                && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()
                && !level.getBlockState(bp.below()).getCollisionShape(level, bp.below()).isEmpty();
    }

    private static void moveGhost(State s) {
        Ghost g = s.ghost;
        if (g == null) return;
        if (++g.age > g.life || !g.mob.isAlive()) {
            g.mob.discard();
            s.ghost = null;
            return;
        }
        Vec3 at = g.from.lerp(g.to, g.age / (double) g.life);
        g.mob.moveTo(at.x, at.y, at.z, g.mob.getYRot(), 0);
    }

    /** Un ruido sordo cerca de ti que solo oyen ellos: el monstruo mas cercano viene a mirar. */
    private static boolean lure(ServerPlayer p) {
        boolean any = false;
        for (SPMonster m : p.serverLevel().getEntitiesOfClass(SPMonster.class, p.getBoundingBox().inflate(40))) {
            if (real(m)) any = true;
        }
        if (!any) return false;
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(-6));
        NoiseSystem.emit(p.serverLevel(), at, 40f, NoiseType.EVENT, null);
        soundTo(p, SoundEvents.METAL_FALL, at, 1.4f, 0.6f);
        return true;
    }

    // Sonidos solo para el jugador -------------------------------------------------------------------

    private static void play(ServerPlayer p, State s, long now) {
        for (Iterator<Pending> it = s.pending.iterator(); it.hasNext(); ) {
            Pending pd = it.next();
            if (pd.at() > now) continue;
            soundTo(p, pd.sound(), pd.pos(), pd.volume(), pd.pitch());
            it.remove();
        }
    }

    /** Un sonido solo para este jugador dentro de {@code delay} ticks. */
    public static void queue(ServerPlayer p, int delay, Vec3 at, SoundEvent sound, float volume, float pitch) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        s.pending.add(new Pending(p.server.getTickCount() + delay, at, sound, volume, pitch));
    }

    /** Calma por delante (tras la llegada a una planta, que ya tiene su momento). */
    public static void calm(ServerPlayer p, int ticks) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        s.phase = Phase.CALM;
        s.left = ticks;
        s.calmSet = true;
    }

    public static void soundTo(ServerPlayer p, SoundEvent sound, Vec3 at, float volume, float pitch) {
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
                at.x, at.y, at.z, volume, pitch, p.getRandom().nextLong()));
    }

    // Estado ---------------------------------------------------------------------------------------

    @Nullable
    public static Phase phase(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? null : s.phase;
    }

    /** Pruebas: salta a una fase con {@code ticks} por delante. */
    public static void debugPhase(ServerPlayer p, Phase ph, int ticks) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        s.phase = ph;
        s.left = ticks;
        s.beatIn = 1;
    }

    public static void forget(UUID player) {
        State s = STATES.remove(player);
        if (s != null && s.ghost != null) s.ghost.mob.discard();
    }

    public static void clearAll() {
        for (State s : STATES.values()) {
            if (s.ghost != null) s.ghost.mob.discard();
        }
        STATES.clear();
        SmileProtocol.LOGGER.debug("miedo: estado borrado");
    }
}
