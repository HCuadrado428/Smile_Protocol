package com.smileprotocol.entity;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.sequence.SequenceManager;
import com.smileprotocol.voice.VoiceManager;
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
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * El Imitador: no se deja ver. Se queda a unos 14 bloques, detras de una pared, y habla con la voz de
 * otro del grupo (frases que ha oido por la voz de proximidad, algo mas graves y con un tartamudeo).
 * Si quien va a mirar llega solo, se le echa encima y lo derriba; si va acompanado o le pillan mirando,
 * se escabulle. Sin Simple Voice Chat, susurra y llama con golpes.
 */
public class Imitador extends SmilerBase {

    public enum State { LURK, MIMIC, HUNT, FLEE }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Imitador.class, EntityDataSerializers.BYTE);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation MIMIC_A = RawAnimation.begin().thenLoop("mimic");
    private static final RawAnimation GRAB = RawAnimation.begin().thenPlay("grab");

    @Nullable private ServerPlayer target;
    private int timer;
    private int nextMimic = 200;
    private int repath;
    private int mimics;
    @Nullable private List<ServerPlayer> cached;
    private boolean cachedSeen;

    public Imitador(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
    }

    @Override
    public String defId() {
        return "imitador";
    }

    public State state() {
        return State.values()[Math.floorMod(entityData.get(STATE), State.values().length)];
    }

    private void setState(State s) {
        if (s != state()) SPDebug.log("imitador: " + s);
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    public int mimics() {
        return mimics;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || sequence() != null) return;
        timer++;
        // rendimiento: la busqueda de jugadores y las miradas, cada 4 ticks (salvo cuando ataca)
        if (cached == null || state() == State.HUNT || tickCount % 4 == 0) {
            cached = players(level);
            cachedSeen = state() != State.HUNT && Paciente.observed(level, getBoundingBox());
        }
        List<ServerPlayer> players = cached;
        if (target == null || !valid(target)) target = players.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) {
            getNavigation().stop();
            return;
        }
        boolean seen = state() != State.HUNT && cachedSeen && nearestDistance(players) < 20;
        if (seen && state() != State.FLEE) {
            flee(level);
            return;
        }
        double d = distanceTo(target);
        switch (state()) {
            case LURK -> {
                if (--nextMimic <= 0 && d < 26) {
                    mimic(level, players);
                    return;
                }
                if (--repath > 0) return;
                repath = 20;
                if (d > 16) getNavigation().moveTo(target, 0.8);
                else if (d < 10) away(target.position(), 8, 0.8);
                else getNavigation().stop();
            }
            case MIMIC -> {
                getNavigation().stop();
                getLookControl().setLookAt(target, 30, 30);
                // quien viene a ver llega solo y cerca: a por el
                if (d < 5 && alone(target, players)) {
                    setState(State.HUNT);
                    level.playSound(null, blockPosition(), ModSounds.SMILEY_LAUGH.get(), SoundSource.HOSTILE, 1f, 1.4f);
                } else if (d < 5) {
                    flee(level);
                } else if (timer > 80) {
                    setState(State.LURK);
                }
            }
            case HUNT -> {
                if (--repath <= 0) {
                    repath = 4;
                    getNavigation().moveTo(target, 1.6);
                }
                if (d < 1.7 && hasLineOfSight(target)) {
                    DownedManager.down(target, "imitador");
                    flee(level);
                } else if (timer > 160) {
                    flee(level);
                }
            }
            case FLEE -> {
                if (getNavigation().isDone() || timer > 100) {
                    setState(State.LURK);
                    nextMimic = 200 + getRandom().nextInt(200);
                }
            }
        }
    }

    private void mimic(ServerLevel level, List<ServerPlayer> players) {
        setState(State.MIMIC);
        mimics++;
        nextMimic = 240 + getRandom().nextInt(160);
        // la voz de otro del grupo (no la del que escucha); si no hay, la de cualquiera
        List<UUID> sources = new ArrayList<>();
        Group g = RunManager.groupOf(target);
        if (g != null) for (UUID id : g.members().keySet()) if (!id.equals(target.getUUID())) sources.add(id);
        boolean voice = VoiceManager.mimic(level, getEyePosition(), sources, 28f)
                || VoiceManager.mimic(level, getEyePosition(), List.of(target.getUUID()), 28f);
        if (!voice) {
            level.playSound(null, blockPosition(), ModSounds.SMILEY_WHISPER.get(), SoundSource.HOSTILE, 1.2f, 1.25f);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.HOSTILE, 0.9f, 0.6f);
        }
        com.smileprotocol.noise.NoiseSystem.emit(level, position(), 4, com.smileprotocol.noise.NoiseType.VOICE, this);
        SPDebug.log("imitador imita" + (voice ? " una voz" : " (sin voz: susurro)") + " cerca de " + target.getGameProfile().getName());
    }

    /** Pruebas: imitar ya. */
    public void forceMimic() {
        if (level() instanceof ServerLevel level) {
            List<ServerPlayer> players = players(level);
            if (target == null && !players.isEmpty()) target = players.get(0);
            if (target != null) mimic(level, players);
        }
    }

    private void flee(ServerLevel level) {
        setState(State.FLEE);
        Vec3 from = target != null ? target.position() : position();
        away(from, 16, 1.4);
    }

    private void away(Vec3 from, int dist, double speed) {
        Vec3 to = LandRandomPos.getPosAway(this, dist, 7, from);
        if (to != null) getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    private boolean alone(ServerPlayer p, List<ServerPlayer> players) {
        for (ServerPlayer o : players) if (o != p && o.distanceTo(p) < 8) return false;
        return true;
    }

    private double nearestDistance(List<ServerPlayer> players) {
        return players.stream().mapToDouble(this::distanceTo).min().orElse(99);
    }

    private List<ServerPlayer> players(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48), this::valid);
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p) && !HideManager.isHidden(p)
                && !SequenceManager.inSequence(p);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case MIMIC -> st.setAndContinue(MIMIC_A);
                case HUNT -> st.setAndContinue(getDeltaMovement().horizontalDistanceSqr() < 1e-4 ? GRAB : RUN);
                case FLEE -> st.setAndContinue(RUN);
                default -> st.setAndContinue(st.isMoving() ? WALK : IDLE);
            };
        }));
    }
}
