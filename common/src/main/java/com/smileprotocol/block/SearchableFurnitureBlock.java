package com.smileprotocol.block;

import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.interact.InteractionManager;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.mechanism.SearchLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Mueble que se registra (fase 24): cajones, archivadores, botiquines, carros y cajas. Con la
 * interaccion mantenida se abre (cambia de modelo), hace ruido y suelta el botin de su planta
 * ({@link SearchLoot}); despues queda abierto y vacio. Cada partida coloca la planta de nuevo, asi
 * que el registro es por instancia.
 */
public class SearchableFurnitureBlock extends FurnitureBlock implements SPInteractable {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private final String kind;

    public SearchableFurnitureBlock(BlockBehaviour.Properties props, int[] box, String kind) {
        super(props, box);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(OPEN);
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(OPEN)) return null;
        int ticks = switch (kind) {
            case "supply_box" -> 40;
            case "medical_cabinet" -> 24;
            default -> 32;
        };
        return InteractionSpec.of(Component.translatable("smileprotocol.search." + kind), ticks, 4f, PoseCalculator.INTERACT_PULL);
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (state.getValue(OPEN)) return;
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        var sound = switch (kind) {
            case "medical_cabinet" -> SoundEvents.IRON_DOOR_OPEN;
            case "supply_box" -> SoundEvents.BARREL_OPEN;
            case "desk" -> SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            default -> SoundEvents.IRON_TRAPDOOR_OPEN;
        };
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.9f, 0.8f + level.random.nextFloat() * 0.3f);
        Direction f = state.getValue(FACING);
        Vec3 front = Vec3.atBottomCenterOf(pos).add(f.getStepX() * 0.8, 0.4, f.getStepZ() * 0.8);
        ItemStack loot = SearchLoot.roll(level, pos, kind, level.random);
        if (!loot.isEmpty()) {
            InteractionManager.spawnItem(level, front, loot).setDeltaMovement(f.getStepX() * 0.08, 0.15, f.getStepZ() * 0.08);
        } else {
            player.displayClientMessage(Component.translatable("smileprotocol.search.empty"), true);
        }
        LabEvents.fire(level, pos, player, "furniture_searched", kind);
    }
}
