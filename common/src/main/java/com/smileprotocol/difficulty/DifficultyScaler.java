package com.smileprotocol.difficulty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Dificultad segun el tamano del grupo (README 6.3, punto 3). Data-driven:
 * {@code data/smileprotocol/difficulty/group_scaling.json}. Un datapack del servidor puede
 * sustituirlo para ajustar los valores sin recompilar.
 * <pre>
 * { "min_fuses": 1,
 *   "sizes": { "2": {"fuses": 0.5, "mobs": 0.6, "qte_time": 1.4}, ... } }
 * </pre>
 * Para tamanos que no estan en la tabla se usa el mas cercano por debajo (o el menor).
 */
public final class DifficultyScaler {

    private static final net.minecraft.resources.ResourceLocation FILE =
            SmileProtocol.id("difficulty/group_scaling.json");

    private DifficultyScaler() {}

    public static DifficultyProfile forGroup(MinecraftServer server, int groupSize) {
        TreeMap<Integer, double[]> table = new TreeMap<>();
        int minFuses = 1;
        Optional<Resource> res = server.getResourceManager().getResource(FILE);
        if (res.isPresent()) {
            try (Reader r = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root.has("min_fuses")) minFuses = root.get("min_fuses").getAsInt();
                JsonObject sizes = root.getAsJsonObject("sizes");
                for (Map.Entry<String, JsonElement> e : sizes.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    table.put(Integer.parseInt(e.getKey()), new double[]{
                            num(o, "fuses"), num(o, "mobs"), num(o, "qte_time")});
                }
            } catch (Exception e) {
                SmileProtocol.LOGGER.error("group_scaling.json no valido; se usa la dificultad base", e);
                table.clear();
            }
        }
        if (table.isEmpty()) return new DifficultyProfile(groupSize, 1, 1, 1, minFuses);
        Map.Entry<Integer, double[]> row = table.floorEntry(groupSize);
        if (row == null) row = table.firstEntry();
        double[] v = row.getValue();
        return new DifficultyProfile(groupSize, v[0], v[1], v[2], Math.max(0, minFuses));
    }

    private static double num(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsDouble() : 1.0;
    }
}
