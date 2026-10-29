package com.smileprotocol.blockentity;

import com.smileprotocol.block.HideBlock;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Escondite dibujado con GeckoLib (README 15.6): su puerta o tapa sigue el estado OPEN. */
public class HideBlockEntity extends AnimatedBlockEntity {

    public HideBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HIDE.get(), pos, state);
    }

    @Override
    protected boolean targetOpen() {
        BlockState st = getBlockState();
        return st.hasProperty(HideBlock.OPEN) && st.getValue(HideBlock.OPEN);
    }

    @Override
    protected float speed() {
        return 1f / 6f;
    }

    /** Nombre del modelo GeckoLib (el id del bloque). */
    public String modelName() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();
    }
}
