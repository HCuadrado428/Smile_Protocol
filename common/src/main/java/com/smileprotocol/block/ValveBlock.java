package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.mechanism.ValveGroups;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Valvula en la pared (README 14.4). Se gira con el click derecho mantenido mientras se llena la
 * barra y hace ruido. Puede faltarle la rueda ({@code WHEEL = false}), que es un objeto que hay
 * que encontrar. Su grupo puede exigir un orden o girarlas a la vez.
 */
public class ValveBlock extends HorizontalDirectionalBlock implements EntityBlock, SPInteractable {

    public static final MapCodec<ValveBlock> CODEC = simpleCodec(ValveBlock::new);
    public static final BooleanProperty TURNING = BooleanProperty.create("turning");
    public static final BooleanProperty TURNED = BooleanProperty.create("turned");
    public static final BooleanProperty WHEEL = BooleanProperty.create("wheel");

    /** Ticks de giro (unos 3 s). */
    public static final int TURN_TICKS = 60;

    private static final VoxelShape NORTH = box(2, 2, 10, 14, 14, 16);
    private static final VoxelShape SOUTH = box(2, 2, 0, 14, 14, 6);
    private static final VoxelShape WEST = box(10, 2, 2, 16, 14, 14);
    private static final VoxelShape EAST = box(0, 2, 2, 6, 14, 14);

    public ValveBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(TURNING, false).setValue(TURNED, false).setValue(WHEEL, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, TURNING, TURNED, WHEEL);
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

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        if (!state.getValue(WHEEL)) {
            boolean has = SPInventoryOps.find(player, s -> s.getItem() == ModItems.VALVE_WHEEL.get(), level.isClientSide) >= 0;
            return has ? InteractionSpec.of(Component.translatable("smileprotocol.valve.place_wheel"), 20, 3, PoseCalculator.INTERACT_FUSE)
                    : InteractionSpec.blocked(Component.translatable("smileprotocol.valve.no_wheel"));
        }
        if (state.getValue(TURNED)) return InteractionSpec.blocked(Component.translatable("smileprotocol.valve.done"));
        return InteractionSpec.of(Component.translatable("smileprotocol.valve.turn"), TURN_TICKS, 10, PoseCalculator.INTERACT_VALVE);
    }

    @Override
    public void onProgress(ServerLevel level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(WHEEL) && state.getValue(TURNING) != active) {
            level.setBlock(pos, state.setValue(TURNING, active), Block.UPDATE_CLIENTS);
            if (active) level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 0.5f);
        }
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        state = level.getBlockState(pos);
        if (!state.getValue(WHEEL)) {
            int slot = SPInventoryOps.find(player, s -> s.getItem() == ModItems.VALVE_WHEEL.get());
            if (slot < 0) return;
            SPInventoryOps.take(player, slot);
            level.setBlock(pos, state.setValue(WHEEL, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 1.6f);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ValveBlockEntity valve)) return;
        String group = valve.group();
        List<ValveBlockEntity> all = group.isEmpty() ? List.of(valve) : ValveGroups.valves(level, pos, group);

        // En orden: todas las de orden menor tienen que estar giradas (README 14.4)
        if (valve.order() > 0) {
            for (ValveBlockEntity o : all) {
                if (o != valve && o.order() > 0 && o.order() < valve.order() && !ValveGroups.isTurned(o)) {
                    fail(level, pos, player, group, "smileprotocol.valve.wrong_order");
                    return;
                }
            }
        }
        // A la vez: las demas cooperativas tienen que estar girandose o recien giradas
        long now = level.getGameTime();
        if (valve.coop()) {
            for (ValveBlockEntity o : all) {
                if (o == valve || !o.coop()) continue;
                BlockState os = o.getBlockState();
                boolean turning = os.getValue(TURNING);
                boolean recent = ValveGroups.isTurned(o) && now - o.turnedAt() <= 40;
                if (!turning && !recent) {
                    fail(level, pos, player, group, "smileprotocol.valve.together");
                    return;
                }
            }
        }
        level.setBlock(pos, state.setValue(TURNED, true).setValue(TURNING, false), Block.UPDATE_ALL);
        valve.setTurnedAt(now);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.5f);
        LabEvents.fire(level, pos, player, "valve_turned", group);
        if (!group.isEmpty()) ValveGroups.onChanged(level, pos, group);
    }

    private static void fail(ServerLevel level, BlockPos pos, ServerPlayer player, String group, String msg) {
        player.displayClientMessage(Component.translatable(msg).withStyle(ChatFormatting.GOLD), true);
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 16, NoiseType.VALVE, player);
        if (!group.isEmpty()) ValveGroups.resetGroup(level, pos, group);
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
