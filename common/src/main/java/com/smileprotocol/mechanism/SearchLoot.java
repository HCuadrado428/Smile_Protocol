package com.smileprotocol.mechanism;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.instance.InstanceContext;
import com.smileprotocol.instance.ZoneSetup;
import com.smileprotocol.instance.ZonePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Botin de los muebles que se registran (fase 24): {@code data/smileprotocol/search_loot/<planta>.json}
 * o {@code default.json}. Cada entrada es un objeto ({@code ZoneSetup.itemFor}: can, bottle, battery,
 * medkit, flare, adrenaline, fuse_blown...) o {@code nothing}, con su peso y, si se quiere, los
 * muebles donde puede salir ({@code kinds}).
 */
public final class SearchLoot {

    private record Entry(String item, int weight, Set<String> kinds) {}

    private static final FileToIdConverter FILES = FileToIdConverter.json("search_loot");
    @Nullable private static ResourceManager loadedFrom;
    private static Map<String, List<Entry>> tables = Map.of();

    private SearchLoot() {}

    private static void load(ResourceManager rm) {
        if (rm == loadedFrom) return;
        loadedFrom = rm;
        Map<String, List<Entry>> m = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(rm).entrySet()) {
            String name = FILES.fileToId(e.getKey()).getPath();
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                List<Entry> list = new ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("entries")) {
                    JsonObject eo = el.getAsJsonObject();
                    Set<String> kinds = new java.util.HashSet<>();
                    if (eo.has("kinds")) for (JsonElement k : eo.getAsJsonArray("kinds")) kinds.add(k.getAsString());
                    list.add(new Entry(eo.get("item").getAsString(), eo.has("weight") ? eo.get("weight").getAsInt() : 1, kinds));
                }
                m.put(name, list);
            } catch (Exception ex) {
                SmileProtocol.LOGGER.error("Botin {} no valido: {}", name, ex.toString());
            }
        }
        tables = m;
    }

    /** Lo que sale de un mueble {@code kind} en la planta donde esta (puede ser vacio). */
    public static ItemStack roll(ServerLevel level, BlockPos pos, String kind, RandomSource rnd) {
        load(level.getServer().getResourceManager());
        String zone = "default";
        InstanceContext ctx = InstanceContext.at(level, pos);
        ZonePlacement z = ctx == null ? null : ctx.zone(level.getServer());
        if (z != null && tables.containsKey(z.zoneId().getPath())) zone = z.zoneId().getPath();
        List<Entry> list = tables.getOrDefault(zone, tables.getOrDefault("default", List.of()));
        int total = 0;
        for (Entry e : list) if (e.kinds().isEmpty() || e.kinds().contains(kind)) total += e.weight();
        if (total <= 0) return ItemStack.EMPTY;
        int pick = rnd.nextInt(total);
        for (Entry e : list) {
            if (!(e.kinds().isEmpty() || e.kinds().contains(kind))) continue;
            pick -= e.weight();
            if (pick < 0) return "nothing".equals(e.item()) ? ItemStack.EMPTY : ZoneSetup.itemFor(e.item());
        }
        return ItemStack.EMPTY;
    }
}
