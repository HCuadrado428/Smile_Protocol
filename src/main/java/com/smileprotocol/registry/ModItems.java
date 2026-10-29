package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Items del mod (README 10): linterna, baterias, fusibles, llaves, tarjetas, botiquines,
 * bengalas, jeringas, walkies y lanzables. Se registran en sus fases.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SmileProtocol.MODID);

    // Bloques del lobby, para que los administradores puedan colocarlos a mano
    public static final RegistryObject<Item> ELEVATOR_DOOR = block("elevator_door", ModBlocks.ELEVATOR_DOOR);
    public static final RegistryObject<Item> ELEVATOR_BUTTON = block("elevator_button", ModBlocks.ELEVATOR_BUTTON);
    public static final RegistryObject<Item> ELEVATOR_BOARD = block("elevator_board", ModBlocks.ELEVATOR_BOARD);
    public static final RegistryObject<Item> FLUORESCENT_LIGHT = block("fluorescent_light", ModBlocks.FLUORESCENT_LIGHT);
    public static final RegistryObject<Item> ELEVATOR_FRAME = block("elevator_frame", ModBlocks.ELEVATOR_FRAME);
    public static final RegistryObject<Item> ELEVATOR_WALL_PANEL = block("elevator_wall_panel", ModBlocks.ELEVATOR_WALL_PANEL);
    public static final RegistryObject<Item> ELEVATOR_FLOOR = block("elevator_floor", ModBlocks.ELEVATOR_FLOOR);

    private ModItems() {}

    private static RegistryObject<Item> block(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
