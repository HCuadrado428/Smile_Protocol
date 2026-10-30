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
 * El Rastreador (docs/SMILERS.md): cazador metodico sin piel, con el ojo en la palma.
 * PATROL (de pie) → oye un ruido: INVESTIGATE → te ve (cono de 20 bloques): CHASE a cuatro patas,
 * mas rapido que tu, pero a los 8 s se cansa (TIRED) → te pierde: SEARCH donde te vio, alzando la
 * palma, y revisa los escondites cercanos (empezando por el que te vio usar) y saca a quien este
 * dentro. Al alcanzarte, agarre con QTE MASH; si fallas quedas derribado y vuelve a rematarte.
 */
public class Rastreador extends SmilerBase implements QTEManager.Grabber {

    public enum State { PATROL, INVESTIGATE, CHASE, TIRED, SEARCH, CHECK, GRAB, STUNNED }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Rastreador.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SEARCH_A = RawAnimation.begin().thenLoop("search");
    private static final RawAnimation TIRED_A = RawAnimation.begin().thenLoop("tired");
    private static final RawAnimation GRAB_A = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation STUN_A = RawAnimation.begin().thenLoop("stunned");

    private int timer;
    private int chaseTicks;
    private int lostTicks;
    private int cooldown;
    @Nullable private ServerPlayer target;
    @Nullable private Vec3 goal;
    @Nullable private Vec3 lastSeen;
    @Nullable private BlockPos seenHiding;
    private final List<BlockPos> toCheck = new ArrayList<>();
    @Nullable private BlockPos checking;
    private boolean forced;

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
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State s) {
        if (s != state()) SPDebug.log("rastreador " + state() + " -> " + s);
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Grabacion y pruebas: estado fijo, sin IA. */
    public void debugForce(State s) {
        forced = true;
        getNavigation().stop();
        entityData.set(STATE, (byte) s.ordinal());
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
        if (s != State.GRAB && s != State.STUNNED && s != State.TIRED && s != State.CHASE && cooldown == 0 && timer % 4 == 0) {
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
        switch (s) {
            case PATROL -> {
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
        setState(State.INVESTIGATE);
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
                case CHASE -> st.isMoving() ? st.setAndContinue(RUN) : st.setAndContinue(IDLE);
                case TIRED -> st.setAndContinue(TIRED_A);
                case SEARCH, CHECK -> st.isMoving() ? st.setAndContinue(WALK) : st.setAndContinue(SEARCH_A);
                case GRAB -> st.setAndContinue(GRAB_A);
                case STUNNED -> st.setAndContinue(STUN_A);
                default -> st.isMoving() ? st.setAndContinue(WALK) : st.setAndContinue(IDLE);
            };
        }));
    }
}
