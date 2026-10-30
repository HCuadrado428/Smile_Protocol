package com.smileprotocol.instance;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.GroupState;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunData;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.session.SPSessionManager;
import com.smileprotocol.zone.ZoneCatalog;
import com.smileprotocol.zone.ZoneDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Huecos y zonas de la dimension de instancias (README 6.3, punto 2).
 * <ul>
 *   <li>Cada grupo tiene un <b>hueco</b>, separado de los demas por {@code instance_spacing}
 *       bloques. El hueco 0 es la sala de pruebas.</li>
 *   <li>Dentro del hueco hay dos <b>sub-huecos</b> a {@code zone_spacing} bloques: la zona actual
 *       y la siguiente, que se precarga mientras se juega. Nunca hay mas de dos por grupo.</li>
 *   <li>Las zonas se colocan desde plantillas NBT ({@code data/<ns>/structure/...}) con sus
 *       marcadores {@code sp:*}. Al sellar una zona se vacia poco a poco (sin parones), se
 *       retiran sus entidades y se libera su ticket de chunks.</li>
 * </ul>
 */
public final class InstanceManager {

    /** Mantiene cargados los chunks de una zona mientras existe (precarga incluida). */
    private static final TicketType<ChunkPos> ZONE_TICKET =
            TicketType.create("smileprotocol_zone", Comparator.comparingLong(ChunkPos::toLong));

    private static final int GRID_COLUMNS = 32;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Cursor de cada limpieza en curso (en memoria: tras un reinicio se empieza de nuevo). */
    private static final Map<RunData.ClearJob, BlockPos.MutableBlockPos> CURSORS = new IdentityHashMap<>();

    private InstanceManager() {}

    // Huecos -------------------------------------------------------------------------------

    public static BlockPos slotOrigin(int slot) {
        int spacing = SPConfig.get().instance_spacing;
        return new BlockPos((slot % GRID_COLUMNS) * spacing, SPConfig.get().instance_y, (slot / GRID_COLUMNS) * spacing);
    }

    public static BlockPos subOrigin(int slot, int sub) {
        return slotOrigin(slot).offset(sub * SPConfig.get().zone_spacing, 0, 0);
    }

    /** Hueco que contiene una posicion (-1 si cae entre huecos). */
    public static int slotAt(BlockPos pos) {
        int spacing = SPConfig.get().instance_spacing;
        int col = Math.floorDiv(pos.getX() + spacing / 4, spacing);
        int row = Math.floorDiv(pos.getZ() + spacing / 4, spacing);
        if (col < 0 || row < 0 || col >= GRID_COLUMNS) return -1;
        return row * GRID_COLUMNS + col;
    }

    /** Huecos ocupados: grupos de partidas abiertas y zonas que aun se estan limpiando. */
    private static Set<Integer> usedSlots(RunData data) {
        Set<Integer> used = new HashSet<>();
        used.add(0); // sala de pruebas
        for (Run r : RunManager.openRuns(data)) for (Group g : r.groups()) used.add(g.slot());
        for (RunData.ClearJob j : RunManager.clears(data)) used.add(j.slot());
        return used;
    }

    public static int allocateSlot(RunData data, Set<Integer> alsoTaken) {
        Set<Integer> used = usedSlots(data);
        used.addAll(alsoTaken);
        int s = 1;
        while (used.contains(s)) s++;
        return s;
    }

    @Nullable
    public static Group groupAt(ServerLevel level, BlockPos pos) {
        if (!SPDimension.isSP(level)) return null;
        int slot = slotAt(pos);
        if (slot <= 0) return null;
        for (Run r : RunManager.openRuns(RunData.get(level.getServer()))) {
            for (Group g : r.groups()) if (g.slot() == slot) return g;
        }
        return null;
    }

    // Colocar zonas -----------------------------------------------------------------------

    /**
     * Coloca la zona {@code index} de la secuencia del grupo en el sub-hueco {@code sub}.
     * Si hay una limpieza pendiente en ese sub-hueco, se termina antes.
     */
    @Nullable
    public static ZonePlacement place(ServerLevel level, Group group, int index, int sub) {
        MinecraftServer server = level.getServer();
        RunData data = RunData.get(server);
        if (index < 0 || index >= group.sequence().size()) return null;
        finishClears(level, data, group.slot(), sub);

        ResourceLocation zoneId = group.sequence().get(index);
        ZoneDef def = ZoneCatalog.get(server, zoneId);
        BlockPos origin = subOrigin(group.slot(), sub);
        int maxSize = SPConfig.get().zone_spacing - 16;

        StructureTemplate template = null;
        if (def != null) {
            Optional<StructureTemplate> t = server.getStructureManager().get(def.template());
            if (t.isPresent()) template = t.get();
            else SmileProtocol.LOGGER.error("Zona {}: no existe la plantilla {}", zoneId, def.template());
        } else {
            SmileProtocol.LOGGER.error("No existe la zona {}", zoneId);
        }

        BoundingBox box;
        Map<String, List<BlockPos>> markers = new LinkedHashMap<>();
        Float markerYaw = null;
        if (template != null) {
            Vec3i size = template.getSize();
            if (size.getX() > maxSize || size.getZ() > maxSize) {
                SmileProtocol.LOGGER.warn("Zona {}: la plantilla mide {}x{}, mas que zone_spacing - 16 ({}); puede solaparse",
                        zoneId, size.getX(), size.getZ(), maxSize);
            }
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setIgnoreEntities(false)
                    .setKnownShape(true)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            box = template.getBoundingBox(settings, origin);
            addTicket(level, box);
            template.placeInWorld(level, origin, origin, settings, RandomSource.create(group.id().getLeastSignificantBits() ^ index),
                    Block.UPDATE_CLIENTS);
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
                CompoundTag nbt = info.nbt();
                if (nbt == null) continue;
                String meta = nbt.getString("metadata").trim();
                if (!meta.startsWith("sp:")) continue;
                String[] parts = meta.substring(3).split("\\s+");
                // sp:item key red -> "item:key:red"
                String name = parts[0].equals("item") ? String.join(":", parts) : parts[0];
                markers.computeIfAbsent(name, k -> new ArrayList<>()).add(info.pos().immutable());
                if (name.equals("entry") && parts.length > 1) {
                    try {
                        markerYaw = Float.parseFloat(parts[1]);
                    } catch (NumberFormatException ignored) {
                        // sin angulo
                    }
                }
            }
        } else {
            box = fallbackRoom(level, origin);
        }

        // Entrada: marcador sp:entry, o la del JSON, o el centro
        Vec3 entry;
        float yaw;
        List<BlockPos> entries = markers.getOrDefault("entry", List.of());
        if (!entries.isEmpty()) {
            entry = Vec3.atBottomCenterOf(entries.get(0));
            yaw = markerYaw != null ? markerYaw : (def != null ? def.entryYaw() : 0f);
        } else if (def != null && def.entry() != null && template != null) {
            entry = Vec3.atBottomCenterOf(origin.offset(def.entry()));
            yaw = def.entryYaw();
        } else {
            entry = new Vec3(box.getCenter().getX() + 0.5, box.minY() + 1, box.getCenter().getZ() + 0.5);
            yaw = 0f;
        }

        // Escalado por tamano de grupo (README 6.3, punto 3)
        RandomSource rnd = RandomSource.create(group.id().getMostSignificantBits() + index * 31L);
        List<BlockPos> mobPoints = markers.getOrDefault("mob_spawn", List.of());
        List<BlockPos> fusePoints = markers.getOrDefault("fuse", List.of());
        List<BlockPos> mobs = pickRandom(mobPoints, group.difficulty().scaleMobs(mobPoints.size(), mobPoints.size()), rnd);
        // Cajas, fusibles, objetos, circuitos y puertas de las cabinas (README 14)
        List<BlockPos> fuses = template != null
                ? ZoneSetup.setup(level, box, markers, group.difficulty(), rnd)
                : List.of();

        if (template != null && !mobs.isEmpty()) {
            com.smileprotocol.entity.MobManager.spawnForZone(level, mobs, entry, def != null ? def.difficulty() : 1,
                    group.difficulty(), rnd);
            com.smileprotocol.entity.MobManager.spawnPaciente(level, zoneId, mobPoints, entry, rnd);
            com.smileprotocol.entity.MobManager.spawnRastreador(level, zoneId, mobPoints, entry, rnd);
            com.smileprotocol.entity.MobManager.spawnAhorcados(level, zoneId, mobPoints, entry, rnd);
        }
        ZonePlacement placement = new ZonePlacement(zoneId, index, sub, box, entry, yaw, markers, mobs, fuses);
        data.changed();
        SPDebug.log("zona " + zoneId + " (" + index + ") colocada para " + group.label() + " en " + origin.toShortString()
                + " mobs=" + mobs.size() + "/" + mobPoints.size() + " fusibles=" + fuses.size() + "/" + fusePoints.size());
        return placement;
    }

    private static List<BlockPos> pickRandom(List<BlockPos> from, int n, RandomSource rnd) {
        List<BlockPos> copy = new ArrayList<>(from);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            BlockPos t = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, t);
        }
        return List.copyOf(copy.subList(0, Math.max(0, Math.min(n, copy.size()))));
    }

    /** Sala de emergencia si falta la plantilla: el grupo no se queda en el vacio. */
    private static BoundingBox fallbackRoom(ServerLevel level, BlockPos origin) {
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 12, origin.getY() + 6, origin.getZ() + 12);
        addTicket(level, box);
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            boolean shell = p.getX() == box.minX() || p.getX() == box.maxX() || p.getY() == box.minY()
                    || p.getY() == box.maxY() || p.getZ() == box.minZ() || p.getZ() == box.maxZ();
            BlockState s = shell ? wall : Blocks.AIR.defaultBlockState();
            if (p.getY() == box.maxY() && p.getX() % 4 == 0 && p.getZ() % 4 == 0) s = Blocks.SEA_LANTERN.defaultBlockState();
            level.setBlock(p, s, Block.UPDATE_CLIENTS);
        }
        return box;
    }

    // Avanzar, precargar y liberar ---------------------------------------------------------------

    /**
     * Lleva al grupo a la zona {@code index}: la coloca si no estaba precargada, teletransporta a
     * los miembros activos a su entrada y sella la anterior. La siguiente se precarga despues.
     */
    public static boolean advance(MinecraftServer server, Group group, int index) {
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null || index < 0 || index > group.lastIndex()) return false;
        RunData data = RunData.get(server);

        ZonePlacement target = group.next();
        if (target == null || target.index() != index) {
            if (target != null) queueClear(data, group.slot(), target);
            int sub = group.current() == null ? 0 : 1 - group.current().sub();
            target = place(level, group, index, sub);
            if (target == null) return false;
        }

        ZonePlacement old = group.current();
        group.setCurrent(target);
        group.setNext(null);
        group.setZoneIndex(index);
        group.setState(index == 0 ? GroupState.INTRO : index == group.lastIndex() ? GroupState.FINAL : GroupState.ZONE);

        for (UUID id : group.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p)) {
                SPSessionManager.teleport(p, level, target.entry(), target.entryYaw(), 0f);
                FloorTitle.show(p, index, target.zoneId());
            }
        }
        if (old != null) queueClear(data, group.slot(), old);
        openEntry(level, target);

        InstanceContext ctx = InstanceContext.forGroup(server, group.id());
        if (ctx != null) ctx.resetZoneState();
        data.changed();
        com.smileprotocol.mechanism.LabEvents.fire(level, BlockPos.containing(target.entry()), null, "zone_entered", target.zoneId().getPath());
        SPDebug.log(group.label() + " -> zona " + index + " (" + target.zoneId() + ")");
        return true;
    }

    /** Coloca la zona de entrada de un grupo recien creado. */
    @Nullable
    public static ZonePlacement placeFirst(ServerLevel level, Group group) {
        ZonePlacement p = place(level, group, 0, 0);
        if (p != null) {
            openEntry(level, p);
            group.setCurrent(p);
            group.setZoneIndex(0);
            group.setState(GroupState.INTRO);
        }
        return p;
    }

    /** Al llegar: se abren las puertas de la cabina de entrada (README 6.4). */
    public static void openEntry(ServerLevel level, ZonePlacement p) {
        for (BlockPos d : p.marker("entry_door")) com.smileprotocol.block.ElevatorDoorBlock.setOpen(level, d, true);
    }

    /** Vacia las zonas del grupo (al acabar su partida). */
    public static void release(MinecraftServer server, Group group) {
        RunData data = RunData.get(server);
        if (group.current() != null) queueClear(data, group.slot(), group.current());
        if (group.next() != null) queueClear(data, group.slot(), group.next());
        group.setCurrent(null);
        group.setNext(null);
        InstanceContext.discard(group.id());
        data.changed();
    }

    private static void queueClear(RunData data, int slot, ZonePlacement p) {
        RunManager.clears(data).add(new RunData.ClearJob(slot, p.sub(), p.box()));
        data.changed();
    }

    // Tick ---------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return;
        RunData data = RunData.get(server);

        // Limpiezas con un presupuesto de bloques por tick
        int budget = SPConfig.get().zone_clear_blocks_per_tick;
        Iterator<RunData.ClearJob> it = RunManager.clears(data).iterator();
        while (it.hasNext() && budget > 0) {
            RunData.ClearJob job = it.next();
            int[] left = {budget};
            if (step(level, job, left)) {
                it.remove();
                CURSORS.remove(job);
                removeTicket(level, job.box());
                data.changed();
            }
            budget = left[0];
        }

        // Precarga: una zona por tick como mucho
        if (server.getTickCount() % 10 == 0) {
            for (Run r : RunManager.openRuns(data)) {
                for (Group g : r.groups()) {
                    if (g.state().isDone() || g.current() == null || g.next() != null) continue;
                    int nextIndex = g.zoneIndex() + 1;
                    if (nextIndex > g.lastIndex()) continue;
                    int sub = 1 - g.current().sub();
                    if (hasClear(data, g.slot(), sub)) continue;
                    ZonePlacement p = place(level, g, nextIndex, sub);
                    g.setNext(p);
                    data.changed();
                    return;
                }
            }
        }
    }

    /** Avanza una limpieza. Devuelve true al terminar. */
    private static boolean step(ServerLevel level, RunData.ClearJob job, int[] budget) {
        BoundingBox b = job.box();
        BlockPos.MutableBlockPos cur = CURSORS.get(job);
        if (cur == null) {
            // Primera vez: fuera las entidades (menos jugadores) y cursor al principio
            AABB area = AABB.of(b).inflate(2);
            for (Entity e : level.getEntities((Entity) null, area, e -> !(e instanceof Player))) e.discard();
            cur = new BlockPos.MutableBlockPos(b.minX(), b.minY(), b.minZ());
            CURSORS.put(job, cur);
        }
        while (budget[0] > 0) {
            if (!level.getBlockState(cur).isAir()) {
                level.setBlock(cur, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                budget[0] -= 4;
            } else {
                budget[0]--;
            }
            // Avance y -> z -> x
            if (cur.getY() < b.maxY()) cur.setY(cur.getY() + 1);
            else {
                cur.setY(b.minY());
                if (cur.getZ() < b.maxZ()) cur.setZ(cur.getZ() + 1);
                else {
                    cur.setZ(b.minZ());
                    if (cur.getX() < b.maxX()) cur.setX(cur.getX() + 1);
                    else return true;
                }
            }
        }
        return false;
    }

    private static boolean hasClear(RunData data, int slot, int sub) {
        for (RunData.ClearJob j : RunManager.clears(data)) if (j.slot() == slot && j.sub() == sub) return true;
        return false;
    }

    /** Termina ya las limpiezas pendientes de un sub-hueco (antes de colocar encima). */
    private static void finishClears(ServerLevel level, RunData data, int slot, int sub) {
        Iterator<RunData.ClearJob> it = RunManager.clears(data).iterator();
        while (it.hasNext()) {
            RunData.ClearJob job = it.next();
            if (job.slot() != slot || job.sub() != sub) continue;
            int[] unlimited = {Integer.MAX_VALUE};
            step(level, job, unlimited);
            it.remove();
            CURSORS.remove(job);
            removeTicket(level, job.box());
            data.changed();
        }
    }

    // Tickets de chunks ------------------------------------------------------------------------

    private static void addTicket(ServerLevel level, BoundingBox box) {
        ServerChunkCache cache = level.getChunkSource();
        cache.addRegionTicket(ZONE_TICKET, ticketCenter(box), ticketRadius(box), ticketCenter(box));
    }

    private static void removeTicket(ServerLevel level, BoundingBox box) {
        ServerChunkCache cache = level.getChunkSource();
        cache.removeRegionTicket(ZONE_TICKET, ticketCenter(box), ticketRadius(box), ticketCenter(box));
    }

    private static ChunkPos ticketCenter(BoundingBox box) {
        return new ChunkPos(box.getCenter());
    }

    private static int ticketRadius(BoundingBox box) {
        return Math.min(16, Math.max(box.getXSpan(), box.getZSpan()) / 32 + 1);
    }

    /** Reaplica los tickets tras un reinicio (no se guardan). */
    public static void onServerStarted(MinecraftServer server) {
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return;
        RunData data = RunData.get(server);
        for (Run r : RunManager.openRuns(data)) {
            for (Group g : r.groups()) {
                if (g.current() != null) addTicket(level, g.current().box());
                if (g.next() != null) addTicket(level, g.next().box());
            }
        }
        for (RunData.ClearJob j : RunManager.clears(data)) addTicket(level, j.box());
        CURSORS.clear();
        InstanceContext.clearAll();
    }

    public static List<String> debugLines(MinecraftServer server) {
        RunData data = RunData.get(server);
        List<String> l = new ArrayList<>();
        int clears = RunManager.clears(data).size();
        if (clears > 0) l.add(" limpiando " + clears + " zonas");
        return l;
    }
}
