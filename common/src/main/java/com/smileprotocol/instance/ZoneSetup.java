package com.smileprotocol.instance;

import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.interact.InteractionManager;
import com.smileprotocol.item.FuseItem;
import com.smileprotocol.item.KeyItem;
import com.smileprotocol.item.SPItems;
import com.smileprotocol.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepara una zona recien colocada (README 6.3, 14):
 * <ul>
 *   <li>escala las cajas de fusibles por el tamano del grupo y reparte los fusibles que hacen falta
 *       (mas un fundido de senuelo si sobran sitios);</li>
 *   <li>pone los objetos de los marcadores {@code sp:item <id> [parametro]};</li>
 *   <li>deja apagado lo que depende de un circuito y cerradas las puertas hidraulicas;</li>
 *   <li>busca las puertas de las cabinas de entrada y salida y les asigna el recorrido.</li>
 * </ul>
 */
public final class ZoneSetup {

    private ZoneSetup() {}

    /** Devuelve las posiciones donde se han dejado fusibles. Anade los marcadores de puertas. */
    public static List<BlockPos> setup(ServerLevel level, BoundingBox box, Map<String, List<BlockPos>> markers,
                                       DifficultyProfile difficulty, RandomSource rnd) {
        Map<String, Integer> required = new LinkedHashMap<>();
        Set<String> circuits = new HashSet<>();
        List<BlockPos> doors = new ArrayList<>();
        List<BlockPos> labDoors = new ArrayList<>();
        List<FluorescentLightBlockEntity> lights = new ArrayList<>();

        CircuitManager.forEachBlockEntity(level, box, be -> {
            if (be instanceof FuseBoxBlockEntity fb) {
                int need = Math.min(4, difficulty.scaleFuses(fb.slots()));
                fb.setRequired(need);
                if (!fb.circuit().isEmpty()) {
                    circuits.add(fb.circuit());
                    required.merge(fb.circuit(), need, Integer::sum);
                }
            } else if (be instanceof ElevatorDoorBlockEntity) {
                BlockState st = be.getBlockState();
                if (st.hasProperty(ElevatorDoorBlock.HALF) && st.getValue(ElevatorDoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    doors.add(be.getBlockPos().immutable());
                }
            } else if (be instanceof LabDoorBlockEntity) {
                labDoors.add(be.getBlockPos().immutable());
            } else if (be instanceof FluorescentLightBlockEntity fl && !fl.circuitId().isEmpty()) {
                lights.add(fl);
            }
        });

        // Lo ligado a un circuito empieza sin corriente
        for (FluorescentLightBlockEntity fl : lights) fl.onCircuit(level, false);
        for (BlockPos d : labDoors) LabDoorBlock.refresh(level, d);

        // Puertas de las cabinas: las de la cabina de salida y el resto (entrada)
        BoundingBox exit = exitCabin(markers);
        List<BlockPos> exitDoors = new ArrayList<>(), entryDoors = new ArrayList<>();
        for (BlockPos d : doors) {
            if (exit != null && exit.inflatedBy(2).isInside(d)) exitDoors.add(d);
            else entryDoors.add(d);
        }
        if (!exitDoors.isEmpty()) {
            ElevatorDoorBlockEntity.assignSlides(level, exitDoors);
            for (BlockPos d : exitDoors) ElevatorDoorBlock.setOpen(level, d, false);
            markers.put("exit_door", exitDoors);
        }
        if (!entryDoors.isEmpty()) {
            ElevatorDoorBlockEntity.assignSlides(level, entryDoors);
            markers.put("entry_door", entryDoors);
        }

        // Fusibles: los que hacen falta en total, en sitios al azar (README 14.1)
        int total = 0;
        for (int n : required.values()) total += n;
        List<BlockPos> spots = new ArrayList<>(markers.getOrDefault("fuse", List.of()));
        shuffle(spots, rnd);
        List<BlockPos> used = new ArrayList<>();
        for (int i = 0; i < spots.size() && used.size() < total; i++) {
            spawn(level, spots.get(i), new ItemStack(ModItems.FUSE.get()));
            used.add(spots.get(i));
        }
        // Un fundido de senuelo si sobra sitio
        if (spots.size() > used.size() && total > 0) {
            BlockPos p = spots.get(used.size());
            spawn(level, p, new ItemStack(ModItems.FUSE_BLOWN.get()));
            used.add(p);
        }

        // Objetos sueltos en los sitios que sobran (README 10.1, 12.7): consumibles y lanzables al azar,
        // mas lanzables que curas. Hasta que lleguen los muebles registrables (fase 24).
        String[] loot = {"can", "bottle", "can", "bottle", "battery", "medkit", "flare", "can", "adrenaline"};
        int extra = Math.min(spots.size() - used.size(), 2 + rnd.nextInt(3));
        for (int i = 0; i < extra; i++) {
            BlockPos p = spots.get(used.size());
            spawn(level, p, itemFor(loot[rnd.nextInt(loot.length)]));
            used.add(p);
        }

        // Objetos de los marcadores sp:item
        for (Map.Entry<String, List<BlockPos>> e : markers.entrySet()) {
            if (!e.getKey().startsWith("item:")) continue;
            ItemStack stack = itemFor(e.getKey().substring(5));
            if (stack.isEmpty()) continue;
            for (BlockPos p : e.getValue()) spawn(level, p, stack.copy());
        }
        return used;
    }

    /** {@code key:red}, {@code keycard:2}, {@code wheel}, {@code fuse:b}, {@code fuse_blown}. */
    static ItemStack itemFor(String spec) {
        String[] parts = spec.split(":");
        String id = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";
        return switch (id) {
            case "key" -> SPItems.withString(new ItemStack(ModItems.KEY.get()), KeyItem.LOCK, arg.isEmpty() ? "red" : arg);
            case "keycard" -> new ItemStack(switch (arg) {
                case "3" -> ModItems.KEYCARD_3.get();
                case "2" -> ModItems.KEYCARD_2.get();
                default -> ModItems.KEYCARD_1.get();
            });
            case "wheel", "valve_wheel" -> new ItemStack(ModItems.VALVE_WHEEL.get());
            case "fuse" -> arg.isEmpty() ? new ItemStack(ModItems.FUSE.get())
                    : SPItems.withString(new ItemStack(ModItems.FUSE.get()), FuseItem.TYPE, arg);
            case "fuse_blown" -> new ItemStack(ModItems.FUSE_BLOWN.get());
            case "medkit" -> new ItemStack(ModItems.MEDKIT.get());
            case "battery" -> new ItemStack(ModItems.BATTERY.get());
            case "flare" -> new ItemStack(ModItems.FLARE.get());
            case "adrenaline", "syringe" -> new ItemStack(ModItems.ADRENALINE.get());
            case "can" -> new ItemStack(ModItems.CAN.get());
            case "bottle" -> new ItemStack(ModItems.BOTTLE.get());
            default -> ItemStack.EMPTY;
        };
    }

    private static void spawn(ServerLevel level, BlockPos pos, ItemStack stack) {
        InteractionManager.spawnItem(level, Vec3.atBottomCenterOf(pos), stack).setDeltaMovement(Vec3.ZERO);
    }

    private static BoundingBox exitCabin(Map<String, List<BlockPos>> markers) {
        List<BlockPos> c = markers.getOrDefault("exit_cabin", List.of());
        return c.size() < 2 ? null : BoundingBox.fromCorners(c.get(0), c.get(1));
    }

    private static <T> void shuffle(List<T> list, RandomSource rnd) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }
}
