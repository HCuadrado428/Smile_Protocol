package com.smileprotocol.hide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Tipos de escondite data-driven (README 15.2). Se releen tras {@code /reload}. */
public final class HideTypes {

    private static final FileToIdConverter FILES = FileToIdConverter.json("hiding_spots");
    @Nullable private static ResourceManager loadedFrom;
    private static Map<String, HideType> types = Map.of();

    private HideTypes() {}

    public static HideType get(MinecraftServer server, String id) {
        ResourceManager rm = server.getResourceManager();
        if (rm != loadedFrom) {
            loadedFrom = rm;
            Map<String, HideType> m = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(rm).entrySet()) {
                String name = FILES.fileToId(e.getKey()).getPath();
                try (Reader r = e.getValue().openAsReader()) {
                    m.put(name, parse(name, JsonParser.parseReader(r).getAsJsonObject()));
                } catch (Exception ex) {
                    SmileProtocol.LOGGER.error("Escondite {} no valido: {}", name, ex.toString());
                }
            }
            types = m;
        }
        return types.getOrDefault(id, HideType.fallback(id));
    }

    private static HideType parse(String id, JsonObject o) {
        return new HideType(id,
                i(o, "capacity", 1), i(o, "enterTime", 16), i(o, "exitTime", 14),
                f(o, "noiseOnEnter", 4), f(o, "viewCone", 70), f(o, "breathDrain", 0.12f),
                f(o, "searchPriority", 1), b(o, "canBeLocked"), b(o, "partial"), b(o, "low"),
                o.has("overlay") ? o.get("overlay").getAsString() : "slits");
    }

    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
    private static boolean b(JsonObject o, String k) { return o.has(k) && o.get(k).getAsBoolean(); }
}
