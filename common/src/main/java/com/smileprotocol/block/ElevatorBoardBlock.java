package com.smileprotocol.block;

import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Pantalla de estado de un ascensor del lobby (README 5.2): «Libre», «2/4 listos»,
 * «En curso», «Cerrado». El texto lo dibuja {@code client.ElevatorBoardRenderer} con lo que
 * envia el servidor. Va pegada a una pared; {@code FACING} es hacia donde mira la pantalla.
 */
public class ElevatorBoardBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SOUTH = Block.box(0, 2, 0, 16, 14, 2);
    private static final VoxelShape NORTH = Block.box(0, 2, 14, 16, 14, 16);
    private static final VoxelShape EAST = Block.box(0, 2, 0, 2, 14, 16);
    private static final VoxelShape WEST = Block.box(14, 2, 0, 16, 14, 16);

    public static final MapCodec<ElevatorBoardBlock> CODEC = simpleCodec(ElevatorBoardBlock::new);

    public ElevatorBoardBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (s.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> SOUTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElevatorBoardBlockEntity(pos, state);
    }
}
