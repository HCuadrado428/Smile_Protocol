package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.smileprotocol.blockentity.VentBlockEntity;
import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.mechanism.LabEvents;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Rejilla de ventilacion en el suelo (README 12.9): tapa la entrada a un conducto de un bloque de
 * alto que pasa por debajo de la planta. Cerrada se pisa; se retira con la interaccion mantenida
 * (2 s, ruido alto), tambien desde dentro del conducto mirando hacia arriba. Una rejilla sellada
 * ({@code sealed}) no se abre hasta que se enciende su circuito.
 */
public class VentGrilleBlock extends HorizontalDirectionalBlock implements EntityBlock, SPInteractable {

    public static final MapCodec<VentGrilleBlock> CODEC = simpleCodec(VentGrilleBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    private static final VoxelShape CLOSED = Block.box(0, 13, 0, 16, 16, 16);
    private static final VoxelShape OPEN_OUTLINE = Block.box(0, 0, 0, 16, 16, 3);
    public static final int OPEN_TICKS = 40;

    public VentGrilleBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(OPEN, false).setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OPEN, SEALED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? rotate(state) : CLOSED;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : CLOSED;
    }

    private static VoxelShape rotate(BlockState state) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 13, 16, 16, 16);
            case EAST -> Block.box(13, 0, 0, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, 3, 16, 16);
            default -> OPEN_OUTLINE;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(OPEN)) return null;
        if (state.getValue(SEALED)) return InteractionSpec.blocked(Component.translatable("smileprotocol.vent.sealed"));
        return InteractionSpec.of(Component.translatable("smileprotocol.vent.open"), OPEN_TICKS, 10, PoseCalculator.INTERACT_PICKUP);
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof VentGrilleBlock) || state.getValue(OPEN) || state.getValue(SEALED)) return;
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 1.6f);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
        LabEvents.fire(level, pos, player, "vent_opened", "");
    }
}
