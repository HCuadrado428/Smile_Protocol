package com.smileprotocol.blockentity;

import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entidad de bloque de la pantalla de ascensor. No guarda nada: existe para que la pantalla
 * tenga un renderer. El texto llega por {@code SyncLobbyBoardsPacket}.
 */
public class ElevatorBoardBlockEntity extends BlockEntity {

    public ElevatorBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_BOARD.get(), pos, state);
    }
}
