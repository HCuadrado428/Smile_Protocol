package com.smileprotocol.instance;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Una zona colocada en el hueco de un grupo (README 6.3). Guarda donde esta, por donde se
 * entra y los marcadores de la plantilla. Los puntos de mobs y fusibles ya vienen escalados
 * por la dificultad del grupo: las fases 9 y 11 los usan para colocar fusibles y mobs.
 */
public final class ZonePlacement {

    private final ResourceLocation zoneId;
    private final int index;
    /** Sub-hueco (0 o 1) dentro del hueco del grupo: la zona actual y la siguiente se alternan. */
    private final int sub;
    private final BoundingBox box;
    private final Vec3 entry;
    private final float entryYaw;
    /** Todos los marcadores {@code sp:<nombre>} de la plantilla, en coordenadas del mundo. */
    private final Map<String, List<BlockPos>> markers;
    /** Puntos de aparicion de mobs elegidos tras escalar. */
    private final List<BlockPos> mobSpawns;
    /** Huecos de fusible elegidos tras escalar. */
    private final List<BlockPos> fuses;

    public ZonePlacement(ResourceLocation zoneId, int index, int sub, BoundingBox box, Vec3 entry, float entryYaw,
                         Map<String, List<BlockPos>> markers, List<BlockPos> mobSpawns, List<BlockPos> fuses) {
        this.zoneId = zoneId;
        this.index = index;
        this.sub = sub;
        this.box = box;
        this.entry = entry;
        this.entryYaw = entryYaw;
        this.markers = markers;
        this.mobSpawns = mobSpawns;
        this.fuses = fuses;
    }

    public ResourceLocation zoneId() { return zoneId; }
    public int index() { return index; }
    public int sub() { return sub; }
    public BoundingBox box() { return box; }
    public Vec3 entry() { return entry; }
    public float entryYaw() { return entryYaw; }
    public Map<String, List<BlockPos>> markers() { return Collections.unmodifiableMap(markers); }
    public List<BlockPos> marker(String name) { return markers.getOrDefault(name, List.of()); }
    public List<BlockPos> mobSpawns() { return mobSpawns; }
    public List<BlockPos> fuses() { return fuses; }

    /** Cabina de salida (marcadores {@code sp:exit_cabin} en dos esquinas), si la plantilla la define. */
    @Nullable
    public BoundingBox exitCabin() {
        List<BlockPos> c = marker("exit_cabin");
        if (c.size() < 2) return null;
        return BoundingBox.fromCorners(c.get(0), c.get(1));
    }

    public boolean contains(Vec3 pos) {
        return box.isInside(BlockPos.containing(pos));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Zone", zoneId.toString());
        t.putInt("Index", index);
        t.putInt("Sub", sub);
        t.putIntArray("Box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        t.putDouble("EX", entry.x);
        t.putDouble("EY", entry.y);
        t.putDouble("EZ", entry.z);
        t.putFloat("EYaw", entryYaw);
        CompoundTag m = new CompoundTag();
        markers.forEach((k, v) -> m.put(k, positions(v)));
        t.put("Markers", m);
        t.put("Mobs", positions(mobSpawns));
        t.put("Fuses", positions(fuses));
        return t;
    }

    @Nullable
    public static ZonePlacement load(CompoundTag t) {
        ResourceLocation id = ResourceLocation.tryParse(t.getString("Zone"));
        int[] b = t.getIntArray("Box");
        if (id == null || b.length < 6) return null;
        Map<String, List<BlockPos>> markers = new LinkedHashMap<>();
        CompoundTag m = t.getCompound("Markers");
        for (String k : m.getAllKeys()) markers.put(k, positions(m.get(k)));
        return new ZonePlacement(id, t.getInt("Index"), t.getInt("Sub"),
                new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                new Vec3(t.getDouble("EX"), t.getDouble("EY"), t.getDouble("EZ")), t.getFloat("EYaw"),
                markers, positions(t.get("Mobs")), positions(t.get("Fuses")));
    }

    private static LongArrayTag positions(List<BlockPos> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i).asLong();
        return new LongArrayTag(a);
    }

    private static List<BlockPos> positions(@Nullable Tag tag) {
        List<BlockPos> out = new ArrayList<>();
        if (tag instanceof LongArrayTag la) for (long l : la.getAsLongArray()) out.add(BlockPos.of(l));
        else if (tag instanceof ListTag) return out;
        return out;
    }
}
