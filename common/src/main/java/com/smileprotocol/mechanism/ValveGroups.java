package com.smileprotocol.mechanism;

import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.block.ValveBlock;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.debug.SPDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Grupos de valvulas y las puertas hidraulicas que abren (README 14.4). */
public final class ValveGroups {

    private ValveGroups() {}

    public static List<ValveBlockEntity> valves(ServerLevel level, BlockPos near, String group) {
        List<ValveBlockEntity> out = new ArrayList<>();
        CircuitManager.forEachBlockEntity(level, CircuitManager.scope(level, near), be -> {
            if (be instanceof ValveBlockEntity v && v.group().equals(group)) out.add(v);
        });
        return out;
    }

    public static boolean isTurned(ValveBlockEntity v) {
        BlockState st = v.getBlockState();
        return st.hasProperty(ValveBlock.TURNED) && st.getValue(ValveBlock.TURNED);
    }

    public static boolean isComplete(ServerLevel level, BlockPos near, String group) {
        if (group.isEmpty()) return false;
        List<ValveBlockEntity> vs = valves(level, near, group);
        if (vs.isEmpty()) return false;
        for (ValveBlockEntity v : vs) if (!isTurned(v)) return false;
        return true;
    }

    /** Todas giradas en orden: se reinicia el grupo con un golpe de presion (y ruido). */
    public static void resetGroup(ServerLevel level, BlockPos near, String group) {
        for (ValveBlockEntity v : valves(level, near, group)) {
            BlockState st = v.getBlockState();
            if (st.hasProperty(ValveBlock.TURNED) && st.getValue(ValveBlock.TURNED)) {
                level.setBlock(v.getBlockPos(), st.setValue(ValveBlock.TURNED, false), Block.UPDATE_ALL);
                v.setTurnedAt(-1);
            }
        }
        level.playSound(null, near, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.5f);
    }

    /** Tras girar una: si el grupo esta completo, abre sus puertas hidraulicas. */
    public static void onChanged(ServerLevel level, BlockPos near, String group) {
        boolean complete = isComplete(level, near, group);
        BoundingBox box = CircuitManager.scope(level, near);
        CircuitManager.forEachBlockEntity(level, box, be -> {
            if (be instanceof LabDoorBlockEntity d && d.type() == DoorType.HYDRAULIC && d.param().equals(group)) {
                LabDoorBlock.setOpenAt(level, d.getBlockPos(), complete);
            }
        });
        if (complete) {
            level.playSound(null, near, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, 0.5f);
            SPDebug.log("valvulas " + group + " completas");
            LabEvents.fire(level, near, null, "valve_group_complete", group);
        }
    }
}
