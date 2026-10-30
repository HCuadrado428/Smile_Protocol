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
            new Def<>(Payloads.ToggleFlashlight.TYPE, Payloads.ToggleFlashlight.CODEC, false, com.smileprotocol.motion.Flashlights::onToggle),
            new Def<>(Payloads.SyncFlashlight.TYPE, Payloads.SyncFlashlight.CODEC, true, null),
            new Def<>(Payloads.SyncDowned.TYPE, Payloads.SyncDowned.CODEC, true, null),
            new Def<>(Payloads.StartQTE.TYPE, Payloads.StartQTE.CODEC, true, null),
            new Def<>(Payloads.SyncRevive.TYPE, Payloads.SyncRevive.CODEC, true, null),
            new Def<>(Payloads.StartSequence.TYPE, Payloads.StartSequence.CODEC, true, null),
            new Def<>(Payloads.QTEState.TYPE, Payloads.QTEState.CODEC, true, null),
            new Def<>(Payloads.QTEEnd.TYPE, Payloads.QTEEnd.CODEC, true, null),
            new Def<>(Payloads.QTEInput.TYPE, Payloads.QTEInput.CODEC, false, com.smileprotocol.qte.QTEManager::onInput)
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
            data.getSPInventory().setSelected(p.slot());
            com.smileprotocol.session.SPSessionManager.sync(player);
        }
    }
}
