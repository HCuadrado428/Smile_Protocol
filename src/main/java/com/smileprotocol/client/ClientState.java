package com.smileprotocol.client;

import com.smileprotocol.network.SyncLobbyBoardsPacket;
import com.smileprotocol.network.SyncReadyPacket;
import com.smileprotocol.player.SPInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Estado del cliente recibido del servidor. Solo se usa en el cliente. */
public final class ClientState {

    private static boolean inSP;
    private static final ItemStack[] slots = new ItemStack[SPInventory.SIZE];
    private static int selected;

    /** Contador del ascensor en el que esta el jugador, o null. */
    @Nullable public static SyncReadyPacket ready;

    private static final Map<BlockPos, SyncLobbyBoardsPacket.Board> boards = new HashMap<>();

    public static List<String> debugLines = List.of();

    // Fundido a negro
    private static int fadeIn, fadeHold, fadeOut, fadeAge = -1;

    static {
        reset();
    }

    private ClientState() {}

    public static boolean inSmileProtocol() {
        return inSP;
    }

    public static void applyState(boolean inSP, ItemStack[] newSlots, int newSelected) {
        ClientState.inSP = inSP;
        for (int i = 0; i < SPInventory.SIZE; i++) slots[i] = newSlots[i];
        selected = newSelected;
    }

    public static ItemStack slot(int i) {
        return slots[i];
    }

    public static int selected() {
        return selected;
    }

    /** Cambio local inmediato; el servidor lo valida con {@code SelectSlotPacket}. */
    public static void select(int slot) {
        selected = slot;
    }

    public static void applyBoards(List<SyncLobbyBoardsPacket.Board> list) {
        boards.clear();
        for (SyncLobbyBoardsPacket.Board b : list) boards.put(b.pos(), b);
    }

    @Nullable
    public static SyncLobbyBoardsPacket.Board board(BlockPos pos) {
        return boards.get(pos);
    }

    public static void startFade(int in, int hold, int out) {
        fadeIn = Math.max(in, 1);
        fadeHold = hold;
        fadeOut = Math.max(out, 1);
        fadeAge = 0;
    }

    public static void tickFade() {
        if (fadeAge < 0) return;
        fadeAge++;
        if (fadeAge > fadeIn + fadeHold + fadeOut) fadeAge = -1;
    }

    /** Opacidad actual del fundido (0 a 1). */
    public static float fadeAlpha(float partialTick) {
        if (fadeAge < 0) return 0f;
        float t = fadeAge + partialTick;
        if (t < fadeIn) return t / fadeIn;
        if (t < fadeIn + fadeHold) return 1f;
        return Math.max(0f, 1f - (t - fadeIn - fadeHold) / fadeOut);
    }

    /** Al desconectarse del servidor. */
    public static void reset() {
        inSP = false;
        for (int i = 0; i < SPInventory.SIZE; i++) slots[i] = ItemStack.EMPTY;
        selected = 0;
        ready = null;
        boards.clear();
        debugLines = List.of();
        fadeAge = -1;
    }
}
