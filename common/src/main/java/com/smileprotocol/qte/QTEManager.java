package com.smileprotocol.qte;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Agarre y minijuego de escape (README 18). El servidor ancla al jugador delante del mob, cuenta
 * el tiempo y valida cada pulsacion; el cliente solo dibuja y envia teclas.
 * <ul>
 *   <li>MASH: pulsaciones validas (como mucho 2 por tick) hasta el objetivo.</li>
 *   <li>TIMING: el servidor calcula donde esta el cursor al recibir la pulsacion (con margen de
 *   latencia) y cuenta aciertos y fallos.</li>
 *   <li>SEQUENCE: la secuencia sale de la semilla; una tecla equivocada quita tiempo.</li>
 *   <li>HOLD: el servidor simula la aguja con el estado de la tecla que manda el cliente.</li>
 * </ul>
 * Exito: el jugador se suelta con un empujon y el mob queda aturdido. Fallo: derribo o muerte.
 */
public final class QTEManager {

    /** El mob que agarra recibe el resultado. */
    public interface Grabber {
        void onGrabStart(ServerPlayer p);

        void onGrabEnd(ServerPlayer p, boolean escaped);
    }

    private static final class Session {
        final UUID mob;
        final QTEType type;
        final int total;
        final long seed;
        final int difficulty;
        final String fail;
        int elapsed;
        int progress;      // MASH: pulsaciones; TIMING: aciertos; SEQUENCE: paso; HOLD: ticks en la zona
        int misses;
        int target;
        int lastPressTick = -10;
        int pressesThisTick;
        boolean holding;
        float needle = 0.3f;
        float velocity;
        Vec3 anchor;
        Vec3 start;
        float yaw;

        Session(UUID mob, QTEType type, int total, long seed, int difficulty, String fail) {
            this.mob = mob;
            this.type = type;
            this.total = total;
            this.seed = seed;
            this.difficulty = difficulty;
            this.fail = fail;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Distancia del mob a la que queda el jugador agarrado (donde estan las manos). */
    public static final double GRAB_DISTANCE = 1.17;
    /** Ticks que tarda en arrastrar al jugador hasta las manos. */
    private static final int PULL_TICKS = 5;

    private QTEManager() {}

    public static boolean isGrabbed(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    /** Empieza un agarre. Devuelve false si no se puede (esquiva con dive, ya agarrado, derribado...). */
    public static boolean startGrab(SPMonster mob, ServerPlayer p, QTEType type, int baseTicks, String fail) {
        if (!SPPlayerData.inSP(p) || p.isSpectator() || p.isCreative() || isGrabbed(p) || DownedManager.isDowned(p)) return false;
        // Dive bien hecho: esquiva el agarre (README 18)
        if (MotionManager.action(p) == Action.DIVE) {
            SPDebug.log(p.getGameProfile().getName() + " esquiva el agarre con dive");
            return false;
        }
        HideManager.forceExit(p, true);
        Group g = RunManager.groupOf(p);
        int ticks = g != null ? g.difficulty().scaleQte(baseTicks) : baseTicks;
        int difficulty = g != null ? Math.max(0, 4 - g.difficulty().groupSize()) : 1;
        long seed = p.getRandom().nextLong();
        Session s = new Session(mob.getUUID(), type, ticks, seed, difficulty, fail);
        s.target = switch (type) {
            case MASH -> 16 + difficulty * 3;
            case TIMING -> QTEType.TIMING_HITS;
            case SEQUENCE -> QTEType.SEQUENCE_LENGTH;
            case HOLD -> Math.round(ticks * 0.45f);
        };
        Vec3 look = Vec3.directionFromRotation(0, mob.getYRot());
        // Las manos del mob sujetan a la victima por los hombros a ~1,17 bloques delante del pecho
        // (animacion "grab"): el jugador es arrastrado hasta ahi en los primeros ticks.
        s.anchor = mob.position().add(look.scale(GRAB_DISTANCE));
        s.start = p.position();
        s.yaw = mob.getYRot() + 180f;
        SESSIONS.put(p.getUUID(), s);
        MotionManager.setAction(p, Action.GRABBED, 0, false);
        p.setSprinting(false);
        p.setDeltaMovement(Vec3.ZERO);
        SPSessionManager.teleport(p, p.serverLevel(), s.start, s.yaw, -28f);
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MOB_GRAB.get(), SoundSource.HOSTILE, 1.2f, 1f);
        if (mob instanceof Grabber gr) gr.onGrabStart(p);
        SPNetwork.sendTo(p, new Payloads.StartQTE(type.ordinal(), ticks, seed, s.target, difficulty));
        SPDebug.log(p.getGameProfile().getName() + " agarrado (" + type + ", " + ticks + " ticks)");
        return true;
    }

    public static void onInput(Payloads.QTEInput in, ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        switch (s.type) {
            case MASH -> {
                if (in.kind() != Payloads.QTEInput.PRESS) return;
                if (s.lastPressTick == s.elapsed) {
                    if (++s.pressesThisTick > 2) return; // mas de 40 por segundo: no es humano
                } else {
                    s.lastPressTick = s.elapsed;
                    s.pressesThisTick = 1;
                }
                s.progress++;
            }
            case TIMING -> {
                if (in.kind() != Payloads.QTEInput.PRESS || s.elapsed - s.lastPressTick < 4) return;
                s.lastPressTick = s.elapsed;
                // Margen de latencia: se acepta si el cursor paso por la zona en los ultimos 3 ticks
                float zone = QTEType.timingZone(s.seed, s.progress);
                float w = QTEType.timingWidth(s.difficulty);
                boolean hit = false;
                for (int back = 0; back <= 3 && !hit; back++) {
                    hit = Math.abs(QTEType.timingCursor(s.elapsed - back) - zone) <= w / 2;
                }
                if (hit) s.progress++;
                else s.misses++;
            }
            case SEQUENCE -> {
                if (in.kind() != Payloads.QTEInput.PRESS) return;
                if (in.value() == QTEType.sequenceKey(s.seed, s.progress)) {
                    s.progress++;
                } else {
                    s.misses++;
                    s.elapsed += 15; // una tecla mala quita tiempo
                }
            }
            case HOLD -> {
                if (in.kind() == Payloads.QTEInput.HOLD) s.holding = in.value() != 0;
            }
        }
        check(p, s);
        sync(p, s);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null || !SPPlayerData.inSP(p) || !p.isAlive()) {
                it.remove();
                if (p != null) cleanup(p, s);
                continue;
            }
            Entity mob = p.serverLevel().getEntity(s.mob);
            if (!(mob instanceof SPMonster) || !mob.isAlive()) {
                it.remove();
                finish(p, s, true);
                continue;
            }
            s.elapsed++;
            if (s.elapsed <= PULL_TICKS) {
                // arrastrado hacia las manos (suave al final) mirando a la cara del mob
                float k = s.elapsed / (float) PULL_TICKS;
                k = 1f - (1f - k) * (1f - k);
                SPSessionManager.teleport(p, p.serverLevel(), s.start.lerp(s.anchor, k), s.yaw, -28f);
            } else if (p.position().distanceToSqr(s.anchor) > 0.01) {
                SPSessionManager.teleport(p, p.serverLevel(), s.anchor, p.getYRot(), p.getXRot());
            }
            if (s.elapsed % 14 == 0) {
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.PLAYER_HEARTBEAT.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            if (s.type == QTEType.HOLD) {
                s.velocity += s.holding ? 0.012f : -0.011f;
                s.velocity *= 0.9f;
                s.needle = Math.max(0f, Math.min(1f, s.needle + s.velocity));
                if (Math.abs(s.needle - QTEType.holdZone(s.seed, s.elapsed)) <= QTEType.HOLD_ZONE / 2) s.progress++;
            }
            if (check(p, s)) {
                it.remove();
                continue;
            }
            if (s.elapsed % 2 == 0) sync(p, s);
        }
    }

    /** Comprueba exito o fallo. Devuelve true si la sesion ha terminado (y ya se ha quitado del mapa). */
    private static boolean check(ServerPlayer p, Session s) {
        Boolean result = null;
        if (s.progress >= s.target) result = true;
        else if (s.type == QTEType.TIMING && s.misses >= QTEType.TIMING_MISSES) result = false;
        else if (s.elapsed >= s.total) result = false;
        if (result == null) return false;
        SESSIONS.remove(p.getUUID());
        finish(p, s, result);
        return true;
    }

    private static void finish(ServerPlayer p, Session s, boolean escaped) {
        cleanup(p, s);
        SPNetwork.sendTo(p, new Payloads.QTEEnd(escaped));
        Entity mob = p.serverLevel().getEntity(s.mob);
        if (mob instanceof Grabber gr) gr.onGrabEnd(p, escaped);
        SPDebug.log(p.getGameProfile().getName() + (escaped ? " se libera" : " falla el agarre"));
        if (escaped) {
            Vec3 away = mob != null ? p.position().subtract(mob.position()).multiply(1, 0, 1).normalize() : Vec3.ZERO;
            p.push(away.x * 0.9, 0.25, away.z * 0.9);
            p.hurtMarked = true;
        } else if ("DEATH".equalsIgnoreCase(s.fail)) {
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        } else {
            DownedManager.down(p, "agarre");
        }
    }

    private static void cleanup(ServerPlayer p, Session s) {
        MotionManager.clearAction(p, Action.GRABBED);
    }

    /** Al salir de la partida o desconectarse. */
    public static void clear(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s != null) {
            cleanup(p, s);
            SPNetwork.sendTo(p, new Payloads.QTEEnd(false));
        }
    }

    private static void sync(ServerPlayer p, Session s) {
        float extra = s.type == QTEType.HOLD ? s.needle : s.misses;
        SPNetwork.sendTo(p, new Payloads.QTEState(s.elapsed, s.progress, extra));
    }

    @Nullable
    static Session session(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }
}
