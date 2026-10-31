package com.smileprotocol.score;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puntuaciones persistentes: la tabla de records (las diez mejores partidas por grupo), las
 * estadisticas de cada jugador y sus logros, y los carteles del tablon del lobby.
 */
public final class ScoreData extends SavedData {

    private static final String NAME = "smileprotocol_scores";
    private static final SavedData.Factory<ScoreData> FACTORY = new SavedData.Factory<>(ScoreData::new, ScoreData::load, null);
    public static final int TOP = 10;

    /** Una partida en la tabla. */
    public record Entry(int score, List<String> names, int zone, int zones, int seconds, String rules, boolean escaped, long when) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Score", score);
            ListTag n = new ListTag();
            for (String s : names) n.add(StringTag.valueOf(s));
            t.put("Names", n);
            t.putInt("Zone", zone);
            t.putInt("Zones", zones);
            t.putInt("Secs", seconds);
            t.putString("Rules", rules);
            t.putBoolean("Escaped", escaped);
            t.putLong("When", when);
            return t;
        }

        static Entry load(CompoundTag t) {
            List<String> n = new ArrayList<>();
            ListTag l = t.getList("Names", Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) n.add(l.getString(i));
            return new Entry(t.getInt("Score"), n, t.getInt("Zone"), t.getInt("Zones"), t.getInt("Secs"), t.getString("Rules"),
                    t.getBoolean("Escaped"), t.getLong("When"));
        }
    }

    /** Estadisticas de un jugador. */
    public static final class Stats {
        public String name = "";
        public int runs, escapes, deaths, best, fuses, hides, stuns, decoys;
        public final Set<String> achievements = new LinkedHashSet<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Name", name);
            t.putInt("Runs", runs);
            t.putInt("Escapes", escapes);
            t.putInt("Deaths", deaths);
            t.putInt("Best", best);
            t.putInt("Fuses", fuses);
            t.putInt("Hides", hides);
            t.putInt("Stuns", stuns);
            t.putInt("Decoys", decoys);
            ListTag a = new ListTag();
            for (String s : achievements) a.add(StringTag.valueOf(s));
            t.put("Achievements", a);
            return t;
        }

        static Stats load(CompoundTag t) {
            Stats s = new Stats();
            s.name = t.getString("Name");
            s.runs = t.getInt("Runs");
            s.escapes = t.getInt("Escapes");
            s.deaths = t.getInt("Deaths");
            s.best = t.getInt("Best");
            s.fuses = t.getInt("Fuses");
            s.hides = t.getInt("Hides");
            s.stuns = t.getInt("Stuns");
            s.decoys = t.getInt("Decoys");
            ListTag a = t.getList("Achievements", Tag.TAG_STRING);
            for (int i = 0; i < a.size(); i++) s.achievements.add(a.getString(i));
            return s;
        }
    }

    final List<Entry> top = new ArrayList<>();
    final Map<UUID, Stats> stats = new LinkedHashMap<>();
    /** Carteles del tablon de records del lobby (de arriba a abajo, de izquierda a derecha). */
    final List<BlockPos> board = new ArrayList<>();

    public static ScoreData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public List<Entry> top() {
        return top;
    }

    public Stats stats(UUID id) {
        return stats.computeIfAbsent(id, k -> new Stats());
    }

    public List<BlockPos> board() {
        return board;
    }

    public void setBoard(List<BlockPos> signs) {
        board.clear();
        board.addAll(signs);
        setDirty();
    }

    /** Mete la partida en la tabla si cabe. Devuelve el puesto (1..10) o 0 si no entra. */
    public int add(Entry e) {
        int pos = 0;
        while (pos < top.size() && top.get(pos).score() >= e.score()) pos++;
        if (pos >= TOP) return 0;
        top.add(pos, e);
        while (top.size() > TOP) top.remove(top.size() - 1);
        setDirty();
        return pos + 1;
    }

    public void clearTop() {
        top.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag t = new ListTag();
        for (Entry e : top) t.add(e.save());
        tag.put("Top", t);
        ListTag s = new ListTag();
        stats.forEach((id, st) -> {
            CompoundTag c = st.save();
            c.putUUID("Id", id);
            s.add(c);
        });
        tag.put("Stats", s);
        ListTag b = new ListTag();
        for (BlockPos p : board) b.add(net.minecraft.nbt.NbtUtils.writeBlockPos(p));
        tag.put("Board", b);
        return tag;
    }

    public static ScoreData load(CompoundTag tag, HolderLookup.Provider registries) {
        ScoreData d = new ScoreData();
        ListTag t = tag.getList("Top", Tag.TAG_COMPOUND);
        for (int i = 0; i < t.size(); i++) d.top.add(Entry.load(t.getCompound(i)));
        ListTag s = tag.getList("Stats", Tag.TAG_COMPOUND);
        for (int i = 0; i < s.size(); i++) {
            CompoundTag c = s.getCompound(i);
            d.stats.put(c.getUUID("Id"), Stats.load(c));
        }
        ListTag b = tag.getList("Board", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < b.size(); i++) {
            int[] a = b.getIntArray(i);
            if (a.length == 3) d.board.add(new BlockPos(a[0], a[1], a[2]));
        }
        return d;
    }
}
