package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Fluorescente de techo. Con {@code FLICKERS} parpadea de vez en cuando (README 5.2:
 * «algun fluorescente que parpadea»). Mas adelante los ligados a un circuito se apagaran y
 * encenderan con los fusibles (README 14.1).
 * <p>
 * En el cliente, su entidad de bloque mantiene una luz dinamica de Veil (README 21.1).
 */
public class FluorescentLightBlock extends Block implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FLICKERS = BooleanProperty.create("flickers");

    public static final MapCodec<FluorescentLightBlock> CODEC = simpleCodec(FluorescentLightBlock::new);

    public FluorescentLightBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(FLICKERS, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, FLICKERS);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FLICKERS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextFloat() < 0.35f) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, 2 + random.nextInt(6));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FLICKERS)) return;
        boolean lit = state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, !lit), Block.UPDATE_ALL);
        if (lit) {
            // Apagado: siempre vuelve a encenderse
            level.scheduleTick(pos, this, 2 + random.nextInt(4));
        } else if (random.nextFloat() < 0.5f) {
            // Encendido: a veces vuelve a fallar enseguida (un parpadeo son varios cortes seguidos)
            level.scheduleTick(pos, this, 2 + random.nextInt(4));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluorescentLightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModBlockEntities.FLUORESCENT_LIGHT.get()) return null;
        return (lvl, pos, st, be) -> FluorescentLightBlockEntity.clientTick(lvl, pos, st, (FluorescentLightBlockEntity) be);
    }
}
