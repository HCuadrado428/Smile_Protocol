package com.smileprotocol.entity;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.sequence.SequenceKind;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * El Paciente (docs/SMILERS.md), inspirado en los angeles llorones: muy alto y rigido, sin ojo.
 * Mientras CUALQUIER jugador lo mire se queda congelado; cuando nadie lo mira salta de golpe por
 * el pasillo hacia su victima (sin animacion de andar: aparece en otra pose). Si llega a ella sin
 * que nadie lo vea, la mata al instante. No salta a un sitio que alguien este mirando, no entra en
 * las salas (no cabe por las puertas) ni en los conductos, y cambia de victima si la suya se aleja
 * mucho y hay otra mas cerca. La linterna no le afecta: solo cuenta que lo miren.
 */
public class Paciente extends SmilerBase {

    public static final int POSES = 6;
    private static final EntityDataAccessor<Byte> POSE_ID = SynchedEntityData.defineId(Paciente.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> WATCHED = SynchedEntityData.defineId(Paciente.class, EntityDataSerializers.BOOLEAN);

    @Nullable private ServerPlayer target;
    private int cooldown;
    private int jumps;
    private boolean forced;

    public Paciente(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 96)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String defId() {
        return "paciente";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(POSE_ID, (byte) 0);
        b.define(WATCHED, false);
    }

    public int poseId() {
        return entityData.get(POSE_ID);
    }

    public boolean watched() {
        return entityData.get(WATCHED);
    }

    /** Saltos dados (para las pruebas). */
    public int jumps() {
        return jumps;
    }

    /** Grabacion y pruebas: quieto en una pose, sin IA. */
    public void debugForce(int pose) {
        forced = true;
        entityData.set(POSE_ID, (byte) Math.floorMod(pose, POSES));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (!(level() instanceof ServerLevel level) || forced || sequence() != null) return;
        boolean seen = observed(level, getBoundingBox());
        if (seen != watched()) entityData.set(WATCHED, seen);
        pickTarget(level);
        if (cooldown > 0) cooldown--;
        if (seen || target == null || cooldown > 0) return;

        // Nadie mira: si ya esta encima de la victima, la mata; si no, salta hacia ella
        if (distanceTo(target) < 1.9 && hasLineOfSight(target)) {
            if (!SequenceManager.start(this, target, SequenceKind.PACIENTE_KILL)) cooldown = 10;
            return;
        }
        jumpToward(level, target);
    }

    private void jumpToward(ServerLevel level, ServerPlayer t) {
        Path path = getNavigation().createPath(t, 1);
        if (path == null || path.getNodeCount() < 2) {
            cooldown = 10;
            return;
        }
        int step = SPConfig.get().paciente_step;
        // del mas lejano al mas cercano: el primer sitio que nadie este mirando
        for (int i = Math.min(path.getNodeCount() - 1, step); i >= 1; i--) {
            BlockPos n = path.getNodePos(i);
            Vec3 to = Vec3.atBottomCenterOf(n);
            if (to.distanceToSqr(position()) < 0.5) continue;
            AABB box = getDimensions(getPose()).makeBoundingBox(to);
            if (!level.noCollision(this, box) || observed(level, box)) continue;
            Vec3 from = position();
            float yaw = (float) Math.toDegrees(Math.atan2(-(t.getX() - to.x), t.getZ() - to.z));
            moveTo(to.x, to.y, to.z, yaw, 0f);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
            int pose = poseId();
            while (pose == poseId()) pose = getRandom().nextInt(POSES);
            entityData.set(POSE_ID, (byte) pose);
            jumps++;
            // un crujido de huesos donde aparece: lo unico que se oye de el
            if (getRandom().nextInt(3) == 0 || from.distanceTo(to) > 2.5) {
                level.playSound(null, n, ModSounds.PACIENTE_CRACK.get(), SoundSource.HOSTILE, 0.7f, 0.85f + getRandom().nextFloat() * 0.3f);
            }
            cooldown = SPConfig.get().paciente_jump_ticks;
            return;
        }
        cooldown = 4;
    }

    /** Victima: la de antes salvo que se haya alejado mucho y haya otra mas cerca (docs/SMILERS.md). */
    private void pickTarget(ServerLevel level) {
        if (target != null && !valid(target)) target = null;
        if (tickCount % 10 != 0 && target != null) return;
        ServerPlayer nearest = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64), this::valid).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) {
            target = nearest;
        } else if (nearest != null && nearest != target && distanceTo(target) > SPConfig.get().paciente_retarget_distance
                && distanceTo(nearest) < distanceTo(target) - 4) {
            SPDebug.log("paciente cambia de victima: " + target.getGameProfile().getName() + " -> " + nearest.getGameProfile().getName());
            target = nearest;
        }
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p) && !HideManager.isHidden(p)
                && !SequenceManager.inSequence(p) && distanceTo(p) < 64;
    }

    /**
     * Alguien esta mirando esa caja: algun punto de ella dentro de su campo de vision y sin pared en
     * medio. Los escondidos no cuentan (ven por una rendija); la luz no importa.
     */
    public static boolean observed(ServerLevel level, AABB box) {
        double cos = Math.cos(Math.toRadians(SPConfig.get().paciente_view_angle));
        Vec3[] pts = {
                new Vec3(box.getCenter().x, box.minY + 0.3, box.getCenter().z),
                box.getCenter(),
                new Vec3(box.getCenter().x, box.maxY - 0.3, box.getCenter().z)};
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box.inflate(64))) {
            if (!p.isAlive() || p.isSpectator() || !SPPlayerData.inSP(p) || DeathManager.isDead(p) || HideManager.isHidden(p)) continue;
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getViewVector(1f);
            for (Vec3 pt : pts) {
                Vec3 v = pt.subtract(eye);
                double len = v.length();
                if (len < 0.01 || len > 64) continue;
                if (v.scale(1 / len).dot(look) < cos) continue;
                HitResult hit = level.clip(new ClipContext(eye, pt, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
                if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(pt) < 0.04) return true;
            }
        }
        return false;
    }

    @Override
    protected void onSequenceEnd() {
        target = null;
        cooldown = 60;
    }

    private static final RawAnimation[] POSE_ANIMS = new RawAnimation[POSES];
    static {
        for (int i = 0; i < POSES; i++) POSE_ANIMS[i] = RawAnimation.begin().thenLoop("pose" + i);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // sin transicion: cada salto es un corte, nunca se le ve moverse
        controllers.add(new AnimationController<>(this, "main", 0, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            st.getController().transitionLength(0);
            return st.setAndContinue(POSE_ANIMS[Math.floorMod(poseId(), POSES)]);
        }));
    }
}
