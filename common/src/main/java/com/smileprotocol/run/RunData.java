package com.smileprotocol.run;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado persistente de las partidas (README 6.1, 6.3, 6.8): partidas en curso con sus grupos y
 * zonas, la ultima secuencia de cada ascensor, las veces que ha jugado cada jugador, el estado
 * del mega ascensor, los grupos fijados por el administrador y las zonas pendientes de limpiar.
 * Sobrevive a reinicios y caidas del servidor.
 */
public final class RunData extends SavedData {

    private static final String NAME = "smileprotocol_runs";
    private static final SavedData.Factory<RunData> FACTORY =
            new SavedData.Factory<>(RunData::new, RunData::load, null);

    /** Una zona que hay que vaciar (sub-hueco {@code sub} del hueco {@code slot}). */
    public record ClearJob(int slot, int sub, BoundingBox box) {}

    final Map<String, Run> runs = new LinkedHashMap<>();
    int counter;
    final Map<String, List<ResourceLocation>> lastSequence = new HashMap<>();
    final Map<UUID, Integer> plays = new HashMap<>();
    /** El mega no se reabre hasta {@code /smileprotocol newrun} (con {@code auto_newrun = false}). */
    boolean megaLocked;
    /** Tiempo de juego en que se reabre el mega tras una partida (-1 = no hay espera). */
    long megaReopenAt = -1;
    @Nullable Long pendingMegaSeed;
    /** Grupos fijados a mano para el mega (README 6.3, {@code /smileprotocol groups set}). */
    final List<List<UUID>> fixedGroups = new ArrayList<>();
    final List<ClearJob> clears = new ArrayList<>();

    public static RunData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rs = new ListTag();
        for (Run r : runs.values()) rs.add(r.save());
        tag.put("Runs", rs);
        tag.putInt("Counter", counter);

        CompoundTag last = new CompoundTag();
        lastSequence.forEach((k, v) -> {
            ListTag l = new ListTag();
            for (ResourceLocation r : v) l.add(StringTag.valueOf(r.toString()));
            last.put(k, l);
        });
        tag.put("LastSequence", last);

        ListTag pl = new ListTag();
        plays.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", k);
            e.putInt("N", v);
            pl.add(e);
        });
        tag.put("Plays", pl);

        tag.putBoolean("MegaLocked", megaLocked);
        tag.putLong("MegaReopenAt", megaReopenAt);
        if (pendingMegaSeed != null) tag.putLong("PendingSeed", pendingMegaSeed);

        ListTag fg = new ListTag();
        for (List<UUID> g : fixedGroups) {
            ListTag l = new ListTag();
            for (UUID u : g) l.add(StringTag.valueOf(u.toString()));
            fg.add(l);
        }
        tag.put("FixedGroups", fg);

        ListTag cl = new ListTag();
        for (ClearJob j : clears) {
            BoundingBox b = j.box();
            cl.add(new IntArrayTag(new int[]{j.slot(), j.sub(), b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
        }
        tag.put("Clears", cl);
        return tag;
    }

    public static RunData load(CompoundTag tag, HolderLookup.Provider registries) {
        RunData d = new RunData();
        ListTag rs = tag.getList("Runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < rs.size(); i++) {
            Run r = Run.load(rs.getCompound(i));
            d.runs.put(r.id(), r);
        }
        d.counter = tag.getInt("Counter");

        CompoundTag last = tag.getCompound("LastSequence");
        for (String k : last.getAllKeys()) {
            List<ResourceLocation> l = new ArrayList<>();
            ListTag lt = last.getList(k, Tag.TAG_STRING);
            for (int i = 0; i < lt.size(); i++) {
                ResourceLocation r = ResourceLocation.tryParse(lt.getString(i));
                if (r != null) l.add(r);
            }
            d.lastSequence.put(k, l);
        }

        ListTag pl = tag.getList("Plays", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) d.plays.put(pl.getCompound(i).getUUID("Id"), pl.getCompound(i).getInt("N"));

        d.megaLocked = tag.getBoolean("MegaLocked");
        d.megaReopenAt = tag.contains("MegaReopenAt") ? tag.getLong("MegaReopenAt") : -1;
        if (tag.contains("PendingSeed")) d.pendingMegaSeed = tag.getLong("PendingSeed");

        ListTag fg = tag.getList("FixedGroups", Tag.TAG_LIST);
        for (int i = 0; i < fg.size(); i++) {
            ListTag l = (ListTag) fg.get(i);
            List<UUID> g = new ArrayList<>();
            for (int j = 0; j < l.size(); j++) {
                try {
                    g.add(UUID.fromString(l.getString(j)));
                } catch (IllegalArgumentException ignored) {
                    // entrada corrupta
                }
            }
            d.fixedGroups.add(g);
        }

        ListTag cl = tag.getList("Clears", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < cl.size(); i++) {
            int[] a = cl.getIntArray(i);
            if (a.length < 8) continue;
            d.clears.add(new ClearJob(a[0], a[1], new BoundingBox(a[2], a[3], a[4], a[5], a[6], a[7])));
        }
        return d;
    }
}
