package com.smileprotocol.entity;

import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.qte.QTEManager;
import com.smileprotocol.registry.ModEntities;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * El combate contra Smiley en el Laboratorio Cero y sus apariciones en las demas plantas
 * (docs/SMILERS.md). La zona del jefe se reconoce por su marcador {@code sp:smiley}.
 * <ul>
 *   <li>Intro: en cuanto el grupo se adentra en la sala se apagan las luces y aparece.</li>
 *   <li>Fase = 1 + generadores encendidos (circuitos gen1, gen2 y gen3), como mucho 3.</li>
 *   <li>Al encender el ultimo se llama al ascensor ({@code boss elevator} en la mision); el
 *   ascensor de salida no abre hasta que llega ({@code boss arrive}), y entonces Smiley queda
 *   encerrado en su celda.</li>
 *   <li>En el resto de plantas se le ve una vez, de lejos, al final de un pasillo.</li>
 * </ul>
 * El estado vive en la instancia: se borra al cambiar de planta.
 */
public final class SmileyFight {

    private static final String KEY = "smiley";
    public static final String[] GENERATORS = {"gen1", "gen2", "gen3"};

    static final class St {
        boolean started;
        boolean called;
        boolean arrived;
        boolean permaDark;
        @Nullable UUID smiley;
        long cameoAt = -1;
        boolean cameoDone;
        long relightAt = -1;
        final List<BlockPos> darkened = new ArrayList<>();
        final List<BlockPos> introDark = new ArrayList<>();
    }

    private SmileyFight() {}

    @Nullable
    private static St state(ServerLevel level, BlockPos pos) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        return ctx == null ? null : ctx.state(KEY, St::new);
    }

    public static boolean isArena(@Nullable ZonePlacement z) {
        return z != null && !z.marker("smiley").isEmpty();
    }

    /** 1 + generadores encendidos (como mucho 3). Sin generadores (fuera de la sala), 1. */
    public static int phase(ServerLevel level, BlockPos near) {
        int on = 0;
        for (String g : GENERATORS) if (CircuitManager.hasCircuit(level, near, g) && CircuitManager.isPowered(level, near, g)) on++;
        return phaseFor(on);
    }

    public static int phaseFor(int generatorsOn) {
        return 1 + Math.min(2, Math.max(0, generatorsOn));
    }

    // Tick ----------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return;
        long now = server.getTickCount();
        for (Run run : RunManager.openRuns(server)) {
            for (Group g : run.groups()) {
                if (g.state().isDone() || g.current() == null) continue;
                ZonePlacement z = g.current();
                InstanceContext ctx = InstanceContext.forGroup(server, g.id());
                if (ctx == null) continue;
                St st = ctx.state(KEY, St::new);
                List<ServerPlayer> players = players(server, g, z);
                if (players.isEmpty()) continue;
                if (st.relightAt > 0 && now >= st.relightAt) {
                    st.relightAt = -1;
                    if (!st.permaDark) setLights(level, st.darkened, true);
                    st.darkened.clear();
                }
                if (isArena(z)) {
                    if (!st.started) {
                        for (ServerPlayer p : players) {
                            if (p.position().distanceTo(z.entry()) > 9) {
                                startIntro(level, z, st, players);
                                break;
                            }
                        }
                    }
                } else if (SPConfig.get().smiley_cameos && !st.cameoDone) {
                    cameo(level, z, st, players, now);
                }
            }
        }
    }

    private static void startIntro(ServerLevel level, ZonePlacement z, St st, List<ServerPlayer> players) {
        st.started = true;
        BlockPos at = z.marker("smiley").get(0);
        // Se apaga todo lo que estaba encendido
        CircuitManager.forEachBlockEntity(level, z.box(), be -> {
            if (be instanceof FluorescentLightBlockEntity fl && lit(fl.getBlockState())) {
                st.introDark.add(fl.getBlockPos().immutable());
            }
        });
        setLights(level, st.introDark, false);
        Smiley s = MobManager.spawn(level, ModEntities.SMILEY.get(), at, level.random);
        if (s == null) return;
        Vec3 e = z.entry();
        float yaw = (float) Math.toDegrees(Math.atan2(-(e.x - s.getX()), e.z - s.getZ()));
        s.setYRot(yaw);
        s.setYBodyRot(yaw);
        s.setYHeadRot(yaw);
        s.startIntro();
        st.smiley = s.getUUID();
        level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
        message(players, "smileprotocol.smiley.intro");
        SPDebug.log("smiley: intro en " + at.toShortString());
    }

    /** Acaba la intro: vuelven las luces que habia. */
    static void onIntroEnd(ServerLevel level, Smiley s) {
        St st = state(level, s.blockPosition());
        if (st == null) return;
        setLights(level, st.introDark, true);
        st.introDark.clear();
    }

    static void onPhase(ServerLevel level, Smiley s, int phase) {
        St st = state(level, s.blockPosition());
        InstanceContext ctx = InstanceContext.at(level, s.blockPosition());
        Group g = ctx == null ? null : ctx.group(level.getServer());
        ZonePlacement z = g == null ? null : g.current();
        List<ServerPlayer> players = g == null || z == null ? List.of() : players(level.getServer(), g, z);
        message(players, "smileprotocol.smiley.phase" + phase);
        if (phase >= 3 && st != null && z != null) {
            // a oscuras: se apaga toda la sala y ya no vuelve (salvo lo que enciendan los generadores)
            st.permaDark = true;
            List<BlockPos> all = new ArrayList<>();
            CircuitManager.forEachBlockEntity(level, z.box(), be -> {
                if (be instanceof FluorescentLightBlockEntity fl && lit(fl.getBlockState())) all.add(fl.getBlockPos().immutable());
            });
            setLights(level, all, false);
            level.playSound(null, s.blockPosition(), ModSounds.EVENT_SURGE.get(), SoundSource.BLOCKS, 2f, 0.4f);
        }
    }

    /** Apaga las luces de alrededor durante un rato. */
    static void darken(ServerLevel level, BlockPos center, int radius, int ticks) {
        St st = state(level, center);
        List<BlockPos> off = new ArrayList<>();
        CircuitManager.forEachBlockEntity(level, new BoundingBox(center).inflatedBy(radius), be -> {
            if (be instanceof FluorescentLightBlockEntity fl && lit(fl.getBlockState())) off.add(fl.getBlockPos().immutable());
        });
        setLights(level, off, false);
        if (st != null) {
            st.darkened.addAll(off);
            st.relightAt = level.getServer().getTickCount() + ticks;
        }
    }

    private static boolean lit(BlockState st) {
        return st.hasProperty(FluorescentLightBlock.LIT) && st.getValue(FluorescentLightBlock.LIT);
    }

    private static void setLights(ServerLevel level, List<BlockPos> list, boolean on) {
        for (BlockPos p : list) {
            BlockState st = level.getBlockState(p);
            if (st.hasProperty(FluorescentLightBlock.LIT) && st.getValue(FluorescentLightBlock.LIT) != on) {
                level.setBlock(p, st.setValue(FluorescentLightBlock.LIT, on), 3);
            }
        }
    }

    // Mision ------------------------------------------------------------------------------------------

    /** Eventos {@code boss} de la mision: {@code elevator} (se llama al ascensor) y {@code arrive} (llega). */
    public static void event(ServerLevel level, Group g, ZonePlacement z, List<ServerPlayer> players, String arg) {
        InstanceContext ctx = InstanceContext.forGroup(level.getServer(), g.id());
        if (ctx == null) return;
        St st = ctx.state(KEY, St::new);
        switch (arg) {
            case "elevator" -> {
                st.called = true;
                message(players, "smileprotocol.smiley.elevator");
                for (ServerPlayer p : players) level.playSound(null, p.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, 0.4f);
            }
            case "arrive" -> {
                st.arrived = true;
                for (ServerPlayer p : players) {
                    if (QTEManager.isGrabbed(p)) QTEManager.clear(p);
                    if (SequenceManager.inSequence(p)) SequenceManager.clear(p);
                }
                if (st.smiley != null && level.getEntity(st.smiley) instanceof Smiley s && !z.marker("cell").isEmpty()) {
                    BlockPos cell = z.marker("cell").get(0);
                    s.seal(Vec3.atBottomCenterOf(cell));
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                            for (int dy = 0; dy <= 4; dy++) {
                                BlockPos b = cell.offset(dx, dy, dz);
                                if (level.getBlockState(b).isAir()) level.setBlock(b, Blocks.IRON_BARS.defaultBlockState(), 3);
                            }
                        }
                    }
                    level.playSound(null, cell, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 3f, 0.5f);
                    level.playSound(null, cell, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5f, 0.5f);
                }
                message(players, "smileprotocol.smiley.sealed");
            }
            default -> { }
        }
    }

    /** El ascensor de la sala no abre hasta que llega (tras encender los generadores y aguantar). */
    public static boolean holdsElevator(MinecraftServer server, Group g) {
        if (!isArena(g.current())) return false;
        InstanceContext ctx = InstanceContext.forGroup(server, g.id());
        if (ctx == null) return false;
        return !ctx.state(KEY, St::new).arrived;
    }

    // Apariciones en las demas plantas --------------------------------------------------------------

    private static void cameo(ServerLevel level, ZonePlacement z, St st, List<ServerPlayer> players, long now) {
        SPConfig.Values cfg = SPConfig.get();
        if (st.cameoAt < 0) {
            int min = Math.max(5, cfg.smiley_cameo_min), max = Math.max(min, cfg.smiley_cameo_max);
            st.cameoAt = now + (min + level.random.nextInt(max - min + 1)) * 20L;
            return;
        }
        if (now < st.cameoAt) return;
        ServerPlayer p = players.get(level.random.nextInt(players.size()));
        List<BlockPos> spots = new ArrayList<>(z.marker("mob_spawn"));
        java.util.Collections.shuffle(spots, new java.util.Random(now));
        for (BlockPos b : spots) {
            double d = Math.sqrt(b.distSqr(p.blockPosition()));
            if (d < 12 || d > 40) continue;
            Vec3 head = Vec3.atBottomCenterOf(b).add(0, 2.6, 0);
            if (level.clip(new ClipContext(p.getEyePosition(), head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    != HitResult.Type.MISS) continue;
            Smiley s = MobManager.spawn(level, ModEntities.SMILEY.get(), b, level.random);
            if (s == null) break;
            if (!level.noCollision(s, s.getBoundingBox())) {
                s.discard();
                continue;
            }
            s.startCameo();
            st.cameoDone = true;
            SPDebug.log("smiley: se deja ver en " + z.zoneId() + " a " + (int) d + " bloques de " + p.getGameProfile().getName());
            return;
        }
        st.cameoAt = now + 200;
    }

    /** Forzar una aparicion ya (pruebas y comando). */
    public static boolean cameoNow(ServerLevel level, ServerPlayer p) {
        Group g = RunManager.groupOf(p);
        if (g == null || g.current() == null) return false;
        InstanceContext ctx = InstanceContext.forGroup(level.getServer(), g.id());
        if (ctx == null) return false;
        St st = ctx.state(KEY, St::new);
        st.cameoDone = false;
        st.cameoAt = 0;
        cameo(level, g.current(), st, List.of(p), level.getServer().getTickCount());
        return st.cameoDone;
    }

    // Sitios -------------------------------------------------------------------------------------------

    /** Lejos de los jugadores, para arrastrar al ultimo. */
    @Nullable
    static Vec3 farPoint(ServerLevel level, Smiley s) {
        List<BlockPos> spots = spots(level, s);
        List<ServerPlayer> ps = level.getEntitiesOfClass(ServerPlayer.class, s.getBoundingBox().inflate(64));
        return spots.stream().max(Comparator.comparingDouble(b -> minDist(b, ps) + 0.3 * Math.sqrt(b.distSqr(s.blockPosition()))))
                .map(Vec3::atBottomCenterOf).orElse(null);
    }

    /** Donde aparece un smiler invocado: lejos de los jugadores. */
    @Nullable
    static BlockPos summonSpot(ServerLevel level, Smiley s, RandomSource rnd) {
        List<ServerPlayer> ps = level.getEntitiesOfClass(ServerPlayer.class, s.getBoundingBox().inflate(64));
        List<BlockPos> ok = new ArrayList<>();
        for (BlockPos b : spots(level, s)) if (minDist(b, ps) > 8) ok.add(b);
        return ok.isEmpty() ? null : ok.get(rnd.nextInt(ok.size()));
    }

    private static List<BlockPos> spots(ServerLevel level, Smiley s) {
        InstanceContext ctx = InstanceContext.at(level, s.blockPosition());
        Group g = ctx == null ? null : ctx.group(level.getServer());
        ZonePlacement z = g == null ? null : g.current();
        if (z == null) return List.of();
        List<BlockPos> l = new ArrayList<>(z.marker("summon"));
        if (l.isEmpty()) l.addAll(z.marker("mob_spawn"));
        return l;
    }

    private static double minDist(BlockPos b, List<ServerPlayer> ps) {
        double d = 999;
        for (ServerPlayer p : ps) d = Math.min(d, Math.sqrt(b.distSqr(p.blockPosition())));
        return d;
    }

    private static void message(List<ServerPlayer> players, String key) {
        Component c = Component.translatable(key).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        for (ServerPlayer p : players) p.displayClientMessage(c, false);
    }

    private static List<ServerPlayer> players(MinecraftServer server, Group g, ZonePlacement z) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p) && !p.isSpectator() && z.contains(p.position())) out.add(p);
        }
        return out;
    }

    /** Para el panel de depuracion. */
    public static String debugLine(MinecraftServer server, Group g) {
        InstanceContext ctx = InstanceContext.forGroup(server, g.id());
        if (ctx == null) return "";
        St st = ctx.state(KEY, St::new);
        return "smiley: " + (isArena(g.current()) ? "sala" + (st.started ? " empezada" : "") + (st.called ? " ascensor llamado" : "")
                + (st.arrived ? " encerrado" : "") : "aparicion " + (st.cameoDone ? "hecha" : "pendiente"));
    }
}
