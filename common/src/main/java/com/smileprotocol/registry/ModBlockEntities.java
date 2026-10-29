package com.smileprotocol.registry;

import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/** Entidades de bloque del mod. */
@SuppressWarnings("DataFlowIssue")
public final class ModBlockEntities {

    public static final Supplier<BlockEntityType<ElevatorBoardBlockEntity>> ELEVATOR_BOARD = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "elevator_board",
            () -> Services.PLATFORM.blockEntityType(ElevatorBoardBlockEntity::new, ModBlocks.ELEVATOR_BOARD.get()));

    public static final Supplier<BlockEntityType<ElevatorDoorBlockEntity>> ELEVATOR_DOOR = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "elevator_door",
            () -> Services.PLATFORM.blockEntityType(ElevatorDoorBlockEntity::new, ModBlocks.ELEVATOR_DOOR.get()));

    public static final Supplier<BlockEntityType<FluorescentLightBlockEntity>> FLUORESCENT_LIGHT = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "fluorescent_light",
            () -> Services.PLATFORM.blockEntityType(FluorescentLightBlockEntity::new, ModBlocks.FLUORESCENT_LIGHT.get()));

    private ModBlockEntities() {}

    public static void init() {}
}
