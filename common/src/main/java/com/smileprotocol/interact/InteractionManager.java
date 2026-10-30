package com.smileprotocol.interact;

import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Interaccion mantenida en el servidor (README 11): el cliente avisa de que empieza o suelta,
 * y aqui se cuenta el tiempo, se comprueba que no se mueva, se emite el ruido (cuanto mas tarda,
 * mas ruido), se juntan los jugadores de una interaccion cooperativa y se decide el resultado.
 */
public final class InteractionManager {

    /** Distancia maxima al objetivo. */
    private static final double REACH = 5.0;

    static final class Session {
        final boolean entity;
        final BlockPos pos;
        final int entityId;
        InteractionSpec spec;
        float progress;
        final Vec3 start;
        int age;

        Session(boolean entity, BlockPos pos, int entityId, InteractionSpec spec, Vec3 start) {
            this.entity = entity;
            this.pos = pos;
            this.entityId = entityId;
            this.spec = spec;
            this.start = start;
        }

        boolean sameTarget(Session o) {
            return entity == o.entity && (entity ? entityId == o.entityId : pos.equals(o.pos));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private InteractionManager() {}

    public static boolean isInteracting(Player p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    // Paquetes -----------------------------------------------------------------------------------

    public static void onInput(Payloads.Interact in, ServerPlayer p) {
        if (in.kind() == Payloads.Interact.STOP) {
            stop(p, true);
            return;
        }
        if (!SPPlayerData.inSP(p) || p.isSpectator()) return;
        Action a = MotionManager.action(p);
        if (a == Action.DIVE || a == Action.VAULT || a == Action.HIDDEN || a == Action.DOWNED || a == Action.GRABBED) return;

        boolean entity = in.kind() == Payloads.Interact.ENTITY;
        BlockPos pos = in.pos();
        InteractionSpec spec;
        if (entity) {
            spec = entitySpec(p, in.entityId());
        } else {
            BlockState st = p.level().getBlockState(pos);
            if (!(st.getBlock() instanceof SPInteractable it)) return;
            pos = it.root(p.level(), pos, st);
            if (Vec3.atCenterOf(pos).distanceToSqr(p.getEyePosition()) > REACH * REACH) return;
            spec = it.spec(p.level(), pos, p.level().getBlockState(pos), p);
        }
        if (spec == null) return;
        if (spec.blocked()) {
            p.displayClientMessage(spec.prompt().copy().withStyle(ChatFormatting.GRAY), true);
            return;
        }
        stop(p, false);
        Session s = new Session(entity, pos.immutable(), in.entityId(), spec, p.position());
        SESSIONS.put(p.getUUID(), s);
        MotionManager.setAction(p, Action.INTERACT, spec.anim(), false);
        if (spec.ticks() <= 0) finish(p, s);
        else if (!entity) onProgressBlock(p, s, true);
    }

    public static void onDrop(Payloads.DropItem in, ServerPlayer p) {
        if (!SPPlayerData.inSP(p) || MotionManager.action(p) == Action.HIDDEN) return;
        SPInventory inv = SPPlayerData.of(p).getSPInventory();
        int slot = inv.getSelected();
        ItemStack stack = inv.get(slot);
        if (stack.isEmpty()) return;
        SPInventoryOps.take(p, slot);
        spawnItem(p.serverLevel(), p.position().add(p.getLookAngle().multiply(0.4, 0, 0.4)), stack);
        NoiseSystem.emit(p.serverLevel(), p.position(), SPConfig.get().noise_drop, NoiseType.ITEM, p);
    }

    /** Objeto de la partida en el suelo: no se recoge solo ni desaparece (README 10, 12.7). */
    public static ItemEntity spawnItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, pos.x, pos.y + 0.1, pos.z, stack.copy(), 0, 0.05, 0);
        e.setNeverPickUp();
        e.setUnlimitedLifetime();
        level.addFreshEntity(e);
        return e;
    }

    // Objetivos ----------------------------------------------------------------------------------

    @Nullable
    private static InteractionSpec entitySpec(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (e instanceof Player other && other != p) {
            if (other.distanceToSqr(p) > PLAYER_REACH * PLAYER_REACH || !other.isAlive()) return null;
            return playerSpec(other);
        }
        if (!(e instanceof ItemEntity item) || !e.isAlive()) return null;
        if (e.position().distanceToSqr(p.getEyePosition()) > REACH * REACH) return null;
        return pickupSpec(item.getItem(), SPInventoryOps.isFull(p));
    }

    /** Distancia maxima a un companero para reanimarlo o ayudarlo. */
    public static final double PLAYER_REACH = 2.6;

    /**
     * Interaccion con un companero (README 9.3, 18): reanimarlo si esta derribado o ayudarle a
     * soltarse si lo tiene agarrado un mob. Null si no hay nada que hacer. Igual en los dos lados.
     */
    @Nullable
    public static InteractionSpec playerSpec(Player other) {
        if (other.isSpectator()) return null;
        com.smileprotocol.motion.MotionView v = com.smileprotocol.motion.MotionStates.view(other);
        SPConfig.Values cfg = SPConfig.get();
        if (v.action() == Action.DOWNED) {
            return InteractionSpec.of(Component.translatable("smileprotocol.interact.revive", other.getDisplayName()),
                    cfg.revive_time, cfg.noise_revive, PoseCalculator.INTERACT_REVIVE);
        }
        if (v.action() == Action.GRABBED) {
            return InteractionSpec.of(Component.translatable("smileprotocol.interact.help", other.getDisplayName()),
                    cfg.qte_help_time, cfg.noise_revive, PoseCalculator.INTERACT_PULL);
        }
        return null;
    }

    /** Recoger un objeto: 0,3 s (README 10). Tambien la usa el cliente para el prompt. */
    public static InteractionSpec pickupSpec(ItemStack stack, boolean full) {
        if (full) return InteractionSpec.blocked(Component.translatable("smileprotocol.interact.full"));
        return InteractionSpec.of(Component.translatable("smileprotocol.interact.pickup", stack.getHoverName()), 6, 1,
                PoseCalculator.INTERACT_PICKUP);
    }

    // Tick ---------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        List<UUID> done = new ArrayList<>();
        // Cuantos jugadores hay en cada objetivo (cooperativas)
        Map<Session, Integer> counts = new HashMap<>();
        for (Session s : SESSIONS.values()) {
            int n = 0;
            for (Session o : SESSIONS.values()) if (s.sameTarget(o)) n++;
            counts.put(s, n);
        }

        for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null || !SPPlayerData.inSP(p) || !SESSIONS.containsKey(e.getKey())) {
                done.add(e.getKey());
                continue;
            }
            if (MotionManager.action(p) != Action.INTERACT) {
                // lo han agarrado, derribado... mientras interactuaba (README 9.3)
                cancel(p, s, Component.empty());
                continue;
            }
            InteractionSpec spec = recompute(p, s);
            if (spec == null || spec.blocked()) {
                cancel(p, s, spec == null ? Component.empty() : spec.prompt());
                continue;
            }
            s.spec = spec;
            if (spec.cancelOnMove() && p.position().distanceToSqr(s.start) > 0.35 * 0.35) {
                cancel(p, s, Component.translatable("smileprotocol.interact.moved"));
                continue;
            }
            s.age++;
            int n = counts.getOrDefault(s, 1);
            boolean waiting = n < spec.minPlayers();
            if (!waiting) s.progress += n > 1 ? 1f + 0.5f * (n - 1) : 1f;
            // Cuanto mas tarda, mas ruido (README 11)
            if (spec.noise() > 0 && s.age % 20 == 0) {
                NoiseSystem.emit(p.serverLevel(), targetPos(p, s), spec.noise() * 0.6f, NoiseType.INTERACT, p);
            }
            SPNetwork.sendTo(p, new Payloads.InteractState(true, (int) s.progress, spec.ticks(), waiting, spec.prompt()));
            if (s.entity && p.level().getEntity(s.entityId) instanceof ServerPlayer other) {
                // la victima ve que la reaniman; al agarrado, la ayuda suma
                if (com.smileprotocol.downed.DownedManager.isDowned(other)) {
                    com.smileprotocol.downed.DownedManager.reviveProgress(other, Math.min(1f, s.progress / spec.ticks()));
                } else if (com.smileprotocol.qte.QTEManager.isGrabbed(other) && !waiting) {
                    float step = (n > 1 ? 1f + 0.5f * (n - 1) : 1f) / spec.ticks();
                    com.smileprotocol.qte.QTEManager.help(other, step / n);
                    if (!com.smileprotocol.qte.QTEManager.isGrabbed(other)) {
                        finish(p, s);
                        continue;
                    }
                }
            }
            if (s.progress >= spec.ticks()) finish(p, s);
        }
        for (UUID id : done) {
            Session s = SESSIONS.remove(id);
            if (s != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) end(p, s);
            }
        }
    }

    @Nullable
    private static InteractionSpec recompute(ServerPlayer p, Session s) {
        if (s.entity) return entitySpec(p, s.entityId);
        BlockState st = p.level().getBlockState(s.pos);
        if (!(st.getBlock() instanceof SPInteractable it)) return null;
        if (Vec3.atCenterOf(s.pos).distanceToSqr(p.getEyePosition()) > REACH * REACH) return null;
        return it.spec(p.level(), s.pos, st, p);
    }

    private static Vec3 targetPos(ServerPlayer p, Session s) {
        if (s.entity) {
            Entity e = p.level().getEntity(s.entityId);
            return e != null ? e.position() : p.position();
        }
        return Vec3.atCenterOf(s.pos);
    }

    private static void finish(ServerPlayer p, Session s) {
        // Todos los que estaban en el mismo objetivo terminan a la vez
        List<ServerPlayer> partners = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            if (e.getValue() == s || e.getValue().sameTarget(s)) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(e.getKey());
                if (o != null) partners.add(o);
                it.remove();
            }
        }
        if (s.spec.noise() > 0) NoiseSystem.emit(p.serverLevel(), targetPos(p, s), s.spec.noise(), NoiseType.INTERACT, p);
        if (s.entity) {
            Entity e = p.level().getEntity(s.entityId);
            if (e instanceof ServerPlayer other) {
                if (com.smileprotocol.downed.DownedManager.isDowned(other)) {
                    com.smileprotocol.downed.DownedManager.revive(other, p);
                    p.serverLevel().playSound(null, other.blockPosition(), com.smileprotocol.registry.ModSounds.PLAYER_REVIVE.get(),
                            SoundSource.PLAYERS, 1f, 1f);
                }
            } else if (e instanceof ItemEntity item && item.isAlive()) {
                if (SPInventoryOps.give(p, item.getItem())) {
                    item.discard();
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.1f);
                } else {
                    p.displayClientMessage(Component.translatable("smileprotocol.interact.full").withStyle(ChatFormatting.GOLD), true);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
                }
            }
        } else {
            BlockState st = p.level().getBlockState(s.pos);
            if (st.getBlock() instanceof SPInteractable it) {
                it.onProgress(p.serverLevel(), s.pos, st, false);
                it.complete(p.serverLevel(), s.pos, st, p);
            }
        }
        for (ServerPlayer o : partners) end(o, s);
        if (!partners.contains(p)) end(p, s);
    }

    private static void cancel(ServerPlayer p, Session s, Component reason) {
        SESSIONS.remove(p.getUUID());
        if (!s.entity && !anyOn(s)) {
            BlockState st = p.level().getBlockState(s.pos);
            if (st.getBlock() instanceof SPInteractable it) it.onProgress(p.serverLevel(), s.pos, st, false);
        }
        if (!reason.getString().isEmpty()) p.displayClientMessage(reason.copy().withStyle(ChatFormatting.GRAY), true);
        end(p, s);
    }

    private static void end(ServerPlayer p, Session s) {
        MotionManager.clearAction(p, Action.INTERACT);
        SPNetwork.sendTo(p, Payloads.InteractState.idle());
    }

    private static boolean anyOn(Session s) {
        for (Session o : SESSIONS.values()) if (o.sameTarget(s)) return true;
        return false;
    }

    private static void onProgressBlock(ServerPlayer p, Session s, boolean active) {
        BlockState st = p.level().getBlockState(s.pos);
        if (st.getBlock() instanceof SPInteractable it) it.onProgress(p.serverLevel(), s.pos, st, active);
    }

    /** Suelta la interaccion en curso. */
    public static void stop(ServerPlayer p, boolean notify) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        if (!s.entity && !anyOn(s)) onProgressBlock(p, s, false);
        if (notify) end(p, s);
        else MotionManager.clearAction(p, Action.INTERACT);
    }

    public static void onLogout(ServerPlayer p) {
        stop(p, false);
    }

    /** Para comprobar que un objetivo sigue siendo el mismo (no se usa fuera). */
    static boolean same(@Nullable Object a, @Nullable Object b) {
        return Objects.equals(a, b);
    }

    public static boolean isClient(Level level) {
        return level.isClientSide;
    }
}
