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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lamina pegada a una pared: huellas, manchas y aranazos ({@code wall_decal}) o pintadas de dos
 * bloques de ancho ({@code wall_writing}, con {@code right} para la mitad derecha). FACING es
 * hacia donde mira el dibujo (la pared queda detras).
 */
public class WallDecalBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<WallDecalBlock> CODEC = simpleCodec(p -> new WallDecalBlock(p, false));
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 7);
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    private static final VoxelShape NORTH = Block.box(0, 0, 15.75, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 0.25);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 0.25, 16, 16);
    private static final VoxelShape WEST = Block.box(15.75, 0, 0, 16, 16, 16);

    private final boolean writing;

    public WallDecalBlock(BlockBehaviour.Properties props, boolean writing) {
        super(props);
        this.writing = writing;
        BlockState s = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0);
        if (s.hasProperty(RIGHT)) s = s.setValue(RIGHT, false);
        registerDefaultState(s);
    }

    /** Las pintadas necesitan la propiedad RIGHT: se pide antes de que exista el campo. */
    private static final ThreadLocal<Boolean> BUILDING_WRITING = ThreadLocal.withInitial(() -> false);

    public static WallDecalBlock writing(BlockBehaviour.Properties props) {
        BUILDING_WRITING.set(true);
        try {
            return new WallDecalBlock(props, true);
        } finally {
            BUILDING_WRITING.set(false);
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, VARIANT);
        if (BUILDING_WRITING.get()) b.add(RIGHT);
    }

    public boolean isWriting() {
        return writing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getClickedFace().getAxis().isHorizontal() ? ctx.getClickedFace() : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, f).setValue(VARIANT, ctx.getLevel().random.nextInt(8));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }
}
