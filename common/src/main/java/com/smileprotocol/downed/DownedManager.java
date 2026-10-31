package com.smileprotocol.downed;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Estado derribado (README 9.3): el jugador cae, se arrastra muy despacio (y eso hace ruido), se
 * queja de vez en cuando y se desangra. Un companero lo puede reanimar con la interaccion
 * mantenida (mientras lo reanima no se desangra); al levantarse recupera parte de la vida y queda
 * protegido de los remates un momento. Si se acaba el tiempo muere; un mob con {@code execution}
 * lo remata con una secuencia ({@code SequenceManager}). La jeringa de adrenalina es de la fase 13.
 */
public final class DownedManager {

    private static final ResourceLocation SLOW = SmileProtocol.id("downed_slow");
    private static final ResourceLocation NO_JUMP = SmileProtocol.id("downed_no_jump");

    private static final class State {
        int bleed;
        final int total;
        int groan;
        int sinceSync;
        /** Progreso de la reanimacion (0-1) y ticks desde el ultimo aviso del rescatador. */
        float revive = -1f;
        int reviveStale;

        State(int total) {
            this.total = total;
            this.bleed = total;
        }
    }

    private static final Map<UUID, State> DOWNED = new HashMap<>();
    /** Proteccion contra remates tras levantarse: tick del servidor en que acaba. */
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();

    private DownedManager() {}

    public static boolean isDowned(ServerPlayer p) {
        return DOWNED.containsKey(p.getUUID());
    }

    /** Derriba al jugador (golpe, QTE fallido...). Si ya estaba derribado no pasa nada. */
    public static void down(ServerPlayer p, @Nullable String cause) {
        if (!SPPlayerData.inSP(p) || p.isSpectator() || p.isCreative() || isDowned(p)) return;
        // Regla «sin reanimacion»: quien cae, muere
        com.smileprotocol.run.Group rg = com.smileprotocol.run.RunManager.groupOf(p);
        if (rg != null && rg.rules().has(com.smileprotocol.difficulty.RunRules.NO_REVIVE)) {
            DeathManager.die(p, cause);
            return;
        }
        com.smileprotocol.score.ScoreManager.onDowned(p);
        com.smileprotocol.debug.Playtest.count("derribo por " + (cause == null || cause.isEmpty() ? "?" : cause));
        HideManager.forceExit(p, true);
        com.smileprotocol.qte.QTEManager.clear(p);
        com.smileprotocol.interact.InteractionManager.stop(p, true);
        com.smileprotocol.item.UseManager.stop(p, true);
        int total = SPConfig.get().bleed_out_time * 20;
        State s = new State(total);
        s.groan = SPConfig.get().downed_noise_interval / 2;
        DOWNED.put(p.getUUID(), s);
        p.setSprinting(false);
        modifiers(p, true);
        MotionManager.setAction(p, Action.DOWNED, 0, true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        sync(p, s);
        com.smileprotocol.mechanism.LabEvents.fire(p.serverLevel(), p.blockPosition(), p, "player_downed", cause != null ? cause : "");
        SPDebug.log(p.getGameProfile().getName() + " derribado" + (cause != null ? " por " + cause : ""));
    }

    /** Se puede rematar: derribado, sin proteccion y sin otra secuencia en marcha. */
    public static boolean canExecute(ServerPlayer p) {
        return isDowned(p) && !isProtected(p) && !com.smileprotocol.sequence.SequenceManager.inSequence(p);
    }

    public static boolean isProtected(ServerPlayer p) {
        Long until = PROTECTED.get(p.getUUID());
        return until != null && p.server.getTickCount() < until;
    }

    /**
     * Remate (README 9.3): el mob empieza su secuencia y al acabar el jugador muere. Devuelve false
     * si no se puede (protegido, ya en otra secuencia...).
     */
    public static boolean execute(ServerPlayer p, com.smileprotocol.entity.SPMonster mob,
                                  com.smileprotocol.sequence.SequenceKind kind) {
        if (!canExecute(p)) return false;
        return com.smileprotocol.sequence.SequenceManager.start(mob, p, kind);
    }

    /** El rescatador avisa cada tick de como va (README 9.3); sin avisos se cancela solo. */
    public static void reviveProgress(ServerPlayer p, float progress) {
        State s = DOWNED.get(p.getUUID());
        if (s == null) return;
        boolean start = s.revive < 0;
        s.revive = progress;
        s.reviveStale = 0;
        if (start) SPNetwork.sendTo(p, new Payloads.SyncRevive(progress, false));
    }

    /** Jeringa de adrenalina (README 10.1): se levanta solo, con poca vida. */
    public static void selfRevive(ServerPlayer p, float healthPercent) {
        if (!isDowned(p)) return;
        revive(p, null);
        p.setHealth(Math.max(1f, p.getMaxHealth() * healthPercent / 100f));
    }

    /** Reanimado: se levanta con parte de la vida y queda protegido un momento. */
    public static void revive(ServerPlayer p, @Nullable ServerPlayer by) {
        if (!isDowned(p)) return;
        DOWNED.remove(p.getUUID());
        cleanup(p);
        SPConfig.Values cfg = SPConfig.get();
        p.setHealth(Math.max(1f, p.getMaxHealth() * cfg.revive_health_percent / 100f));
        PROTECTED.put(p.getUUID(), (long) p.server.getTickCount() + cfg.revive_protection);
        MotionManager.setAction(p, Action.GETUP, by != null ? 1 : 0, false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1f, 0.8f);
        if (by != null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("smileprotocol.downed.revived_by", by.getDisplayName())
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
        com.smileprotocol.mechanism.LabEvents.fire(p.serverLevel(), p.blockPosition(), p, "player_revived", "");
        SPDebug.log(p.getGameProfile().getName() + " reanimado" + (by != null ? " por " + by.getGameProfile().getName() : ""));
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, State>> it = DOWNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            State s = e.getValue();
            if (p == null || !SPPlayerData.inSP(p) || !p.isAlive()) {
                if (p != null) cleanup(p);
                it.remove();
                continue;
            }
            p.setSprinting(false);
            Action a = MotionManager.action(p);
            if (a != Action.DOWNED && a != Action.SEQUENCE) MotionManager.setAction(p, Action.DOWNED, 0, true);
            if (--s.groan <= 0) {
                s.groan = SPConfig.get().downed_noise_interval;
                NoiseSystem.emit(p.serverLevel(), p.position(), SPConfig.get().noise_groan, NoiseType.GROAN, p);
                p.serverLevel().playSound(null, p.blockPosition(), com.smileprotocol.registry.ModSounds.PLAYER_GROAN.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            }
            boolean reviving = s.revive >= 0;
            if (reviving && ++s.reviveStale > 3) {
                // el rescatador ha soltado
                s.revive = -1f;
                SPNetwork.sendTo(p, new Payloads.SyncRevive(-1f, false));
                reviving = false;
            }
            if (++s.sinceSync >= 10) {
                s.sinceSync = 0;
                sync(p, s);
                if (reviving) SPNetwork.sendTo(p, new Payloads.SyncRevive(s.revive, false));
            }
            // Mientras lo reanima un companero, o durante un remate, no se desangra
            if (reviving || com.smileprotocol.sequence.SequenceManager.inSequence(p)) continue;
            if (--s.bleed <= 0) {
                it.remove();
                cleanup(p);
                kill(p);
            }
        }
    }

    /** Al salir de la partida, morir o desconectarse: se quita todo. */
    public static void clear(ServerPlayer p) {
        if (DOWNED.remove(p.getUUID()) != null) cleanup(p);
    }

    private static void kill(ServerPlayer p) {
        DOWNED.remove(p.getUUID());
        cleanup(p);
        DeathManager.die(p, "desangrado");
    }

    private static void cleanup(ServerPlayer p) {
        modifiers(p, false);
        MotionManager.clearAction(p, Action.DOWNED);
        SPNetwork.sendTo(p, new Payloads.SyncDowned(false, 0f));
        SPNetwork.sendTo(p, new Payloads.SyncRevive(-1f, false));
    }

    private static void modifiers(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(SLOW);
            if (on) speed.addTransientModifier(new AttributeModifier(SLOW, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (jump != null) {
            jump.removeModifier(NO_JUMP);
            if (on) jump.addTransientModifier(new AttributeModifier(NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void sync(ServerPlayer p, State s) {
        SPNetwork.sendTo(p, new Payloads.SyncDowned(true, s.bleed / (float) s.total));
    }
}
