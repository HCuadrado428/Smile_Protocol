package com.smileprotocol.entity;

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
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.sequence.SequenceKind;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
import java.util.UUID;

/**
 * Smiley, el jefe (docs/SMILERS.md). Silueta negra de 4 bloques con el ojo derecho y la sonrisa
 * rojos. Se le ve un instante en cada planta ({@link State#CAMEO}) y espera en el Laboratorio Cero:
 * <ul>
 *   <li>Fases: una mas por cada generador encendido ({@link SmileyFight#phase}). 1: lento y
 *   teatral, agarre largo. 2: mas rapido, invoca smilers y apaga luces. 3: a oscuras y furioso,
 *   mata al instante.</li>
 *   <li>Grito que aturde a los que tiene cerca; susurros con el nombre de los jugadores.</li>
 *   <li>Si se cansa de perseguir a alguien, se funde en una mancha de la pared y sale junto a otro.</li>
 *   <li>Retrocede si dos linternas o mas le dan a la vez (o una, si solo queda uno).</li>
 *   <li>Agarre con un minijuego distinto cada vez; si se falla, remate. Al ultimo en pie se lo
 *   lleva a rastras ({@link SequenceKind#SMILEY_DRAG}).</li>
 *   <li>Al llegar el ascensor queda encerrado en su celda ({@link State#SEALED}).</li>
 * </ul>
 */
public class Smiley extends SmilerBase implements QTEManager.Grabber {

    public enum State { STALK, CAMEO, INTRO, SCREAM, SUMMON, SMEAR_IN, HIDDEN, SMEAR_OUT, RETREAT, GRAB, STUNNED, SEALED }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Smiley.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Smiley.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE_A = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_A = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_A = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation INTRO_A = RawAnimation.begin().thenPlayAndHold("intro");
    private static final RawAnimation SCREAM_A = RawAnimation.begin().thenPlayAndHold("scream");
    private static final RawAnimation SUMMON_A = RawAnimation.begin().thenPlayAndHold("summon");
    private static final RawAnimation RETREAT_A = RawAnimation.begin().thenLoop("retreat");
    private static final RawAnimation SMEAR_IN_A = RawAnimation.begin().thenPlayAndHold("smear_in");
    private static final RawAnimation SMEAR_OUT_A = RawAnimation.begin().thenPlayAndHold("smear_out");
    private static final RawAnimation GRAB_A = RawAnimation.begin().thenLoop("grab");
    private static final RawAnimation SEALED_A = RawAnimation.begin().thenPlayAndHold("sealed");

    /** Minijuegos del agarre, uno distinto cada vez. */
    private static final QTEType[] QTES = {QTEType.MASH, QTEType.SEQUENCE, QTEType.TIMING, QTEType.HOLD};

    public static final int INTRO_TICKS = 100;

    @Nullable private ServerPlayer target;
    @Nullable private ServerPlayer hidingVictim;
    @Nullable private BlockPos hideSpot;
    @Nullable private Vec3 emergeAt;
    private int timer;
    private int cooldown;
    private int chase;
    private double bestDist = Double.MAX_VALUE;
    private int screamCd = 200, summonCd = 600, lightsCd = 300, whisperCd = 160;
    private int grabs;
    private int unlit;
    private int seen;
    private int dragTicks;
    private boolean vanish;
    private boolean forced;
    private final List<UUID> summoned = new ArrayList<>();

    public Smiley(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    public boolean canBeStunned() {
        return false;
    }

    @Override
    public String defId() {
        return "smiley";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, (byte) 0);
        b.define(PHASE, (byte) 1);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setState(State s) {
        if (s != state()) SPDebug.log("smiley " + state() + " -> " + s);
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Pruebas y grabaciones: fija el estado y la fase sin IA. */
    public void debugForce(State s, int phase) {
        forced = true;
        entityData.set(STATE, (byte) s.ordinal());
        entityData.set(PHASE, (byte) phase);
    }

    public void debugRelease() {
        forced = false;
        timer = 0;
    }

    public void startIntro() {
        setState(State.INTRO);
        entityData.set(PHASE, (byte) 1);
    }

    public void startCameo() {
        setState(State.CAMEO);
        entityData.set(PHASE, (byte) 0);
    }

    /** Derrota: queda encerrado en su celda, con el ojo apagado y la sonrisa encendida. */
    public void seal(Vec3 at) {
        getNavigation().stop();
        setInvisible(false);
        moveTo(at.x, at.y, at.z, getYRot(), 0f);
        setState(State.SEALED);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || forced) return;
        SequenceKind seq = sequence();
        if (seq != null) {
            if (seq.drag) drag(level);
            return;
        }
        timer++;
        if (cooldown > 0) cooldown--;
        if (screamCd > 0) screamCd--;
        if (summonCd > 0) summonCd--;
        if (lightsCd > 0) lightsCd--;
        if (whisperCd > 0) whisperCd--;
        State s = state();
        if (s == State.CAMEO) {
            cameo(level);
            return;
        }
        if (s == State.SEALED) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (tickCount % 20 == 0 && s != State.INTRO) updatePhase(level);
        int phase = phase();

        // Luz: si le dan bastantes linternas a la vez, retrocede
        if (s != State.INTRO && s != State.GRAB && s != State.HIDDEN && s != State.SMEAR_IN && s != State.SMEAR_OUT) {
            List<ServerPlayer> lit = lighters(level);
            if (!lit.isEmpty() && lit.size() >= neededLights(level)) {
                unlit = 0;
                if (s != State.RETREAT) {
                    level.playSound(null, blockPosition(), ModSounds.SMILEY_SCREAM.get(), SoundSource.HOSTILE, 0.8f, 1.6f);
                    setState(State.RETREAT);
                    s = State.RETREAT;
                }
                Vec3 away = Vec3.ZERO;
                for (ServerPlayer p : lit) away = away.add(position().subtract(p.position()).multiply(1, 0, 1).normalize());
                Vec3 to = position().add(away.normalize().scale(4));
                if (timer % 10 == 0) getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
        }

        switch (s) {
            case INTRO -> {
                getNavigation().stop();
                ServerPlayer near = nearest(level, 64);
                if (near != null) getLookControl().setLookAt(near, 10, 10);
                if (timer == 34) level.playSound(null, blockPosition(), ModSounds.SMILEY_WHISPER.get(), SoundSource.HOSTILE, 2f, 0.7f);
                if (timer == 70) level.playSound(null, blockPosition(), ModSounds.SMILEY_LAUGH.get(), SoundSource.HOSTILE, 2f, 0.8f);
                if (timer >= INTRO_TICKS) {
                    SmileyFight.onIntroEnd(level, this);
                    setState(State.STALK);
                }
            }
            case SCREAM -> {
                getNavigation().stop();
                if (timer == 6) scream(level);
                if (timer >= 40) setState(State.STALK);
            }
            case SUMMON -> {
                getNavigation().stop();
                if (timer == 20) summon(level);
                if (timer >= 40) setState(State.STALK);
            }
            case SMEAR_IN -> {
                getNavigation().stop();
                if (timer % 3 == 0) smearParticles(level, position(), 14);
                if (timer == 2) level.playSound(null, blockPosition(), ModSounds.SMILEY_SMEAR.get(), SoundSource.HOSTILE, 1.2f, 0.8f);
                if (timer >= 20) {
                    if (vanish) {
                        discard();
                        return;
                    }
                    emergeAt = pickEmerge(level);
                    if (emergeAt == null) {
                        setState(State.STALK);
                        return;
                    }
                    setInvisible(true);
                    noPhysics = true;
                    setState(State.HIDDEN);
                }
            }
            case HIDDEN -> {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                if (emergeAt != null && timer % 4 == 0) smearParticles(level, emergeAt, 6);
                if (timer == 20 && emergeAt != null) {
                    level.playSound(null, BlockPos.containing(emergeAt), ModSounds.SMILEY_SMEAR.get(), SoundSource.HOSTILE, 1.4f, 0.6f);
                }
                if (timer >= 30) {
                    if (emergeAt != null) moveTo(emergeAt.x, emergeAt.y, emergeAt.z, getYRot(), 0f);
                    ServerPlayer near = nearest(level, 32);
                    if (near != null) faceTowards(near.position());
                    setInvisible(false);
                    noPhysics = false;
                    chase = 0;
                    bestDist = Double.MAX_VALUE;
                    setState(State.SMEAR_OUT);
                }
            }
            case SMEAR_OUT -> {
                getNavigation().stop();
                if (timer >= 20) setState(State.STALK);
            }
            case RETREAT -> {
                if (lighters(level).size() < neededLights(level)) unlit++;
                if (unlit > 20) {
                    unlit = 0;
                    // harto de la luz: se va por la pared, o vuelve a por ellos
                    setState(random.nextBoolean() ? State.SMEAR_IN : State.STALK);
                }
            }
            case GRAB -> {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                if (timer > 400) setState(State.STALK);
            }
            case STUNNED -> {
                getNavigation().stop();
                if (timer > 40) {
                    cooldown = 40;
                    setState(State.STALK);
                }
            }
            case STALK -> stalk(level, phase);
            default -> { }
        }
    }

    private void stalk(ServerLevel level, int phase) {
        SPConfig.Values cfg = SPConfig.get();
        // Sacar de su escondite a quien se escondio delante de el
        if (hideSpot != null && hidingVictim != null) {
            if (!HideManager.isHidden(hidingVictim) || !hideSpot.equals(HideManager.spotOf(hidingVictim))) {
                hideSpot = null;
                hidingVictim = null;
            } else {
                if (timer % 10 == 0) getNavigation().moveTo(hideSpot.getX() + 0.5, hideSpot.getY(), hideSpot.getZ() + 0.5, speed(phase));
                if (position().distanceTo(Vec3.atBottomCenterOf(hideSpot)) < 2.6) {
                    HideManager.forceExit(hidingVictim, true);
                    target = hidingVictim;
                    hideSpot = null;
                    hidingVictim = null;
                    cooldown = 0;
                }
                return;
            }
        }
        if (target != null && HideManager.isHidden(target) && distanceTo(target) < 12 && HideManager.spotOf(target) != null) {
            hidingVictim = target;
            hideSpot = HideManager.spotOf(target);
            target = null;
            return;
        }
        if (target == null || !valid(target) || timer % 40 == 0) {
            ServerPlayer t = nearest(level, 64);
            if (t != target) {
                chase = 0;
                bestDist = Double.MAX_VALUE;
            }
            target = t;
        }
        if (whisperCd == 0) {
            whisper(level);
            whisperCd = 500 + random.nextInt(300);
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        double dist = distanceTo(target);
        getLookControl().setLookAt(target, 30, 30);
        if (phase >= 2 && lightsCd == 0) {
            SmileyFight.darken(level, target.blockPosition(), 12, cfg.smiley_dark_ticks);
            level.playSound(null, target.blockPosition(), ModSounds.EVENT_SURGE.get(), SoundSource.BLOCKS, 0.8f, 0.5f);
            lightsCd = cfg.smiley_lights_cooldown;
        }
        if (phase >= 2 && summonCd == 0 && dist > 6) {
            summonCd = cfg.smiley_summon_cooldown;
            setState(State.SUMMON);
            return;
        }
        if (screamCd == 0 && dist < 10 && hasLineOfSight(target)) {
            screamCd = phase == 1 ? cfg.smiley_scream_cooldown : cfg.smiley_scream_cooldown * 2 / 3;
            setState(State.SCREAM);
            return;
        }
        if (timer % 8 == 0) getNavigation().moveTo(target, speed(phase));
        // Se cansa si no le gana terreno: se va por la pared y sale junto a otro
        if (dist < bestDist - 0.5) {
            bestDist = dist;
            chase = 0;
        } else if (++chase > cfg.smiley_chase_ticks || (dist > 30 && timer % 60 == 59)) {
            chase = 0;
            setState(State.SMEAR_IN);
            return;
        }
        if (cooldown == 0 && dist < 2.4 && Math.abs(target.getY() - getY()) < 1.6) catchPlayer(target, phase);
    }

    private double speed(int phase) {
        List<Double> s = SPConfig.get().smiley_speed;
        return s.isEmpty() ? 1.0 : s.get(Mth.clamp(phase - 1, 0, s.size() - 1));
    }

    private void catchPlayer(ServerPlayer p, int phase) {
        getNavigation().stop();
        if (phase >= 3) {
            if (!SequenceManager.start(this, p, killSequenceFor(p))) cooldown = 10;
            return;
        }
        List<Integer> t = SPConfig.get().smiley_qte_ticks;
        int ticks = t.isEmpty() ? 120 : t.get(Mth.clamp(phase - 1, 0, t.size() - 1));
        if (QTEManager.startGrab(this, p, nextQte(), ticks, "DEATH")) {
            grabs++;
            setState(State.GRAB);
        } else {
            cooldown = 30;
        }
    }

    /** Minijuego del proximo agarre (cambia cada vez). */
    public QTEType nextQte() {
        return QTES[grabs % QTES.length];
    }

    public void debugGrabbed() {
        grabs++;
    }

    /** Remate normal, o arrastrar al ultimo que queda en pie. */
    @Override
    public SequenceKind killSequenceFor(ServerPlayer p) {
        return lastStanding(p) ? SequenceKind.SMILEY_DRAG : SequenceKind.SMILEY_KILL;
    }

    /** Nadie mas del grupo sigue vivo y en pie. */
    public static boolean lastStanding(ServerPlayer p) {
        Group g = RunManager.groupOf(p);
        if (g == null) return true;
        for (UUID id : g.activeMembers()) {
            if (id.equals(p.getUUID())) continue;
            ServerPlayer o = p.server.getPlayerList().getPlayer(id);
            if (o != null && o.isAlive() && SPPlayerData.inSP(o) && !o.isSpectator() && !DeathManager.isDead(o)
                    && !DownedManager.isDowned(o)) return false;
        }
        return true;
    }

    /** Arrastra a la victima hacia la oscuridad; al final, la risa. */
    private void drag(ServerLevel level) {
        dragTicks++;
        if (dragTicks == 1) {
            Vec3 far = SmileyFight.farPoint(level, this);
            if (far != null) getNavigation().moveTo(far.x, far.y, far.z, 0.45);
        }
        if (dragTicks % 30 == 0 && getNavigation().isDone()) {
            Vec3 far = SmileyFight.farPoint(level, this);
            if (far != null) getNavigation().moveTo(far.x, far.y, far.z, 0.45);
        }
        if (dragTicks == 100) level.playSound(null, blockPosition(), ModSounds.SMILEY_LAUGH.get(), SoundSource.HOSTILE, 2.5f, 0.85f);
    }

    @Override
    protected void onSequenceEnd() {
        dragTicks = 0;
        target = null;
        cooldown = 60;
        setState(State.STALK);
    }

    private void updatePhase(ServerLevel level) {
        int now = SmileyFight.phase(level, blockPosition());
        int before = phase();
        if (now == before) return;
        entityData.set(PHASE, (byte) now);
        SPDebug.log("smiley: fase " + now);
        if (now > before) {
            SmileyFight.onPhase(level, this, now);
            screamCd = 0;
            if (state() == State.STALK) setState(State.SCREAM);
        }
    }

    /** Grito: aturde (lento y a ciegas) a los que estan cerca y le ven. */
    private void scream(ServerLevel level) {
        level.playSound(null, blockPosition(), ModSounds.SMILEY_SCREAM.get(), SoundSource.HOSTILE, 3f, 0.9f);
        List<ServerPlayer> hit = new ArrayList<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(14), this::valid)) {
            if (!hasLineOfSight(p)) continue;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
            p.setSprinting(false);
            hit.add(p);
        }
        com.smileprotocol.labevent.LabActions.scare(hit, com.smileprotocol.labevent.LabActions.SCARE_SHAKE, 1.3f);
    }

    /** Invoca a 1 o 2 smilers (mas con grupos grandes) lejos de los jugadores. */
    private void summon(ServerLevel level) {
        summoned.removeIf(id -> !(level.getEntity(id) instanceof SPMonster m) || !m.isAlive());
        int max = SPConfig.get().smiley_summon_max;
        Group g = target != null ? RunManager.groupOf(target) : null;
        int n = 1 + (g != null && g.difficulty().groupSize() >= 3 ? 1 : 0) + (phase() >= 3 ? 1 : 0);
        level.playSound(null, blockPosition(), ModSounds.SMILEY_LAUGH.get(), SoundSource.HOSTILE, 2f, 0.6f);
        for (int i = 0; i < n && summoned.size() < max; i++) {
            BlockPos at = SmileyFight.summonSpot(level, this, random);
            if (at == null) break;
            var type = switch (random.nextInt(3)) {
                case 0 -> com.smileprotocol.registry.ModEntities.RASTREADOR.get();
                case 1 -> com.smileprotocol.registry.ModEntities.APAGON.get();
                default -> com.smileprotocol.registry.ModEntities.BERSERKER.get();
            };
            SPMonster m = MobManager.spawn(level, type, at, random);
            if (m != null) {
                summoned.add(m.getUUID());
                smearParticles(level, Vec3.atBottomCenterOf(at), 20);
                SPDebug.log("smiley invoca " + m.defId() + " en " + at.toShortString());
            }
        }
    }

    /** Susurro con el nombre de un jugador (solo lo oye el). */
    private void whisper(ServerLevel level) {
        List<ServerPlayer> all = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64), this::valid);
        if (all.isEmpty()) return;
        ServerPlayer p = all.get(random.nextInt(all.size()));
        int n = random.nextInt(6);
        p.displayClientMessage(Component.translatable("smileprotocol.smiley.whisper." + n, p.getGameProfile().getName())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        p.playNotifySound(ModSounds.SMILEY_WHISPER.get(), SoundSource.HOSTILE, 0.9f, 0.8f + random.nextFloat() * 0.2f);
    }

    /** Se le ve un momento en una planta: se queda mirando y, en cuanto le miran un rato o se acercan, se va. */
    private void cameo(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        ServerPlayer near = nearest(level, 64);
        if (near != null) getLookControl().setLookAt(near, 10, 10);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48), this::valid)) {
            if (p.distanceTo(this) < 7) seen += 20;
            Vec3 v = getBoundingBox().getCenter().add(0, 1, 0).subtract(p.getEyePosition());
            if (v.normalize().dot(p.getViewVector(1f)) > Math.cos(Math.toRadians(12)) && p.hasLineOfSight(this)) seen++;
        }
        if (seen > 14 || timer > 240) {
            vanish = true;
            setState(State.SMEAR_IN);
        }
    }

    private void smearParticles(ServerLevel level, Vec3 at, int n) {
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.8, at.z, n, 0.35, 1.2, 0.35, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.5, at.z, n / 2, 0.3, 1.0, 0.3, 0.0);
    }

    /** Sale junto a otro jugador (si hay), por detras y a unos bloques. */
    @Nullable
    private Vec3 pickEmerge(ServerLevel level) {
        List<ServerPlayer> all = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64), this::valid));
        if (all.isEmpty()) return null;
        if (all.size() > 1 && target != null) all.remove(target);
        ServerPlayer p = all.get(random.nextInt(all.size()));
        target = p;
        Vec3 back = p.getViewVector(1f).multiply(-1, 0, -1).normalize();
        for (int i = 0; i < 30; i++) {
            double ang = (random.nextDouble() - 0.5) * (i < 15 ? 1.6 : 6.3);
            double r = 3 + random.nextDouble() * 3;
            Vec3 dir = back.yRot((float) ang);
            Vec3 at = new Vec3(Math.floor(p.getX() + dir.x * r) + 0.5, Math.floor(p.getY()), Math.floor(p.getZ() + dir.z * r) + 0.5);
            if (standable(level, at) && clearPath(level, at.add(0, 1.5, 0), p.getEyePosition())) return at;
        }
        return null;
    }

    private boolean standable(ServerLevel level, Vec3 at) {
        BlockPos below = BlockPos.containing(at).below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        AABB box = getType().getDimensions().makeBoundingBox(at);
        return level.noCollision(this, box);
    }

    private boolean clearPath(ServerLevel level, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void faceTowards(Vec3 to) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - getX()), to.z - getZ()));
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    /** Linternas que le dan ahora (con linea de vision). */
    public List<ServerPlayer> lighters(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        double cos = Math.cos(Math.toRadians(14));
        Vec3 c = getBoundingBox().getCenter();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(20))) {
            if (!p.isAlive() || p.isSpectator() || !SPPlayerData.inSP(p) || !Flashlights.isOn(p)) continue;
            Vec3 v = c.subtract(p.getEyePosition());
            if (v.length() > 20) continue;
            // le da en cualquier punto del cuerpo (es muy alto)
            boolean on = false;
            for (double dy : new double[]{-1.2, 0, 1.2}) {
                if (c.add(0, dy, 0).subtract(p.getEyePosition()).normalize().dot(p.getViewVector(1f)) >= cos) on = true;
            }
            if (on && p.hasLineOfSight(this)) out.add(p);
        }
        return out;
    }

    /** Linternas que hacen falta: las de la ficha, o tantas como jugadores queden cerca. */
    public int neededLights(ServerLevel level) {
        int near = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48), this::valid).size();
        return Math.max(1, Math.min(SPConfig.get().smiley_retreat_lights, near));
    }

    @Nullable
    private ServerPlayer nearest(ServerLevel level, double range) {
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range), this::valid).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !HideManager.isHidden(p) && !SequenceManager.inSequence(p) && !QTEManager.isGrabbed(p)
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p);
    }

    @Override
    public void onGrabStart(ServerPlayer p) {
        setState(State.GRAB);
    }

    @Override
    public void onGrabEnd(ServerPlayer p, boolean escaped) {
        target = null;
        if (escaped) {
            level().playSound(null, blockPosition(), ModSounds.SMILEY_SCREAM.get(), SoundSource.HOSTILE, 1.2f, 1.4f);
            setState(State.STUNNED);
        } else {
            cooldown = 60;
            setState(State.STALK);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            PlayState seq = sequenceAnimation(st);
            if (seq != null) return seq;
            return switch (state()) {
                case INTRO -> st.setAndContinue(INTRO_A);
                case SCREAM -> st.setAndContinue(SCREAM_A);
                case SUMMON -> st.setAndContinue(SUMMON_A);
                case RETREAT -> st.setAndContinue(RETREAT_A);
                case SMEAR_IN, HIDDEN -> st.setAndContinue(SMEAR_IN_A);
                case SMEAR_OUT -> st.setAndContinue(SMEAR_OUT_A);
                case GRAB, STUNNED -> st.setAndContinue(GRAB_A);
                case SEALED -> st.setAndContinue(SEALED_A);
                case CAMEO -> st.setAndContinue(IDLE_A);
                default -> st.setAndContinue(st.isMoving() ? (phase() >= 3 ? RUN_A : WALK_A) : IDLE_A);
            };
        }));
    }
}
