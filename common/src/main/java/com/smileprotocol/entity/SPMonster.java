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

    protected SPMonster(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
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
