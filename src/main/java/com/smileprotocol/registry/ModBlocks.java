package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.block.ElevatorButtonBlock;
import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.block.FluorescentLightBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Bloques del mod. Se iran anadiendo por fases: cajas de fusibles, puertas y valvulas (F09),
 * escondites (F10), rejillas (F17)...
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SmileProtocol.MODID);

    // Lobby (README 5.2, 6.2). Irrompibles para los jugadores: solo un admin en creativo los quita.
    public static final RegistryObject<ElevatorDoorBlock> ELEVATOR_DOOR = BLOCKS.register("elevator_door",
            () -> new ElevatorDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<ElevatorButtonBlock> ELEVATOR_BUTTON = BLOCKS.register("elevator_button",
            () -> new ElevatorButtonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.METAL).noOcclusion().noCollission()
                    .lightLevel(s -> 4)));

    public static final RegistryObject<ElevatorBoardBlock> ELEVATOR_BOARD = BLOCKS.register("elevator_board",
            () -> new ElevatorBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(s -> 6)));

    public static final RegistryObject<FluorescentLightBlock> FLUORESCENT_LIGHT = BLOCKS.register("fluorescent_light",
            () -> new FluorescentLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ)
                    .strength(0.5f).sound(SoundType.GLASS)
                    .lightLevel(s -> s.getValue(FluorescentLightBlock.LIT) ? 15 : 0)));

    private ModBlocks() {}
}
