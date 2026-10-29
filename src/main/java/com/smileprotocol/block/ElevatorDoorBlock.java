package com.smileprotocol.block;

import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Hoja de puerta de ascensor (dos bloques de alto). La abre y cierra el servidor segun el
 * estado del ascensor (README 6.2): abierta no tiene colision. La hoja se desliza animada
 * con GeckoLib ({@link ElevatorDoorBlockEntity}).
 * {@code FACING} es hacia donde mira la cara exterior (hacia el lobby).
 */
public class ElevatorDoorBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape NS = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape EW = Block.box(7, 0, 0, 9, 16, 16);

    public ElevatorDoorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, OPEN);
    }

    private static VoxelShape closedShape(BlockState s) {
        return s.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(OPEN) ? Shapes.empty() : closedShape(s);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(OPEN) ? Shapes.empty() : closedShape(s);
    }

    /** La hoja la dibuja GeckoLib desde la mitad inferior; la superior no se dibuja. */
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ElevatorDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModBlockEntities.ELEVATOR_DOOR.get()) return null;
        return (lvl, pos, st, be) -> ElevatorDoorBlockEntity.clientTick(lvl, pos, st, (ElevatorDoorBlockEntity) be);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOther = dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP);
        if (towardsOther && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Abre o cierra la hoja completa a partir de su mitad inferior. */
    public static void setOpen(Level level, BlockPos lower, boolean open) {
        for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ElevatorDoorBlock && s.getValue(OPEN) != open) {
                level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
