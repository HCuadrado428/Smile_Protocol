package com.smileprotocol.registry;

import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import com.smileprotocol.item.FuseItem;
import com.smileprotocol.item.KeyItem;
import com.smileprotocol.item.KeycardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
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

    public static final Supplier<Item> FUSE_BOX = block("fuse_box", ModBlocks.FUSE_BOX);
    public static final Supplier<Item> LAB_DOOR = doubleBlock("lab_door", ModBlocks.LAB_DOOR);
    public static final Supplier<Item> SECURITY_DOOR = doubleBlock("security_door", ModBlocks.SECURITY_DOOR);
    public static final Supplier<Item> VALVE = block("valve", ModBlocks.VALVE);
    public static final Supplier<Item> LOCKER = block("locker", ModBlocks.LOCKER);
    public static final Supplier<Item> WARDROBE = block("wardrobe", ModBlocks.WARDROBE);
    public static final Supplier<Item> WASTE_CONTAINER = block("waste_container", ModBlocks.WASTE_CONTAINER);
    public static final Supplier<Item> GURNEY = block("gurney", ModBlocks.GURNEY);
    public static final Supplier<Item> CURTAIN = block("curtain", ModBlocks.CURTAIN);

    // Objetos de la partida (README 10)
    public static final Supplier<FuseItem> FUSE = item("fuse", () -> new FuseItem(one(), false));
    public static final Supplier<FuseItem> FUSE_BLOWN = item("fuse_blown", () -> new FuseItem(one(), true));
    public static final Supplier<KeyItem> KEY = item("key", () -> new KeyItem(one()));
    public static final Supplier<KeycardItem> KEYCARD_1 = item("keycard_1", () -> new KeycardItem(one(), 1));
    public static final Supplier<KeycardItem> KEYCARD_2 = item("keycard_2", () -> new KeycardItem(one(), 2));
    public static final Supplier<KeycardItem> KEYCARD_3 = item("keycard_3", () -> new KeycardItem(one(), 3));
    public static final Supplier<Item> VALVE_WHEEL = item("valve_wheel", () -> new Item(one()));
    public static final Supplier<Item> FLASHLIGHT = item("flashlight", () -> new Item(one()));
    // Consumibles y lanzables (fase 13)
    public static final Supplier<Item> MEDKIT = item("medkit", () -> new com.smileprotocol.item.ConsumableItem(one()));
    public static final Supplier<Item> BATTERY = item("battery", () -> new com.smileprotocol.item.ConsumableItem(one()));
    public static final Supplier<Item> FLARE = item("flare", () -> new com.smileprotocol.item.ConsumableItem(one()));
    public static final Supplier<Item> ADRENALINE = item("adrenaline", () -> new com.smileprotocol.item.ConsumableItem(one()));
    /** Walkie (README 17.3): se usa como consumible que no se gasta (encender y apagar). */
    public static final Supplier<Item> WALKIE = item("walkie", () -> new com.smileprotocol.item.ConsumableItem(one()));
    public static final Supplier<Item> CAN = item("can", () -> new com.smileprotocol.item.ThrowableItem(one()));
    public static final Supplier<Item> BOTTLE = item("bottle", () -> new com.smileprotocol.item.ThrowableItem(one()));

    static {
        for (String n : ModBlocks.BUILDING_NAMES) block(n, ModBlocks.BUILDING.get(n));
        block("lab_debris", ModBlocks.LAB_DEBRIS);
        block("blood_floor", ModBlocks.BLOOD_FLOOR);
        block("wall_decal", ModBlocks.WALL_DECAL);
        block("wall_writing", ModBlocks.WALL_WRITING);
        block("vent_grille", ModBlocks.VENT_GRILLE);
        block("vent_fan", ModBlocks.VENT_FAN);
        for (String n : ModBlocks.FURNITURE.keySet()) block(n, ModBlocks.FURNITURE.get(n));
    }

    private ModItems() {}

    private static Item.Properties one() {
        return new Item.Properties().stacksTo(1);
    }

    private static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        Supplier<T> s = Services.PLATFORM.register(BuiltInRegistries.ITEM, name, factory);
        ALL.add(s);
        return s;
    }

    /** Item de un bloque registrado mas adelante. */
    public static Supplier<Item> blockItem(String name, Supplier<? extends Block> block) {
        return block(name, block);
    }

    public static List<Supplier<? extends Item>> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static Supplier<Item> doubleBlock(String name, Supplier<? extends Block> block) {
        Supplier<Item> s = Services.PLATFORM.register(BuiltInRegistries.ITEM, name,
                () -> new DoubleHighBlockItem(block.get(), new Item.Properties()));
        ALL.add(s);
        return s;
    }

    private static Supplier<Item> block(String name, Supplier<? extends Block> block) {
        Supplier<Item> s = Services.PLATFORM.register(BuiltInRegistries.ITEM, name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        ALL.add(s);
        return s;
    }

    public static void init() {}
}
