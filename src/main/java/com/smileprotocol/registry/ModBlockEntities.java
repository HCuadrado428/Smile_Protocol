package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entidades de bloque del mod. */
@SuppressWarnings("DataFlowIssue")
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SmileProtocol.MODID);

    public static final RegistryObject<BlockEntityType<ElevatorBoardBlockEntity>> ELEVATOR_BOARD =
            BLOCK_ENTITIES.register("elevator_board", () -> BlockEntityType.Builder
                    .of(ElevatorBoardBlockEntity::new, ModBlocks.ELEVATOR_BOARD.get()).build(null));

    public static final RegistryObject<BlockEntityType<ElevatorDoorBlockEntity>> ELEVATOR_DOOR =
            BLOCK_ENTITIES.register("elevator_door", () -> BlockEntityType.Builder
                    .of(ElevatorDoorBlockEntity::new, ModBlocks.ELEVATOR_DOOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluorescentLightBlockEntity>> FLUORESCENT_LIGHT =
            BLOCK_ENTITIES.register("fluorescent_light", () -> BlockEntityType.Builder
                    .of(FluorescentLightBlockEntity::new, ModBlocks.FLUORESCENT_LIGHT.get()).build(null));

    private ModBlockEntities() {}
}
