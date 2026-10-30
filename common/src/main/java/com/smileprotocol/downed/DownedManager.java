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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * Estado derribado minimo (README 9.3) para que el golpe del Berserker tenga efecto: el jugador
 * cae, se arrastra muy despacio (y eso hace ruido), se queja de vez en cuando y se desangra.
 * Al acabar el tiempo, o si un mob con {@code execution} lo remata, muere. La reanimacion por
 * los companeros, la jeringa y las animaciones propias llegan en la fase 12.
 */
public final class DownedManager {

    private static final ResourceLocation SLOW = SmileProtocol.id("downed_slow");
    private static final ResourceLocation NO_JUMP = SmileProtocol.id("downed_no_jump");

    private static final class State {
        int bleed;
        final int total;
        int groan;
        int sinceSync;

        State(int total) {
            this.total = total;
            this.bleed = total;
        }
    }

    private static final Map<UUID, State> DOWNED = new HashMap<>();

    private DownedManager() {}

    public static boolean isDowned(ServerPlayer p) {
        return DOWNED.containsKey(p.getUUID());
    }

    /** Derriba al jugador (golpe, QTE fallido...). Si ya estaba derribado no pasa nada. */
    public static void down(ServerPlayer p, @Nullable String cause) {
        if (!SPPlayerData.inSP(p) || p.isSpectator() || p.isCreative() || isDowned(p)) return;
        HideManager.forceExit(p, true);
        int total = SPConfig.get().bleed_out_time * 20;
        State s = new State(total);
        s.groan = SPConfig.get().downed_noise_interval / 2;
        DOWNED.put(p.getUUID(), s);
        p.setSprinting(false);
        modifiers(p, true);
        MotionManager.setAction(p, Action.DOWNED, 0, true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        p.displayClientMessage(Component.translatable("smileprotocol.downed.title").withStyle(ChatFormatting.DARK_RED), true);
        sync(p, s);
        SPDebug.log(p.getGameProfile().getName() + " derribado" + (cause != null ? " por " + cause : ""));
    }

    /** Remate (README 9.3): de momento, muerte directa; la secuencia con animacion es de la fase 12. */
    public static void execute(ServerPlayer p, @Nullable String by) {
        if (!isDowned(p)) return;
        SPDebug.log(p.getGameProfile().getName() + " rematado" + (by != null ? " por " + by : ""));
        kill(p);
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
            if (MotionManager.action(p) != Action.DOWNED) MotionManager.setAction(p, Action.DOWNED, 0, true);
            if (--s.groan <= 0) {
                s.groan = SPConfig.get().downed_noise_interval;
                NoiseSystem.emit(p.serverLevel(), p.position(), SPConfig.get().noise_groan, NoiseType.GROAN, p);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.9f, 0.6f);
            }
            if (++s.sinceSync >= 10) {
                s.sinceSync = 0;
                sync(p, s);
            }
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
        p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static void cleanup(ServerPlayer p) {
        modifiers(p, false);
        MotionManager.clearAction(p, Action.DOWNED);
        SPNetwork.sendTo(p, new Payloads.SyncDowned(false, 0f));
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
