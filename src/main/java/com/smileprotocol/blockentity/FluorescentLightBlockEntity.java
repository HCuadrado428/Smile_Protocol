package com.smileprotocol.blockentity;

import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Entidad de bloque del fluorescente. Solo sirve en el cliente: mantiene la luz dinamica de
 * Veil del fluorescente (README 21.1) y la apaga y enciende con el parpadeo.
 */
public class FluorescentLightBlockEntity extends BlockEntity {

    public FluorescentLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUORESCENT_LIGHT.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FluorescentLightBlockEntity be) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.smileprotocol.client.light.DynamicLights.tickFluorescent(be));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.smileprotocol.client.light.DynamicLights.remove(worldPosition));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.smileprotocol.client.light.DynamicLights.remove(worldPosition));
        }
    }
}
