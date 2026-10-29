package com.smileprotocol.registry;

import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.HideBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;

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

    public static final Supplier<BlockEntityType<FuseBoxBlockEntity>> FUSE_BOX = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "fuse_box",
            () -> Services.PLATFORM.blockEntityType(FuseBoxBlockEntity::new, ModBlocks.FUSE_BOX.get()));

    public static final Supplier<BlockEntityType<LabDoorBlockEntity>> LAB_DOOR = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "lab_door",
            () -> Services.PLATFORM.blockEntityType(LabDoorBlockEntity::new, ModBlocks.LAB_DOOR.get(), ModBlocks.SECURITY_DOOR.get()));

    public static final Supplier<BlockEntityType<ValveBlockEntity>> VALVE = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "valve",
            () -> Services.PLATFORM.blockEntityType(ValveBlockEntity::new, ModBlocks.VALVE.get()));

    public static final Supplier<BlockEntityType<HideBlockEntity>> HIDE = Services.PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "hide",
            () -> Services.PLATFORM.blockEntityType(HideBlockEntity::new, ModBlocks.LOCKER.get(), ModBlocks.WARDROBE.get(),
                    ModBlocks.WASTE_CONTAINER.get(), ModBlocks.GURNEY.get(), ModBlocks.CURTAIN.get()));

    private ModBlockEntities() {}

    public static void init() {}
}
