package com.smileprotocol.circuit;

import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.run.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Circuitos (README 14.1). Un circuito esta encendido cuando todas sus cajas de fusibles de la
 * zona estan completas y sin fusibles fundidos. El estado sale de las propias cajas (que se
 * guardan con el mundo), asi que sobrevive a reinicios; cada instancia es independiente porque
 * solo se miran los bloques de su zona.
 */
public final class CircuitManager {

    private static final String KEY = "circuits";

    private CircuitManager() {}

    /** Zona donde buscar: la zona de la instancia que contiene la posicion, o 48 bloques alrededor. */
    public static BoundingBox scope(ServerLevel level, BlockPos pos) {
        if (SPDimension.isSP(level)) {
            Group g = InstanceManager.groupAt(level, pos);
            if (g != null) {
                for (ZonePlacement z : new ZonePlacement[]{g.current(), g.next()}) {
                    if (z != null && z.box().inflatedBy(2).isInside(pos)) return z.box().inflatedBy(2);
                }
            }
        }
        return new BoundingBox(pos).inflatedBy(48);
    }

    /** Recorre las entidades de bloque de una caja. */
    public static void forEachBlockEntity(ServerLevel level, BoundingBox box, Consumer<BlockEntity> action) {
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (box.isInside(be.getBlockPos())) found.add(be);
                }
            }
        }
        found.forEach(action);
    }

    public static boolean hasCircuit(ServerLevel level, BlockPos near, String circuit) {
        boolean[] any = {false};
        forEachBlockEntity(level, scope(level, near), be -> {
            if (be instanceof FuseBoxBlockEntity fb && fb.circuit().equals(circuit)) any[0] = true;
        });
        return any[0];
    }

    public static boolean isPowered(ServerLevel level, BlockPos near, String circuit) {
        if (circuit.isEmpty()) return true;
        int[] boxes = {0};
        boolean[] ok = {true};
        forEachBlockEntity(level, scope(level, near), be -> {
            if (be instanceof FuseBoxBlockEntity fb && fb.circuit().equals(circuit)) {
                boxes[0]++;
                if (!fb.isComplete()) ok[0] = false;
            }
        });
        return boxes[0] > 0 && ok[0];
    }

    /** Lo llama la caja al cambiar sus fusibles: recalcula y avisa a los bloques ligados si cambia. */
    public static void onFusesChanged(ServerLevel level, BlockPos boxPos, String circuit) {
        if (circuit.isEmpty()) return;
        boolean powered = isPowered(level, boxPos, circuit);
        Map<String, Boolean> cache = cache(level, boxPos);
        Boolean before = cache.put(circuit, powered);
        if (before != null && before == powered) return;
        if (before == null && !powered) return;
        notify(level, boxPos, circuit, powered);
    }

    /** Fuerza el estado de los bloques ligados (al colocar una zona). */
    public static void notify(ServerLevel level, BlockPos near, String circuit, boolean powered) {
        BoundingBox box = scope(level, near);
        forEachBlockEntity(level, box, be -> {
            if (be instanceof CircuitListener l && l.circuitId().equals(circuit)) l.onCircuit(level, powered);
            if (be instanceof FuseBoxBlockEntity fb && fb.circuit().equals(circuit)) fb.setPoweredVisual(powered);
        });
        if (powered) {
            level.playSound(null, near, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 0.6f);
        } else {
            level.playSound(null, near, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        SPDebug.log("circuito " + circuit + (powered ? " encendido" : " apagado") + " cerca de " + near.toShortString());
        com.smileprotocol.mechanism.ZoneElevators.onCircuit(level, near, circuit, powered);
        com.smileprotocol.mission.MissionManager.onCircuit(level, near, circuit, powered);
    }

    private static Map<String, Boolean> cache(ServerLevel level, BlockPos pos) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        if (ctx == null) return FALLBACK;
        return ctx.state(KEY, HashMap::new);
    }

    private static final Map<String, Boolean> FALLBACK = new HashMap<>();

    /** Circuitos de una zona y si estan encendidos (para depuracion). */
    public static Map<String, Boolean> describe(ServerLevel level, BoundingBox box) {
        Map<String, int[]> counts = new HashMap<>();
        forEachBlockEntity(level, box, be -> {
            if (be instanceof FuseBoxBlockEntity fb && !fb.circuit().isEmpty()) {
                int[] c = counts.computeIfAbsent(fb.circuit(), k -> new int[2]);
                c[0]++;
                if (fb.isComplete()) c[1]++;
            }
        });
        Map<String, Boolean> out = new HashMap<>();
        counts.forEach((k, v) -> out.put(k, v[0] == v[1]));
        return out;
    }
}
