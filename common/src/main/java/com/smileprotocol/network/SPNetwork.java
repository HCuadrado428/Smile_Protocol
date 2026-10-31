package com.smileprotocol.network;

import com.smileprotocol.compat.ModCheck;
import com.smileprotocol.platform.Services;
import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lista de paquetes y sus manejadores (README 23). Cada loader recorre {@link #DEFINITIONS}
 * y los registra con su API; los de servidor a cliente se despachan con
 * {@link #handleClient(CustomPacketPayload)} a los manejadores que pone el cliente.
 */
public final class SPNetwork {

    /** Un paquete: tipo, codec, direccion y, si va al servidor, su manejador. */
    public record Def<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                       StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                       boolean toClient,
                                                       @Nullable BiConsumer<T, ServerPlayer> serverHandler) {}

    public static final List<Def<?>> DEFINITIONS = List.of(
            new Def<>(Payloads.SyncState.TYPE, Payloads.SyncState.CODEC, true, null),
            new Def<>(Payloads.SyncReady.TYPE, Payloads.SyncReady.CODEC, true, null),
            new Def<>(Payloads.SyncBoards.TYPE, Payloads.SyncBoards.CODEC, true, null),
            new Def<>(Payloads.ScreenFade.TYPE, Payloads.ScreenFade.CODEC, true, null),
            new Def<>(Payloads.DebugData.TYPE, Payloads.DebugData.CODEC, true, null),
            new Def<>(Payloads.SelectSlot.TYPE, Payloads.SelectSlot.CODEC, false, SPNetwork::onSelectSlot),
            new Def<>(Payloads.Handshake.TYPE, Payloads.Handshake.CODEC, false, (p, player) -> ModCheck.onHandshake(player, p.mods())),
            new Def<>(Payloads.MotionInput.TYPE, Payloads.MotionInput.CODEC, false, com.smileprotocol.motion.MotionManager::onInput),
            new Def<>(Payloads.SyncMotion.TYPE, Payloads.SyncMotion.CODEC, true, null),
            new Def<>(Payloads.Interact.TYPE, Payloads.Interact.CODEC, false, com.smileprotocol.interact.InteractionManager::onInput),
            new Def<>(Payloads.InteractState.TYPE, Payloads.InteractState.CODEC, true, null),
            new Def<>(Payloads.DropItem.TYPE, Payloads.DropItem.CODEC, false, com.smileprotocol.interact.InteractionManager::onDrop),
            new Def<>(Payloads.SyncHide.TYPE, Payloads.SyncHide.CODEC, true, null),
            new Def<>(Payloads.HideInput.TYPE, Payloads.HideInput.CODEC, false, com.smileprotocol.hide.HideManager::onInput),
            new Def<>(Payloads.SetFloor.TYPE, Payloads.SetFloor.CODEC, true, null),
            new Def<>(Payloads.SetRules.TYPE, Payloads.SetRules.CODEC, false, com.smileprotocol.lobby.LobbyManager::onSetRules),
            new Def<>(Payloads.SetAccessibility.TYPE, Payloads.SetAccessibility.CODEC, false,
                    (p, player) -> com.smileprotocol.qte.QTEManager.setAssist(player, p.qteAssist())),
            new Def<>(Payloads.ToggleFlashlight.TYPE, Payloads.ToggleFlashlight.CODEC, false, com.smileprotocol.motion.Flashlights::onToggle),
            new Def<>(Payloads.SyncFlashlight.TYPE, Payloads.SyncFlashlight.CODEC, true, null),
            new Def<>(Payloads.SyncDowned.TYPE, Payloads.SyncDowned.CODEC, true, null),
            new Def<>(Payloads.StartQTE.TYPE, Payloads.StartQTE.CODEC, true, null),
            new Def<>(Payloads.SyncRevive.TYPE, Payloads.SyncRevive.CODEC, true, null),
            new Def<>(Payloads.SyncMission.TYPE, Payloads.SyncMission.CODEC, true, null),
            new Def<>(Payloads.PlayJumpscare.TYPE, Payloads.PlayJumpscare.CODEC, true, null),
            new Def<>(Payloads.GroupEnd.TYPE, Payloads.GroupEnd.CODEC, true, null),
            new Def<>(Payloads.Results.TYPE, Payloads.Results.CODEC, true, null),
            new Def<>(Payloads.ReturnLobby.TYPE, Payloads.ReturnLobby.CODEC, false, (p, player) -> {
                if (com.smileprotocol.player.SPPlayerData.inSP(player) && com.smileprotocol.run.RunManager.canReturnToLobby(player)) {
                    com.smileprotocol.session.SPSessionManager.leave(player, com.smileprotocol.session.SPSessionManager.Destination.LOBBY);
                }
            }),
            new Def<>(Payloads.UseItem.TYPE, Payloads.UseItem.CODEC, false, com.smileprotocol.item.UseManager::onInput),
            new Def<>(Payloads.StartSequence.TYPE, Payloads.StartSequence.CODEC, true, null),
            new Def<>(Payloads.QTEState.TYPE, Payloads.QTEState.CODEC, true, null),
            new Def<>(Payloads.QTEEnd.TYPE, Payloads.QTEEnd.CODEC, true, null),
            new Def<>(Payloads.QTEInput.TYPE, Payloads.QTEInput.CODEC, false, com.smileprotocol.qte.QTEManager::onInput),
            new Def<>(Payloads.PlaceMarker.TYPE, Payloads.PlaceMarker.CODEC, false, com.smileprotocol.marker.MarkerManager::onInput),
            new Def<>(Payloads.SyncMarkers.TYPE, Payloads.SyncMarkers.CODEC, true, null),
            new Def<>(Payloads.WalkieInput.TYPE, Payloads.WalkieInput.CODEC, false, com.smileprotocol.voice.WalkieManager::onInput),
            new Def<>(Payloads.SyncWalkie.TYPE, Payloads.SyncWalkie.CODEC, true, null)
    );

    private static final Map<CustomPacketPayload.Type<?>, Consumer<CustomPacketPayload>> CLIENT_HANDLERS = new HashMap<>();

    private SPNetwork() {}

    /** Lo llama el cliente al arrancar. */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void setClientHandler(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        CLIENT_HANDLERS.put(type, p -> handler.accept((T) p));
    }

    public static void handleClient(CustomPacketPayload payload) {
        Consumer<CustomPacketPayload> h = CLIENT_HANDLERS.get(payload.type());
        if (h != null) h.accept(payload);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        Services.PLATFORM.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Services.PLATFORM.sendToServer(payload);
    }

    private static void onSelectSlot(Payloads.SelectSlot p, ServerPlayer player) {
        if (p.slot() < 0 || p.slot() >= SPInventory.SIZE) return;
        SPPlayerData data = SPPlayerData.of(player);
        if (data.inSP()) {
            if (data.getSPInventory().getSelected() != p.slot()) com.smileprotocol.item.UseManager.stop(player, true);
            data.getSPInventory().setSelected(p.slot());
            com.smileprotocol.session.SPSessionManager.sync(player);
        }
    }
}
