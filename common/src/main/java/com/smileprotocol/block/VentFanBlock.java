package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.smileprotocol.blockentity.VentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Ventilador de un conducto (README 12.9): mientras gira bloquea el paso (y suena). Lo mueve su
 * circuito ({@code Circuit}; con {@code Brake}, el circuito lo frena). Parado se pasa entre las aspas.
 */
public class VentFanBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<VentFanBlock> CODEC = simpleCodec(VentFanBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    private static final VoxelShape THIN_NS = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape THIN_EW = Block.box(7, 0, 0, 9, 16, 16);

    public VentFanBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(RUNNING, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, RUNNING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    private static VoxelShape thin(BlockState st) {
        return st.getValue(FACING).getAxis() == net.minecraft.core.Direction.Axis.Z ? THIN_NS : THIN_EW;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return thin(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(RUNNING) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(RUNNING) && random.nextInt(3) == 0) {
            level.playLocalSound(pos, SoundEvents.MINECART_INSIDE_UNDERWATER, SoundSource.BLOCKS, 0.15f, 2f, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentBlockEntity(pos, state);
    }
}
