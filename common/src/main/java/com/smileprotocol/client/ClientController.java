package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.platform.Services;
import com.smileprotocol.player.SPInventory;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Logica de cliente que llaman los loaders: arranque, conexion y tick (README 8). */
public final class ClientController {

    private ClientController() {}

    /** Al arrancar el cliente: manejadores de los paquetes que llegan del servidor. */
    public static void init() {
        SPNetwork.setClientHandler(Payloads.SyncState.TYPE, ClientState::applyState);
        SPNetwork.setClientHandler(Payloads.SyncReady.TYPE, p -> ClientState.ready = p.show() ? p : null);
        SPNetwork.setClientHandler(Payloads.SyncBoards.TYPE, p -> ClientState.applyBoards(p.boards()));
        SPNetwork.setClientHandler(Payloads.ScreenFade.TYPE, p -> ClientState.startFade(p.fadeIn(), p.hold(), p.fadeOut()));
        SPNetwork.setClientHandler(Payloads.DebugData.TYPE, p -> ClientState.debugLines = p.lines());
        SPNetwork.setClientHandler(Payloads.SyncMotion.TYPE, ClientMotion::onSync);
        SPNetwork.setClientHandler(Payloads.InteractState.TYPE, ClientInteraction::onState);
        SPNetwork.setClientHandler(Payloads.SyncHide.TYPE, ClientHide::onSync);
        com.smileprotocol.interact.ClientInventoryView.setProvider(ClientState::slot);
    }

    /** Al entrar en un servidor: capa 2 del aislamiento, la lista de mods (README 7.1). */
    public static void onJoin() {
        SPNetwork.sendToServer(new Payloads.Handshake(Services.PLATFORM.topLevelModIds()));
    }

    public static void onLeave() {
        ClientState.reset();
        ClientMotion.reset();
        ClientInteraction.reset();
        ClientHide.reset();
        com.smileprotocol.client.light.DynamicLights.clear();
    }

    public static void tick(Minecraft mc) {
        ClientState.tickFade();
        ClientMotion.tick(mc);
        ClientHide.tick(mc);
        ClientInteraction.tick(mc);
        if (mc.player == null || !ClientState.inSmileProtocol()) return;

        // F3 y F5 (README 8.2)
        if (mc.getDebugOverlay().showDebugScreen()) mc.getDebugOverlay().toggleOverlay();
        if (SPConfig.get().force_first_person && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        // Huecos 1-4; el resto de teclas de la barra no hace nada
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < hotbar.length; i++) {
            while (hotbar[i].consumeClick()) {
                if (i < SPInventory.SIZE) select(i);
            }
        }
        // Teclas vanilla sin uso dentro: soltar, cambiar de mano, inventario, logros
        for (KeyMapping km : new KeyMapping[]{mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keyInventory, mc.options.keyAdvancements}) {
            while (km.consumeClick()) { /* descartar */ }
        }
        ClientIsolation.tickKeys(mc);
    }

    /** Rueda del raton: +1 / -1 hueco. */
    public static void scroll(int dir) {
        select(Math.floorMod(ClientState.selected() + dir, SPInventory.SIZE));
    }

    private static void select(int slot) {
        if (slot == ClientState.selected()) return;
        ClientState.select(slot);
        SPNetwork.sendToServer(new Payloads.SelectSlot(slot));
    }
}
