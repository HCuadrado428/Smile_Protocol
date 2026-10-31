package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.item.FuseItem;
import com.smileprotocol.item.SPItems;
import com.smileprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Caja de fusibles en la pared (README 14.1). Se meten y se sacan fusibles con interaccion mantenida. */
public class FuseBoxBlock extends HorizontalDirectionalBlock implements EntityBlock, SPInteractable {

    public static final MapCodec<FuseBoxBlock> CODEC = simpleCodec(FuseBoxBlock::new);
    public static final IntegerProperty FILLED = IntegerProperty.create("filled", 0, 4);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private static final VoxelShape NORTH = box(2, 1, 12, 14, 15, 16);
    private static final VoxelShape SOUTH = box(2, 1, 0, 14, 15, 4);
    private static final VoxelShape WEST = box(12, 1, 2, 16, 15, 14);
    private static final VoxelShape EAST = box(0, 1, 2, 4, 15, 14);

    public FuseBoxBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILLED, 0).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FILLED, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuseBoxBlockEntity(pos, state);
    }

    private static boolean isFuse(ItemStack s) {
        return s.getItem() instanceof FuseItem;
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level.getBlockEntity(pos) instanceof FuseBoxBlockEntity box)) return null;
        int have = box.fuses().size(), need = box.required();
        int fuseSlot = SPInventoryOps.find(player, FuseBoxBlock::isFuse, level.isClientSide);
        if (fuseSlot >= 0 && !box.isFull()) {
            ItemStack fuse = slotStack(player, fuseSlot, level.isClientSide);
            if (!box.accepts().isEmpty() && fuse.getItem() == ModItems.FUSE.get()
                    && !FuseItem.type(fuse).equals(box.accepts())) {
                return InteractionSpec.blocked(Component.translatable("smileprotocol.fusebox.wrong_type",
                        box.accepts().toUpperCase(java.util.Locale.ROOT)));
            }
            return InteractionSpec.of(Component.translatable("smileprotocol.fusebox.insert", have + 1, need), 30, 4,
                    PoseCalculator.INTERACT_FUSE);
        }
        if (have > 0) {
            boolean full = level.isClientSide ? clientFull() : SPInventoryOps.isFull((ServerPlayer) player);
            if (full) return InteractionSpec.blocked(Component.translatable("smileprotocol.interact.full"));
            return InteractionSpec.of(Component.translatable("smileprotocol.fusebox.remove", have, need), 25, 4,
                    PoseCalculator.INTERACT_FUSE);
        }
        return InteractionSpec.blocked(Component.translatable("smileprotocol.fusebox.need", have, need));
    }

    private static ItemStack slotStack(Player p, int slot, boolean client) {
        if (client) return com.smileprotocol.interact.ClientInventoryView.slot(slot);
        return SPInventoryOps.inv((ServerPlayer) p).get(slot);
    }

    private static boolean clientFull() {
        for (int i = 0; i < 4; i++) if (com.smileprotocol.interact.ClientInventoryView.slot(i).isEmpty()) return false;
        return true;
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof FuseBoxBlockEntity box)) return;
        int fuseSlot = SPInventoryOps.find(player, FuseBoxBlock::isFuse);
        if (fuseSlot >= 0 && !box.isFull()) {
            ItemStack fuse = SPInventoryOps.take(player, fuseSlot);
            boolean blown = fuse.getItem() instanceof FuseItem f && f.isBlown();
            box.insert(blown ? FuseBoxBlockEntity.BLOWN : FuseItem.type(fuse));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.6f);
            if (blown) level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
            com.smileprotocol.mechanism.LabEvents.fire(level, pos, player, "fuse_inserted", box.circuit());
            com.smileprotocol.score.ScoreManager.onFuseInserted(player);
        } else if (!box.fuses().isEmpty()) {
            String type = box.removeLast();
            ItemStack out = type.equals(FuseBoxBlockEntity.BLOWN) ? new ItemStack(ModItems.FUSE_BLOWN.get())
                    : SPItems.withString(new ItemStack(ModItems.FUSE.get()), FuseItem.TYPE, type);
            SPInventoryOps.give(player, out);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.4f);
            com.smileprotocol.mechanism.LabEvents.fire(level, pos, player, "fuse_removed", box.circuit());
        }
        CircuitManager.onFusesChanged(level, pos, box.circuit());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof com.smileprotocol.blockentity.AnimatedBlockEntity a) {
                com.smileprotocol.blockentity.AnimatedBlockEntity.clientTick(lvl, pos, st, a);
            }
        };
    }
}
