package com.smileprotocol.item;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.entity.FlareEntity;
import com.smileprotocol.entity.ThrownSPItem;
import com.smileprotocol.interact.InteractionManager;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.Flashlights;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModEntities;
import com.smileprotocol.session.SPHeal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Uso de objetos en el servidor (README 10.1, 11.1, 12.7): consumibles con el click derecho
 * mantenido (cuenta el tiempo, cancela si se suelta, se cambia de hueco, lo agarran o lo derriban,
 * y aplica el efecto al acabar), y lanzables (apuntar y lanzar, con espera entre lanzamientos).
 * El cliente decide que gesto es; aqui se valida todo.
 */
public final class UseManager {

    private static final class Use {
        final int slot;
        final ItemDefs.Consumable def;
        final ItemStack stack;
        final boolean downed;
        int ticks;

        Use(int slot, ItemDefs.Consumable def, ItemStack stack, boolean downed) {
            this.slot = slot;
            this.def = def;
            this.stack = stack;
            this.downed = downed;
        }
    }

    private static final Map<UUID, Use> USES = new HashMap<>();
    private static final Map<UUID, Integer> AIMING = new HashMap<>();
    private static final Map<UUID, Long> LAST_THROW = new HashMap<>();
    /** Ticks que dura el gesto de lanzar. */
    public static final int THROW_TICKS = 8;

    private UseManager() {}

    public static boolean isUsing(ServerPlayer p) {
        return USES.containsKey(p.getUUID());
    }

    public static void onInput(Payloads.UseItem in, ServerPlayer p) {
        switch (in.kind()) {
            case Payloads.UseItem.START -> start(p, in.slot());
            case Payloads.UseItem.STOP -> stop(p, true);
            case Payloads.UseItem.AIM -> aim(p, in.slot());
            case Payloads.UseItem.AIM_STOP -> stopAim(p);
            case Payloads.UseItem.THROW -> throwItem(p, in.slot(), in.yaw(), in.pitch());
            default -> { }
        }
    }

    // Consumibles ------------------------------------------------------------------------------

    private static void start(ServerPlayer p, int slot) {
        if (!SPPlayerData.inSP(p) || p.isSpectator() || isUsing(p)) return;
        SPPlayerData data = SPPlayerData.of(p);
        if (slot != data.getSPInventory().getSelected()) return;
        ItemStack stack = data.getSPInventory().get(slot);
        ItemDefs.Consumable def = ItemDefs.consumable(p.server, stack);
        if (def == null) return;
        Action a = MotionManager.action(p);
        boolean downed = DownedManager.isDowned(p);
        // la prioridad del click derecho la resuelve el cliente; si ya interactua con algo, no (README 11.1)
        if (InteractionManager.isInteracting(p)) return;
        if (downed ? !def.downed() || a != Action.DOWNED : a != Action.NONE) return;
        if (def.onlyDowned() && !downed) {
            p.displayClientMessage(Component.translatable("smileprotocol.use.only_downed").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if ("HEAL".equals(def.effect()) && p.getHealth() >= p.getMaxHealth()) {
            p.displayClientMessage(Component.translatable("smileprotocol.use.full_health").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if ("BATTERY".equals(def.effect()) && Flashlights.battery(p) >= 0.999f) {
            p.displayClientMessage(Component.translatable("smileprotocol.use.full_battery").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        USES.put(p.getUUID(), new Use(slot, def, stack, downed));
        p.setSprinting(false);
        if (downed) MotionManager.setAction(p, Action.DOWNED, 1, true);    // derribado: se pincha la jeringa
        else MotionManager.setAction(p, Action.USE, def.anim(), false);
        sync(p, USES.get(p.getUUID()));
    }

    /** Suelta o se cancela (cambio de hueco, agarre, derribo, secuencia...). */
    public static void stop(ServerPlayer p, boolean notify) {
        Use u = USES.remove(p.getUUID());
        if (u != null) {
            if (u.downed) {
                if (DownedManager.isDowned(p)) MotionManager.setAction(p, Action.DOWNED, 0, true);
            } else {
                MotionManager.clearAction(p, Action.USE);
            }
            if (notify) SPNetwork.sendTo(p, Payloads.InteractState.idle());
        }
        stopAim(p);
    }

    public static void tick(MinecraftServer server) {
        if (!USES.isEmpty()) {
            for (Map.Entry<UUID, Use> e : new HashMap<>(USES).entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                Use u = e.getValue();
                if (p == null || !SPPlayerData.inSP(p)) {
                    USES.remove(e.getKey());
                    continue;
                }
                SPPlayerData data = SPPlayerData.of(p);
                Action a = MotionManager.action(p);
                boolean ok = data.getSPInventory().getSelected() == u.slot && ItemStack.isSameItem(data.getSPInventory().get(u.slot), u.stack)
                        && (u.downed ? a == Action.DOWNED && DownedManager.isDowned(p) : a == Action.USE);
                if (!ok) {
                    stop(p, true);
                    continue;
                }
                u.ticks++;
                if (u.def.noise() > 0 && u.ticks % 20 == 0) NoiseSystem.emit(p.serverLevel(), p.position(), u.def.noise(), NoiseType.ITEM, p);
                sync(p, u);
                if (u.ticks >= u.def.useTime()) finish(p, u);
            }
        }
        // el gesto de lanzar dura unos ticks
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (MotionManager.action(p) == Action.THROW) {
                Long t = LAST_THROW.get(p.getUUID());
                if (t == null || p.serverLevel().getGameTime() - t >= THROW_TICKS) MotionManager.clearAction(p, Action.THROW);
            }
        }
    }

    private static void sync(ServerPlayer p, Use u) {
        SPNetwork.sendTo(p, new Payloads.InteractState(true, u.ticks, u.def.useTime(), false,
                Component.translatable("smileprotocol.use.using", u.stack.getHoverName())));
    }

    private static void finish(ServerPlayer p, Use u) {
        USES.remove(p.getUUID());
        if (u.def.consume()) SPInventoryOps.take(p, u.slot);
        SPNetwork.sendTo(p, Payloads.InteractState.idle());
        if (u.def.sound() != null) {
            SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(u.def.sound());
            if (snd != null) p.serverLevel().playSound(null, p.blockPosition(), snd, SoundSource.PLAYERS, 0.8f, 1f);
        }
        switch (u.def.effect()) {
            case "HEAL" -> SPHeal.heal(p, u.def.amount());
            case "BATTERY" -> Flashlights.recharge(p, u.def.amount());
            case "FLARE" -> {
                FlareEntity f = ModEntities.FLARE.get().create(p.serverLevel());
                if (f != null) {
                    Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
                    // se suelta a los pies, un poco por delante
                    f.moveTo(p.getX() + look.x * 0.6, p.getY() + 0.5, p.getZ() + look.z * 0.6, p.getYRot(), 0);
                    f.setDeltaMovement(look.x * 0.12, 0.05, look.z * 0.12);
                    f.setBurnTime((int) u.def.amount());
                    p.serverLevel().addFreshEntity(f);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
                }
            }
            case "WALKIE" -> com.smileprotocol.voice.WalkieManager.toggle(p);
            case "ADRENALINE" -> {
                if (DownedManager.isDowned(p)) DownedManager.selfRevive(p, u.def.amount());
            }
            default -> { }
        }
        if (!u.downed) MotionManager.clearAction(p, Action.USE);
        NoiseSystem.emit(p.serverLevel(), p.position(), u.def.noise(), NoiseType.ITEM, p);
        SPDebug.log(p.getGameProfile().getName() + " usa " + u.stack.getHoverName().getString());
    }

    // Lanzables --------------------------------------------------------------------------------

    private static void aim(ServerPlayer p, int slot) {
        if (!SPPlayerData.inSP(p) || p.isSpectator()) return;
        SPPlayerData data = SPPlayerData.of(p);
        if (slot != data.getSPInventory().getSelected()) return;
        if (ItemDefs.throwable(p.server, data.getSPInventory().get(slot)) == null) return;
        // no corriendo, ni en dive, vault, escondites, agarrado o en secuencias (README 12.7)
        if (MotionManager.action(p) != Action.NONE || p.isSprinting() || p.isVisuallyCrawling() || isUsing(p)) return;
        AIMING.put(p.getUUID(), slot);
        MotionManager.setAction(p, Action.AIM, 0, false);
    }

    private static void stopAim(ServerPlayer p) {
        if (AIMING.remove(p.getUUID()) != null) MotionManager.clearAction(p, Action.AIM);
    }

    private static void throwItem(ServerPlayer p, int slot, float yaw, float pitch) {
        Integer aimSlot = AIMING.get(p.getUUID());
        long now = p.serverLevel().getGameTime();
        Long last = LAST_THROW.get(p.getUUID());
        if (aimSlot == null || aimSlot != slot || MotionManager.action(p) != Action.AIM
                || (last != null && now - last < SPConfig.get().throw_cooldown)) return;
        ItemStack stack = SPPlayerData.of(p).getSPInventory().get(slot);
        ItemDefs.Throwable def = ItemDefs.throwable(p.server, stack);
        if (def == null) return;
        // la direccion la manda el cliente; no puede alejarse mucho de hacia donde mira
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot())) > 30 || Math.abs(pitch - p.getXRot()) > 30) {
            yaw = p.getYRot();
            pitch = p.getXRot();
        }
        AIMING.remove(p.getUUID());
        ItemStack thrown = SPInventoryOps.take(p, slot);
        ThrownSPItem e = new ThrownSPItem(ModEntities.THROWN_ITEM.get(), p, p.serverLevel(), def.noise(), def.breaks());
        e.setItem(thrown);
        e.shootFromRotation(p, pitch, yaw, 0f, Math.min(def.speed(), 1.6f), 0.5f);
        p.serverLevel().addFreshEntity(e);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.7f);
        LAST_THROW.put(p.getUUID(), now);
        MotionManager.setAction(p, Action.THROW, 0, false);
        SPDebug.log(p.getGameProfile().getName() + " lanza " + thrown.getHoverName().getString());
    }

    public static void onLogout(ServerPlayer p) {
        USES.remove(p.getUUID());
        AIMING.remove(p.getUUID());
        LAST_THROW.remove(p.getUUID());
    }
}
