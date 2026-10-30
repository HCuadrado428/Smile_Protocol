package com.smileprotocol.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smileprotocol.SmileProtocol;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Fichas de los consumibles ({@code data/smileprotocol/consumables}) y de los lanzables
 * ({@code data/smileprotocol/throwables}), README 10.1 y 12.7. Se releen tras {@code /reload}.
 */
public final class ItemDefs {

    /**
     * @param useTime  ticks de click derecho mantenido
     * @param effect   HEAL, BATTERY, FLARE o ADRENALINE
     * @param amount   vida que cura, bateria que recarga (0-1), duracion de la bengala (ticks) o
     *                 vida al levantarse (%)
     * @param noise    radio del ruido mientras se usa
     * @param anim     gesto ({@code PoseCalculator.USE_*})
     * @param downed   se puede usar derribado
     * @param onlyDowned solo sirve derribado (la jeringa)
     * @param consume  se gasta al usarlo (el walkie no: se enciende o se apaga)
     */
    public record Consumable(Item item, int useTime, String effect, float amount, float noise, int anim,
                             boolean downed, boolean onlyDowned, @Nullable ResourceLocation sound, boolean consume) {}

    /**
     * @param noise  radio del ruido al caer
     * @param breaks se rompe al caer (si no, queda en el suelo y se puede recoger)
     * @param speed  fuerza del lanzamiento
     */
    public record Throwable(Item item, float noise, boolean breaks, float speed) {}

    private static final FileToIdConverter CONSUMABLES = FileToIdConverter.json("consumables");
    private static final FileToIdConverter THROWABLES = FileToIdConverter.json("throwables");
    @Nullable private static ResourceManager loadedFrom;
    private static Map<Item, Consumable> consumables = Map.of();
    private static Map<Item, Throwable> throwables = Map.of();

    private ItemDefs() {}

    @Nullable
    public static Consumable consumable(MinecraftServer server, ItemStack stack) {
        load(server);
        return stack.isEmpty() ? null : consumables.get(stack.getItem());
    }

    @Nullable
    public static Throwable throwable(MinecraftServer server, ItemStack stack) {
        load(server);
        return stack.isEmpty() ? null : throwables.get(stack.getItem());
    }

    private static void load(MinecraftServer server) {
        ResourceManager rm = server.getResourceManager();
        if (rm == loadedFrom) return;
        loadedFrom = rm;
        Map<Item, Consumable> c = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : CONSUMABLES.listMatchingResources(rm).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.get("item").getAsString()));
                JsonObject eff = o.has("effect") ? o.getAsJsonObject("effect") : new JsonObject();
                c.put(item, new Consumable(item, i(o, "use_time", 40), s(eff, "type", "HEAL").toUpperCase(java.util.Locale.ROOT),
                        f(eff, "amount", 1f), f(o, "noise", 2f), anim(s(o, "anim", "")),
                        b(o, "usable_downed", false), b(o, "only_downed", false),
                        o.has("sound") ? ResourceLocation.parse(o.get("sound").getAsString()) : null,
                        b(o, "consume", true)));
            } catch (Exception ex) {
                SmileProtocol.LOGGER.error("Consumible {} no valido: {}", e.getKey(), ex.toString());
            }
        }
        Map<Item, Throwable> t = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : THROWABLES.listMatchingResources(rm).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.get("item").getAsString()));
                t.put(item, new Throwable(item, f(o, "noise", 10f), b(o, "breaks", false), f(o, "speed", 1.1f)));
            } catch (Exception ex) {
                SmileProtocol.LOGGER.error("Lanzable {} no valido: {}", e.getKey(), ex.toString());
            }
        }
        consumables = c;
        throwables = t;
    }

    private static int anim(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "battery" -> com.smileprotocol.client.anim.PoseCalculator.USE_BATTERY;
            case "flare" -> com.smileprotocol.client.anim.PoseCalculator.USE_FLARE;
            case "syringe" -> com.smileprotocol.client.anim.PoseCalculator.USE_SYRINGE;
            default -> com.smileprotocol.client.anim.PoseCalculator.USE_MEDKIT;
        };
    }

    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static String s(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    private static boolean b(JsonObject o, String k, boolean d) { return o.has(k) ? o.get(k).getAsBoolean() : d; }
}
