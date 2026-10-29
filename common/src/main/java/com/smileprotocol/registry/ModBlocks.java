package com.smileprotocol.registry;

import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.block.ElevatorButtonBlock;
import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/** Bloques del mod. Los del lobby son irrompibles salvo para un admin en creativo. */
public final class ModBlocks {

    public static final Supplier<ElevatorDoorBlock> ELEVATOR_DOOR = register("elevator_door",
            () -> new ElevatorDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.METAL).noOcclusion()));

    public static final Supplier<ElevatorButtonBlock> ELEVATOR_BUTTON = register("elevator_button",
            () -> new ElevatorButtonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.METAL).noOcclusion().noCollission()
                    .lightLevel(s -> 4)));

    public static final Supplier<ElevatorBoardBlock> ELEVATOR_BOARD = register("elevator_board",
            () -> new ElevatorBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0f, 3600000.0f).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(s -> 6)));

    public static final Supplier<FluorescentLightBlock> FLUORESCENT_LIGHT = register("fluorescent_light",
            () -> new FluorescentLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ)
                    .strength(0.5f).sound(SoundType.GLASS)
                    .lightLevel(s -> s.getValue(FluorescentLightBlock.LIT) ? 15 : 0)));

    // Acabados de las cabinas
    public static final Supplier<Block> ELEVATOR_FRAME = register("elevator_frame", () -> new Block(steel(SoundType.METAL)));
    public static final Supplier<Block> ELEVATOR_WALL_PANEL = register("elevator_wall_panel", () -> new Block(steel(SoundType.METAL)));
    public static final Supplier<Block> ELEVATOR_FLOOR = register("elevator_floor", () -> new Block(steel(SoundType.NETHERITE_BLOCK)));

    private ModBlocks() {}

    private static BlockBehaviour.Properties steel(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(sound);
    }

    private static <T extends Block> Supplier<T> register(String name, Supplier<T> factory) {
        return Services.PLATFORM.register(BuiltInRegistries.BLOCK, name, factory);
    }

    /** Fuerza la carga de la clase (y el registro) desde el arranque del loader. */
    public static void init() {}
}
