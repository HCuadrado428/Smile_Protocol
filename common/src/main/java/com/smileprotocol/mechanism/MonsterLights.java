package com.smileprotocol.mechanism;

import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Las luces fallan cuando hay un monstruo cerca: los fluorescentes encendidos a pocos bloques se
 * ponen a parpadear y vuelven a la normalidad un rato despues de que se aleje.
 */
public final class MonsterLights {

    private record Key(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<Key, Long> FLICKERING = new HashMap<>();

    private MonsterLights() {}

    /** Lo llama cada monstruo de vez en cuando. */
    public static void near(ServerLevel level, BlockPos at) {
        long until = level.getServer().getTickCount() + 100;
        CircuitManager.forEachBlockEntity(level, new BoundingBox(at).inflatedBy(4), be -> {
            if (!(be instanceof FluorescentLightBlockEntity)) return;
            BlockState st = be.getBlockState();
            if (!st.hasProperty(FluorescentLightBlock.LIT) || !st.getValue(FluorescentLightBlock.LIT)) return;
            Key k = new Key(level.dimension(), be.getBlockPos().immutable());
            if (!st.getValue(FluorescentLightBlock.FLICKERS)) {
                // ya parpadeaba por si sola: no es nuestra, no se toca
                if (!FLICKERING.containsKey(k)) {
                    level.setBlock(k.pos(), st.setValue(FluorescentLightBlock.FLICKERS, true), 3);
                    FLICKERING.put(k, until);
                }
            } else if (FLICKERING.containsKey(k)) {
                FLICKERING.put(k, until);
            }
        });
    }

    public static void tick(MinecraftServer server) {
        if (FLICKERING.isEmpty() || server.getTickCount() % 20 != 0) return;
        long now = server.getTickCount();
        for (Iterator<Map.Entry<Key, Long>> it = FLICKERING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Long> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerLevel level = server.getLevel(e.getKey().dim());
            if (level == null || !level.isLoaded(e.getKey().pos())) continue;
            BlockState st = level.getBlockState(e.getKey().pos());
            if (st.hasProperty(FluorescentLightBlock.FLICKERS) && st.getValue(FluorescentLightBlock.FLICKERS)) {
                level.setBlock(e.getKey().pos(), st.setValue(FluorescentLightBlock.FLICKERS, false), 3);
            }
        }
    }
}
