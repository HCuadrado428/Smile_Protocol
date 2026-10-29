package com.smileprotocol.registry;

import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.block.ElevatorButtonBlock;
import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.block.FuseBoxBlock;
import com.smileprotocol.block.HideBlock;
import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.block.ValveBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
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
                    .lightLevel(s -> s.getValue(FluorescentLightBlock.LIT) ? 10 : 0)));

    // Acabados de las cabinas
    public static final Supplier<Block> ELEVATOR_FRAME = register("elevator_frame", () -> new Block(steel(SoundType.METAL)));
    public static final Supplier<Block> ELEVATOR_WALL_PANEL = register("elevator_wall_panel", () -> new Block(steel(SoundType.METAL)));
    public static final Supplier<Block> ELEVATOR_FLOOR = register("elevator_floor", () -> new Block(steel(SoundType.NETHERITE_BLOCK)));

    // Mecanismos del laboratorio (README 14)
    public static final Supplier<FuseBoxBlock> FUSE_BOX = register("fuse_box",
            () -> new FuseBoxBlock(lab(SoundType.METAL).noOcclusion()));
    public static final Supplier<LabDoorBlock> LAB_DOOR = register("lab_door",
            () -> new LabDoorBlock(BlockSetType.OAK, lab(SoundType.WOOD).noOcclusion()));
    public static final Supplier<LabDoorBlock> SECURITY_DOOR = register("security_door",
            () -> new LabDoorBlock(BlockSetType.IRON, lab(SoundType.METAL).noOcclusion()));
    public static final Supplier<ValveBlock> VALVE = register("valve",
            () -> new ValveBlock(lab(SoundType.METAL).noOcclusion()));

    // Escondites (README 15)
    public static final Supplier<HideBlock> LOCKER = register("locker",
            () -> new HideBlock(lab(SoundType.METAL).noOcclusion(), "locker", HideBlock.Kind.ENCLOSED));
    public static final Supplier<HideBlock> WARDROBE = register("wardrobe",
            () -> new HideBlock(lab(SoundType.WOOD).noOcclusion(), "wardrobe", HideBlock.Kind.ENCLOSED));
    public static final Supplier<HideBlock> WASTE_CONTAINER = register("waste_container",
            () -> new HideBlock(lab(SoundType.METAL).noOcclusion(), "container", HideBlock.Kind.ENCLOSED));
    public static final Supplier<HideBlock> GURNEY = register("gurney",
            () -> new HideBlock(lab(SoundType.METAL).noOcclusion(), "gurney", HideBlock.Kind.LOW));
    public static final Supplier<HideBlock> CURTAIN = register("curtain",
            () -> new HideBlock(lab(SoundType.WOOL).noOcclusion().noCollission(), "curtain", HideBlock.Kind.CURTAIN));

    // Construccion del laboratorio: suelos, paredes y techos (tools/textures_lab.py)
    public static final String[] BUILDING_NAMES = {
            "lab_tile_floor", "lab_tile_floor_dark", "lab_linoleum", "lab_concrete_floor", "lab_grate", "lab_wood_floor",
            "lab_morgue_floor", "lab_moss_floor", "lab_plaster", "lab_plaster_green", "lab_plaster_blue", "lab_wall_tiles",
            "lab_wall_tiles_teal", "lab_wall_tiles_white", "lab_concrete_wall", "lab_metal_wall", "lab_hazard",
            "lab_ceiling", "lab_ceiling_concrete", "lab_crate", "lab_pipes", "lab_server", "lab_machine"};
    public static final java.util.Map<String, Supplier<Block>> BUILDING = new java.util.LinkedHashMap<>();

    static {
        for (String n : BUILDING_NAMES) {
            SoundType snd = n.contains("metal") || n.contains("grate") || n.contains("pipes") || n.contains("server") || n.contains("machine") ? SoundType.METAL
                    : n.contains("wood") || n.contains("crate") ? SoundType.WOOD
                    : n.contains("moss") ? SoundType.MOSS : SoundType.STONE;
            BUILDING.put(n, register(n, () -> new Block(steel(snd))));
        }
    }

    public static final Supplier<net.minecraft.world.level.block.SlabBlock> LAB_DEBRIS = register("lab_debris",
            () -> new net.minecraft.world.level.block.SlabBlock(steel(SoundType.GRAVEL)));

    // Sangre y pintadas
    public static final Supplier<com.smileprotocol.block.DecalBlock> BLOOD_FLOOR = register("blood_floor",
            () -> new com.smileprotocol.block.DecalBlock(decal()));
    public static final Supplier<com.smileprotocol.block.WallDecalBlock> WALL_DECAL = register("wall_decal",
            () -> new com.smileprotocol.block.WallDecalBlock(decal(), false));
    public static final Supplier<com.smileprotocol.block.WallDecalBlock> WALL_WRITING = register("wall_writing",
            () -> com.smileprotocol.block.WallDecalBlock.writing(decal()));

    public static Block building(String name) {
        return BUILDING.get(name).get();
    }

    private static BlockBehaviour.Properties decal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0f, 3600000.0f)
                .sound(SoundType.SLIME_BLOCK).noOcclusion().noCollission().replaceable();
    }

    private ModBlocks() {}

    /** Mecanismos y mobiliario de las zonas: irrompibles salvo en creativo. */
    private static BlockBehaviour.Properties lab(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(sound);
    }

    private static BlockBehaviour.Properties steel(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(sound);
    }

    private static <T extends Block> Supplier<T> register(String name, Supplier<T> factory) {
        return Services.PLATFORM.register(BuiltInRegistries.BLOCK, name, factory);
    }

    /** Fuerza la carga de la clase (y el registro) desde el arranque del loader. */
    public static void init() {}
}
