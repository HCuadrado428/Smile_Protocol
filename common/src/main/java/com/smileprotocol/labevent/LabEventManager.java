package com.smileprotocol.labevent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eventos de laboratorio y jumpscares (README 14.7): fichas en {@code data/smileprotocol/lab_events}
 * con un disparador, condiciones (una vez o con espera, probabilidad, jugadores minimos, planta),
 * un retraso, a quien afecta y una lista de acciones ({@link LabActions}). Todo en el servidor;
 * los sustos visuales y sonoros van solo a quien toca, con un presupuesto por jugador para no
 * saturar. El estado (eventos ya disparados) es por instancia.
 */
public final class LabEventManager {

    record Def(String id, String trigger, String arg, boolean once, int cooldown, float chance, int minPlayers,
               String zone, int delay, String scope, List<JsonObject> actions) {}

    /** Accion pendiente (retraso) o efecto que dura (parpadeo, apagon, alarma). */
    private static final class Running {
        final LabActions.Ctx ctx;
        final String kind;
        final String arg;
        final List<JsonObject> actions;
        int left;
        final Set<BlockPos> changed = new HashSet<>();

        Running(LabActions.Ctx ctx, String kind, int left, String arg, List<JsonObject> actions) {
            this.ctx = ctx;
            this.kind = kind;
            this.left = left;
            this.arg = arg;
            this.actions = actions;
        }
    }

    private static final FileToIdConverter FILES = FileToIdConverter.json("lab_events");
    @Nullable private static ResourceManager loadedFrom;
    private static List<Def> defs = List.of();
    private static final List<Running> RUNNING = new ArrayList<>();
    private static final Map<UUID, Long> LAST_SCARE = new HashMap<>();
    private static final Map<Entity, Integer> DESPAWN = new HashMap<>();
    /** Ultimo fusible retirado por cada jugador (para {@code fuse_moved}). */
    private static final Map<UUID, Object[]> LAST_REMOVED = new HashMap<>();

    private LabEventManager() {}

    private static List<Def> defs(MinecraftServer server) {
        ResourceManager rm = server.getResourceManager();
        if (rm != loadedFrom) {
            loadedFrom = rm;
            List<Def> list = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(rm).entrySet()) {
                String name = FILES.fileToId(e.getKey()).getPath();
                try (Reader r = e.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    JsonObject t = o.getAsJsonObject("trigger");
                    JsonObject c = o.has("conditions") ? o.getAsJsonObject("conditions") : new JsonObject();
                    List<JsonObject> acts = new ArrayList<>();
                    JsonArray arr = o.has("actions") ? o.getAsJsonArray("actions") : new JsonArray();
                    for (JsonElement a : arr) acts.add(a.getAsJsonObject());
                    list.add(new Def(LabActions.s(o, "id", name), LabActions.s(t, "type", ""), LabActions.s(t, "arg", ""),
                            c.has("once") && c.get("once").getAsBoolean(), LabActions.i(c, "cooldown", 0), LabActions.f(c, "chance", 1f),
                            LabActions.i(c, "min_players", 0), LabActions.s(c, "zone", ""), LabActions.i(o, "delay_ticks", 0),
                            LabActions.s(o, "scope", "players_in_zone"), acts));
                } catch (Exception ex) {
                    SmileProtocol.LOGGER.error("Evento de laboratorio {} no valido: {}", name, ex.toString());
                }
            }
            defs = list;
        }
        return defs;
    }

    // Disparadores -------------------------------------------------------------------------------

    /** Lo llama {@code LabEvents.fire} con cualquier disparador. */
    public static void onTrigger(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, String trigger, String arg) {
        // fuse_moved: un fusible retirado de una caja y puesto en otra por el mismo jugador
        if (player != null && "fuse_removed".equals(trigger)) {
            LAST_REMOVED.put(player.getUUID(), new Object[]{pos.immutable(), arg, level.getGameTime()});
        } else if (player != null && "fuse_inserted".equals(trigger)) {
            Object[] last = LAST_REMOVED.remove(player.getUUID());
            if (last != null && !last[0].equals(pos) && level.getGameTime() - (long) last[2] < 20 * 120) {
                fire(level, pos, player, "fuse_moved", last[1] + ">" + arg);
            }
        }
        fire(level, pos, player, trigger, arg);
    }

    private static void fire(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, String trigger, String arg) {
        InstanceContext ctx = InstanceContext.at(level, pos);
        if (ctx == null) return;
        Group g = ctx.group(level.getServer());
        ZonePlacement z = g == null ? null : g.current();
        if (z == null) return;
        Map<String, Long> fired = ctx.state("lab_events", HashMap::new);
        long now = level.getGameTime();
        List<ServerPlayer> group = groupPlayers(level.getServer(), g);
        for (Def d : defs(level.getServer())) {
            if (!d.trigger().equals(trigger)) continue;
            if (!d.arg().isEmpty() && !d.arg().equals(arg) && !(d.arg().endsWith("*") && arg.startsWith(d.arg().substring(0, d.arg().length() - 1)))) continue;
            if (!d.zone().isEmpty() && !z.zoneId().getPath().equals(d.zone()) && !z.zoneId().toString().equals(d.zone())) continue;
            Long last = fired.get(d.id());
            if (last != null && (d.once() || now - last < d.cooldown())) continue;
            if (d.chance() < 1f && level.random.nextFloat() >= d.chance()) continue;
            List<ServerPlayer> inZone = new ArrayList<>();
            for (ServerPlayer p : group) if (z.contains(p.position())) inZone.add(p);
            if (inZone.size() < d.minPlayers()) continue;
            fired.put(d.id(), now);
            List<ServerPlayer> targets = switch (d.scope()) {
                case "trigger_player" -> player != null ? List.of(player) : inZone;
                case "all" -> group;
                default -> inZone;
            };
            LabActions.Ctx c = new LabActions.Ctx(level, z, pos.immutable(), player, targets, group);
            if (d.delay() > 0) RUNNING.add(new Running(c, "delay", d.delay(), d.id(), d.actions()));
            else runAll(c, d.id(), d.actions());
        }
    }

    private static void runAll(LabActions.Ctx c, String id, List<JsonObject> actions) {
        boolean real = false;
        for (JsonObject a : actions) real |= LabActions.run(c, a);
        SPDebug.log("evento de laboratorio " + id + (real ? " (amenaza real)" : " (inofensivo)") + " en " + c.pos().toShortString());
    }

    // Presupuesto de sustos, efectos que duran y temporizador ------------------------------------

    /** Un jumpscare por jugador cada {@code jumpscare_cooldown} segundos como mucho. */
    static boolean budget(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = LAST_SCARE.get(p.getUUID());
        if (last != null && now - last < SPConfig.get().jumpscare_cooldown * 20L) return false;
        LAST_SCARE.put(p.getUUID(), now);
        return true;
    }

    static void schedule(LabActions.Ctx c, String kind, int ticks, String arg) {
        Running r = new Running(c, kind, ticks, arg, List.of());
        if ("blackout".equals(kind)) {
            // apaga y recuerda cuales estaban encendidos para devolverlos al acabar
            CircuitManager.forEachBlockEntity(c.level(), c.zone().box(), be -> {
                if (be instanceof FluorescentLightBlockEntity fl && (arg.isEmpty() || arg.equals(fl.circuitId()))) {
                    BlockState st = fl.getBlockState();
                    if (st.hasProperty(FluorescentLightBlock.LIT) && st.getValue(FluorescentLightBlock.LIT)) {
                        r.changed.add(fl.getBlockPos().immutable());
                        c.level().setBlock(fl.getBlockPos(), st.setValue(FluorescentLightBlock.LIT, false), 3);
                    }
                }
            });
            c.level().playSound(null, c.pos(), ModSounds.EVENT_SURGE.get(), SoundSource.BLOCKS, 1.2f, 0.8f);
        }
        if ("relight".equals(kind)) {
            r.left = ticks;
        }
        RUNNING.add(r);
    }

    static void despawnLater(Entity e, int ticks) {
        DESPAWN.put(e, ticks);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Running> it = RUNNING.iterator();
        List<Running> done = new ArrayList<>();
        while (it.hasNext()) {
            Running r = it.next();
            r.left--;
            ServerLevel level = r.ctx.level();
            switch (r.kind) {
                case "flicker" -> {
                    if (r.left % 3 == 0) {
                        CircuitManager.forEachBlockEntity(level, r.ctx.zone().box(), be -> {
                            if (!(be instanceof FluorescentLightBlockEntity fl) || !(r.arg.isEmpty() || r.arg.equals(fl.circuitId()))) return;
                            if (fl.getBlockPos().distSqr(r.ctx.pos()) > 24 * 24) return;
                            BlockState st = fl.getBlockState();
                            if (!st.hasProperty(FluorescentLightBlock.LIT)) return;
                            boolean lit = st.getValue(FluorescentLightBlock.LIT);
                            if (lit) r.changed.add(fl.getBlockPos().immutable());
                            if (r.changed.contains(fl.getBlockPos()) && level.random.nextFloat() < 0.5f) {
                                level.setBlock(fl.getBlockPos(), st.setValue(FluorescentLightBlock.LIT, !lit), 3);
                            }
                        });
                    }
                }
                case "alarm" -> {
                    if (r.left % 48 == 47 || r.left == 0) {
                        level.playSound(null, r.ctx.pos(), ModSounds.EVENT_ALARM.get(), SoundSource.BLOCKS, 2.5f, 1f);
                    }
                }
                default -> { }
            }
            if (r.left <= 0) {
                it.remove();
                done.add(r);
            }
        }
        for (Running r : done) {
            switch (r.kind) {
                case "delay" -> runAll(r.ctx, r.arg, r.actions);
                case "flicker", "blackout" -> {
                    for (BlockPos p : r.changed) {
                        BlockState st = r.ctx.level().getBlockState(p);
                        if (st.hasProperty(FluorescentLightBlock.LIT)) r.ctx.level().setBlock(p, st.setValue(FluorescentLightBlock.LIT, true), 3);
                    }
                }
                case "relight" -> LabActions.setLights(r.ctx, true, "", 1f);
                default -> { }
            }
        }
        // smilers que se asoman y desaparecen
        Iterator<Map.Entry<Entity, Integer>> dit = DESPAWN.entrySet().iterator();
        while (dit.hasNext()) {
            Map.Entry<Entity, Integer> e = dit.next();
            int left = e.getValue() - 1;
            if (left <= 0 || e.getKey().isRemoved()) {
                if (!e.getKey().isRemoved() && e.getKey().level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, e.getKey().getX(), e.getKey().getY() + 1,
                            e.getKey().getZ(), 12, 0.3, 0.8, 0.3, 0.01);
                    e.getKey().discard();
                }
                dit.remove();
            } else {
                e.setValue(left);
            }
        }
        // temporizador: un disparador "timer" por grupo cada segundo (las fichas ponen la probabilidad)
        if (server.getTickCount() % 20 == 0) {
            ServerLevel level = server.getLevel(SPDimension.SP_DIM);
            if (level == null) return;
            for (Run run : RunManager.openRuns(server)) {
                for (Group g : run.groups()) {
                    if (g.state().isDone() || g.current() == null) continue;
                    List<ServerPlayer> ps = groupPlayers(server, g);
                    if (ps.isEmpty()) continue;
                    ServerPlayer p = ps.get(level.random.nextInt(ps.size()));
                    fire(level, p.blockPosition(), p, "timer", "");
                }
            }
        }
    }

    private static List<ServerPlayer> groupPlayers(MinecraftServer server, Group g) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p) && g.id().equals(SPPlayerData.of(p).getGroupId()) && !p.isSpectator()) out.add(p);
        }
        return out;
    }

    /** Efectos en marcha (pruebas y depuracion). */
    public static int running() {
        return RUNNING.size();
    }

    public static void onLogout(ServerPlayer p) {
        LAST_SCARE.remove(p.getUUID());
        LAST_REMOVED.remove(p.getUUID());
    }
}
