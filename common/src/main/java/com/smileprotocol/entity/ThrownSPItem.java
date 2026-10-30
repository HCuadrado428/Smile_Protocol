package com.smileprotocol.entity;

import com.smileprotocol.interact.InteractionManager;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lata o botella lanzada (README 12.7): no hace dano; al caer hace ruido en ese punto (los mobs van
 * a investigar) con un sonido segun lo que golpea. La botella se rompe; la lata rebota y queda en
 * el suelo para volver a cogerla.
 */
public class ThrownSPItem extends ThrowableItemProjectile {

    private float noise = 10f;
    private boolean breaks;

    public ThrownSPItem(EntityType<? extends ThrownSPItem> type, Level level) {
        super(type, level);
    }

    public ThrownSPItem(EntityType<? extends ThrownSPItem> type, LivingEntity owner, Level level, float noise, boolean breaks) {
        super(type, owner, level);
        this.noise = noise;
        this.breaks = breaks;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CAN.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        Vec3 at = hit.getLocation();
        // sonido del golpe segun la superficie (cristal, metal, piedra...) y el del propio objeto
        if (hit instanceof BlockHitResult bh) {
            BlockState st = sl.getBlockState(bh.getBlockPos());
            SoundType t = st.getSoundType();
            sl.playSound(null, bh.getBlockPos(), t.getHitSound(), SoundSource.BLOCKS, 1.0f, t.getPitch() * 0.9f);
        }
        if (breaks) {
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.9f + random.nextFloat() * 0.2f);
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), at.x, at.y + 0.1, at.z, 14, 0.15, 0.1, 0.15, 0.12);
        } else {
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.LANTERN_FALL, SoundSource.PLAYERS, 1.2f, 1.6f);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.8f, 1.8f);
            Vec3 back = getDeltaMovement().normalize().scale(-0.25);
            InteractionManager.spawnItem(sl, at.add(back.x, 0.05, back.z), getItem().copy());
        }
        NoiseSystem.emit(sl, at, noise, NoiseType.THROW_IMPACT, getOwner() instanceof LivingEntity le ? le : null);
        com.smileprotocol.debug.SPDebug.log("lanzable cae en " + net.minecraft.core.BlockPos.containing(at).toShortString() + " ruido " + noise);
        discard();
    }
}
