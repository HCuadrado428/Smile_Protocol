package com.smileprotocol.network;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Paquetes del mod (README 23). */
public final class Payloads {

    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(SmileProtocol.id(name));
    }

    /** S -> C: si el jugador esta en una partida y su inventario de 4 huecos. */
    public record SyncState(boolean inSP, List<ItemStack> slots, int selected) implements CustomPacketPayload {
        public static final Type<SyncState> TYPE = typeOf("sync_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncState> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncState::inSP,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SPInventory.SIZE)), SyncState::slots,
                ByteBufCodecs.VAR_INT, SyncState::selected,
                SyncState::new);

        public static SyncState of(SPPlayerData data) {
            SPInventory inv = data.getSPInventory();
            List<ItemStack> slots = new ArrayList<>();
            for (int i = 0; i < SPInventory.SIZE; i++) slots.add(inv.get(i).copy());
            return new SyncState(data.inSP(), slots, inv.getSelected());
        }

        @Override public Type<SyncState> type() { return TYPE; }
    }

    /** C -> S: hueco activo (1-4). */
    public record SelectSlot(int slot) implements CustomPacketPayload {
        public static final Type<SelectSlot> TYPE = typeOf("select_slot");
        public static final StreamCodec<ByteBuf, SelectSlot> CODEC =
                ByteBufCodecs.VAR_INT.map(SelectSlot::new, SelectSlot::slot);

        @Override public Type<SelectSlot> type() { return TYPE; }
    }

    /** C -> S: mods cargados en el cliente (README 7.1, capa 2). */
    public record Handshake(List<String> mods) implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = typeOf("handshake");
        public static final StreamCodec<ByteBuf, Handshake> CODEC =
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(1024)).map(Handshake::new, Handshake::mods);

        @Override public Type<Handshake> type() { return TYPE; }
    }

    /** S -> C: contador «X/Y listos» del ascensor del lobby. {@code show == false} lo oculta. */
    public record SyncReady(boolean show, Component title, int ready, int total, int countdown, Component status)
            implements CustomPacketPayload {
        public static final Type<SyncReady> TYPE = typeOf("sync_ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncReady> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncReady::show,
                ComponentSerialization.TRUSTED_STREAM_CODEC, SyncReady::title,
                ByteBufCodecs.VAR_INT, SyncReady::ready,
                ByteBufCodecs.VAR_INT, SyncReady::total,
                ByteBufCodecs.INT, SyncReady::countdown,
                ComponentSerialization.TRUSTED_STREAM_CODEC, SyncReady::status,
                SyncReady::new);

        public static SyncReady hidden() {
            return new SyncReady(false, Component.empty(), 0, 0, -1, Component.empty());
        }

        @Override public Type<SyncReady> type() { return TYPE; }
    }

    /** Una pantalla de ascensor: dos lineas y el color del estado. */
    public record Board(BlockPos pos, Component title, Component status, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Board::pos,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Board::title,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Board::status,
                ByteBufCodecs.INT, Board::color,
                Board::new);
    }

    /** S -> C: lo que muestra cada pantalla de ascensor del lobby. */
    public record SyncBoards(List<Board> boards) implements CustomPacketPayload {
        public static final Type<SyncBoards> TYPE = typeOf("sync_boards");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncBoards> CODEC =
                Board.CODEC.apply(ByteBufCodecs.list(256)).map(SyncBoards::new, SyncBoards::boards);

        @Override public Type<SyncBoards> type() { return TYPE; }
    }

    /** S -> C: fundido a negro (entrada, negro y salida, en ticks). */
    public record ScreenFade(int fadeIn, int hold, int fadeOut) implements CustomPacketPayload {
        public static final Type<ScreenFade> TYPE = typeOf("screen_fade");
        public static final StreamCodec<ByteBuf, ScreenFade> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ScreenFade::fadeIn,
                ByteBufCodecs.VAR_INT, ScreenFade::hold,
                ByteBufCodecs.VAR_INT, ScreenFade::fadeOut,
                ScreenFade::new);

        @Override public Type<ScreenFade> type() { return TYPE; }
    }

    /** S -> C: lineas del modo depuracion, solo para operadores (README 24.1). Vacio = apagado. */
    public record DebugData(List<String> lines) implements CustomPacketPayload {
        public static final Type<DebugData> TYPE = typeOf("debug_data");
        public static final StreamCodec<ByteBuf, DebugData> CODEC =
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64)).map(DebugData::new, DebugData::lines);

        @Override public Type<DebugData> type() { return TYPE; }
    }

    /**
     * C -> S: peticion de movimiento (README 12). {@code kind}: 0 estado (asomarse y mirar atras),
     * 1 dive, 2 vault (con el bloque que se salta).
     */
    public record MotionInput(int kind, int lean, boolean lookBack, BlockPos obstacle) implements CustomPacketPayload {
        public static final int STATE = 0, DIVE = 1, VAULT = 2;
        public static final Type<MotionInput> TYPE = typeOf("motion_input");
        public static final StreamCodec<ByteBuf, MotionInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MotionInput::kind,
                ByteBufCodecs.VAR_INT, MotionInput::lean,
                ByteBufCodecs.BOOL, MotionInput::lookBack,
                BlockPos.STREAM_CODEC, MotionInput::obstacle,
                MotionInput::new);

        @Override public Type<MotionInput> type() { return TYPE; }
    }

    /** S -> C: estado de movimiento de un jugador, para animarlo (README 13.2, {@code SyncPlayerState}). */
    public record SyncMotion(int entityId, int action, int lean, boolean lookBack, int detail, boolean lowPose)
            implements CustomPacketPayload {
        public static final Type<SyncMotion> TYPE = typeOf("sync_motion");
        public static final StreamCodec<ByteBuf, SyncMotion> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncMotion::entityId,
                ByteBufCodecs.VAR_INT, SyncMotion::action,
                ByteBufCodecs.VAR_INT, SyncMotion::lean,
                ByteBufCodecs.BOOL, SyncMotion::lookBack,
                ByteBufCodecs.VAR_INT, SyncMotion::detail,
                ByteBufCodecs.BOOL, SyncMotion::lowPose,
                SyncMotion::new);

        @Override public Type<SyncMotion> type() { return TYPE; }
    }
}
