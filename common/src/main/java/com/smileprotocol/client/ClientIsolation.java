package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Capa 3 del aislamiento, parte de cliente (README 7.3 y 8.2): dentro de la partida no se
 * abren pantallas de otros mods ni el inventario, y las teclas de otros mods no hacen nada.
 */
public final class ClientIsolation {

    /** Paquetes de clases cuyas pantallas se pueden abrir dentro. */
    private static final String[] SCREEN_PACKAGES = {
            "net.minecraft.", "com.mojang.", "com.smileprotocol.", "de.maxhenkel.voicechat."
    };

    /** Categorias de teclas que siguen funcionando dentro. */
    private static final Set<String> KEY_CATEGORIES = Set.of(
            KeyMapping.CATEGORY_MOVEMENT, KeyMapping.CATEGORY_GAMEPLAY, KeyMapping.CATEGORY_INVENTORY,
            KeyMapping.CATEGORY_MULTIPLAYER, KeyMapping.CATEGORY_MISC, KeyMapping.CATEGORY_INTERFACE,
            KeyMapping.CATEGORY_CREATIVE, "key.categories." + SmileProtocol.MODID);

    private ClientIsolation() {}

    /** Capas de HUD que siguen visibles dentro (solo NeoForge puede filtrar las de otros mods). */
    public static boolean isHudLayerAllowed(ResourceLocation id, Set<ResourceLocation> vanillaAllowed) {
        String ns = id.getNamespace();
        if (SmileProtocol.MODID.equals(ns) || "voicechat".equals(ns)) return true;
        return vanillaAllowed.contains(id);
    }

    public static boolean isScreenAllowed(Screen screen) {
        if (screen == null) return true;
        // Inventario (tecla E), logros y estadisticas: bloqueados (README 8.2)
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof AdvancementsScreen || screen instanceof StatsScreen) {
            return false;
        }
        String cls = screen.getClass().getName();
        for (String p : SCREEN_PACKAGES) if (cls.startsWith(p)) return true;
        return false;
    }

    private static boolean isKeyAllowed(KeyMapping km) {
        String cat = km.getCategory();
        if (KEY_CATEGORIES.contains(cat)) return true;
        return cat.contains("voicechat") || km.getName().contains("voicechat");
    }

    /** Cada tick dentro: suelta las teclas de otros mods y consume sus pulsaciones. */
    public static void tickKeys(Minecraft mc) {
        for (KeyMapping km : mc.options.keyMappings) {
            if (isKeyAllowed(km)) continue;
            while (km.consumeClick()) { /* descartar */ }
            km.setDown(false);
        }
    }
}
