package com.smileprotocol.entity;

import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.motion.Flashlights;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Apagon (docs/SMILERS.md): humo y ceniza con muchos ojos rojos. Siente a quien esta a oscuras
 * (sin linterna ni luces cerca) y va a por el que mas se ha separado del grupo. Flota y atraviesa
 * las puertas: si no hay camino, cruza como humo. A oscuras es enorme y rapida; a la luz, pequena
 * y lenta. La linterna la hace retroceder, pero le come la bateria a quien le apunta. Apaga los
 * fluorescentes por donde pasa. Agarre HOLD; no remata, respeta los escondites, no entra en
 * los conductos y mete interferencias en los walkies.
 */
public class Apagon extends SmilerBase implements QTEManager.Grabber {

    public enum State { DRIFT, STALK, RECOIL, GRAB, STUNNED }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Apagon.class, EntityDataSerializers.BYTE);
    /** Tamano a dibujar (0,6 a la luz, 1,6 a oscuras), lo decide el servidor. */
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(Apagon.class, EntityDataSerializers.FLOAT);

    private static final RawAnimation FLOAT_A = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation MOVE_A = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation RECOIL_A = RawAnimation.begin().thenLoop("recoil");
    private static final RawAnimation GRAB_A = RawAnimation.begin().thenLoop("grab");

    @Nullable private ServerPlayer target;
    @Nullable private ServerPlayer lighter;
    private int timer;
    private int cooldown;
    private boolean forced;
    private float prevSize = 1f;

    public Apagon(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String defId() {
        return "apagon";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
        b.define(SIZE, 1f);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State s) {
        if (s != state()) SPDebug.log("apagon " + state() + " -> " + s);
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Escala del dibujo, suavizada en el cliente. */
    public float size(float pt) {
        float now = entityData.get(SIZE);
        float s = Mth.lerp(pt, prevSize, now);
        return s;
    }

    @Override
    public void tick() {
        prevSize = Mth.lerp(0.15f, prevSize, entityData.get(SIZE));
        super.tick();
    }

    public void debugForce(State s, float size) {
        forced = true;
        entityData.set(STATE, (byte) s.ordinal());
        entityData.set(SIZE, size);
    }

    /** A oscuras: sin linterna encendida y con poca luz de bloques alrededor. */
    public static boolean inDarkness(ServerPlayer p) {
        return !Flashlights.isOn(p) && p.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(p.getEyePosition())) < 6;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || forced) return;
        if (sequence() != null) return;
        timer++;
        if (cooldown > 0) cooldown--;
        boolean darkHere = level.getBrightness(LightLayer.BLOCK, blockPosition().above()) < 6;
        // Tamano: enorme a oscuras, encogida a la luz
        float want = darkHere ? 1.6f : 0.65f;
        float size = entityData.get(SIZE);
        if (Math.abs(size - want) > 0.01f) entityData.set(SIZE, size + (want - size) * 0.08f);
        if (tickCount % 10 == 0) killLights(level);

        // La linterna de alguien le da: retrocede y se come esa bateria
        ServerPlayer lit = litBy(level);
        State s = state();
        if (lit != null && s != State.GRAB && s != State.STUNNED) {
            lighter = lit;
            Flashlights.drain(lit, SPConfig.get().apagon_battery_drain);
            if (s != State.RECOIL) {
                level.playSound(null, blockPosition(), ModSounds.APAGON_HISS.get(), SoundSource.HOSTILE, 1.2f, 1f);
                setState(State.RECOIL);
                s = State.RECOIL;
            }
        }
        MobDef def = def();
        double speed = darkHere ? def.runSpeed() : def.walkSpeed();
        switch (s) {
            case DRIFT -> {
                setDeltaMovement(getDeltaMovement().scale(0.8));
                if (timer % 20 == 0) {
                    target = pickTarget(level);
                    if (target != null) setState(State.STALK);
                }
            }
            case STALK -> {
                if (target == null || !valid(target) || timer > 600) {
                    target = null;
                    noPhysics = false;
                    setState(State.DRIFT);
                    return;
                }
                if (timer % 40 == 0) {
                    ServerPlayer better = pickTarget(level);
                    if (better != null) target = better;
                }
                getLookControl().setLookAt(target, 30, 30);
                if (timer % 70 == 35) {
                    level.playSound(null, target.blockPosition(), ModSounds.APAGON_WHISPER.get(), SoundSource.HOSTILE, 0.5f, 1f);
                }
                approach(target.position(), speed);
                if (cooldown == 0 && distanceTo(target) < 1.6) {
                    noPhysics = false;
                    getNavigation().stop();
                    if (QTEManager.startGrab(this, target, QTEType.parse(def.qteType()), def.qteTicks(), def.qteFail())) {
                        setState(State.GRAB);
                    } else {
                        cooldown = 30;
                    }
                }
            }
            case RECOIL -> {
                getNavigation().stop();
                noPhysics = false;
                if (lighter != null) {
                    Vec3 away = position().subtract(lighter.position()).multiply(1, 0, 1).normalize().scale(0.12);
                    move(net.minecraft.world.entity.MoverType.SELF, away);
                }
                if (lit == null && timer > 20) setState(target != null ? State.STALK : State.DRIFT);
            }
            case GRAB -> {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                if (timer > 400) setState(State.DRIFT);
            }
            case STUNNED -> {
                setDeltaMovement(Vec3.ZERO);
                if (timer > 60) {
                    cooldown = 60;
                    setState(State.DRIFT);
                }
            }
        }
    }

    /** Por el camino si lo hay; si no (una puerta cerrada, una pared fina), cruza como humo. */
    private void approach(Vec3 to, double speed) {
        if (timer % 5 == 1) {
            Path path = getNavigation().createPath(BlockPos.containing(to), 1);
            if (path != null && path.canReach()) {
                noPhysics = false;
                getNavigation().moveTo(path, speed);
            } else {
                getNavigation().stop();
                noPhysics = true;
            }
        }
        if (noPhysics) {
            Vec3 d = to.subtract(position());
            Vec3 step = new Vec3(d.x, 0, d.z).normalize().scale(0.09 * speed);
            setPos(getX() + step.x, getY(), getZ() + step.z);
        }
    }

    @Nullable
    private ServerPlayer litBy(ServerLevel level) {
        double cos = Math.cos(Math.toRadians(16));
        Vec3 c = getBoundingBox().getCenter();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(14))) {
            if (!p.isAlive() || p.isSpectator() || !SPPlayerData.inSP(p) || !Flashlights.isOn(p)) continue;
            Vec3 v = c.subtract(p.getEyePosition());
            if (v.length() > 14 || v.normalize().dot(p.getViewVector(1f)) < cos) continue;
            if (p.hasLineOfSight(this)) return p;
        }
        return null;
    }

    /** El que esta a oscuras y mas lejos de sus companeros (docs/SMILERS.md). */
    @Nullable
    private ServerPlayer pickTarget(ServerLevel level) {
        List<ServerPlayer> all = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40), this::valid);
        return all.stream().filter(Apagon::inDarkness).max(Comparator.comparingDouble(p -> {
            double near = all.stream().filter(o -> o != p).mapToDouble(o -> o.distanceTo(p)).min().orElse(40);
            return near - p.distanceTo(this) * 0.3;
        })).orElse(null);
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !HideManager.isHidden(p) && !SequenceManager.inSequence(p) && !QTEManager.isGrabbed(p)
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p);
    }

    /** Apaga los fluorescentes que tiene encima (vuelven si alguien repone la corriente). */
    private void killLights(ServerLevel level) {
        BoundingBox box = new BoundingBox(blockPosition()).inflatedBy(3);
        CircuitManager.forEachBlockEntity(level, box, be -> {
            if (!(be instanceof FluorescentLightBlockEntity)) return;
            BlockState st = be.getBlockState();
            if (st.hasProperty(FluorescentLightBlock.LIT) && st.getValue(FluorescentLightBlock.LIT)) {
                level.setBlock(be.getBlockPos(), st.setValue(FluorescentLightBlock.LIT, false), 3);
                level.playSound(null, be.getBlockPos(), ModSounds.EVENT_SURGE.get(), SoundSource.BLOCKS, 0.5f, 1.6f);
            }
        });
    }

    @Override
    public void onGrabStart(ServerPlayer p) {
        setState(State.GRAB);
    }

    @Override
    public void onGrabEnd(ServerPlayer p, boolean escaped) {
        target = null;
        setState(escaped ? State.STUNNED : State.DRIFT);
        if (!escaped) cooldown = 100;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case RECOIL, STUNNED -> st.setAndContinue(RECOIL_A);
                case GRAB -> st.setAndContinue(GRAB_A);
                case STALK -> st.setAndContinue(MOVE_A);
                default -> st.setAndContinue(st.isMoving() ? MOVE_A : FLOAT_A);
            };
        }));
    }
}
