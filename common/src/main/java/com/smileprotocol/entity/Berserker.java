package com.smileprotocol.entity;

import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.noise.NoiseEvent;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Berserker (README 20.2): smiler ciego que solo oye. Cuando un ruido supera su umbral corre al
 * punto donde sono y golpea esa zona: derriba a todos los que sigan dentro y remata a los que ya
 * estaban derribados. No persigue a nadie (no tiene CHASE) y no revisa escondites, pero si alguien
 * hace ruido dentro de uno, golpea ese sitio.
 * <p>
 * Estados: {@code PATROL -> INVESTIGATE -> STRIKE -> RECOVER -> SEARCH -> PATROL}. Mide 2,5 bloques
 * encorvado, pero su caja es de 1,95 para pasar por las puertas del laboratorio (se agacha).
 */
public class Berserker extends SmilerBase {

    public enum State { PATROL, INVESTIGATE, STRIKE, RECOVER, SEARCH }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Berserker.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> MOVING_FAST = SynchedEntityData.defineId(Berserker.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation STRIKE_ANIM = RawAnimation.begin().thenPlay("strike");
    private static final RawAnimation RECOVER_ANIM = RawAnimation.begin().thenLoop("recover");
    private static final RawAnimation SEARCH_ANIM = RawAnimation.begin().thenLoop("search");

    // Servidor
    @Nullable private Vec3 target;
    private float targetLoudness;
    private int timer;
    private int wanderCooldown;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private int ambientCooldown = 100;
    private boolean awake;
    /** Grabacion de animaciones (prueba en el cliente): estado fijo, sin IA. */
    private boolean forced;

    public void debugForce(State s, boolean fast, @Nullable Vec3 to, double speed) {
        forced = true;
        setNoAi(false);
        entityData.set(STATE, (byte) s.ordinal());
        entityData.set(MOVING_FAST, fast);
        if (to != null) getNavigation().moveTo(to.x, to.y, to.z, speed);
        else getNavigation().stop();
    }

    public Berserker(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String defId() {
        return "berserker";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
        b.define(MOVING_FAST, false);
    }

    public State state() {
        byte i = entityData.get(STATE);
        return State.values()[Mth.clamp(i, 0, State.values().length - 1)];
    }

    private void setState(State s) {
        if (state() == s) return;
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
        stuckTicks = 0;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    // Oido -----------------------------------------------------------------------------------

    /** Lo llama {@link MobManager} con cada ruido de su nivel. */
    void hear(ServerLevel level, NoiseEvent e) {
        if (!isAlive() || !awake || getUUID().equals(e.source())) return;
        if (e.type() == NoiseType.STRIKE) return;
        // Solo los ruidos de su propia instancia
        InstanceContext mine = InstanceContext.at(level, blockPosition());
        InstanceContext theirs = InstanceContext.at(level, BlockPos.containing(e.pos()));
        if (mine == null || mine != theirs) return;
        MobDef def = def();
        float effective = e.radius() * def.hearingMultiplier();
        // Atenuacion por paredes: si hay algo solido en medio, se oye bastante menos
        Vec3 ear = getEyePosition();
        if (level.clip(new ClipContext(ear, e.pos().add(0, 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                this)).getType() == HitResult.Type.BLOCK) {
            effective *= 0.6f;
        }
        if (effective < def.minNoise()) return;
        double dist = ear.distanceTo(e.pos());
        if (dist > effective) return;
        float loudness = (float) (effective - dist) + effective * 0.25f;
        State s = state();
        if (s == State.STRIKE || s == State.RECOVER) return;
        if (s == State.INVESTIGATE && target != null && loudness < targetLoudness * def.retargetRatio()) return;
        target = e.pos();
        targetLoudness = loudness;
        if (s != State.INVESTIGATE) {
            playSound(ModSounds.BERSERKER_LISTEN.get(), 1.6f, 1.0f);
        }
        setState(State.INVESTIGATE);
        SPDebug.log("berserker oye " + e.type() + " r=" + e.radius() + " ef=" + String.format("%.1f", effective));
    }

    // IA -----------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (forced) return;
        if (sequence() != null) {
            getNavigation().stop();
            return;
        }
        if (tickCount % 20 == 0) {
            awake = !com.smileprotocol.config.SPConfig.get().mobs_wait_for_players
                    || level.getNearestPlayer(this, 72) instanceof ServerPlayer sp && SPPlayerData.inSP(sp);
        }
        if (!awake) {
            getNavigation().stop();
            return;
        }
        MobDef def = def();
        timer++;
        if (--ambientCooldown <= 0) {
            ambientCooldown = 120 + random.nextInt(160);
            playSound(ModSounds.BERSERKER_BREATH.get(), 1.2f, 0.9f + random.nextFloat() * 0.2f);
        }
        openDoorsAhead(level, def);
        switch (state()) {
            case PATROL -> patrol(def);
            case INVESTIGATE -> investigate(level, def);
            case STRIKE -> {
                getNavigation().stop();
                if (target != null) getLookControl().setLookAt(target.x, target.y + 0.5, target.z);
                if (timer == 1) playSound(ModSounds.BERSERKER_ROAR.get(), 1.6f, 1.0f);
                if (timer >= def.windup()) {
                    impact(level, def);
                    setState(State.RECOVER);
                }
            }
            case RECOVER -> {
                getNavigation().stop();
                if (timer >= def.recovery()) {
                    setState(State.SEARCH);
                    playSound(ModSounds.BERSERKER_SNIFF.get(), 1.4f, 1.0f);
                }
            }
            case SEARCH -> {
                getNavigation().stop();
                // gira la cabeza a un lado y a otro, escuchando
                float yaw = getYRot() + Mth.sin(timer * 0.08f) * 70f;
                Vec3 look = getEyePosition().add(Vec3.directionFromRotation(10f, yaw).scale(3));
                getLookControl().setLookAt(look.x, look.y, look.z);
                if (timer % 40 == 20) playSound(ModSounds.BERSERKER_SNIFF.get(), 1.2f, 0.9f);
                if (timer >= def.searchTime()) setState(State.PATROL);
            }
        }
        entityData.set(MOVING_FAST, state() == State.INVESTIGATE);
    }

    private void patrol(MobDef def) {
        if (!"WANDER".equalsIgnoreCase(def.idleBehavior())) {
            getNavigation().stop();
            return;
        }
        if (getNavigation().isDone() && --wanderCooldown <= 0) {
            wanderCooldown = 60 + random.nextInt(140);
            if (random.nextFloat() < 0.35f) return; // a veces se queda quieto, girando la cabeza
            Vec3 p = DefaultRandomPos.getPos(this, 12, 3);
            if (p != null) getNavigation().moveTo(p.x, p.y, p.z, def.walkSpeed());
        }
    }

    private void investigate(ServerLevel level, MobDef def) {
        if (target == null) {
            setState(State.SEARCH);
            return;
        }
        double d = position().distanceTo(target);
        if (d <= 1.6 || (timer > 10 && getNavigation().isDone() && d <= def.strikeRadius() + 1.5)) {
            setState(State.STRIKE);
            return;
        }
        if (timer % 10 == 1 || getNavigation().isDone()) {
            if (!getNavigation().moveTo(target.x, target.y, target.z, def.runSpeed()) && timer > 20) {
                // no hay camino: golpea donde puede o se queda escuchando
                setState(d <= def.strikeRadius() + 2 ? State.STRIKE : State.SEARCH);
                return;
            }
        }
        // atascado (puerta cerrada que no puede abrir, mueble...): se rinde al rato
        if (position().distanceToSqr(lastPos) < 0.0025) stuckTicks++;
        else stuckTicks = 0;
        lastPos = position();
        if (stuckTicks > 60) setState(d <= def.strikeRadius() + 2 ? State.STRIKE : State.SEARCH);
        if (timer > 20 * 30) setState(State.SEARCH);
    }

    /** El golpe (README 20.2): derriba a todos los que esten dentro del area y remata a los derribados. */
    private void impact(ServerLevel level, MobDef def) {
        Vec3 c = target != null && target.distanceTo(position()) <= def.strikeRadius() + 2
                ? target : position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        double r = def.strikeRadius();
        playSound(ModSounds.BERSERKER_IMPACT.get(), 2.0f, 1.0f);
        level.playSound(null, BlockPos.containing(c), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0f, 0.6f);
        BlockState floor = level.getBlockState(BlockPos.containing(c).below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), c.x, c.y + 0.1, c.z, 40, r * 0.4, 0.1, r * 0.4, 0.15);
        NoiseSystem.emit(level, c, 10f, NoiseType.STRIKE, this);
        List<ServerPlayer> hit = level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(r, 2.0, r),
                p -> SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative()
                        && Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= r);
        for (ServerPlayer p : hit) {
            if (DownedManager.isDowned(p)) {
                // remata al primero (una secuencia por mob); los demas siguen derribados
                if (def.execution()) DownedManager.execute(p, this, def.executionSequence());
            } else if ("INSTANT_KILL".equalsIgnoreCase(def.onCatch())) {
                if (!com.smileprotocol.sequence.SequenceManager.start(this, p, def.killSequence())) DownedManager.down(p, "berserker");
            } else {
                DownedManager.down(p, "berserker");
                p.push((p.getX() - c.x) * 0.3, 0.25, (p.getZ() - c.z) * 0.3);
                p.hurtMarked = true;
            }
        }
        SPDebug.log("berserker golpea en " + BlockPos.containing(c).toShortString() + " alcanzados=" + hit.size());
        target = null;
    }

    /** Abre las puertas normales del laboratorio que tiene delante; las cerradas con llave le cortan el paso. */
    private void openDoorsAhead(ServerLevel level, MobDef def) {
        if (!def.opensDoors() || getNavigation().isDone() || tickCount % 4 != 0) return;
        Vec3 dir = getDeltaMovement().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        for (double k : new double[]{0.6, 1.2}) {
            BlockPos p = BlockPos.containing(getX() + dir.x * k, getY() + 0.5, getZ() + dir.z * k);
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DoorBlock && !st.getValue(DoorBlock.OPEN)) {
                LabDoorBlockEntity be = LabDoorBlock.config(level, p, st);
                boolean free = be == null ? st.getBlock() instanceof DoorBlock d && d.type().canOpenByHand()
                        : be.type() == DoorType.NORMAL || be.unlocked();
                if (free) {
                    LabDoorBlock.setOpenAt(level, p, true);
                    NoiseSystem.emit(level, Vec3.atCenterOf(p), 8f, NoiseType.DOOR, this);
                }
                return;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Pasos pesados (se oyen, pero no generan ruido para los mobs)
        if (level().isClientSide && onGround() && walkAnimation.speed() > 0.1f && tickCount % (entityData.get(MOVING_FAST) ? 6 : 11) == 0) {
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.BERSERKER_STEP.get(), SoundSource.HOSTILE,
                    entityData.get(MOVING_FAST) ? 1.0f : 0.6f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("SPState", (byte) state().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // tras recargar vuelve a merodear
        entityData.set(STATE, (byte) 0);
    }

    // Animaciones -----------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, st -> {
            software.bernie.geckolib.animation.PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            switch (state()) {
                case STRIKE -> {
                    return st.setAndContinue(STRIKE_ANIM);
                }
                case RECOVER -> {
                    return st.setAndContinue(RECOVER_ANIM);
                }
                case SEARCH -> {
                    return st.setAndContinue(SEARCH_ANIM);
                }
                default -> {
                    if (st.isMoving()) return st.setAndContinue(entityData.get(MOVING_FAST) ? RUN : WALK);
                    return st.setAndContinue(IDLE);
                }
            }
        }));
    }
}
