package com.smileprotocol.entity;

import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.qte.QTEType;
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
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Mob de pruebas para el agarre y el QTE (README 18, fase 11) hasta que se defina el siguiente
 * smiler: ve a los jugadores que tiene delante, los persigue y los agarra. Si se le escapan se
 * queda aturdido; si no, el jugador cae derribado (o muere, segun la ficha). Usa el modelo del
 * Berserker con el ojo abierto. Solo aparece con {@code /spadmin mob spawn smiler_test}.
 */
public class SmilerTest extends SmilerBase implements QTEManager.Grabber {

    public enum State { IDLE, CHASE, GRAB, STUNNED }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SmilerTest.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation GRAB_ANIM = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation STUN_ANIM = RawAnimation.begin().thenLoop("stunned");

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

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        MobDef def = def();
        timer++;
        if (cooldown > 0) cooldown--;
        switch (state()) {
            case IDLE -> {
                getNavigation().stop();
                if (timer % 10 == 0) {
                    target = findTarget(level, def);
                    if (target != null) setState(State.CHASE);
                }
            }
            case CHASE -> {
                if (target == null || !valid(target) || distanceTo(target) > def.sightRange() * 1.5f) {
                    target = null;
                    setState(State.IDLE);
                    return;
                }
                getLookControl().setLookAt(target, 30f, 30f);
                if (timer % 5 == 1) getNavigation().moveTo(target, def.runSpeed());
                if (cooldown == 0 && distanceTo(target) < 1.8 && hasLineOfSight(target)) {
                    getNavigation().stop();
                    setYRot((float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ())));
                    yBodyRot = getYRot();
                    if (QTEManager.startGrab(this, target, QTEType.parse(def.qteType()), def.qteTicks(), def.qteFail())) {
                        setState(State.GRAB);
                    } else {
                        cooldown = 30; // lo ha esquivado
                    }
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

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && !DownedManager.isDowned(p)
                && !HideManager.isHidden(p) && p.level() == level();
    }

    @Nullable
    private ServerPlayer findTarget(ServerLevel level, MobDef def) {
        float range = def.sightRange();
        if (range <= 0) return null;
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range), this::valid).stream()
                .filter(this::hasLineOfSight)
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
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
            cooldown = 80;
            target = null;
            setState(State.IDLE);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, st -> switch (state()) {
            case GRAB -> st.setAndContinue(GRAB_ANIM);
            case STUNNED -> st.setAndContinue(STUN_ANIM);
            default -> st.isMoving() ? st.setAndContinue(state() == State.CHASE ? RUN : WALK) : st.setAndContinue(IDLE);
        }));
    }
}
