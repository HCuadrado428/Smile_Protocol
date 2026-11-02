package com.smileprotocol.entity;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.motion.Flashlights;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * El Ahorcado (docs/SMILERS.md): muneco de trapo colgado del techo. Cae sobre quien pasa justo
 * debajo y lo agarra (QTE SEQUENCE); si falla, se arrastra muy rapido 2 s y luego los cables lo
 * vuelven a subir. Si alguien le apunta con la linterna se queda quieto (lo han visto) y no cae;
 * un objeto lanzado debajo lo hace caer antes de tiempo. No persigue lejos ni remata.
 * <p>
 * Cuelgan otros muñecos iguales a su alrededor ({@link #isDecoy()}: sin vida); solo uno es el
 * Ahorcado. La linterna lo delata porque gira la cabeza hacia la luz. Cae en la cara: mientras
 * agarra, la tela del saco tapa la vista.
 */
public class Ahorcado extends SmilerBase implements QTEManager.Grabber {

    public enum State { HANG, DROP, CRAWL, GRAB, STUNNED, CLIMB }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Ahorcado.class, EntityDataSerializers.BYTE);
    /** Muñeco de adorno: cuelga igual pero no tiene vida. */
    private static final EntityDataAccessor<Boolean> DECOY = SynchedEntityData.defineId(Ahorcado.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation HANG_A = RawAnimation.begin().thenLoop("hang");
    private static final RawAnimation DROP_A = RawAnimation.begin().thenPlayAndHold("drop");
    private static final RawAnimation CRAWL_A = RawAnimation.begin().thenLoop("crawl");
    private static final RawAnimation GRAB_A = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation STUN_A = RawAnimation.begin().thenLoop("stunned");
    private static final RawAnimation CLIMB_A = RawAnimation.begin().thenLoop("climb");

    @Nullable private Vec3 anchor;
    private int timer;
    private int lit;
    @Nullable private ServerPlayer target;
    private boolean forced;
    private boolean decoysPlaced;
    private final java.util.List<java.util.UUID> decoys = new java.util.ArrayList<>();

    public Ahorcado(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String defId() {
        return "ahorcado";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
        b.define(DECOY, false);
    }

    public boolean isDecoy() {
        return entityData.get(DECOY);
    }

    /** Muñecos iguales colgados alrededor (pruebas). */
    public int decoyCount() {
        return decoys.size();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Decoy", isDecoy());
        tag.putBoolean("DecoysPlaced", decoysPlaced);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DECOY, tag.getBoolean("Decoy"));
        decoysPlaced = tag.getBoolean("DecoysPlaced");
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && reason.shouldDestroy()) {
            for (java.util.UUID id : decoys) {
                net.minecraft.world.entity.Entity d = sl.getEntity(id);
                if (d != null) d.discard();
            }
        }
        super.remove(reason);
    }

    private void swapWithDecoy(ServerLevel level) {
        java.util.UUID id = decoys.get(getRandom().nextInt(decoys.size()));
        if (!(level.getEntity(id) instanceof Ahorcado d) || anchor == null || d.anchor == null) return;
        if (Paciente.observed(level, getBoundingBox()) || Paciente.observed(level, d.getBoundingBox())) return;
        Vec3 mine = anchor, theirs = d.anchor;
        d.hangAt(mine);
        hangAt(theirs);
        level.playSound(null, BlockPos.containing(theirs), ModSounds.AHORCADO_CREAK.get(), SoundSource.HOSTILE, 0.5f, 0.8f);
        SPDebug.log("ahorcado cambia de sitio con un muneco");
    }

    /** Cuelga dos o tres muñecos iguales cerca, a la misma altura, donde quepan. */
    private void placeDecoys(ServerLevel level) {
        decoysPlaced = true;
        if (anchor == null) return;
        int want = 2 + getRandom().nextInt(2);
        for (int i = 0; i < 24 && decoys.size() < want; i++) {
            double ang = getRandom().nextDouble() * Math.PI * 2, r = 2.5 + getRandom().nextDouble() * 4.5;
            Vec3 at = new Vec3(Math.floor(anchor.x + Math.cos(ang) * r) + 0.5, anchor.y, Math.floor(anchor.z + Math.sin(ang) * r) + 0.5);
            if (!level.noCollision(this, getType().getDimensions().makeBoundingBox(at))) continue;
            // colgado de algo: techo a pocos bloques por encima
            boolean roof = false;
            for (int dy = 1; dy <= 4 && !roof; dy++) {
                BlockPos up = BlockPos.containing(at.x, anchor.y + 1.5 + dy, at.z);
                roof = !level.getBlockState(up).getCollisionShape(level, up).isEmpty();
            }
            if (!roof) continue;
            boolean far = true;
            for (java.util.UUID id : decoys) {
                net.minecraft.world.entity.Entity d = level.getEntity(id);
                if (d != null && d.position().distanceTo(at) < 2) far = false;
            }
            if (!far) continue;
            Ahorcado d = (Ahorcado) getType().create(level);
            if (d == null) return;
            d.entityData.set(DECOY, true);
            d.decoysPlaced = true;
            d.setYRot(getRandom().nextFloat() * 360f);
            d.hangAt(at);
            level.addFreshEntity(d);
            decoys.add(d.getUUID());
        }
        SPDebug.log("ahorcado: " + decoys.size() + " munecos iguales a su alrededor");
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State s) {
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Lo cuelga en ese punto (pies a esa altura). */
    public void hangAt(Vec3 at) {
        anchor = at;
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        setNoGravity(true);
        setState(State.HANG);
    }

    /** Grabacion y pruebas: estado fijo, sin IA. */
    public void debugForce(State s) {
        forced = true;
        entityData.set(STATE, (byte) s.ordinal());
    }

    public boolean litUp() {
        return lit > 0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || forced) return;
        if (anchor == null) hangAt(position());
        if (isDecoy()) {
            // un muñeco sin vida: cuelga y se balancea, nada mas
            setDeltaMovement(Vec3.ZERO);
            if (position().distanceToSqr(anchor) > 0.01) setPos(anchor);
            return;
        }
        if (!decoysPlaced) placeDecoys(level);
        if (sequence() != null) return;
        timer++;
        if (lit > 0) lit--;
        MobDef def = def();
        switch (state()) {
            case HANG -> {
                setDeltaMovement(Vec3.ZERO);
                if (position().distanceToSqr(anchor) > 0.01) setPos(anchor);
                // la linterna lo delata: se queda quieto mientras le apunten
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12), this::valid)) {
                    if (!Flashlights.isOn(p)) continue;
                    Vec3 v = getBoundingBox().getCenter().subtract(p.getEyePosition());
                    if (v.normalize().dot(p.getViewVector(1f)) > Math.cos(Math.toRadians(18)) && p.hasLineOfSight(this)) {
                        if (lit == 0) level.playSound(null, blockPosition(), ModSounds.AHORCADO_CREAK.get(), SoundSource.HOSTILE, 0.8f, 1.3f);
                        lit = 30;
                        // lo delata: gira la cabeza hacia la luz (los muñecos de verdad no)
                        getLookControl().setLookAt(p, 20, 20);
                    }
                }
                if (lit > 0 || timer < 20) return;
                // cuando nadie mira, cambia de sitio con uno de los munecos: el que creias falso es el
                if (timer % 500 == 499 && !decoys.isEmpty()) swapWithDecoy(level);
                ServerPlayer below = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(1.2, 3.5, 1.2), this::valid)
                        .stream().filter(p -> p.getY() < getY() && horizontal(p) < 0.95)
                        .min(Comparator.comparingDouble(this::horizontal)).orElse(null);
                if (below != null) drop(level, below);
            }
            case DROP -> {
                if (onGround() || timer > 30) {
                    ServerPlayer near = target != null && valid(target) && distanceTo(target) < 1.5 ? target : null;
                    if (near != null && catchTarget(near, def)) return;
                    level.playSound(null, blockPosition(), ModSounds.AHORCADO_LAUGH.get(), SoundSource.HOSTILE, 1f, 1.1f);
                    setState(State.CRAWL);
                }
            }
            case CRAWL -> {
                if (target == null || !valid(target) || distanceTo(target) > 7) {
                    target = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6), this::valid).stream()
                            .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                }
                if (target != null) {
                    if (timer % 3 == 1) getNavigation().moveTo(target, def.runSpeed());
                    if (distanceTo(target) < 1.2 && catchTarget(target, def)) return;
                }
                if (timer >= 40) {
                    getNavigation().stop();
                    setState(State.CLIMB);
                }
            }
            case GRAB -> {
                getNavigation().stop();
                if (timer > 400) setState(State.CLIMB);
            }
            case STUNNED -> {
                getNavigation().stop();
                if (timer >= 40) setState(State.CLIMB);
            }
            case CLIMB -> climb();
        }
    }

    private void drop(ServerLevel level, @Nullable ServerPlayer p) {
        target = p;
        setNoGravity(false);
        setDeltaMovement(0, -0.6, 0);
        level.playSound(null, blockPosition(), ModSounds.AHORCADO_LAUGH.get(), SoundSource.HOSTILE, 1.2f, 0.9f);
        SPDebug.log("ahorcado cae" + (p != null ? " sobre " + p.getGameProfile().getName() : ""));
        setState(State.DROP);
    }

    private boolean catchTarget(ServerPlayer p, MobDef def) {
        getNavigation().stop();
        if (QTEManager.startGrab(this, p, QTEType.parse(def.qteType()), def.qteTicks(), def.qteFail())) {
            setState(State.GRAB);
            return true;
        }
        return false;
    }

    /** Vuelve bajo su cable andando y los cables lo suben. */
    private void climb() {
        if (anchor == null) return;
        Vec3 base = new Vec3(anchor.x, getY(), anchor.z);
        if (getY() < anchor.y - 0.3 && position().distanceTo(base) > 0.6 && timer < 100) {
            if (timer % 10 == 1) getNavigation().moveTo(anchor.x, getY(), anchor.z, def().walkSpeed());
            return;
        }
        getNavigation().stop();
        setNoGravity(true);
        Vec3 to = position().lerp(anchor, 0.12);
        setPos(to);
        setDeltaMovement(Vec3.ZERO);
        if (position().distanceTo(anchor) < 0.1) {
            setPos(anchor);
            setState(State.HANG);
        }
    }

    private double horizontal(ServerPlayer p) {
        double dx = p.getX() - getX(), dz = p.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !HideManager.isHidden(p) && !SequenceManager.inSequence(p) && !QTEManager.isGrabbed(p)
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p);
    }

    /** Un objeto lanzado que cae justo debajo lo hace caer antes de tiempo (docs/SMILERS.md). */
    @Override
    public void hear(ServerLevel level, NoiseEvent e) {
        if (forced || isDecoy() || state() != State.HANG || e.type() != NoiseType.THROW_IMPACT) return;
        double dx = e.pos().x - getX(), dz = e.pos().z - getZ();
        if (Math.sqrt(dx * dx + dz * dz) < 2.0 && e.pos().y < getY()) drop(level, null);
    }

    @Override
    public void onGrabStart(ServerPlayer p) {
        setState(State.GRAB);
    }

    @Override
    public void onGrabEnd(ServerPlayer p, boolean escaped) {
        setState(escaped ? State.STUNNED : State.CLIMB);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case HANG -> st.setAndContinue(HANG_A);
                case DROP -> st.setAndContinue(DROP_A);
                case CRAWL -> st.setAndContinue(CRAWL_A);
                case GRAB -> st.setAndContinue(GRAB_A);
                case STUNNED -> st.setAndContinue(STUN_A);
                case CLIMB -> st.setAndContinue(CLIMB_A);
            };
        }));
    }
}
