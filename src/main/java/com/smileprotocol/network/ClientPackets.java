package com.smileprotocol.network;

import com.smileprotocol.client.ClientState;

/**
 * Puente entre los paquetes y el estado del cliente. Solo se carga en el cliente
 * (a traves de {@code DistExecutor}), asi que puede tocar clases de cliente.
 */
final class ClientPackets {

    private ClientPackets() {}

    static void onSyncState(SyncSPStatePacket p) {
        ClientState.applyState(p.inSP(), p.slots(), p.selected());
    }

    static void onSyncReady(SyncReadyPacket p) {
        ClientState.ready = p.show() ? p : null;
    }

    static void onSyncBoards(SyncLobbyBoardsPacket p) {
        ClientState.applyBoards(p.boards());
    }

    static void onFade(ScreenFadePacket p) {
        ClientState.startFade(p.fadeIn(), p.hold(), p.fadeOut());
    }

    static void onDebug(DebugDataPacket p) {
        ClientState.debugLines = p.lines();
    }
}
