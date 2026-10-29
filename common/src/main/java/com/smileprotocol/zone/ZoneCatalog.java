package com.smileprotocol.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de zonas, leido de los datos del servidor (mod y datapacks):
 * <ul>
 *   <li>{@code data/<ns>/zone/*.json}: definiciones ({@link ZoneDef}).</li>
 *   <li>{@code data/<ns>/zone_pool/*.json}: zonas intermedias que pueden salir
 *       ({@code {"zones": ["smileprotocol:lab_storage", ...]}}). Se suman todos los ficheros.</li>
 * </ul>
 * Se relee si cambia el gestor de recursos (tras {@code /reload}).
 */
public final class ZoneCatalog {

    private static final FileToIdConverter ZONES = FileToIdConverter.json("zone");
    private static final FileToIdConverter POOLS = FileToIdConverter.json("zone_pool");

    @Nullable private static ResourceManager loadedFrom;
    private static Map<ResourceLocation, ZoneDef> zones = Map.of();
    private static List<ResourceLocation> pool = List.of();

    private ZoneCatalog() {}

    private static void ensure(MinecraftServer server) {
        ResourceManager rm = server.getResourceManager();
        if (rm == loadedFrom) return;
        loadedFrom = rm;

        Map<ResourceLocation, ZoneDef> z = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : ZONES.listMatchingResources(rm).entrySet()) {
            ResourceLocation id = ZONES.fileToId(e.getKey());
            try (Reader r = e.getValue().openAsReader()) {
                z.put(id, parse(id, JsonParser.parseReader(r).getAsJsonObject()));
            } catch (Exception ex) {
                SmileProtocol.LOGGER.error("Zona {} no valida: {}", id, ex.toString());
            }
        }
        Set<ResourceLocation> p = new LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, Resource> e : POOLS.listMatchingResources(rm).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("zones");
                for (JsonElement el : arr) {
                    ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
                    if (id == null) continue;
                    ZoneDef def = z.get(id);
                    if (def == null) SmileProtocol.LOGGER.warn("zone_pool {}: la zona {} no existe", e.getKey(), id);
                    else if (def.kind() != ZoneKind.INTERMEDIATE) SmileProtocol.LOGGER.warn("zone_pool: {} no es intermedia", id);
                    else p.add(id);
                }
            } catch (Exception ex) {
                SmileProtocol.LOGGER.error("zone_pool {} no valido: {}", e.getKey(), ex.toString());
            }
        }
        zones = Collections.unmodifiableMap(z);
        pool = List.copyOf(p);
        SmileProtocol.LOGGER.info("Smile Protocol: {} zonas, {} en el catalogo de intermedias", zones.size(), pool.size());
    }

    private static ZoneDef parse(ResourceLocation id, JsonObject o) {
        ResourceLocation template = ResourceLocation.parse(o.get("template").getAsString());
        ZoneKind kind = ZoneKind.valueOf(o.has("kind") ? o.get("kind").getAsString().toUpperCase(Locale.ROOT) : "INTERMEDIATE");
        int difficulty = o.has("difficulty") ? o.get("difficulty").getAsInt() : 1;
        BlockPos entry = null;
        if (o.has("entry")) {
            JsonArray a = o.getAsJsonArray("entry");
            entry = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
        float yaw = o.has("entry_yaw") ? o.get("entry_yaw").getAsFloat() : 0f;
        String name = o.has("name") ? o.get("name").getAsString() : id.getPath();
        return new ZoneDef(id, template, kind, difficulty, entry, yaw, name);
    }

    @Nullable
    public static ZoneDef get(MinecraftServer server, ResourceLocation id) {
        ensure(server);
        return zones.get(id);
    }

    public static List<ResourceLocation> pool(MinecraftServer server) {
        ensure(server);
        return pool;
    }

    public static List<ResourceLocation> all(MinecraftServer server) {
        ensure(server);
        return new ArrayList<>(zones.keySet());
    }
}
