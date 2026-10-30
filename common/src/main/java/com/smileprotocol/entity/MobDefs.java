package com.smileprotocol.entity;

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

/** Fichas de los mobs ({@code data/smileprotocol/mobs}). Se releen tras {@code /reload}. */
public final class MobDefs {

    private static final FileToIdConverter FILES = FileToIdConverter.json("mobs");
    @Nullable private static ResourceManager loadedFrom;
    private static Map<String, MobDef> defs = Map.of();

    private MobDefs() {}

    public static MobDef get(MinecraftServer server, String id) {
        ResourceManager rm = server.getResourceManager();
        if (rm != loadedFrom) {
            loadedFrom = rm;
            Map<String, MobDef> m = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(rm).entrySet()) {
                String name = FILES.fileToId(e.getKey()).getPath();
                try (Reader r = e.getValue().openAsReader()) {
                    m.put(name, MobDef.parse(name, JsonParser.parseReader(r).getAsJsonObject()));
                } catch (Exception ex) {
                    SmileProtocol.LOGGER.error("Ficha de mob {} no valida: {}", name, ex.toString());
                }
            }
            defs = m;
        }
        return defs.getOrDefault(id, MobDef.berserkerDefaults());
    }
}
