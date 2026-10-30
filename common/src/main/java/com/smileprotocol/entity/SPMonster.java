package com.smileprotocol.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base de los mobs de la partida (README 20): no hacen dano a golpes (su amenaza es el agarre,
 * el derribo y el remate), son inmunes a los jugadores y al entorno, no se empujan ni
 * desaparecen solos. Solo los quita la limpieza de la zona o un {@code /kill}.
 */
public abstract class SPMonster extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Secuencia de remate o muerte en marcha (ordinal + 1; 0 = ninguna), para la animacion. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> SEQUENCE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SPMonster.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** Victima de la secuencia (id de entidad), para dibujar lo que lleva en la mano. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SEQUENCE_VICTIM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SPMonster.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** Cliente: ultima secuencia animada, para reiniciar la animacion al empezar otra. */
    private int animatedSequence;
    /** Cliente: tick en que empezo la secuencia que se esta animando. */
    private int sequenceStartTick;

    protected SPMonster(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SEQUENCE, (byte) 0);
        b.define(SEQUENCE_VICTIM, -1);
    }

    /** La empieza {@code SequenceManager}: el mob se queda quieto y reproduce su animacion. */
    public void startSequence(com.smileprotocol.sequence.SequenceKind kind, net.minecraft.server.level.ServerPlayer victim) {
        getNavigation().stop();
        entityData.set(SEQUENCE_VICTIM, victim.getId());
        entityData.set(SEQUENCE, (byte) (kind.ordinal() + 1));
    }

    public int sequenceVictim() {
        return entityData.get(SEQUENCE_VICTIM);
    }

    /** Cliente: ticks desde que empezo la animacion de la secuencia. */
    public float sequenceAge(float pt) {
        return tickCount - sequenceStartTick + pt;
    }

    public void endSequence() {
        entityData.set(SEQUENCE, (byte) 0);
        onSequenceEnd();
    }

    /** Despues de rematar o matar (para volver a su IA). */
    protected void onSequenceEnd() {}

    @javax.annotation.Nullable
    public com.smileprotocol.sequence.SequenceKind sequence() {
        int v = entityData.get(SEQUENCE);
        return v <= 0 ? null : com.smileprotocol.sequence.SequenceKind.of(v - 1);
    }

    /**
     * Para los controladores de GeckoLib: si hay una secuencia, su animacion (se reproduce una vez
     * y se queda en el ultimo fotograma). Devuelve null si no hay.
     */
    @javax.annotation.Nullable
    protected software.bernie.geckolib.animation.PlayState sequenceAnimation(
            software.bernie.geckolib.animation.AnimationState<? extends SPMonster> st) {
        com.smileprotocol.sequence.SequenceKind k = sequence();
        int id = k == null ? 0 : k.ordinal() + 1;
        if (id != animatedSequence) {
            animatedSequence = id;
            sequenceStartTick = tickCount;
            st.getController().forceAnimationReset();
            // sin transicion: los golpes caen a la vez que el sonido y la sacudida de la victima
            st.getController().transitionLength(k != null ? 0 : 4);
        }
        if (k == null) return null;
        return st.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold(k.anim));
    }

    /** Id de su ficha en {@code data/smileprotocol/mobs}. */
    public abstract String defId();

    public MobDef def() {
        return level() instanceof ServerLevel sl ? MobDefs.get(sl.getServer(), defId()) : MobDef.berserkerDefaults();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Solo /kill y el vacio
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {
        // nadie lo mueve
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof Player;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
