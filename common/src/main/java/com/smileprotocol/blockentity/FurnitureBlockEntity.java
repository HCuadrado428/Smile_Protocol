package com.smileprotocol.blockentity;

import com.smileprotocol.block.SearchableFurnitureBlock;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Mueble que se registra dibujado con GeckoLib: el cajon, las puertas o la tapa se abren con suavidad. */
public class FurnitureBlockEntity extends AnimatedBlockEntity {

    public FurnitureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNITURE.get(), pos, state);
    }

    @Override
    protected boolean targetOpen() {
        BlockState st = getBlockState();
        return st.hasProperty(SearchableFurnitureBlock.OPEN) && st.getValue(SearchableFurnitureBlock.OPEN);
    }

    @Override
    protected float speed() {
        return 1f / 10f;
    }
}
