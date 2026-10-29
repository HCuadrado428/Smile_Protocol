package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entidades de bloque del mod. */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SmileProtocol.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ElevatorBoardBlockEntity>> ELEVATOR_BOARD =
            BLOCK_ENTITIES.register("elevator_board", () -> BlockEntityType.Builder
                    .of(ElevatorBoardBlockEntity::new, ModBlocks.ELEVATOR_BOARD.get()).build(null));

    private ModBlockEntities() {}
}
