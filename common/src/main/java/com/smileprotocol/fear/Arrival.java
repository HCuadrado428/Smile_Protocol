package com.smileprotocol.fear;

import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.labevent.LabActions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * La llegada a cada planta sale mal (docs/MIEDO.md): el ascensor frena de golpe, se va la luz de la
 * cabina y del vestibulo, algo se aleja corriendo por el pasillo y, al fondo, un portazo. La luz
 * vuelve sola al cabo de un rato, parpadeando.
 */
public final class Arrival {

    private record Dark(ResourceKey<Level> dim, List<BlockPos> lights, long until) {}

    private static final List<Dark> DARK = new ArrayList<>();
    /** Ticks a oscuras al llegar. */
    public static final int DARK_TICKS = 20 * 25;

    private Arrival() {}

    public static void start(ServerLevel level, ZonePlacement z, List<ServerPlayer> riders, int darkTicks) {
        Vec3 e = z.entry();
        BlockPos c = BlockPos.containing(e);
        // fuera las luces de alrededor de la llegada (las que esten encendidas; se apuntan para devolverlas)
        List<BlockPos> off = new ArrayList<>();
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-12, -2, -10), c.offset(12, 6, 14))) {
            BlockState st = level.getBlockState(bp);
            if (st.getBlock() instanceof FluorescentLightBlock && st.getValue(FluorescentLightBlock.LIT) && z.box().isInside(bp)) {
                level.setBlock(bp, st.setValue(FluorescentLightBlock.LIT, false), 3);
                off.add(bp.immutable());
            }
        }
        if (!off.isEmpty()) DARK.add(new Dark(level.dimension(), off, level.getServer().getTickCount() + darkTicks));
        level.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.4f, 0.55f);
        LabActions.scare(riders, LabActions.SCARE_SHAKE, 0.9f);
        // algo que estaba ahi se va corriendo por el pasillo, y al fondo da un portazo
        Vec3 fwd = Vec3.directionFromRotation(0, z.entryYaw());
        for (ServerPlayer p : riders) {
            FearDirector.queue(p, 2, e, SoundEvents.IRON_DOOR_CLOSE, 1.5f, 0.5f);
            for (int i = 0; i < 10; i++) {
                Vec3 at = e.add(fwd.scale(9 + i * 1.6)).add(0, 0.2, 0);
                FearDirector.queue(p, 34 + i * 6, at, SoundEvents.DEEPSLATE_STEP, 1.5f - i * 0.08f, 0.8f + p.getRandom().nextFloat() * 0.1f);
            }
            FearDirector.queue(p, 130, e.add(fwd.scale(28)), SoundEvents.WOODEN_DOOR_CLOSE, 2.5f, 0.5f);
            FearDirector.calm(p, 20 * 50);
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Dark> it = DARK.iterator(); it.hasNext(); ) {
            Dark d = it.next();
            if (now < d.until()) continue;
            it.remove();
            ServerLevel level = server.getLevel(d.dim());
            if (level == null) continue;
            for (BlockPos bp : d.lights()) {
                BlockState st = level.getBlockState(bp);
                if (st.getBlock() instanceof FluorescentLightBlock && !st.getValue(FluorescentLightBlock.LIT)) {
                    level.setBlock(bp, st.setValue(FluorescentLightBlock.LIT, true), 3);
                }
            }
            if (!d.lights().isEmpty()) com.smileprotocol.mechanism.MonsterLights.near(level, d.lights().get(0));
        }
    }

    /** Pruebas: cuantas luces siguen apagadas por una llegada. */
    public static int darkLights() {
        return DARK.stream().mapToInt(d -> d.lights().size()).sum();
    }

    public static void clearAll() {
        DARK.clear();
    }
}
