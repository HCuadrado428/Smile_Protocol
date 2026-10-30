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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Construye el lobby automaticamente (README 5.2): la recepcion del laboratorio S.M.I.L.E. y su
 * hospital. Entrando desde el sur: el control de seguridad con tornos y la garita, la recepcion
 * con el logotipo de la empresa (una cara sonriente con un ojo que gotea rojo), la cafeteria con
 * maquinas expendedoras, la sala de espera con televisores, un muro de desaparecidos, tablones con
 * avisos que hablan de los smilers sin nombrarlos, taquillas, la sala de observacion con camillas
 * y cortinas, el acceso a urgencias precintado y, al fondo, los ascensores: cuatro mini y el mega
 * en el centro. Un rastro de sangre muy discreto entra en el ascensor 3. Registra la region
 * protegida, el punto de aparicion y los cinco ascensores.
 * <p>
 * Orientacion fija: la entrada mira al sur (+Z) y los ascensores estan al norte (-Z).
 * Coordenadas locales: X 0..40, Z 0..46 la sala, Z -7..-1 las cabinas, Y 0 el suelo.
 */
public final class LobbyBuilder {

    public static final int WIDTH = 41;
    public static final int DEPTH = 47;
    /** Punto de aparicion en coordenadas locales. */
    private static final int SPAWN_X = 20, SPAWN_Z = 43;

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
        b.observation();
        b.cafeteria();
        b.security();
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
        if (y <= 2) return ModBlocks.building("lab_wall_tiles_white").defaultBlockState();
        if (y == 3) return ModBlocks.building("lab_wall_tiles_teal").defaultBlockState();
        return ModBlocks.building("lab_plaster").defaultBlockState();
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
        fill(0, 0, -7, WIDTH - 1, 8, -1, ModBlocks.building("lab_concrete_wall"));
        // Pared del fondo, de acero
        fill(0, 1, 0, WIDTH - 1, 7, 0, ModBlocks.building("lab_concrete_wall"));
        fill(1, 3, 0, WIDTH - 2, 3, 0, ModBlocks.building("lab_hazard"));
        // Tejado
        fill(-1, 9, -8, WIDTH, 9, DEPTH, ModBlocks.building("lab_ceiling_concrete"));
    }

    private void floorAndCeiling() {
        fill(0, 0, 0, WIDTH - 1, 0, DEPTH - 1, ModBlocks.building("lab_tile_floor_dark"));
        fill(1, 0, 1, WIDTH - 2, 0, DEPTH - 2, ModBlocks.building("lab_tile_floor"));
        // Lineas de guia del suelo, como en los hospitales: hacia los ascensores
        fill(SPAWN_X, 0, 3, SPAWN_X, 0, DEPTH - 2, ModBlocks.building("lab_linoleum"));
        fill(5, 0, 3, 35, 0, 3, ModBlocks.building("lab_linoleum"));
        // Franja de espera frente a los ascensores
        fill(1, 0, 1, WIDTH - 2, 0, 1, ModBlocks.building("lab_hazard"));

        fill(0, 8, 0, WIDTH - 1, 8, DEPTH - 1, ModBlocks.building("lab_ceiling"));
        BlockState light = ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState();
        for (int x = 5; x <= 35; x += 5) {
            for (int z = 4; z <= DEPTH - 3; z += 6) {
                boolean flicker = (x == 30 && z == 22) || (x == 10 && z == 10) || (x == 25 && z == 4) || (x == 35 && z == 40);
                boolean dead = (x == 15 && z == 16) || (x == 35 && z == 10);
                set(x, 8, z, light.setValue(FluorescentLightBlock.FLICKERS, flicker).setValue(FluorescentLightBlock.LIT, !dead));
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
        fill(17, 1, z, 17, 5, z, ModBlocks.building("lab_metal_wall"));
        fill(23, 1, z, 23, 5, z, ModBlocks.building("lab_metal_wall"));
        fill(18, 5, z, 22, 5, z, ModBlocks.building("lab_metal_wall"));
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
            BlockState wallPanel = ModBlocks.ELEVATOR_WALL_PANEL.get().defaultBlockState();
            BlockState frame = ModBlocks.ELEVATOR_FRAME.get().defaultBlockState();
            fill(c.x1(), 0, c.zMin(), c.x2(), 0, -1, ModBlocks.ELEVATOR_FLOOR.get().defaultBlockState());
            // Paredes interiores de paneles de acero, con el techo enmarcado
            fill(c.x1() - 1, 1, c.zMin(), c.x1() - 1, c.height(), -1, wallPanel);
            fill(c.x2() + 1, 1, c.zMin(), c.x2() + 1, c.height(), -1, wallPanel);
            fill(c.x1(), 1, c.zMin() - 1, c.x2(), c.height(), c.zMin() - 1, wallPanel);
            fill(c.x1() - 1, c.height() + 1, c.zMin() - 1, c.x2() + 1, c.height() + 1, -1, frame);
            int cx = (c.x1() + c.x2()) / 2;
            set(cx, c.height() + 1, (c.zMin() - 1) / 2, light);
            if (c.type() == ElevatorType.MEGA) set(cx + 2, c.height() + 1, (c.zMin() - 1) / 2, light);

            // Hueco de la puerta en la pared del fondo, con marco
            fill(c.door1() - 1, 1, 0, c.door2() + 1, 3, 0, frame);
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
        // Mostrador curvo delante del logotipo de la pared oeste (x 0, z 30..38)
        BlockState counter = Blocks.POLISHED_DIORITE.defaultBlockState();
        fill(7, 1, 30, 7, 1, 38, counter);
        fill(3, 1, 29, 7, 1, 29, counter);
        fill(3, 1, 39, 6, 1, 39, counter);
        BlockState monitor = Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        set(7, 2, 31, monitor);
        set(7, 2, 32, Blocks.GRAY_CARPET);
        set(7, 2, 34, Blocks.WHITE_CARPET);
        set(7, 2, 36, monitor);
        set(7, 2, 37, Blocks.POTTED_DEAD_BUSH);
        set(7, 2, 33, Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR)
                .setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.EAST));
        // Sillas del personal (vacias) y archivadores
        set(5, 1, 31, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
        set(5, 1, 36, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST).setValue(StairBlock.FACING, Direction.NORTH));
        set(2, 1, 33, ModBlocks.building("lab_crate"));
        set(2, 2, 33, ModBlocks.building("lab_crate"));
        set(2, 1, 35, Blocks.BOOKSHELF);
        hangingSign(5, 7, 34, 4, "RECEPCIÓN", "Información", "y admisión", "");
        wallSign(8, 1, 34, Direction.EAST, "Por favor,", "espere su", "turno", "Gracias :)");
        // Logotipo de la empresa, metido en la pared: la cara sonriente, un ojo que gotea rojo
        String[] logo = {
                "..YYYYY..",
                ".YYYYYYY.",
                "YYKYYYRYY",
                "YYYYYYRYY",
                "YKYYYYYKY",
                ".YKKKKKY.",
                "..YYYYY..",
        };
        for (int row = 0; row < logo.length; row++) {
            int y = 7 - row;
            for (int k = 0; k < 9; k++) {
                char c = logo[row].charAt(k);
                if (c == '.') continue;
                Block b = c == 'Y' ? Blocks.YELLOW_CONCRETE : c == 'K' ? Blocks.BLACK_CONCRETE : Blocks.RED_CONCRETE;
                set(0, y, 38 - k, b);
            }
        }
        wallSign(1, 1, 40, Direction.EAST, "S.M.I.L.E.", "Servicio Médico", "de Investigación", "y Lab. Exp.");
        set(1, 1, 28, Blocks.POTTED_DEAD_BUSH);
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
            fill(1, 1, z, 1, 2, z, ModBlocks.building("lab_metal_wall"));
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
        // Muro de desaparecidos: fotos (marcos negros) y nombres
        for (int z = 21; z <= 27; z += 2) {
            set(1, 4, z, Blocks.BLACK_CONCRETE);
            set(1, 5, z, Blocks.GRAY_CONCRETE);
        }
        wallSign(2, 3, 21, Direction.EAST, "DESAPARECIDO", "Dr. A. Ferrer", "Planta -3", "Morgue");
        wallSign(2, 3, 23, Direction.EAST, "DESAPARECIDA", "Enf. L. Prieto", "Planta -2", "Oficinas");
        wallSign(2, 3, 25, Direction.EAST, "DESAPARECIDO", "Téc. J. Ibáñez", "Planta -4", "Energía");
        wallSign(2, 3, 27, Direction.EAST, "DESAPARECIDA", "Niña sin", "identificar", "¿La ha visto?");
        hangingSign(4, 7, 24, 4, "¿LOS HA", "VISTO?", "Llame a", "seguridad");
        set(2, 1, 22, Blocks.POTTED_WITHER_ROSE);
        set(2, 1, 26, Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
    }

    private void eastWall() {
        int x = WIDTH - 2;
        // Televisores, uno apagado y otro con estatica
        fill(x, 3, 21, x, 4, 23, Blocks.BLACK_CONCRETE);
        fill(x, 3, 25, x, 4, 27, Blocks.BLACK_CONCRETE);
        set(x, 3, 26, Blocks.LIGHT_GRAY_STAINED_GLASS);
        set(x, 4, 26, Blocks.WHITE_STAINED_GLASS);
        wallSign(x, 2, 24, Direction.WEST, "NOTICIAS", "«Cierran el", "laboratorio", "por un brote»");
    }

    /** Sala de observacion: camillas con cortinas, goteros, y la puerta de urgencias precintada. */
    private void observation() {
        BlockState gurney = ModBlocks.GURNEY.get().defaultBlockState().setValue(com.smileprotocol.block.HideBlock.FACING, Direction.EAST);
        BlockState curtain = ModBlocks.CURTAIN.get().defaultBlockState().setValue(com.smileprotocol.block.HideBlock.FACING, Direction.NORTH);
        for (int z : new int[]{5, 9, 13}) {
            set(36, 1, z, gurney.setValue(com.smileprotocol.block.HideBlock.HALF, DoubleBlockHalf.LOWER));
            set(37, 1, z, gurney.setValue(com.smileprotocol.block.HideBlock.HALF, DoubleBlockHalf.UPPER));
            // gotero
            set(35, 1, z - 1, Blocks.IRON_BARS);
            set(35, 2, z - 1, Blocks.IRON_BARS);
            set(35, 3, z - 1, Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, false));
            if (z < 13) {
                for (int x = 34; x <= 38; x++) {
                    set(x, 1, z + 2, curtain.setValue(com.smileprotocol.block.HideBlock.HALF, DoubleBlockHalf.LOWER));
                    set(x, 2, z + 2, curtain.setValue(com.smileprotocol.block.HideBlock.HALF, DoubleBlockHalf.UPPER));
                }
            }
        }
        // Silla de ruedas
        set(31, 1, 14, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.EAST));
        set(32, 1, 14, Blocks.IRON_BARS.defaultBlockState());
        hangingSign(34, 7, 9, 12, "OBSERVACIÓN", "", "Silencio,", "por favor");
        // Urgencias: puerta de seguridad cerrada en la pared este, precintada
        BlockState door = ModBlocks.SECURITY_DOOR.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.WEST);
        set(WIDTH - 1, 1, 17, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(WIDTH - 1, 2, 17, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER));
        fill(WIDTH - 2, 1, 16, WIDTH - 2, 1, 18, ModBlocks.building("lab_hazard"));
        set(WIDTH - 2, 2, 16, Blocks.IRON_BARS);
        set(WIDTH - 2, 2, 18, Blocks.IRON_BARS);
        wallSign(WIDTH - 2, 3, 17, Direction.WEST, "URGENCIAS", "CERRADO POR", "PROTOCOLO", "S.M.I.L.E.");
    }

    /** Cafeteria: mesas con sillas, maquinas expendedoras, cafetera y papeleras. */
    private void cafeteria() {
        BlockState top = Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
        for (int x = 29; x <= 35; x += 3) {
            for (int z = 33; z <= 37; z += 4) {
                set(x, 1, z, top);
                set(x, 1, z - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
                set(x, 1, z + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            }
        }
        set(32, 2, 33, Blocks.FLOWER_POT);
        set(29, 2, 37, Blocks.WHITE_CARPET);
        Block[][] machines = {
                {ModBlocks.building("lab_metal_wall"), Blocks.LIGHT_BLUE_STAINED_GLASS},
                {Blocks.RED_CONCRETE, Blocks.WHITE_STAINED_GLASS},
                {ModBlocks.building("lab_metal_wall"), Blocks.LIME_STAINED_GLASS},
        };
        for (int i = 0; i < machines.length; i++) {
            int z = 31 + i * 2;
            set(WIDTH - 2, 1, z, machines[i][0]);
            set(WIDTH - 2, 2, z, machines[i][1]);
            set(WIDTH - 2, 3, z, ModBlocks.building("lab_metal_wall"));
        }
        set(WIDTH - 2, 1, 37, Blocks.SMOKER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));     // cafetera
        set(WIDTH - 2, 1, 38, Blocks.CAULDRON);
        hangingSign(33, 7, 35, 0, "CAFETERÍA", "", "Hoy: nada", "");
    }

    /** Control de seguridad: tornos delante de la entrada y la garita acristalada con sus monitores. */
    private void security() {
        int z = DEPTH - 6;
        for (int x = 13; x <= 27; x++) {
            if (x % 2 == 1) set(x, 1, z, Blocks.IRON_BARS);
        }
        set(12, 1, z, ModBlocks.building("lab_metal_wall"));
        set(28, 1, z, ModBlocks.building("lab_metal_wall"));
        // garita: x 32..39, z DEPTH-8 .. DEPTH-2
        int z0 = DEPTH - 8, z1 = DEPTH - 2;
        for (int y = 1; y <= 4; y++) {
            for (int x = 32; x <= 39; x++) {
                set(x, y, z0, y == 1 || y == 4 ? ModBlocks.building("lab_metal_wall").defaultBlockState()
                        : Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            }
            for (int zz = z0; zz <= z1; zz++) {
                if (zz == z1 - 2 && y <= 2) continue;                        // puerta
                set(32, y, zz, y == 1 || y == 4 ? ModBlocks.building("lab_metal_wall").defaultBlockState()
                        : Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            }
        }
        fill(33, 4, z0 + 1, 39, 4, z1, ModBlocks.building("lab_ceiling"));
        // monitores de las camaras: uno muestra el ascensor 3, otro esta en negro
        fill(39, 2, z0 + 1, 39, 3, z0 + 3, Blocks.BLACK_CONCRETE);
        set(39, 2, z0 + 1, Blocks.LIME_STAINED_GLASS);
        set(39, 3, z0 + 2, Blocks.GREEN_STAINED_GLASS);
        set(39, 2, z0 + 3, Blocks.LIME_STAINED_GLASS);
        fill(38, 1, z0 + 1, 38, 1, z0 + 3, ModBlocks.building("lab_metal_wall"));
        set(37, 1, z0 + 2, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
        wallSign(31, 3, z0 + 3, Direction.WEST, "SEGURIDAD", "", "Muestre su", "tarjeta");
        set(36, 2, z1, Blocks.POTTED_DEAD_BUSH);
    }

    private void details() {
        // Cartel de «suelo mojado» y el carro de limpieza abandonado
        set(SPAWN_X + 1, 1, 15, Blocks.YELLOW_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 2));
        set(SPAWN_X + 3, 1, 16, Blocks.CAULDRON);
        set(SPAWN_X + 4, 1, 16, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.EAST)
                .setValue(TrapDoorBlock.OPEN, false));
        // Columnas con carteles
        for (int x : new int[]{14, 26}) {
            for (int z : new int[]{12, 28}) {
                fill(x, 1, z, x, 7, z, ModBlocks.building("lab_wall_tiles_white"));
                set(x, 3, z, ModBlocks.building("lab_wall_tiles_teal"));
            }
        }
        wallSign(14, 4, 13, Direction.SOUTH, "PROTOCOLO", "Si algo le mira", "sin ojos, NO", "aparte la vista");
        wallSign(26, 4, 13, Direction.SOUTH, "PROTOCOLO", "Lleve linterna.", "En la oscuridad", "no está solo");
        wallSign(14, 4, 29, Direction.SOUTH, "PROTOCOLO", "No pase bajo", "los juguetes", "colgados");
        wallSign(26, 4, 29, Direction.SOUTH, "PROTOCOLO", "Si oye correr,", "escóndase y", "no respire");
        // Papeleras y algun papel por el suelo
        set(16, 1, 23, Blocks.CAULDRON);
        set(23, 1, 37, Blocks.WHITE_CARPET);
        set(11, 1, 18, Blocks.WHITE_CARPET);
        // Rastro de sangre muy discreto que entra en el ascensor 3
        BlockState blood = ModBlocks.BLOOD_FLOOR.get().defaultBlockState();
        int[][] trail = {{25, 9}, {26, 7}, {26, 5}, {27, 3}, {27, 2}};
        for (int i = 0; i < trail.length; i++) {
            set(trail[i][0], 1, trail[i][1], blood.setValue(com.smileprotocol.block.DecalBlock.VARIANT, i % 6));
        }
        // Bancos frente a los ascensores
        for (int x : new int[]{8, 30}) {
            fill(x, 1, 5, x + 2, 1, 5, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        }
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
