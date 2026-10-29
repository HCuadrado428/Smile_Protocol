package com.smileprotocol.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Entidad de bloque dibujada con GeckoLib cuya parte movil (puerta, tapa, rueda...) sigue un
 * estado del bloque con una transicion suave. El movimiento lo aplica su renderer desde codigo.
 */
public abstract class AnimatedBlockEntity extends SyncedBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float progress = -1f;
    private float prevProgress;

    protected AnimatedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Estado al que tiende la animacion (abierta = true). */
    protected abstract boolean targetOpen();

    /** Fraccion por tick (1/8 = 8 ticks). */
    protected float speed() {
        return 1f / 8f;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AnimatedBlockEntity be) {
        float target = be.targetOpen() ? 1f : 0f;
        if (be.progress < 0f) be.progress = target;
        be.prevProgress = be.progress;
        if (be.progress < target) be.progress = Math.min(target, be.progress + be.speed());
        else if (be.progress > target) be.progress = Math.max(target, be.progress - be.speed());
    }

    /** De 0 (cerrada) a 1 (abierta), suavizada. */
    public float openAmount(float pt) {
        float p = progress < 0f ? (targetOpen() ? 1f : 0f) : prevProgress + (progress - prevProgress) * pt;
        return p * p * (3f - 2f * p);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Sin animaciones de archivo: el renderer mueve las piezas
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
