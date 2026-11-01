package com.smileprotocol.entity;

import com.smileprotocol.blockentity.HideBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.noise.NoiseEvent;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.qte.QTEType;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.motion.MotionManager;
import net.minecraft.sounds.SoundEvents;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * El Rastreador (docs/SMILERS.md): cazador metodico sin piel, con el ojo en la palma. Va a cuatro
 * patas y solo se levanta para buscar con la mano en alto.
 * PATROL → oye un ruido: INVESTIGATE → te ve (cono de 20 bloques): CHASE, mas rapido que tu, pero
 * a los 8 s se cansa (TIRED) → te pierde: SEARCH donde te vio, alzando la palma; si encuentra tu
 * rastro fresco lo sigue olfateando ({@link State#TRACK}, mas deprisa si sangras, {@link ScentTrail});
 * si no, revisa los escondites cercanos y saca a quien este dentro. Antes de cruzar una puerta
 * cerrada mete la mano por debajo para mirar ({@link State#PEEK}). Deja arañazos en las paredes.
 * Al alcanzarte, agarre con QTE MASH; si fallas quedas derribado y vuelve a rematarte.
 */
public class Rastreador extends SmilerBase implements QTEManager.Grabber {

    public enum State { PATROL, INVESTIGATE, CHASE, TIRED, SEARCH, CHECK, GRAB, STUNNED, VENT, TRACK, PEEK }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Rastreador.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SEARCH_A = RawAnimation.begin().thenLoop("search");
    private static final RawAnimation TIRED_A = RawAnimation.begin().thenLoop("tired");
    private static final RawAnimation GRAB_A = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation STUN_A = RawAnimation.begin().thenLoop("stunned");
    private static final RawAnimation SNIFF_A = RawAnimation.begin().thenLoop("sniff");
    private static final RawAnimation PEEK_A = RawAnimation.begin().thenLoop("peek_door");

    private int timer;
    private int chaseTicks;
    private int lostTicks;
    private int cooldown;
    @Nullable private ServerPlayer target;
    @Nullable private Vec3 goal;
    @Nullable private Vec3 lastSeen;
    @Nullable private BlockPos seenHiding;
    /** Persiguiendo por los conductos: rejilla por la que entra (o ha entrado) y camino por dentro. */
    @Nullable private BlockPos ventGrille;
    private boolean inDuct;
    private final java.util.ArrayDeque<BlockPos> ductPath = new java.util.ArrayDeque<>();
    private final List<BlockPos> toCheck = new ArrayList<>();
    @Nullable private BlockPos checking;
    private boolean forced;
    /** Siguiendo un rastro: el punto al que va. */
    @Nullable private ScentTrail.Crumb crumb;
    /** Puerta por debajo de la que esta mirando, y el estado al que vuelve. */
    @Nullable private BlockPos peekDoor;
    private State afterPeek = State.PATROL;
    private int scratchCd = 60;
    /** El ultimo punto de rastro que ya siguio hasta el final (no vuelve a seguir ese rastro). */
    private long trailDone = -1;

    public Rastreador(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String defId() {
        return "rastreador";
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        var nav = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State s) {
        if (s != state()) SPDebug.log("rastreador " + state() + " -> " + s);
        // si sale del estado de conducto por otro camino (aturdido, remate...), sale del conducto
        if (s != State.VENT && inDuct && level() instanceof ServerLevel sl) leaveDuct(sl, nearestGrille(sl, blockPosition(), 12));
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Grabacion y pruebas: estado fijo, sin IA. */
    public void debugForce(State s) {
        forced = true;
        getNavigation().stop();
        entityData.set(STATE, (byte) s.ordinal());
    }

    /** Pruebas: persigue ya a ese jugador. */
    public void debugChase(ServerPlayer p) {
        forced = false;
        target = p;
        chaseTicks = 0;
        lostTicks = 0;
        setState(State.CHASE);
    }

    /** Pruebas: vuelve a su IA. */
    public void debugRelease() {
        forced = false;
        setState(State.PATROL);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || forced) return;
        if (sequence() != null) {
            getNavigation().stop();
            return;
        }
        MobDef def = def();
        timer++;
        if (cooldown > 0) cooldown--;
        // La vista manda: si ve a alguien (y no esta agarrando ni cansado), va a por el
        State s = state();
        // mira cada 4 ticks, y siempre justo al cambiar de estado (antes de echar a andar hacia otro lado)
        if (s != State.GRAB && s != State.STUNNED && s != State.TIRED && s != State.CHASE && s != State.VENT && cooldown == 0
                && (tickCount % 4 == 0 || timer <= 1)) {
            ServerPlayer seen = see(level, def);
            if (seen != null) {
                target = seen;
                chaseTicks = 0;
                lostTicks = 0;
                level.playSound(null, blockPosition(), ModSounds.RASTREADOR_SCREECH.get(), SoundSource.HOSTILE, 1.4f, 1f);
                setState(State.CHASE);
                s = State.CHASE;
            }
        }
        claws(level, s);
        if (s != State.CHASE && s != State.VENT && s != State.PEEK && s != State.GRAB && s != State.STUNNED && doorAhead(level, s)) return;
        switch (s) {
            case PEEK -> peek(level);
            case TRACK -> track(level, def);
            case PATROL -> {
                // un rastro fresco a sus pies: lo sigue
                if (timer % 20 == 0 && startTrack(level, 3, 900)) return;
                if (goal == null || getNavigation().isDone() || timer > 200) {
                    Vec3 p = DefaultRandomPos.getPos(this, 10, 4);
                    goal = p;
                    timer = 0;
                    if (p != null) getNavigation().moveTo(p.x, p.y, p.z, def.walkSpeed());
                }
            }
            case INVESTIGATE -> {
                if (goal == null) {
                    setState(State.PATROL);
                    return;
                }
                if (timer % 10 == 1) getNavigation().moveTo(goal.x, goal.y, goal.z, def.walkSpeed() * 1.5);
                if (position().distanceTo(goal) < 2 || timer > 240) {
                    lastSeen = goal;
                    startSearch(level);
                }
            }
            case CHASE -> chase(level, def);
            case VENT -> vent(level, def);
            case TIRED -> {
                getNavigation().stop();
                if (timer >= 50) {
                    if (target != null && valid(target) && canSee(target)) {
                        chaseTicks = 0;
                        setState(State.CHASE);
                    } else {
                        startSearch(level);
                    }
                }
            }
            case SEARCH -> {
                // con la palma en alto, barriendo la sala; despues revisa los escondites
                if (timer < 15 && lastSeen != null) getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, def.walkSpeed());
                if (timer > 15) getNavigation().stop();
                if (timer == 40 && startTrack(level, 7, 1200)) return;
                if (timer >= 70) nextCheck(level, def);
            }
            case CHECK -> check(level, def);
            case GRAB -> {
                getNavigation().stop();
                if (timer > 400) setState(State.PATROL);
            }
            case STUNNED -> {
                getNavigation().stop();
                if (timer >= 60) {
                    cooldown = 50;
                    setState(State.PATROL);
                }
            }
        }
    }

    private void chase(ServerLevel level, MobDef def) {
        if (target == null || !valid(target) && !(target.isAlive() && HideManager.isHidden(target))) {
            startSearch(level);
            return;
        }
        // se ha escondido delante de el: ese escondite primero
        if (HideManager.isHidden(target)) {
            seenHiding = HideManager.spotOf(target);
            if (seenHiding != null) HideManager.compromise(seenHiding);
            lastSeen = target.position();
            target = null;
            startSearch(level);
            return;
        }
        // se ha metido en un conducto: a la rejilla mas cercana y detras de el (README: el Rastreador si entra)
        if (MotionManager.inDuct(target)) {
            BlockPos g = nearestGrille(level, target.blockPosition(), 8);
            if (g != null) {
                ventGrille = g;
                inDuct = false;
                ductPath.clear();
                setState(State.VENT);
                return;
            }
        }
        chaseTicks++;
        boolean visible = canSee(target);
        if (visible) {
            lostTicks = 0;
            lastSeen = target.position();
        } else if (++lostTicks > 40) {
            startSearch(level);
            return;
        }
        getLookControl().setLookAt(target, 40f, 40f);
        openDoorAhead(level, true);
        boolean downed = DownedManager.isDowned(target);
        if (timer % 4 == 1) getNavigation().moveTo(target, downed ? def.walkSpeed() : def.runSpeed());
        if (chaseTicks >= com.smileprotocol.config.SPConfig.get().rastreador_stamina) {
            getNavigation().stop();
            level.playSound(null, blockPosition(), ModSounds.RASTREADOR_PANT.get(), SoundSource.HOSTILE, 1f, 1f);
            setState(State.TIRED);
            return;
        }
        if (cooldown == 0 && distanceTo(target) < (downed ? 2.1 : 1.8) && visible) {
            getNavigation().stop();
            face(target);
            catchTarget(target, def, downed);
        }
    }

    // Conductos -------------------------------------------------------------------------------------------

    /**
     * Va a la rejilla, la arranca y se mete. Por dentro no se le ve: se le oye arañar la chapa mientras
     * avanza (mas despacio que un jugador gateando, {@code rastreador_vent_speed}). Si alcanza a alguien
     * dentro, lo derriba; si su presa sale por otra rejilla, sale detras; si la pierde, sale y la busca.
     */
    private void vent(ServerLevel level, MobDef def) {
        var cfg = com.smileprotocol.config.SPConfig.get();
        if (!inDuct) {
            // camino de la rejilla
            if (ventGrille == null || target == null || !target.isAlive()) {
                startSearch(level);
                return;
            }
            if (timer % 5 == 1) getNavigation().moveTo(ventGrille.getX() + 0.5, ventGrille.getY(), ventGrille.getZ() + 0.5, def.runSpeed());
            if (position().distanceTo(Vec3.atBottomCenterOf(ventGrille)) < 1.8) {
                getNavigation().stop();
                var gs = level.getBlockState(ventGrille);
                if (gs.getBlock() instanceof com.smileprotocol.block.VentGrilleBlock && !gs.getValue(com.smileprotocol.block.VentGrilleBlock.OPEN)) {
                    level.setBlock(ventGrille, gs.setValue(com.smileprotocol.block.VentGrilleBlock.OPEN, true), 3);
                }
                level.playSound(null, ventGrille, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.HOSTILE, 1.2f, 0.6f);
                level.playSound(null, ventGrille, ModSounds.RASTREADOR_SCREECH.get(), SoundSource.HOSTILE, 1f, 0.8f);
                inDuct = true;
                setNoGravity(true);
                noPhysics = true;
                setInvisible(true);
                setPos(ventGrille.getX() + 0.5, ventGrille.getY() - 1, ventGrille.getZ() + 0.5);
                ductPath.clear();
            } else if (timer > 200) {
                startSearch(level);
            }
            return;
        }
        // dentro del conducto
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (timer > cfg.rastreador_vent_seconds * 20 || target == null || !target.isAlive() || DeathManager.isDead(target)
                || HideManager.isHidden(target)) {
            leaveDuct(level, null);
            startSearch(level);
            return;
        }
        if (timer % 12 == 0) {
            level.playSound(null, blockPosition(), ModSounds.RASTREADOR_SCRATCH.get(), SoundSource.HOSTILE, 1.3f, 0.75f + getRandom().nextFloat() * 0.2f);
        }
        if (timer % 37 == 0) level.playSound(null, blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.HOSTILE, 0.6f, 0.5f);
        boolean targetIn = MotionManager.inDuct(target);
        if (!targetIn) {
            // ha salido: va a la rejilla por la que salio y sale detras
            BlockPos exit = nearestGrille(level, target.blockPosition(), 4);
            if (exit != null && timer % 10 == 0) ductPath.clear();
            BlockPos goalCell = exit != null ? exit.below() : null;
            if (goalCell == null || blockPosition().distManhattan(goalCell) <= 1) {
                leaveDuct(level, exit);
                lastSeen = target.position();
                chaseTicks = 0;
                lostTicks = 0;
                level.playSound(null, blockPosition(), ModSounds.RASTREADOR_SCREECH.get(), SoundSource.HOSTILE, 1.4f, 1f);
                setState(State.CHASE);
                return;
            }
            crawlTo(level, goalCell, cfg.rastreador_vent_speed);
            return;
        }
        if (distanceTo(target) < 1.3) {
            // le alcanza dentro del conducto: lo derriba y sale por la rejilla mas cercana
            DownedManager.down(target, "rastreador");
            level.playSound(null, blockPosition(), ModSounds.RASTREADOR_SCREECH.get(), SoundSource.HOSTILE, 1.5f, 1.2f);
            leaveDuct(level, nearestGrille(level, blockPosition(), 12));
            cooldown = 60;
            startSearch(level);
            return;
        }
        if (timer % 10 == 0) ductPath.clear();
        crawlTo(level, target.blockPosition(), cfg.rastreador_vent_speed);
    }

    /** Avanza por las celdas del conducto hacia {@code goal} (camino por anchura, recalculado a menudo). */
    private void crawlTo(ServerLevel level, BlockPos goal, double speed) {
        if (ductPath.isEmpty()) {
            List<BlockPos> path = ductRoute(level, blockPosition(), goal);
            ductPath.addAll(path);
        }
        BlockPos next = ductPath.peekFirst();
        if (next == null) return;
        Vec3 to = Vec3.atBottomCenterOf(next);
        Vec3 d = to.subtract(position());
        double len = d.length();
        if (len <= speed) {
            setPos(to.x, to.y, to.z);
            ductPath.pollFirst();
        } else {
            Vec3 step = d.scale(speed / len);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
    }

    private static boolean ductCell(ServerLevel level, BlockPos c) {
        return !level.getBlockState(c).isSolid()
                && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(c.below()).getBlock()).getPath().equals("lab_vent_duct");
    }

    private static List<BlockPos> ductRoute(ServerLevel level, BlockPos from, BlockPos to) {
        BlockPos start = from, goal = new BlockPos(to.getX(), from.getY(), to.getZ());
        java.util.Map<BlockPos, BlockPos> prev = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        q.add(start);
        prev.put(start, start);
        BlockPos found = null, best = start;
        while (!q.isEmpty() && prev.size() < 600) {
            BlockPos c = q.poll();
            if (c.distManhattan(goal) < best.distManhattan(goal)) best = c;
            if (c.equals(goal)) {
                found = c;
                break;
            }
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = c.relative(dir);
                if (prev.containsKey(n) || !ductCell(level, n)) continue;
                prev.put(n, c);
                q.add(n);
            }
        }
        BlockPos end = found != null ? found : best;
        java.util.LinkedList<BlockPos> path = new java.util.LinkedList<>();
        for (BlockPos c = end; !c.equals(start); c = prev.get(c)) path.addFirst(c);
        return path;
    }

    /** Sale del conducto por esa rejilla (o por donde esta) y vuelve a verse. */
    private void leaveDuct(ServerLevel level, @Nullable BlockPos grille) {
        if (!inDuct) return;
        inDuct = false;
        ductPath.clear();
        setNoGravity(false);
        noPhysics = false;
        setInvisible(false);
        BlockPos at = grille != null ? grille : blockPosition().above();
        // al lado de la rejilla, en el suelo de la sala
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos s = at.relative(dir).above();
            if (level.noCollision(this, getDimensions(getPose()).makeBoundingBox(Vec3.atBottomCenterOf(s)))) {
                at = s;
                break;
            }
        }
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.playSound(null, at, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.HOSTILE, 1.2f, 0.6f);
    }

    /** Rejilla de conducto mas cercana a {@code near} (en su planta, a {@code r} bloques). */
    @Nullable
    static BlockPos nearestGrille(ServerLevel level, BlockPos near, int r) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-r, -2, -r), near.offset(r, 2, r))) {
            if (level.getBlockState(p).getBlock() instanceof com.smileprotocol.block.VentGrilleBlock
                    && !level.getBlockState(p).getValue(com.smileprotocol.block.VentGrilleBlock.SEALED)) {
                double d = p.distSqr(near);
                if (d < bd) {
                    bd = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /** En el conducto ahora mismo (pruebas). */
    public boolean inDuct() {
        return inDuct;
    }

    // Rastro --------------------------------------------------------------------------------------------

    /** Si hay un rastro fresco cerca, se pone a seguirlo. */
    private boolean startTrack(ServerLevel level, double radius, int maxAge) {
        ScentTrail.Crumb c = ScentTrail.freshNear(level, position(), radius, maxAge);
        if (c == null || c.tick() <= trailDone) return false;
        // el mas antiguo de los cercanos no: el mas reciente, para ir hacia donde se fue
        crumb = c;
        level.playSound(null, blockPosition(), ModSounds.BERSERKER_SNIFF.get(), SoundSource.HOSTILE, 0.9f, 1.3f);
        SPDebug.log("rastreador encuentra un rastro" + (c.blood() ? " de sangre" : ""));
        setState(State.TRACK);
        return true;
    }

    /** Olfatea punto a punto hacia donde fue (si sangra, mas deprisa). */
    private void track(ServerLevel level, MobDef def) {
        if (crumb == null || timer > 900) {
            crumb = null;
            setState(State.PATROL);
            return;
        }
        Vec3 at = crumb.pos();
        double speed = def.walkSpeed() * (crumb.blood() ? 1.6 : 1.15);
        if (position().distanceToSqr(at) < 1.6 * 1.6) {
            // olfatea un momento cada pocos puntos y sigue
            ScentTrail.Crumb n = ScentTrail.next(crumb);
            if (n == null) {
                trailDone = crumb.tick();
                lastSeen = at;
                crumb = null;
                startSearch(level);
                return;
            }
            crumb = n;
            if (random.nextInt(6) == 0) {
                level.playSound(null, blockPosition(), ModSounds.BERSERKER_SNIFF.get(), SoundSource.HOSTILE, 0.8f, 1.35f);
            }
        }
        if (timer % 6 == 0) getNavigation().moveTo(crumb.pos().x, crumb.pos().y, crumb.pos().z, speed);
    }

    /** Pruebas: sigue el rastro mas reciente cerca de el. */
    public boolean debugTrack(double radius) {
        forced = false;
        return level() instanceof ServerLevel sl && startTrack(sl, radius, ScentTrail.LIFE);
    }

    /** Siguiendo un rastro ahora (pruebas). */
    public boolean tracking() {
        return state() == State.TRACK && crumb != null;
    }

    // Puertas ---------------------------------------------------------------------------------------

    /** Puerta cerrada del laboratorio justo delante, que se puede abrir. */
    @Nullable
    private BlockPos closedDoorAhead(ServerLevel level) {
        Vec3 dir = getDeltaMovement().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4 || getNavigation().isDone()) return null;
        dir = dir.normalize();
        for (double k : new double[]{0.7, 1.4}) {
            BlockPos p = BlockPos.containing(getX() + dir.x * k, getY() + 0.5, getZ() + dir.z * k);
            var st = level.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && !st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                var be = com.smileprotocol.block.LabDoorBlock.config(level, p, st);
                boolean free = be == null ? st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock d && d.type().canOpenByHand()
                        : be.type() == com.smileprotocol.mechanism.DoorType.NORMAL || be.unlocked();
                return free ? p : null;
            }
        }
        return null;
    }

    /** Persiguiendo: abre la puerta y sigue. */
    private void openDoorAhead(ServerLevel level, boolean noisy) {
        if (tickCount % 4 != 0) return;
        BlockPos d = closedDoorAhead(level);
        if (d == null) return;
        com.smileprotocol.block.LabDoorBlock.setOpenAt(level, d, true);
        if (noisy) com.smileprotocol.noise.NoiseSystem.emit(level, Vec3.atCenterOf(d), 8f, NoiseType.DOOR, this);
    }

    /** Sin prisa, ante una puerta cerrada: se agacha a mirar por debajo antes de abrirla. */
    private boolean doorAhead(ServerLevel level, State s) {
        if (tickCount % 4 != 0) return false;
        BlockPos d = closedDoorAhead(level);
        if (d == null) return false;
        peekDoor = d;
        afterPeek = s;
        getNavigation().stop();
        setState(State.PEEK);
        return true;
    }

    /** Mira por debajo de la puerta con el ojo de la mano; si ve a alguien al otro lado, va a por el. */
    private void peek(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (peekDoor == null) {
            setState(State.PATROL);
            return;
        }
        getLookControl().setLookAt(Vec3.atCenterOf(peekDoor));
        if (timer == 4) level.playSound(null, peekDoor, ModSounds.RASTREADOR_SCRATCH.get(), SoundSource.HOSTILE, 0.5f, 1.4f);
        if (timer % 10 == 5) {
            // el ojo mira desde el suelo, al otro lado de la puerta
            Vec3 door = Vec3.atBottomCenterOf(peekDoor);
            Vec3 across = door.subtract(position()).multiply(1, 0, 1).normalize();
            Vec3 eye = door.add(across.scale(0.7)).add(0, 0.15, 0);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12), this::valid)) {
                if (p.position().subtract(door).dot(across) < 0.3) continue;
                if (level.clip(new ClipContext(eye, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                        .getType() != HitResult.Type.MISS) continue;
                com.smileprotocol.block.LabDoorBlock.setOpenAt(level, peekDoor, true);
                target = p;
                chaseTicks = 0;
                lostTicks = 0;
                level.playSound(null, blockPosition(), ModSounds.RASTREADOR_SCREECH.get(), SoundSource.HOSTILE, 1.4f, 1f);
                peekDoor = null;
                setState(State.CHASE);
                return;
            }
        }
        if (timer >= 50) {
            com.smileprotocol.block.LabDoorBlock.setOpenAt(level, peekDoor, true);
            peekDoor = null;
            State back = afterPeek == State.PEEK || afterPeek == State.CHASE ? State.PATROL : afterPeek;
            entityData.set(STATE, (byte) back.ordinal());
            timer = 0;
        }
    }

    /** Pruebas: esta mirando por debajo de una puerta. */
    public boolean peeking() {
        return state() == State.PEEK;
    }

    // Arañazos --------------------------------------------------------------------------------------

    /** Al pasar pegado a una pared, a veces la araña: se queda la marca. */
    private void claws(ServerLevel level, State s) {
        if (scratchCd > 0) {
            scratchCd--;
            return;
        }
        if (s != State.CHASE && s != State.TRACK && s != State.PATROL && s != State.SEARCH) return;
        if (getDeltaMovement().horizontalDistanceSqr() < 1.0E-3 || random.nextInt(4) != 0) return;
        if (scratchWall(level)) scratchCd = 120 + random.nextInt(160);
    }

    /** Deja un arañazo en la pared mas cercana a su lado, si hay sitio. Devuelve si lo ha hecho. */
    public boolean scratchWall(ServerLevel level) {
        BlockPos base = blockPosition().above(random.nextInt(2));
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos wall = base.relative(d);
            if (!level.getBlockState(base).isAir() || !level.getBlockState(wall).isFaceSturdy(level, wall, d.getOpposite())) continue;
            // no repetir pegado a otro
            for (BlockPos q : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 1, 2))) {
                if (level.getBlockState(q).is(com.smileprotocol.registry.ModBlocks.WALL_DECAL.get())) return false;
            }
            level.setBlock(base, com.smileprotocol.registry.ModBlocks.WALL_DECAL.get().defaultBlockState()
                    .setValue(com.smileprotocol.block.WallDecalBlock.FACING, d.getOpposite())
                    .setValue(com.smileprotocol.block.WallDecalBlock.VARIANT, random.nextBoolean() ? 3 : 6), 3);
            level.playSound(null, base, ModSounds.RASTREADOR_SCRATCH.get(), SoundSource.HOSTILE, 0.7f, 1.2f);
            return true;
        }
        return false;
    }

    private void catchTarget(ServerPlayer p, MobDef def, boolean downed) {
        if (downed) {
            if (!DownedManager.execute(p, this, def.executionSequence())) cooldown = 20;
            return;
        }
        if (QTEManager.startGrab(this, p, QTEType.parse(def.qteType()), def.qteTicks(), def.qteFail())) {
            setState(State.GRAB);
        } else {
            cooldown = 30;   // esquivado con un dive
        }
    }

    private void startSearch(ServerLevel level) {
        getNavigation().stop();
        toCheck.clear();
        Vec3 c = lastSeen != null ? lastSeen : position();
        List<BlockPos> spots = new ArrayList<>();
        BoundingBox box = new BoundingBox(BlockPos.containing(c)).inflatedBy(7);
        CircuitManager.forEachBlockEntity(level, box, be -> {
            if (be instanceof HideBlockEntity) spots.add(be.getBlockPos().immutable());
        });
        spots.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(c)));
        if (seenHiding != null) {
            spots.remove(seenHiding);
            spots.add(0, seenHiding);
            seenHiding = null;
        }
        for (int i = 0; i < Math.min(3, spots.size()); i++) toCheck.add(spots.get(i));
        setState(State.SEARCH);
    }

    private void nextCheck(ServerLevel level, MobDef def) {
        if (toCheck.isEmpty()) {
            setState(State.PATROL);
            return;
        }
        checking = toCheck.remove(0);
        setState(State.CHECK);
    }

    private void check(ServerLevel level, MobDef def) {
        if (checking == null) {
            setState(State.PATROL);
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(checking);
        if (position().distanceTo(at) > 2.2) {
            if (timer % 10 == 1) getNavigation().moveTo(at.x, at.y, at.z, def.walkSpeed() * 1.3);
            if (timer > 160) {
                checking = null;
                nextCheck(level, def);
            }
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(at.x, at.y + 1, at.z);
        if (timer < 200) timer = 200;   // fase de abrir
        HideManager.setSearching(checking, true);
        if (timer == 200) level.playSound(null, checking, ModSounds.RASTREADOR_SCRATCH.get(), SoundSource.HOSTILE, 1f, 1f);
        if (timer >= 230) {
            HideManager.setSearching(checking, false);
            for (java.util.UUID id : HideManager.occupantsOf(checking)) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p == null) continue;
                SPDebug.log("rastreador saca a " + p.getGameProfile().getName() + " del escondite");
                HideManager.forceExit(p, true);
                target = p;
                face(p);
                catchTarget(p, def, false);
                checking = null;
                return;
            }
            checking = null;
            nextCheck(level, def);
        }
    }

    /** Un jugador en su cono de vision (README 20.1 y docs/SMILERS.md). */
    @Nullable
    private ServerPlayer see(ServerLevel level, MobDef def) {
        float range = def.sightRange() > 0 ? def.sightRange() : 20f;
        Vec3 eye = getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, getYHeadRot());
        double cos = Math.cos(Math.toRadians(70));
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range), this::valid).stream()
                .filter(p -> {
                    Vec3 v = p.getEyePosition().subtract(eye);
                    if (v.length() > range) return false;
                    Vec3 flat = new Vec3(v.x, 0, v.z).normalize();
                    return (v.length() < 3 || flat.dot(look) > cos) && canSee(p);
                })
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** Pruebas: si ahora mismo, con su orientacion actual, veria a ese jugador. */
    public boolean wouldSee(ServerPlayer p) {
        return level() instanceof ServerLevel sl && see(sl, def()) == p;
    }

    private boolean canSee(ServerPlayer p) {
        return hasLineOfSight(p) && level().clip(new ClipContext(getEyePosition(), p.getEyePosition(), ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private boolean valid(ServerPlayer p) {
        if (!p.isAlive() || !SPPlayerData.inSP(p) || p.isSpectator() || p.isCreative() || p.level() != level()
                || HideManager.isHidden(p) || SequenceManager.inSequence(p) || QTEManager.isGrabbed(p) || DeathManager.isDead(p)) return false;
        if (DownedManager.isDowned(p)) return def().execution() && DownedManager.canExecute(p);
        return true;
    }

    @Override
    public void hear(ServerLevel level, NoiseEvent e) {
        if (!isAlive() || forced || getUUID().equals(e.source()) || e.type() == NoiseType.STRIKE) return;
        State s = state();
        if (s == State.CHASE || s == State.GRAB || s == State.STUNNED || s == State.CHECK) return;
        InstanceContext mine = InstanceContext.at(level, blockPosition());
        if (mine == null || mine != InstanceContext.at(level, BlockPos.containing(e.pos()))) return;
        MobDef def = def();
        float effective = e.radius() * def.hearingMultiplier();
        if (level.clip(new ClipContext(getEyePosition(), e.pos().add(0, 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                this)).getType() == HitResult.Type.BLOCK) effective *= 0.6f;
        if (effective < def.minNoise() || getEyePosition().distanceTo(e.pos()) > effective) return;
        goal = e.pos();
        if (s != State.INVESTIGATE) setState(State.INVESTIGATE);
    }

    private void face(ServerPlayer p) {
        setYRot((float) Math.toDegrees(Math.atan2(-(p.getX() - getX()), p.getZ() - getZ())));
        yBodyRot = getYRot();
    }

    @Override
    protected void onSequenceEnd() {
        target = null;
        cooldown = 80;
        setState(State.PATROL);
    }

    @Override
    public void onGrabStart(ServerPlayer p) {
        setState(State.GRAB);
    }

    @Override
    public void onGrabEnd(ServerPlayer p, boolean escaped) {
        if (escaped) {
            setState(State.STUNNED);
        } else {
            // derribado: se aparta un momento y vuelve a rematarlo
            cooldown = 60;
            target = p;
            chaseTicks = 0;
            setState(State.CHASE);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case CHASE, VENT -> st.isMoving() ? st.setAndContinue(RUN) : st.setAndContinue(IDLE);
                case TIRED -> st.setAndContinue(TIRED_A);
                case SEARCH, CHECK -> st.isMoving() ? st.setAndContinue(WALK) : st.setAndContinue(SEARCH_A);
                case TRACK -> st.isMoving() ? st.setAndContinue(WALK) : st.setAndContinue(SNIFF_A);
                case PEEK -> st.setAndContinue(PEEK_A);
                case GRAB -> st.setAndContinue(GRAB_A);
                case STUNNED -> st.setAndContinue(STUN_A);
                default -> st.isMoving() ? st.setAndContinue(WALK) : st.setAndContinue(IDLE);
            };
        }));
    }
}
