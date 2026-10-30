package com.smileprotocol.entity;

import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModItems;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Bengala encendida en el suelo (README 10.1): luz roja dinamica (Veil, en el cliente), chispas y
 * humo, y cada pocos segundos un ruido que atrae a los mobs hacia ella. Se apaga sola.
 */
public class FlareEntity extends Entity implements ItemSupplier {

    private static final EntityDataAccessor<Integer> LEFT = SynchedEntityData.defineId(FlareEntity.class, EntityDataSerializers.INT);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.15f, 0.1f), 0.9f);

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
    }

    public void setBurnTime(int ticks) {
        entityData.set(LEFT, ticks);
    }

    /** Ticks que le quedan (0 = apagada). */
    public int burnLeft() {
        return entityData.get(LEFT);
    }

    /** Intensidad de la luz (parpadea y se apaga al final). */
    public float intensity(float pt) {
        float left = burnLeft();
        float fade = Math.min(1f, left / 60f);
        float t = tickCount + pt;
        float flicker = 0.82f + 0.1f * (float) Math.sin(t * 1.7f) + 0.08f * (float) Math.sin(t * 4.3f + 1f);
        return Math.max(0f, fade * flicker);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(LEFT, 800);
    }

    @Override
    public void tick() {
        super.tick();
        // cae y rueda un poco hasta quedarse quieta
        Vec3 v = getDeltaMovement();
        if (!onGround()) v = v.add(0, -0.04, 0);
        move(MoverType.SELF, v);
        v = getDeltaMovement().multiply(onGround() ? 0.4 : 0.9, 0.98, onGround() ? 0.4 : 0.9);
        setDeltaMovement(v);
        int left = burnLeft();
        if (level().isClientSide) {
            if (left > 0) {
                level().addParticle(RED, getX(), getY() + 0.12, getZ(), 0, 0.02, 0);
                if (random.nextInt(2) == 0) {
                    level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.1, getZ(),
                            random.nextGaussian() * 0.01, 0.03, random.nextGaussian() * 0.01);
                }
                if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0, 0.04, 0);
                if (random.nextInt(6) == 0) {
                    level().addParticle(ParticleTypes.LAVA, getX(), getY() + 0.1, getZ(), 0, 0, 0);
                }
            }
            return;
        }
        if (left <= 0) {
            if (tickCount % 20 == 0 && random.nextInt(4) == 0) discard();
            return;
        }
        entityData.set(LEFT, left - 1);
        if (tickCount % 20 == 0) {
            level().playSound(null, blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.9f, 1.5f);
        }
        // atrae a los mobs: ruido medio cada 2 s mientras arde
        if (tickCount % 40 == 0 && level() instanceof ServerLevel sl) {
            NoiseSystem.emit(sl, position(), com.smileprotocol.config.SPConfig.get().noise_flare, NoiseType.ITEM, null);
        }
        if (left == 1) level().playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.FLARE.get());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBurnTime(tag.getInt("Burn"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Burn", burnLeft());
    }
}
