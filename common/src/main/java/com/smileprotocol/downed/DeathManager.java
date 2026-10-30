package com.smileprotocol.downed;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.MemberStatus;
import com.smileprotocol.run.RunData;
import com.smileprotocol.run.RunManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Muerte final dentro de la partida (README 9.3): el jugador no ve la pantalla de muerte de
 * vanilla; pasa a espectador y su camara sigue a un companero vivo (cambia de companero con
 * click). Sigue en el grupo con estado {@code DEAD}; al subir el grupo en el ascensor reaparece
 * en la cabina (fase 16). Si mueren todos, el grupo queda eliminado.
 */
public final class DeathManager {

    private DeathManager() {}

    public static boolean isDead(ServerPlayer p) {
        Group g = RunManager.groupOf(p);
        return g != null && g.isDead(p.getUUID());
    }

    /** Muere de verdad. Fuera de la partida es la muerte de vanilla. */
    public static void die(ServerPlayer p, @Nullable String cause) {
        if (!SPPlayerData.inSP(p) || isDead(p)) {
            if (!SPPlayerData.inSP(p)) p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            return;
        }
        DownedManager.clear(p);
        com.smileprotocol.qte.QTEManager.clear(p);
        com.smileprotocol.interact.InteractionManager.stop(p, true);
        com.smileprotocol.hide.HideManager.forceExit(p, false);
        com.smileprotocol.sequence.SequenceManager.clear(p);
        com.smileprotocol.motion.MotionManager.setAction(p, com.smileprotocol.motion.Action.NONE, 0, false);

        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1f, 0.8f);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        Group g = RunManager.groupOf(p);
        if (g != null) {
            g.setStatus(p.getUUID(), MemberStatus.DEAD);
            RunData.get(p.server).changed();
            Component msg = Component.translatable("smileprotocol.downed.died", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED);
            for (UUID id : g.members().keySet()) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(id);
                if (o != null && SPPlayerData.inSP(o)) o.sendSystemMessage(msg);
            }
        }
        p.setGameMode(GameType.SPECTATOR);
        SPNetwork.sendTo(p, new Payloads.SyncRevive(-1f, true));
        spectate(p, 0);
        SPDebug.log(p.getGameProfile().getName() + " muere" + (cause != null ? " (" + cause + ")" : ""));
    }

    /** Al volver a conectarse estando muerto: sigue de espectador. */
    public static void onRejoinDead(ServerPlayer p) {
        p.setGameMode(GameType.SPECTATOR);
        SPNetwork.sendTo(p, new Payloads.SyncRevive(-1f, true));
        spectate(p, 0);
    }

    /** Los muertos solo pueden mirar a sus companeros vivos. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!SPPlayerData.inSP(p) || !isDead(p)) continue;
            if (!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
            Entity cam = p.getCamera();
            if (cam == p || !validTarget(p, cam)) spectate(p, 0);
        }
    }

    /** Pasa al siguiente companero vivo (click del espectador). */
    public static void cycle(ServerPlayer p, int dir) {
        if (isDead(p)) spectate(p, dir);
    }

    private static void spectate(ServerPlayer p, int dir) {
        List<ServerPlayer> alive = aliveMates(p);
        if (alive.isEmpty()) {
            if (p.getCamera() != p) p.setCamera(p);
            return;
        }
        Entity cam = p.getCamera();
        int idx = alive.indexOf(cam instanceof ServerPlayer sp ? sp : null);
        ServerPlayer next;
        if (idx < 0) next = alive.stream().min(Comparator.comparingDouble(o -> o.distanceToSqr(p))).orElse(alive.get(0));
        else next = alive.get(Math.floorMod(idx + dir, alive.size()));
        if (next != cam) {
            if (next.level() != p.level()) com.smileprotocol.session.SPSessionManager.teleport(p, next.serverLevel(), next.position(), next.getYRot(), next.getXRot());
            p.setCamera(next);
            p.displayClientMessage(Component.translatable("smileprotocol.downed.spectating", next.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static boolean validTarget(ServerPlayer p, Entity cam) {
        return cam instanceof ServerPlayer o && aliveMates(p).contains(o);
    }

    private static List<ServerPlayer> aliveMates(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        Group g = RunManager.groupOf(p);
        if (g == null) return out;
        for (UUID id : g.activeMembers()) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(id);
            if (o != null && o != p && o.isAlive() && !o.isSpectator() && SPPlayerData.inSP(o)) out.add(o);
        }
        return out;
    }
}
