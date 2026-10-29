package com.smileprotocol.registry;

import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Items del mod (README 10). Por ahora, los bloques del lobby para los administradores. */
public final class ModItems {

    private static final List<Supplier<? extends Item>> ALL = new ArrayList<>();

    public static final Supplier<Item> ELEVATOR_DOOR = block("elevator_door", ModBlocks.ELEVATOR_DOOR);
    public static final Supplier<Item> ELEVATOR_BUTTON = block("elevator_button", ModBlocks.ELEVATOR_BUTTON);
    public static final Supplier<Item> ELEVATOR_BOARD = block("elevator_board", ModBlocks.ELEVATOR_BOARD);
    public static final Supplier<Item> FLUORESCENT_LIGHT = block("fluorescent_light", ModBlocks.FLUORESCENT_LIGHT);
    public static final Supplier<Item> ELEVATOR_FRAME = block("elevator_frame", ModBlocks.ELEVATOR_FRAME);
    public static final Supplier<Item> ELEVATOR_WALL_PANEL = block("elevator_wall_panel", ModBlocks.ELEVATOR_WALL_PANEL);
    public static final Supplier<Item> ELEVATOR_FLOOR = block("elevator_floor", ModBlocks.ELEVATOR_FLOOR);

    private ModItems() {}

    public static List<Supplier<? extends Item>> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static Supplier<Item> block(String name, Supplier<? extends Block> block) {
        Supplier<Item> s = Services.PLATFORM.register(BuiltInRegistries.ITEM, name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        ALL.add(s);
        return s;
    }

    public static void init() {}
}
