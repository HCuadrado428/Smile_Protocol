package com.smileprotocol.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.hide.HideType;
import com.smileprotocol.hide.HideTypes;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Escondite (README 15): taquilla, armario, bajo camilla, cortina o contenedor. Los altos ocupan
 * dos bloques. El jugador entra con interaccion mantenida y queda anclado dentro; la forma es
 * hueca para que quepa sin asfixiarse.
 */
public class HideBlock extends HorizontalDirectionalBlock implements SPInteractable {

    public enum Kind { ENCLOSED, LOW, CURTAIN }

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public static final MapCodec<HideBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("hide_type").forGetter(b -> b.typeId),
            Codec.STRING.fieldOf("kind").forGetter(b -> b.kind.name()),
            propertiesCodec()).apply(i, (t, k, p) -> new HideBlock(p, t, Kind.valueOf(k))));

    private static final VoxelShape HOLLOW_LOWER = Shapes.or(
            box(0, 0, 0, 16, 1, 16), box(0, 0, 0, 16, 16, 2), box(0, 0, 14, 16, 16, 16),
            box(0, 0, 0, 2, 16, 16), box(14, 0, 0, 16, 16, 16));
    private static final VoxelShape HOLLOW_UPPER = Shapes.or(
            box(0, 15, 0, 16, 16, 16), box(0, 0, 0, 16, 16, 2), box(0, 0, 14, 16, 16, 16),
            box(0, 0, 0, 2, 16, 16), box(14, 0, 0, 16, 16, 16));
    private static final VoxelShape GURNEY = Shapes.or(
            box(0, 10, 0, 16, 13, 16), box(0, 0, 0, 1, 10, 1), box(15, 0, 0, 16, 10, 1),
            box(0, 0, 15, 1, 10, 16), box(15, 0, 15, 16, 10, 16));
    private static final VoxelShape CURTAIN_NS = box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape CURTAIN_EW = box(7, 0, 0, 9, 16, 16);

    private final String typeId;
    private final Kind kind;

    public HideBlock(Properties props, String typeId, Kind kind) {
        super(props);
        this.typeId = typeId;
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
    }

    public String typeId() {
        return typeId;
    }

    public boolean isTall() {
        return kind != Kind.LOW;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (isTall() && !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (isTall()) level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (isTall()) {
            DoubleBlockHalf half = state.getValue(HALF);
            if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
                if (!neighbor.is(this) || neighbor.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
                return state.setValue(OPEN, neighbor.getValue(OPEN)).setValue(FACING, neighbor.getValue(FACING));
            }
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (kind) {
            case LOW -> GURNEY;
            case CURTAIN -> state.getValue(FACING).getAxis() == Direction.Axis.Z ? CURTAIN_NS : CURTAIN_EW;
            default -> state.getValue(HALF) == DoubleBlockHalf.LOWER ? HOLLOW_LOWER : HOLLOW_UPPER;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return kind == Kind.CURTAIN ? Shapes.empty() : getShape(state, level, pos, ctx);
    }

    @Override
    public BlockPos root(Level level, BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Abre o cierra la puerta del escondite (las dos mitades). */
    public static void setOpen(Level level, BlockPos root, boolean open) {
        BlockState st = level.getBlockState(root);
        if (!(st.getBlock() instanceof HideBlock hb) || st.getValue(OPEN) == open) return;
        level.setBlock(root, st.setValue(OPEN, open), Block.UPDATE_CLIENTS);
        if (hb.isTall()) {
            BlockState up = level.getBlockState(root.above());
            if (up.is(hb)) level.setBlock(root.above(), up.setValue(OPEN, open), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        String name = Component.translatable("smileprotocol.hide." + typeId).getString();
        if (level.isClientSide) {
            return InteractionSpec.of(Component.translatable("smileprotocol.hide.enter", name), 16, 0,
                    PoseCalculator.INTERACT_DOOR);
        }
        ServerLevel sl = (ServerLevel) level;
        HideType type = HideTypes.get(sl.getServer(), typeId);
        if (HideManager.occupants(pos) >= type.capacity()) {
            return InteractionSpec.blocked(Component.translatable("smileprotocol.hide.full"));
        }
        // El ruido se emite al entrar, no mientras se mantiene
        return InteractionSpec.of(Component.translatable("smileprotocol.hide.enter", name), type.enterTime(), 0,
                PoseCalculator.INTERACT_DOOR);
    }

    @Override
    public void onProgress(ServerLevel level, BlockPos pos, BlockState state, boolean active) {
        if (kind == Kind.ENCLOSED) setOpen(level, pos, active);
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        HideManager.enter(player, pos, HideTypes.get(level.getServer(), typeId), this);
    }

    public Kind kind() {
        return kind;
    }

    public static String kindName(Kind k) {
        return k.name().toLowerCase(Locale.ROOT);
    }
}
