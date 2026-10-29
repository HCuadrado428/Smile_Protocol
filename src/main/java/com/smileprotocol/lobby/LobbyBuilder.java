package com.smileprotocol.lobby;

import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.block.ElevatorButtonBlock;
import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Construye el lobby automaticamente (README 5.2): una recepcion de laboratorio y hospital
 * con sala de espera, maquinas expendedoras, televisores, tablon de avisos, taquillas,
 * plantas secas, camillas, una silla de ruedas, un cartel de «suelo mojado» y, al fondo,
 * los ascensores: cuatro mini y el mega en el centro. Registra la region protegida, el punto
 * de aparicion y los cinco ascensores.
 * <p>
 * Orientacion fija: la entrada mira al sur (+Z) y los ascensores estan al norte (-Z).
 * Coordenadas locales: X 0..40, Z 0..34 la sala, Z -7..-1 las cabinas, Y 0 el suelo.
 */
public final class LobbyBuilder {

    public static final int WIDTH = 41;
    public static final int DEPTH = 35;
    /** Punto de aparicion en coordenadas locales. */
    private static final int SPAWN_X = 20, SPAWN_Z = 29;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Cabinas: id, nombre, X interior minima y maxima. */
    private record Cabin(String id, String name, ElevatorType type, int x1, int x2, int zMin, int height,
                         int door1, int door2, int boardX) {}

    private static final Cabin[] CABINS = {
            new Cabin("mini1", "Ascensor 1", ElevatorType.MINI, 4, 7, -4, 4, 5, 6, 5),
            new Cabin("mini2", "Ascensor 2", ElevatorType.MINI, 11, 14, -4, 4, 12, 13, 12),
            new Cabin("mega", "Ascensor de servicio", ElevatorType.MEGA, 17, 23, -6, 5, 18, 22, 20),
            new Cabin("mini3", "Ascensor 3", ElevatorType.MINI, 26, 29, -4, 4, 27, 28, 27),
            new Cabin("mini4", "Ascensor 4", ElevatorType.MINI, 33, 36, -4, 4, 34, 35, 34),
    };

    private final ServerLevel level;
    private final BlockPos o;

    private LobbyBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.o = origin;
    }

    /** Origen (esquina local 0,0,0) para que el punto de aparicion quede en {@code spawn}. */
    public static BlockPos originFor(BlockPos spawn) {
        return spawn.offset(-SPAWN_X, -1, -SPAWN_Z);
    }

    /** Caja que ocupara el lobby (region protegida). */
    public static BoundingBox regionFor(BlockPos origin) {
        return new BoundingBox(origin.getX() - 1, origin.getY() - 1, origin.getZ() - 8,
                origin.getX() + WIDTH, origin.getY() + 10, origin.getZ() + DEPTH);
    }

    /** Construye el lobby con su punto de aparicion en {@code spawn} y lo registra. */
    public static Component build(ServerLevel level, BlockPos spawn) {
        BlockPos origin = originFor(spawn);
        LobbyBuilder b = new LobbyBuilder(level, origin);
        b.clear();
        b.shell();
        b.floorAndCeiling();
        b.frontWall();
        b.elevatorBank();
        b.reception();
        b.waitingArea();
        b.westWall();
        b.eastWall();
        b.details();
        return b.register(spawn);
    }

    // Helpers -------------------------------------------------------------------

    private BlockPos at(int x, int y, int z) {
        return o.offset(x, y, z);
    }

    private void set(int x, int y, int z, BlockState s) {
        level.setBlock(at(x, y, z), s, FLAGS);
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
    }

    /** Pared de hospital: zocalo gris, franja turquesa y blanco arriba. */
    private static BlockState wall(int y) {
        if (y <= 2) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        if (y == 3) return Blocks.CYAN_TERRACOTTA.defaultBlockState();
        return Blocks.WHITE_CONCRETE.defaultBlockState();
    }

    private static BlockState stairs(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private void wallSign(int x, int y, int z, Direction facing, String... lines) {
        set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        writeSign(at(x, y, z), lines);
    }

    private void hangingSign(int x, int y, int z, int rotation, String... lines) {
        set(x, y, z, Blocks.BIRCH_HANGING_SIGN.defaultBlockState()
                .setValue(CeilingHangingSignBlock.ROTATION, rotation)
                .setValue(CeilingHangingSignBlock.ATTACHED, false));
        writeSign(at(x, y, z), lines);
    }

    private void writeSign(BlockPos pos, String... lines) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = sign.getFrontText();
        for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
    }

    // Partes --------------------------------------------------------------------

    private void clear() {
        fill(-1, 0, -8, WIDTH, 10, DEPTH, Blocks.AIR);
        // Cimientos para que no quede flotando
        fill(-1, -1, -8, WIDTH, -1, DEPTH, Blocks.STONE);
    }

    private void shell() {
        // Paredes laterales y frontal
        for (int y = 1; y <= 7; y++) {
            BlockState w = wall(y);
            fill(0, y, 0, 0, y, DEPTH - 1, w);
            fill(WIDTH - 1, y, 0, WIDTH - 1, y, DEPTH - 1, w);
            fill(0, y, DEPTH - 1, WIDTH - 1, y, DEPTH - 1, w);
        }
        // Bloque de los ascensores (detras de la pared del fondo)
        fill(0, 0, -7, WIDTH - 1, 8, -1, Blocks.GRAY_CONCRETE);
        // Pared del fondo, de acero
        fill(0, 1, 0, WIDTH - 1, 7, 0, Blocks.SMOOTH_STONE);
        fill(1, 3, 0, WIDTH - 2, 3, 0, Blocks.CYAN_TERRACOTTA);
        // Tejado
        fill(-1, 9, -8, WIDTH, 9, DEPTH, Blocks.GRAY_CONCRETE);
    }

    private void floorAndCeiling() {
        fill(0, 0, 0, WIDTH - 1, 0, DEPTH - 1, Blocks.LIGHT_GRAY_CONCRETE);
        fill(1, 0, 1, WIDTH - 2, 0, DEPTH - 2, Blocks.WHITE_CONCRETE);
        // Lineas de guia del suelo, como en los hospitales: hacia los ascensores
        fill(SPAWN_X, 0, 3, SPAWN_X, 0, DEPTH - 2, Blocks.LIGHT_BLUE_CONCRETE);
        fill(5, 0, 3, 35, 0, 3, Blocks.LIGHT_BLUE_CONCRETE);
        // Franja de espera frente a los ascensores
        fill(1, 0, 1, WIDTH - 2, 0, 1, Blocks.GRAY_CONCRETE);

        fill(0, 8, 0, WIDTH - 1, 8, DEPTH - 1, Blocks.SMOOTH_STONE);
        BlockState light = ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
        for (int x = 5; x <= 35; x += 5) {
            for (int z = 4; z <= 28; z += 6) {
                boolean flicker = (x == 30 && z == 22) || (x == 10 && z == 10) || (x == 25 && z == 4);
                set(x, 8, z, light.setValue(FluorescentLightBlock.FLICKERS, flicker));
            }
        }
    }

    private void frontWall() {
        int z = DEPTH - 1;
        // Ventanas con parteluces
        for (int x = 2; x <= WIDTH - 3; x++) {
            if (x >= 17 && x <= 23) continue;
            if (x % 4 == 1) continue;
            fill(x, 2, z, x, 5, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
        }
        // Puertas de entrada de cristal, cerradas
        fill(17, 1, z, 17, 5, z, Blocks.IRON_BLOCK);
        fill(23, 1, z, 23, 5, z, Blocks.IRON_BLOCK);
        fill(18, 5, z, 22, 5, z, Blocks.IRON_BLOCK);
        fill(18, 1, z, 22, 4, z, Blocks.GLASS);
        fill(18, 1, z - 2, 22, 1, z - 1, Blocks.GRAY_CARPET);
        wallSign(20, 5, z - 1, Direction.NORTH, "SALIDA", "", "Cerrada por", "protocolo");
    }

    private void elevatorBank() {
        BlockState light = ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
        BlockState board = ModBlocks.ELEVATOR_BOARD.get().defaultBlockState().setValue(ElevatorBoardBlock.FACING, Direction.SOUTH);
        BlockState button = ModBlocks.ELEVATOR_BUTTON.get().defaultBlockState().setValue(ElevatorButtonBlock.FACING, Direction.SOUTH);
        BlockState door = ModBlocks.ELEVATOR_DOOR.get().defaultBlockState()
                .setValue(ElevatorDoorBlock.FACING, Direction.SOUTH).setValue(ElevatorDoorBlock.OPEN, true);

        for (Cabin c : CABINS) {
            // Interior de la cabina
            fill(c.x1(), 1, c.zMin(), c.x2(), c.height(), -1, Blocks.AIR);
            fill(c.x1(), 0, c.zMin(), c.x2(), 0, -1, Blocks.POLISHED_ANDESITE);
            // Paredes interiores de acero cepillado
            fill(c.x1() - 1, 1, c.zMin(), c.x1() - 1, c.height(), -1, Blocks.IRON_BLOCK);
            fill(c.x2() + 1, 1, c.zMin(), c.x2() + 1, c.height(), -1, Blocks.IRON_BLOCK);
            fill(c.x1(), 1, c.zMin() - 1, c.x2(), c.height(), c.zMin() - 1, Blocks.IRON_BLOCK);
            fill(c.x1(), 2, c.zMin() - 1, c.x2(), 2, c.zMin() - 1, Blocks.POLISHED_ANDESITE); // pasamanos
            int cx = (c.x1() + c.x2()) / 2;
            set(cx, c.height() + 1, (c.zMin() - 1) / 2, light);
            if (c.type() == ElevatorType.MEGA) set(cx + 2, c.height() + 1, (c.zMin() - 1) / 2, light);

            // Hueco de la puerta en la pared del fondo, con marco
            fill(c.door1() - 1, 1, 0, c.door2() + 1, 3, 0, Blocks.IRON_BLOCK);
            for (int x = c.door1(); x <= c.door2(); x++) {
                set(x, 1, 0, door.setValue(ElevatorDoorBlock.HALF, DoubleBlockHalf.LOWER));
                set(x, 2, 0, door.setValue(ElevatorDoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            // Pantalla encima de la puerta, mirando al lobby
            set(c.boardX(), 4, 1, board);
            // Boton dentro, en la pared del fondo de la cabina
            set(cx, 2, c.zMin(), button);
        }
        hangingSign(SPAWN_X, 7, 6, 0, "ASCENSORES", "Acceso a", "laboratorios", "Planta -1");
    }

    private void reception() {
        BlockState counter = Blocks.POLISHED_DIORITE.defaultBlockState();
        fill(6, 1, 22, 14, 1, 22, counter);
        fill(6, 1, 23, 6, 1, 24, counter);
        fill(14, 1, 23, 14, 1, 24, counter);
        // Encima del mostrador: ordenadores, teclado, papeles y una planta seca
        BlockState monitor = Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState()
                .setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        set(8, 2, 22, monitor);
        set(9, 2, 22, Blocks.GRAY_CARPET);
        set(11, 2, 22, Blocks.WHITE_CARPET);
        set(12, 2, 22, monitor);
        set(13, 2, 22, Blocks.POTTED_DEAD_BUSH);
        // Sillas del personal y archivadores
        set(9, 1, 24, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        set(12, 1, 24, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        set(7, 1, 26, Blocks.BARREL);
        set(13, 1, 26, Blocks.BARREL);
        set(10, 1, 26, Blocks.BOOKSHELF);
        hangingSign(10, 7, 22, 0, "RECEPCIÓN", "Información", "y admisión", "");
        // Cartel pegado al frente del mostrador
        wallSign(10, 1, 21, Direction.NORTH, "Por favor,", "espere su", "turno", "Gracias :)");
    }

    private void waitingArea() {
        BlockState chair = stairs(Blocks.BIRCH_STAIRS, Direction.WEST);
        for (int x = 28; x <= 34; x += 3) {
            for (int z = 20; z <= 28; z++) {
                if (z == 24) continue; // pasillo
                set(x, 1, z, chair);
            }
        }
        hangingSign(31, 7, 18, 0, "SALA DE", "ESPERA", "", "");
        // Plantas secas
        set(37, 1, 19, Blocks.POTTED_DEAD_BUSH);
        set(37, 1, 30, Blocks.POTTED_DEAD_BUSH);
        set(25, 1, 30, Blocks.POTTED_DEAD_BUSH);
        // Fuente de agua
        set(38, 1, 18, Blocks.CAULDRON);
    }

    private void westWall() {
        // Taquillas
        for (int z = 6; z <= 12; z++) {
            fill(1, 1, z, 1, 2, z, Blocks.IRON_BLOCK);
            BlockState door = Blocks.IRON_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING, Direction.EAST).setValue(TrapDoorBlock.OPEN, true);
            set(2, 1, z, door);
            set(2, 2, z, door);
        }
        // Tablon de avisos
        fill(1, 3, 15, 1, 5, 19, Blocks.DARK_OAK_PLANKS);
        wallSign(2, 4, 15, Direction.EAST, "AVISO", "Prohibido el", "paso al personal", "no autorizado");
        wallSign(2, 4, 17, Direction.EAST, "Horario de", "visitas", "10:00 - 20:00", "(suspendido)");
        wallSign(2, 4, 19, Direction.EAST, "PROTOCOLO", "S.M.I.L.E.", "Mantenga la", "calma. Sonría.");
        // Maquinas expendedoras
        Block[][] machines = {
                {Blocks.IRON_BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS},
                {Blocks.RED_CONCRETE, Blocks.WHITE_STAINED_GLASS},
                {Blocks.IRON_BLOCK, Blocks.LIME_STAINED_GLASS},
        };
        for (int i = 0; i < machines.length; i++) {
            int z = 25 + i * 2;
            set(1, 1, z, machines[i][0]);
            set(1, 2, z, machines[i][1]);
            set(1, 3, z, Blocks.IRON_BLOCK);
        }
        set(2, 1, 31, Blocks.CAULDRON); // papelera
    }

    private void eastWall() {
        int x = WIDTH - 2;
        // Televisores, uno apagado y otro con estatica
        fill(x, 3, 21, x, 4, 23, Blocks.BLACK_CONCRETE);
        fill(x, 3, 25, x, 4, 27, Blocks.BLACK_CONCRETE);
        set(x, 3, 26, Blocks.LIGHT_GRAY_STAINED_GLASS);
        set(x, 4, 26, Blocks.WHITE_STAINED_GLASS);
        // Camillas aparcadas
        for (int z : new int[]{6, 9}) {
            set(38, 1, z, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            set(38, 1, z + 1, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        }
        // Silla de ruedas
        set(37, 1, 13, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.EAST));
        set(38, 1, 13, Blocks.IRON_BARS.defaultBlockState());
    }

    private void details() {
        // Cartel de «suelo mojado»
        set(SPAWN_X + 1, 1, 15, Blocks.YELLOW_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 2));
        // Papelera junto a la recepcion
        set(16, 1, 23, Blocks.CAULDRON);
    }

    private Component register(BlockPos spawn) {
        LobbyData data = LobbyData.get(level.getServer());
        data.setRegion(regionFor(o));
        data.setSpawn(spawn, 180f);
        level.setDefaultSpawnPos(spawn, 180f);
        data.clearElevators();
        for (Cabin c : CABINS) {
            BoundingBox cabin = new BoundingBox(
                    o.getX() + c.x1(), o.getY() + 1, o.getZ() + c.zMin(),
                    o.getX() + c.x2(), o.getY() + c.height(), o.getZ() - 1);
            LobbyManager.registerElevator(level, c.type(), c.id(), c.name(), cabin);
        }
        SPDebug.log("lobby construido en " + o.toShortString());
        return Component.translatable("smileprotocol.lobby.built", spawn.toShortString(), CABINS.length);
    }
}
