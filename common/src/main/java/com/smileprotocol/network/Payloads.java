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
    /** {@code rules}: reglas de la cabina ({@link com.smileprotocol.difficulty.RunRules#encode}); vacio si no se eligen ahi. */
    public record SyncReady(boolean show, Component title, int ready, int total, int countdown, Component status, String rules)
            implements CustomPacketPayload {
        public static final Type<SyncReady> TYPE = typeOf("sync_ready");
        // composite admite hasta seis campos: este tiene siete
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncReady> CODEC = StreamCodec.of((buf, v) -> {
            ByteBufCodecs.BOOL.encode(buf, v.show());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.title());
            ByteBufCodecs.VAR_INT.encode(buf, v.ready());
            ByteBufCodecs.VAR_INT.encode(buf, v.total());
            ByteBufCodecs.INT.encode(buf, v.countdown());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.status());
            ByteBufCodecs.STRING_UTF8.encode(buf, v.rules());
        }, buf -> new SyncReady(ByteBufCodecs.BOOL.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

        public static SyncReady hidden() {
            return new SyncReady(false, Component.empty(), 0, 0, -1, Component.empty(), "");
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
        public static final int STATE = 0, DIVE = 1, VAULT = 2, SPECTATE = 3;
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

    /** C -> S: empezar o dejar de mantener una interaccion (README 11). kind: 0 soltar, 1 bloque, 2 entidad. */
    public record Interact(int kind, BlockPos pos, int entityId) implements CustomPacketPayload {
        public static final int STOP = 0, BLOCK = 1, ENTITY = 2;
        public static final Type<Interact> TYPE = typeOf("interact");
        public static final StreamCodec<ByteBuf, Interact> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Interact::kind,
                BlockPos.STREAM_CODEC, Interact::pos,
                ByteBufCodecs.VAR_INT, Interact::entityId,
                Interact::new);

        @Override public Type<Interact> type() { return TYPE; }
    }

    /** S -> C: progreso de la interaccion en curso. {@code waiting}: esperando a otro jugador. */
    public record InteractState(boolean active, int progress, int total, boolean waiting, Component prompt)
            implements CustomPacketPayload {
        public static final Type<InteractState> TYPE = typeOf("interact_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, InteractState> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, InteractState::active,
                ByteBufCodecs.VAR_INT, InteractState::progress,
                ByteBufCodecs.VAR_INT, InteractState::total,
                ByteBufCodecs.BOOL, InteractState::waiting,
                ComponentSerialization.TRUSTED_STREAM_CODEC, InteractState::prompt,
                InteractState::new);

        public static InteractState idle() {
            return new InteractState(false, 0, 0, false, Component.empty());
        }

        @Override public Type<InteractState> type() { return TYPE; }
    }

    /** C -> S: soltar el objeto del hueco activo (tecla G, README 12.7). */
    public record DropItem(int slot) implements CustomPacketPayload {
        public static final Type<DropItem> TYPE = typeOf("drop_item");
        public static final StreamCodec<ByteBuf, DropItem> CODEC = ByteBufCodecs.VAR_INT.map(DropItem::new, DropItem::slot);

        @Override public Type<DropItem> type() { return TYPE; }
    }

    /** S -> C: estado del escondite del jugador (README 15.4). */
    public record SyncHide(boolean hidden, String hideType, String overlay, float breath, boolean holding, float yaw,
                           float cone, boolean partial) implements CustomPacketPayload {
        public static final Type<SyncHide> TYPE = typeOf("sync_hide");
        public static final StreamCodec<ByteBuf, SyncHide> CODEC = new StreamCodec<>() {
            @Override
            public SyncHide decode(ByteBuf b) {
                return new SyncHide(b.readBoolean(), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        b.readFloat(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readBoolean());
            }

            @Override
            public void encode(ByteBuf b, SyncHide p) {
                b.writeBoolean(p.hidden());
                ByteBufCodecs.STRING_UTF8.encode(b, p.hideType());
                ByteBufCodecs.STRING_UTF8.encode(b, p.overlay());
                b.writeFloat(p.breath());
                b.writeBoolean(p.holding());
                b.writeFloat(p.yaw());
                b.writeFloat(p.cone());
                b.writeBoolean(p.partial());
            }
        };

        public static SyncHide none() {
            return new SyncHide(false, "", "", 1f, false, 0f, 360f, false);
        }

        @Override public Type<SyncHide> type() { return TYPE; }
    }

    /** C -> S: aguantar la respiracion (Shift) o salir del escondite (click derecho). */
    public record HideInput(boolean holding, boolean exit) implements CustomPacketPayload {
        public static final Type<HideInput> TYPE = typeOf("hide_input");
        public static final StreamCodec<ByteBuf, HideInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, HideInput::holding,
                ByteBufCodecs.BOOL, HideInput::exit,
                HideInput::new);

        @Override public Type<HideInput> type() { return TYPE; }
    }

    /** C -> S: opciones de accesibilidad que valida el servidor (README 24.2): minijuego asistido. */
    /** S -> C: la planta en la que esta el jugador (para su musica); vacio al salir. */
    public record SetFloor(String zone) implements CustomPacketPayload {
        public static final Type<SetFloor> TYPE = typeOf("set_floor");
        public static final StreamCodec<ByteBuf, SetFloor> CODEC = ByteBufCodecs.STRING_UTF8.map(SetFloor::new, SetFloor::zone);

        @Override public Type<SetFloor> type() { return TYPE; }
    }

    /** Cliente -> servidor: reglas elegidas en la cabina de un ascensor pequeno (F7 dentro). */
    public record SetRules(String rules) implements CustomPacketPayload {
        public static final Type<SetRules> TYPE = typeOf("set_rules");
        public static final StreamCodec<ByteBuf, SetRules> CODEC = ByteBufCodecs.STRING_UTF8.map(SetRules::new, SetRules::rules);

        @Override public Type<SetRules> type() { return TYPE; }
    }

    public record SetAccessibility(boolean qteAssist) implements CustomPacketPayload {
        public static final Type<SetAccessibility> TYPE = typeOf("set_accessibility");
        public static final StreamCodec<ByteBuf, SetAccessibility> CODEC = ByteBufCodecs.BOOL.map(SetAccessibility::new, SetAccessibility::qteAssist);

        @Override public Type<SetAccessibility> type() { return TYPE; }
    }

    /** C -> S: encender o apagar la linterna (tecla F). */
    public record ToggleFlashlight(boolean on) implements CustomPacketPayload {
        public static final Type<ToggleFlashlight> TYPE = typeOf("toggle_flashlight");
        public static final StreamCodec<ByteBuf, ToggleFlashlight> CODEC = ByteBufCodecs.BOOL.map(ToggleFlashlight::new, ToggleFlashlight::on);

        @Override public Type<ToggleFlashlight> type() { return TYPE; }
    }

    /** S -> C: linterna encendida o apagada de un jugador (README 21.1). */
    public record SyncFlashlight(int entityId, boolean on, float battery) implements CustomPacketPayload {
        public static final Type<SyncFlashlight> TYPE = typeOf("sync_flashlight");
        public static final StreamCodec<ByteBuf, SyncFlashlight> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncFlashlight::entityId,
                ByteBufCodecs.BOOL, SyncFlashlight::on,
                ByteBufCodecs.FLOAT, SyncFlashlight::battery,
                SyncFlashlight::new);

        @Override public Type<SyncFlashlight> type() { return TYPE; }
    }

    /** S -> C: derribado y cuanto le queda antes de desangrarse (0-1). */
    public record SyncDowned(boolean downed, float bleed) implements CustomPacketPayload {
        public static final Type<SyncDowned> TYPE = typeOf("sync_downed");
        public static final StreamCodec<ByteBuf, SyncDowned> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncDowned::downed,
                ByteBufCodecs.FLOAT, SyncDowned::bleed,
                SyncDowned::new);

        @Override public Type<SyncDowned> type() { return TYPE; }
    }

    /**
     * S -> C: estado del derribado para la victima: si la estan reanimando (progreso 0-1, -1 si
     * no) y si es espectador tras morir (README 9.3).
     */
    public record SyncRevive(float progress, boolean dead) implements CustomPacketPayload {
        public static final Type<SyncRevive> TYPE = typeOf("sync_revive");
        public static final StreamCodec<ByteBuf, SyncRevive> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, SyncRevive::progress,
                ByteBufCodecs.BOOL, SyncRevive::dead,
                SyncRevive::new);

        @Override public Type<SyncRevive> type() { return TYPE; }
    }

    /**
     * S -> C: empieza (o termina, con {@code ticks} 0) una secuencia de remate o de muerte
     * instantanea (README 9.3). La recibe la victima para la camara y los efectos; las animaciones
     * van por el estado sincronizado del jugador y del mob.
     */
    public record StartSequence(int kind, int mobId, int ticks) implements CustomPacketPayload {
        public static final Type<StartSequence> TYPE = typeOf("start_sequence");
        public static final StreamCodec<ByteBuf, StartSequence> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StartSequence::kind,
                ByteBufCodecs.VAR_INT, StartSequence::mobId,
                ByteBufCodecs.VAR_INT, StartSequence::ticks,
                StartSequence::new);

        @Override public Type<StartSequence> type() { return TYPE; }
    }

    /**
     * C -> S: usar el objeto del hueco activo (README 10.1, 12.7): empezar o soltar un consumible,
     * empezar o dejar de apuntar un lanzable, y lanzarlo hacia {@code yaw}/{@code pitch}.
     */
    public record UseItem(int kind, int slot, float yaw, float pitch) implements CustomPacketPayload {
        public static final int START = 0, STOP = 1, AIM = 2, AIM_STOP = 3, THROW = 4;
        public static final Type<UseItem> TYPE = typeOf("use_item");
        public static final StreamCodec<ByteBuf, UseItem> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UseItem::kind,
                ByteBufCodecs.VAR_INT, UseItem::slot,
                ByteBufCodecs.FLOAT, UseItem::yaw,
                ByteBufCodecs.FLOAT, UseItem::pitch,
                UseItem::new);

        @Override public Type<UseItem> type() { return TYPE; }
    }

    /**
     * S -> C: objetivo actual de la mision de la planta (README 19): texto, progreso y en que paso
     * va. {@code active} false = no hay objetivo que mostrar.
     */
    public record SyncMission(boolean active, Component text, int progress, int total, int step, int steps)
            implements CustomPacketPayload {
        public static final Type<SyncMission> TYPE = typeOf("sync_mission");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncMission> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncMission::active,
                ComponentSerialization.TRUSTED_STREAM_CODEC, SyncMission::text,
                ByteBufCodecs.VAR_INT, SyncMission::progress,
                ByteBufCodecs.VAR_INT, SyncMission::total,
                ByteBufCodecs.VAR_INT, SyncMission::step,
                ByteBufCodecs.VAR_INT, SyncMission::steps,
                SyncMission::new);

        @Override public Type<SyncMission> type() { return TYPE; }
    }

    /** S -> C: susto local (README 14.7): cara, destello, sacudida o golpe de sonido. */
    public record PlayJumpscare(int kind, float strength) implements CustomPacketPayload {
        public static final Type<PlayJumpscare> TYPE = typeOf("play_jumpscare");
        public static final StreamCodec<ByteBuf, PlayJumpscare> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlayJumpscare::kind,
                ByteBufCodecs.FLOAT, PlayJumpscare::strength,
                PlayJumpscare::new);

        @Override public Type<PlayJumpscare> type() { return TYPE; }
    }

    /**
     * S -> C: final del grupo (README 6.6, 6.7): GAME OVER (todos muertos) o escapados, con la planta
     * en que acabo. C -> S no; para volver al lobby desde la pantalla se usa {@link ReturnLobby}.
     */
    public record GroupEnd(boolean eliminated, Component zone, int zoneIndex, int zones, int seconds, int score) implements CustomPacketPayload {
        public static final Type<GroupEnd> TYPE = typeOf("group_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, GroupEnd> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, GroupEnd::eliminated,
                ComponentSerialization.TRUSTED_STREAM_CODEC, GroupEnd::zone,
                ByteBufCodecs.VAR_INT, GroupEnd::zoneIndex,
                ByteBufCodecs.VAR_INT, GroupEnd::zones,
                ByteBufCodecs.VAR_INT, GroupEnd::seconds,
                ByteBufCodecs.VAR_INT, GroupEnd::score,
                GroupEnd::new);

        @Override public Type<GroupEnd> type() { return TYPE; }
    }

    /** S -> C: resultados de la partida (README 6.8), una linea por grupo. */
    public record Results(List<Component> lines, int seconds) implements CustomPacketPayload {
        public static final Type<Results> TYPE = typeOf("results");
        public static final StreamCodec<RegistryFriendlyByteBuf, Results> CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(32)), Results::lines,
                ByteBufCodecs.VAR_INT, Results::seconds,
                Results::new);

        @Override public Type<Results> type() { return TYPE; }
    }

    /** C -> S: «Volver al lobby» desde la pantalla de GAME OVER (README 6.7). */
    public record ReturnLobby(boolean confirm) implements CustomPacketPayload {
        public static final Type<ReturnLobby> TYPE = typeOf("return_lobby");
        public static final StreamCodec<ByteBuf, ReturnLobby> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReturnLobby::confirm, ReturnLobby::new);

        @Override public Type<ReturnLobby> type() { return TYPE; }
    }

    /** S -> C: empieza el minijuego de escape (README 18). */
    public record StartQTE(int qteType, int ticks, long seed, int target, int difficulty) implements CustomPacketPayload {
        public static final Type<StartQTE> TYPE = typeOf("start_qte");
        public static final StreamCodec<ByteBuf, StartQTE> CODEC = new StreamCodec<>() {
            @Override
            public StartQTE decode(ByteBuf b) {
                return new StartQTE(b.readByte(), b.readInt(), b.readLong(), b.readInt(), b.readByte());
            }

            @Override
            public void encode(ByteBuf b, StartQTE p) {
                b.writeByte(p.qteType());
                b.writeInt(p.ticks());
                b.writeLong(p.seed());
                b.writeInt(p.target());
                b.writeByte(p.difficulty());
            }
        };

        @Override public Type<StartQTE> type() { return TYPE; }
    }

    /** S -> C: estado autoritativo del minijuego (tick, progreso y aguja o fallos). */
    public record QTEState(int elapsed, int progress, float extra) implements CustomPacketPayload {
        public static final Type<QTEState> TYPE = typeOf("qte_state");
        public static final StreamCodec<ByteBuf, QTEState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QTEState::elapsed,
                ByteBufCodecs.VAR_INT, QTEState::progress,
                ByteBufCodecs.FLOAT, QTEState::extra,
                QTEState::new);

        @Override public Type<QTEState> type() { return TYPE; }
    }

    /** S -> C: fin del minijuego. */
    public record QTEEnd(boolean success) implements CustomPacketPayload {
        public static final Type<QTEEnd> TYPE = typeOf("qte_end");
        public static final StreamCodec<ByteBuf, QTEEnd> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, QTEEnd::success, QTEEnd::new);

        @Override public Type<QTEEnd> type() { return TYPE; }
    }

    /** C -> S: pulsacion (con la tecla en {@code value}) o cambio de mantener. */
    public record QTEInput(int kind, int value) implements CustomPacketPayload {
        public static final int PRESS = 0;
        public static final int HOLD = 1;
        public static final Type<QTEInput> TYPE = typeOf("qte_input");
        public static final StreamCodec<ByteBuf, QTEInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QTEInput::kind,
                ByteBufCodecs.VAR_INT, QTEInput::value,
                QTEInput::new);

        @Override public Type<QTEInput> type() { return TYPE; }
    }

    /** C -> S: marcador de equipo (README 8.4). El servidor calcula a donde se mira. */
    public record PlaceMarker(int kind) implements CustomPacketPayload {
        public static final Type<PlaceMarker> TYPE = typeOf("place_marker");
        public static final StreamCodec<ByteBuf, PlaceMarker> CODEC = ByteBufCodecs.VAR_INT.map(PlaceMarker::new, PlaceMarker::kind);

        @Override public Type<PlaceMarker> type() { return TYPE; }
    }

    /** S -> C: marcadores vivos del grupo ({@code MarkerManager.write}). */
    public record SyncMarkers(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncMarkers> TYPE = typeOf("sync_markers");
        public static final StreamCodec<ByteBuf, SyncMarkers> CODEC = ByteBufCodecs.COMPOUND_TAG.map(SyncMarkers::new, SyncMarkers::data);

        @Override public Type<SyncMarkers> type() { return TYPE; }
    }

    /** C -> S: tecla del walkie pulsada o soltada (README 17.3). */
    public record WalkieInput(boolean talk) implements CustomPacketPayload {
        public static final Type<WalkieInput> TYPE = typeOf("walkie_input");
        public static final StreamCodec<ByteBuf, WalkieInput> CODEC = ByteBufCodecs.BOOL.map(WalkieInput::new, WalkieInput::talk);

        @Override public Type<WalkieInput> type() { return TYPE; }
    }

    /**
     * S -> C: estado del walkie del jugador: si lo lleva y esta encendido, si transmite, quien
     * se oye ({@code rx}, vacio = nadie) y si hay interferencias.
     */
    public record SyncWalkie(boolean has, boolean on, boolean tx, String rx, boolean interference) implements CustomPacketPayload {
        public static final Type<SyncWalkie> TYPE = typeOf("sync_walkie");
        public static final StreamCodec<ByteBuf, SyncWalkie> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncWalkie::has,
                ByteBufCodecs.BOOL, SyncWalkie::on,
                ByteBufCodecs.BOOL, SyncWalkie::tx,
                ByteBufCodecs.STRING_UTF8, SyncWalkie::rx,
                ByteBufCodecs.BOOL, SyncWalkie::interference,
                SyncWalkie::new);

        public static final SyncWalkie NONE = new SyncWalkie(false, false, false, "", false);

        @Override public Type<SyncWalkie> type() { return TYPE; }
    }
}
