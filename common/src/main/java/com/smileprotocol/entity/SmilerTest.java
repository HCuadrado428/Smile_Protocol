package com.smileprotocol.entity;

import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.qte.QTEType;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Mob de pruebas del agarre, el QTE, los remates y la muerte instantanea (README 18 y 9.3, fases
 * 11 y 12) hasta que se definan los siguientes smilers: ve a los jugadores que tiene delante, los
 * persigue y hace lo que diga su ficha al alcanzarlos ({@code on_catch}): agarrar con QTE,
 * derribar o matar al instante. Si tiene {@code execution}, va a por los derribados que ve y los
 * remata con su secuencia. Si se le escapan del agarre se queda aturdido. Usa el modelo del
 * Berserker con el ojo abierto ({@code smiler_test}) o los dos ({@code smiler_kill}, muerte
 * instantanea). Solo aparecen con {@code /spadmin mob spawn}.
 */
public class SmilerTest extends SmilerBase implements QTEManager.Grabber {

    public enum State { IDLE, CHASE, GRAB, STUNNED }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SmilerTest.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation GRAB_ANIM = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation STUN_ANIM = RawAnimation.begin().thenLoop("stunned");

    /** Variante de la muerte instantanea. */
    public static class Killer extends SmilerTest {
        public Killer(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        public String defId() {
            return "smiler_kill";
        }
    }

    private int timer;
    private int cooldown;
    @Nullable private ServerPlayer target;

    public SmilerTest(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Berserker.attributes();
    }

    @Override
    public String defId() {
        return "smiler_test";
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
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    private boolean forced;

    /** Grabacion de animaciones: estado fijo, sin IA. */
    public void debugForce(State s) {
        forced = true;
        setNoAi(false);
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
        switch (state()) {
            case IDLE -> {
                getNavigation().stop();
                if (cooldown == 0 && timer % 10 == 0) {
                    target = findTarget(level, def);
                    if (target != null) setState(State.CHASE);
                }
            }
            case CHASE -> {
                if (target == null || !valid(target, def) || distanceTo(target) > def.sightRange() * 1.5f) {
                    target = null;
                    setState(State.IDLE);
                    return;
                }
                getLookControl().setLookAt(target, 30f, 30f);
                boolean downed = DownedManager.isDowned(target);
                if (timer % 5 == 1) getNavigation().moveTo(target, downed ? def.walkSpeed() : def.runSpeed());
                double reach = downed ? 2.1 : 1.8;
                if (cooldown == 0 && distanceTo(target) < reach && hasLineOfSight(target)) {
                    getNavigation().stop();
                    faceTarget(target);
                    catchTarget(target, def, downed);
                }
            }
            case GRAB -> {
                getNavigation().stop();
                if (target != null) getLookControl().setLookAt(target, 30f, 30f);
                if (timer > 400) setState(State.IDLE); // por si se pierde el final
            }
            case STUNNED -> {
                getNavigation().stop();
                if (timer >= 70) {
                    cooldown = 60;
                    setState(State.IDLE);
                }
            }
        }
    }

    /** Al alcanzarlo: remate si esta derribado; si no, lo que diga {@code on_catch} (README 18.1). */
    private void catchTarget(ServerPlayer p, MobDef def, boolean downed) {
        if (downed) {
            if (!DownedManager.execute(p, this, def.executionSequence())) cooldown = 20;
            return;
        }
        switch (def.onCatch().toUpperCase(java.util.Locale.ROOT)) {
            case "INSTANT_KILL" -> {
                if (!SequenceManager.start(this, p, def.killSequence())) cooldown = 20;
            }
            case "DOWN" -> {
                DownedManager.down(p, defId());
                cooldown = 60;
                setState(State.IDLE);
            }
            default -> {
                if (QTEManager.startGrab(this, p, QTEType.parse(def.qteType()), def.qteTicks(), def.qteFail())) {
                    setState(State.GRAB);
                } else {
                    cooldown = 30; // lo ha esquivado
                }
            }
        }
    }

    private void faceTarget(ServerPlayer p) {
        setYRot((float) Math.toDegrees(Math.atan2(-(p.getX() - getX()), p.getZ() - getZ())));
        yBodyRot = getYRot();
    }

    /** Objetivo: vivo y jugando, o derribado y rematable si tiene {@code execution}. */
    private boolean valid(ServerPlayer p, MobDef def) {
        if (!p.isAlive() || !SPPlayerData.inSP(p) || p.isSpectator() || p.isCreative() || p.level() != level()
                || HideManager.isHidden(p) || SequenceManager.inSequence(p) || QTEManager.isGrabbed(p)) return false;
        if (DownedManager.isDowned(p)) return def.execution() && DownedManager.canExecute(p);
        return true;
    }

    @Nullable
    private ServerPlayer findTarget(ServerLevel level, MobDef def) {
        float range = def.sightRange();
        if (range <= 0) return null;
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range), p -> valid(p, def)).stream()
                .filter(this::hasLineOfSight)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    @Override
    protected void onSequenceEnd() {
        target = null;
        cooldown = 60;
        setState(State.IDLE);
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
            // lo deja derribado y se aparta un momento antes de volver a rematarlo
            cooldown = 100;
            target = null;
            setState(State.IDLE);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case GRAB -> st.setAndContinue(GRAB_ANIM);
                case STUNNED -> st.setAndContinue(STUN_ANIM);
                default -> st.isMoving() ? st.setAndContinue(state() == State.CHASE ? RUN : WALK) : st.setAndContinue(IDLE);
            };
        }));
    }
}
