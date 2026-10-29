package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.network.HandshakePacket;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.network.SelectSlotPacket;
import com.smileprotocol.player.SPInventory;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.List;

/**
 * Eventos de cliente (bus de Forge). Todo lo que bloquea solo actua dentro de la partida
 * (README 1, regla de oro).
 */
@Mod.EventBusSubscriber(modid = SmileProtocol.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    // Conexion -------------------------------------------------------------------

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Capa 2: enviar la lista de mods cargados (README 7.1)
        List<String> mods = ModList.get().getMods().stream().map(IModInfo::getModId).toList();
        SPNetwork.sendToServer(new HandshakePacket(mods));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }

    // HUD (README 8.1) -------------------------------------------------------------

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!ClientState.inSmileProtocol()) return;
        if (!ClientIsolation.isOverlayAllowed(event.getOverlay().id())) event.setCanceled(true);
    }

    // Pantallas (README 8.2, 7.3) ----------------------------------------------------

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ClientState.inSmileProtocol()) return;
        if (!ClientIsolation.isScreenAllowed(event.getNewScreen())) event.setCanceled(true);
    }

    // Teclas ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientState.tickFade();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientState.inSmileProtocol()) return;

        // F3 y F5 (README 8.2)
        mc.options.renderDebug = false;
        if (SPConfig.FORCE_FIRST_PERSON.get() && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        // Huecos 1-4; el resto de teclas de la barra no hace nada
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < hotbar.length; i++) {
            while (hotbar[i].consumeClick()) {
                if (i < SPInventory.SIZE) select(i);
            }
        }
        // Teclas vanilla sin uso dentro: soltar, cambiar de mano, inventario, elegir bloque
        for (KeyMapping km : new KeyMapping[]{mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keyInventory, mc.options.keyPickItem, mc.options.keyAdvancements}) {
            while (km.consumeClick()) { /* descartar */ }
        }
        ClientIsolation.tickKeys(mc);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.inSmileProtocol() || mc.screen != null) return;
        event.setCanceled(true);
        int dir = event.getScrollDelta() > 0 ? -1 : 1;
        select(Math.floorMod(ClientState.selected() + dir, SPInventory.SIZE));
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientState.inSmileProtocol()) return;
        // Sin ataques (README 8.3). El click izquierdo para lanzar llega en la fase 13.
        if (event.isAttack() || event.isPickBlock()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void select(int slot) {
        if (slot == ClientState.selected()) return;
        ClientState.select(slot);
        SPNetwork.sendToServer(new SelectSlotPacket(slot));
    }

    /** Para referencia: overlays vanilla que se cancelan dentro. */
    @SuppressWarnings("unused")
    private static final VanillaGuiOverlay[] HIDDEN = {VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.PLAYER_HEALTH,
            VanillaGuiOverlay.FOOD_LEVEL, VanillaGuiOverlay.ARMOR_LEVEL, VanillaGuiOverlay.AIR_LEVEL,
            VanillaGuiOverlay.EXPERIENCE_BAR, VanillaGuiOverlay.ITEM_NAME, VanillaGuiOverlay.CROSSHAIR,
            VanillaGuiOverlay.DEBUG_TEXT};
}
