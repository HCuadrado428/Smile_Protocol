package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mueble de decoracion del laboratorio (fase 24, {@link FurnitureDefs}): modelo de bloque normal
 * (sin entidad de bloque), con su caja de colision girada segun hacia donde mira.
 */
public class FurnitureBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<FurnitureBlock> CODEC = simpleCodec(p -> new FurnitureBlock(p, new int[]{0, 0, 0, 16, 16, 16}));

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public FurnitureBlock(BlockBehaviour.Properties props, int[] box) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
        // caja mirando al sur (frente +Z); se gira para el resto
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        shapes.put(Direction.SOUTH, Block.box(x0, y0, z0, x1, y1, z1));
        shapes.put(Direction.NORTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        shapes.put(Direction.EAST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
        shapes.put(Direction.WEST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }
}
