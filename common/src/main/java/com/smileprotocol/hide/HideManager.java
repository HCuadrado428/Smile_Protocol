package com.smileprotocol.hide;

import com.smileprotocol.block.HideBlock;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Escondites en el servidor (README 15.3, 15.4): {@code OUTSIDE -> ENTERING -> HIDDEN -> EXITING}.
 * Entrar es una interaccion mantenida (ENTERING); dentro el jugador queda anclado, invisible y con
 * la vista limitada, y puede aguantar la respiracion mientras le dure el aliento. La IA de los
 * mobs (fase 11) consulta {@link #isHidden}, {@link #isCompromised} y {@link #setSearching}.
 */
public final class HideManager {

    enum Phase { HIDDEN, EXITING }

    static final class Hidden {
        final BlockPos spot;
        final HideType type;
        final Vec3 anchor;
        final float yaw;
        final Direction facing;
        Phase phase = Phase.HIDDEN;
        int timer;
        float breath = 1f;
        boolean holding;
        boolean exhausted;
        int age;

        Hidden(BlockPos spot, HideType type, Vec3 anchor, float yaw, Direction facing) {
            this.spot = spot;
            this.type = type;
            this.anchor = anchor;
            this.yaw = yaw;
            this.facing = facing;
        }
    }

    private static final Map<UUID, Hidden> HIDDEN = new HashMap<>();
    private static final Map<BlockPos, Set<UUID>> OCCUPANTS = new HashMap<>();
    /** Escondites que un mob ha visto usar (README 15.3). */
    private static final Set<BlockPos> COMPROMISED = new HashSet<>();
    /** Escondites que un mob esta revisando: no se puede salir (README 15.3). */
    private static final Set<BlockPos> SEARCHING = new HashSet<>();

    private HideManager() {}

    public static int occupants(BlockPos spot) {
        Set<UUID> s = OCCUPANTS.get(spot);
        return s == null ? 0 : s.size();
    }

    public static boolean isHidden(ServerPlayer p) {
        Hidden h = HIDDEN.get(p.getUUID());
        return h != null && h.phase == Phase.HIDDEN;
    }

    /** Aguantando la respiracion: casi sin ruido (README 15.4). */
    public static boolean isHoldingBreath(ServerPlayer p) {
        Hidden h = HIDDEN.get(p.getUUID());
        return h != null && h.holding;
    }

    /** Escondite donde esta el jugador (null si no esta escondido). */
    @javax.annotation.Nullable
    public static BlockPos spotOf(ServerPlayer p) {
        Hidden h = HIDDEN.get(p.getUUID());
        return h == null ? null : h.spot;
    }

    /** Quien esta dentro de un escondite. */
    public static java.util.List<UUID> occupantsOf(BlockPos spot) {
        Set<UUID> s = OCCUPANTS.get(spot);
        return s == null ? java.util.List.of() : java.util.List.copyOf(s);
    }

    public static boolean isCompromised(BlockPos spot) {
        return COMPROMISED.contains(spot);
    }

    public static void compromise(BlockPos spot) {
        COMPROMISED.add(spot.immutable());
    }

    public static void setSearching(BlockPos spot, boolean searching) {
        if (searching) SEARCHING.add(spot.immutable());
        else SEARCHING.remove(spot);
    }

    // Entrar y salir -----------------------------------------------------------------------------

    public static void enter(ServerPlayer p, BlockPos spot, HideType type, HideBlock block) {
        if (HIDDEN.containsKey(p.getUUID()) || occupants(spot) >= type.capacity()) return;
        ServerLevel level = p.serverLevel();
        BlockState st = level.getBlockState(spot);
        Direction facing = st.hasProperty(HideBlock.FACING) ? st.getValue(HideBlock.FACING) : Direction.NORTH;
        // Dentro, mirando hacia fuera (la cara frontal del bloque)
        int n = occupants(spot);
        Vec3 side = Vec3.atLowerCornerOf(facing.getClockWise().getNormal()).scale(type.capacity() > 1 ? (n == 0 ? -0.18 : 0.18) : 0);
        Vec3 anchor = Vec3.atBottomCenterOf(spot).add(side).add(0, block.kind() == HideBlock.Kind.ENCLOSED ? 0.0625 : 0, 0);
        if (block.kind() == HideBlock.Kind.LOW) {
            // Bajo la camilla: en medio de las dos mitades
            anchor = anchor.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        }
        if (block.kind() == HideBlock.Kind.CURTAIN) {
            anchor = anchor.add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(0.3));
        }
        float yaw = facing.toYRot();

        Hidden h = new Hidden(spot.immutable(), type, anchor, yaw, facing);
        HIDDEN.put(p.getUUID(), h);
        OCCUPANTS.computeIfAbsent(h.spot, k -> new HashSet<>()).add(p.getUUID());

        SPSessionManager.teleport(p, level, anchor, yaw, 0f);
        if (!type.partial()) p.setInvisible(true);
        MotionManager.setAction(p, Action.HIDDEN, 0, type.low());
        HideBlock.setOpen(level, spot, false);
        level.playSound(null, spot, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.3f);
        NoiseSystem.emit(level, anchor, type.noiseOnEnter() * (n > 0 ? 1.5f : 1f), NoiseType.HIDE, p);
        LabEvents.fire(level, spot, p, "hide_enter", type.id());
        sync(p, h);
    }

    /** Empieza a salir (tarda {@code exitTime}). No se puede si un mob esta revisando el escondite. */
    public static void requestExit(ServerPlayer p) {
        Hidden h = HIDDEN.get(p.getUUID());
        if (h == null || h.phase != Phase.HIDDEN) return;
        if (SEARCHING.contains(h.spot)) {
            p.displayClientMessage(Component.translatable("smileprotocol.hide.cant_exit").withStyle(ChatFormatting.RED), true);
            return;
        }
        h.phase = Phase.EXITING;
        h.timer = h.type.exitTime();
        h.holding = false;
        HideBlock.setOpen(p.serverLevel(), h.spot, true);
    }

    /** Sale ya (al terminar EXITING, al salir de la partida, al desconectarse...). */
    public static void forceExit(ServerPlayer p, boolean teleportOut) {
        Hidden h = HIDDEN.remove(p.getUUID());
        if (h == null) return;
        Set<UUID> occ = OCCUPANTS.get(h.spot);
        if (occ != null) {
            occ.remove(p.getUUID());
            if (occ.isEmpty()) OCCUPANTS.remove(h.spot);
        }
        ServerLevel level = p.serverLevel();
        p.setInvisible(false);
        MotionManager.clearAction(p, Action.HIDDEN);
        if (teleportOut) {
            // La camilla ocupa el bloque de delante: se sale por un lado
            BlockState st = level.getBlockState(h.spot);
            boolean low = st.getBlock() instanceof HideBlock hb && hb.kind() == HideBlock.Kind.LOW;
            Vec3 out = Vec3.atBottomCenterOf(h.spot.relative(h.facing));
            if (low) {
                Direction side = h.facing.getClockWise();
                if (!level.getBlockState(h.spot.relative(side)).getCollisionShape(level, h.spot.relative(side)).isEmpty()) {
                    side = side.getOpposite();
                }
                out = Vec3.atBottomCenterOf(h.spot.relative(side)).add(Vec3.atLowerCornerOf(h.facing.getNormal()).scale(0.5));
            }
            SPSessionManager.teleport(p, level, out, h.yaw, 0f);
        }
        if (occupants(h.spot) == 0) {
            HideBlock.setOpen(level, h.spot, false);
            COMPROMISED.remove(h.spot);
        }
        level.playSound(null, h.spot, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.3f);
        LabEvents.fire(level, h.spot, p, "hide_exit", h.type.id());
        SPNetwork.sendTo(p, Payloads.SyncHide.none());
    }

    public static void onInput(Payloads.HideInput in, ServerPlayer p) {
        Hidden h = HIDDEN.get(p.getUUID());
        if (h == null) return;
        if (in.exit()) {
            requestExit(p);
            return;
        }
        boolean hold = in.holding() && !h.exhausted && !h.type.partial() && h.phase == Phase.HIDDEN;
        if (hold != h.holding) {
            h.holding = hold;
            sync(p, h);
        }
    }

    // Tick -----------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (HIDDEN.isEmpty()) return;
        for (Map.Entry<UUID, Hidden> e : new HashMap<>(HIDDEN).entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Hidden h = e.getValue();
            if (p == null) {
                HIDDEN.remove(e.getKey());
                Set<UUID> occ = OCCUPANTS.get(h.spot);
                if (occ != null) occ.remove(e.getKey());
                continue;
            }
            ServerLevel level = p.serverLevel();
            if (!SPPlayerData.inSP(p) || !(level.getBlockState(h.spot).getBlock() instanceof HideBlock)) {
                forceExit(p, false);
                continue;
            }
            h.age++;
            // Anclado al escondite (README 13.3)
            if (p.position().distanceToSqr(h.anchor) > 0.01) {
                SPSessionManager.teleport(p, level, h.anchor, p.getYRot(), p.getXRot());
            }
            if (!h.type.partial()) p.setInvisible(true);

            if (h.phase == Phase.EXITING) {
                if (--h.timer <= 0) forceExit(p, true);
                continue;
            }
            tickBreath(p, h, level);
        }
    }

    private static void tickBreath(ServerPlayer p, Hidden h, ServerLevel level) {
        boolean before = h.holding;
        float beforeBreath = h.breath;
        if (h.holding) {
            h.breath -= h.type.breathDrain() / 20f;
            if (h.breath <= 0) {
                // Se agota: exhala y hace ruido (README 15.4)
                h.breath = 0;
                h.holding = false;
                h.exhausted = true;
                NoiseSystem.emit(level, h.anchor, 7, NoiseType.BREATH, p);
                level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1f, 0.7f);
                p.displayClientMessage(Component.translatable("smileprotocol.hide.exhale").withStyle(ChatFormatting.RED), true);
            }
        } else {
            h.breath = Math.min(1f, h.breath + 0.15f / 20f);
            if (h.exhausted && h.breath >= 0.35f) h.exhausted = false;
            // Respiracion normal: un poco de ruido de vez en cuando
            if (!h.type.partial() && h.age % 40 == 0) NoiseSystem.emit(level, h.anchor, 1.5f, NoiseType.BREATH, p);
        }
        if (before != h.holding || Math.abs(beforeBreath - h.breath) > 0.001f && h.age % 2 == 0) sync(p, h);
    }

    private static void sync(ServerPlayer p, Hidden h) {
        SPNetwork.sendTo(p, new Payloads.SyncHide(true, h.type.id(), h.type.overlay(), h.breath, h.holding, h.yaw,
                h.type.viewCone(), h.type.partial()));
    }

    public static void onLogout(ServerPlayer p) {
        forceExit(p, true);
    }
}
