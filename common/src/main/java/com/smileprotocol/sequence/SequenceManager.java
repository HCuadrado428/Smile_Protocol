package com.smileprotocol.sequence;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Secuencias dirigidas por el servidor (README 9.3): remates y muertes instantaneas. El servidor
 * ancla a la victima delante del mob, bloquea su control (es invulnerable mientras dura), avisa a
 * su cliente con {@code StartSequence} para la camara y, al acabar, decide el resultado: muere.
 * Una vez empezada no se puede cancelar (solo si el mob desaparece o la victima sale de la partida).
 */
public final class SequenceManager {

    private static final class Session {
        final UUID mob;
        final SequenceKind kind;
        final Vec3 anchor;
        final Vec3 mobPos;
        final float mobYaw;
        int age;

        Session(UUID mob, SequenceKind kind, Vec3 anchor, Vec3 mobPos, float mobYaw) {
            this.mob = mob;
            this.kind = kind;
            this.anchor = anchor;
            this.mobPos = mobPos;
            this.mobYaw = mobYaw;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Grabacion de animaciones: al acabar la secuencia la victima no muere. */
    public static volatile boolean TEST_NO_KILL;

    private SequenceManager() {}

    public static boolean inSequence(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    /** El mob esta ocupado con alguna victima. */
    public static boolean busy(SPMonster mob) {
        for (Session s : SESSIONS.values()) if (s.mob.equals(mob.getUUID())) return true;
        return false;
    }

    public static boolean start(SPMonster mob, ServerPlayer p, SequenceKind kind) {
        if (inSequence(p) || busy(mob) || !SPPlayerData.inSP(p) || p.isSpectator() || !p.isAlive()) return false;
        com.smileprotocol.hide.HideManager.forceExit(p, true);
        com.smileprotocol.qte.QTEManager.clear(p);
        com.smileprotocol.interact.InteractionManager.stop(p, true);

        // El mob se gira hacia la victima y la victima queda a su distancia, delante
        float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - mob.getX()), p.getZ() - mob.getZ()));
        mob.getNavigation().stop();
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        Vec3 anchor = new Vec3(mob.getX() + dir.x * kind.distance, p.getY(), mob.getZ() + dir.z * kind.distance);
        float victimYaw = kind.facesMob ? yaw + 180f : yaw;

        Session s = new Session(mob.getUUID(), kind, anchor, mob.position(), yaw);
        SESSIONS.put(p.getUUID(), s);
        p.setSprinting(false);
        p.setDeltaMovement(Vec3.ZERO);
        MotionManager.setAction(p, Action.SEQUENCE, kind.ordinal(), kind.prone);
        com.smileprotocol.session.SPSessionManager.teleport(p, p.serverLevel(), anchor, victimYaw, kind.prone ? 5f : -25f);
        mob.startSequence(kind, p);
        SPNetwork.sendTo(p, new Payloads.StartSequence(kind.ordinal(), mob.getId(), kind.ticks));
        p.serverLevel().playSound(null, mob.blockPosition(), kind.execution ? ModSounds.BERSERKER_ROAR.get() : ModSounds.MOB_GRAB.get(),
                SoundSource.HOSTILE, 1.4f, kind.execution ? 0.8f : 1f);
        SPDebug.log(p.getGameProfile().getName() + (kind.execution ? " rematado por " : " muerte instantanea por ")
                + mob.defId() + " (" + kind.name().toLowerCase() + ")");
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Entity me = p == null ? null : p.serverLevel().getEntity(s.mob);
            SPMonster mob = me instanceof SPMonster m && m.isAlive() ? m : null;
            if (p == null || !SPPlayerData.inSP(p) || !p.isAlive() || p.isSpectator()) {
                it.remove();
                if (mob != null) mob.endSequence();
                continue;
            }
            if (mob == null) {
                // el mob ha desaparecido (limpieza de la zona): la victima vuelve a como estaba
                it.remove();
                release(p, s);
                continue;
            }
            s.age++;
            // Victima anclada (sin tocar su giro: la camara la lleva el cliente) y mob quieto
            Vec3 at = s.anchor.add(0, s.kind.lift(s.age), 0);
            if (p.position().distanceToSqr(at) > 0.0004) {
                p.connection.teleport(at.x, at.y, at.z, 0f, 0f, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
            }
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            if (mob.position().distanceToSqr(s.mobPos) > 0.01) mob.moveTo(s.mobPos.x, s.mobPos.y, s.mobPos.z, s.mobYaw, 0f);
            mob.setYRot(s.mobYaw);
            mob.setYBodyRot(s.mobYaw);
            for (int t : s.kind.impacts) {
                if (s.age == t) {
                    boolean bite = s.kind == SequenceKind.SMILER_KILL && t == s.kind.impacts[0];
                    p.serverLevel().playSound(null, p.blockPosition(), bite ? ModSounds.MOB_BITE.get() : ModSounds.MOB_CRUNCH.get(),
                            SoundSource.HOSTILE, 1.3f, 0.95f + 0.05f * (t % 3));
                    if (s.kind == SequenceKind.BERSERKER_EXECUTION) {
                        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.BERSERKER_IMPACT.get(), SoundSource.HOSTILE, 0.9f, 1.1f);
                    }
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.7f);
                }
            }
            if (s.age >= s.kind.ticks) {
                it.remove();
                mob.endSequence();
                SPNetwork.sendTo(p, new Payloads.StartSequence(s.kind.ordinal(), mob.getId(), 0));
                if (TEST_NO_KILL) release(p, s);
                else DeathManager.die(p, s.kind.name().toLowerCase());
            }
        }
    }

    /** Sale de la partida o muere por otra via: se quita sin matar. */
    public static void clear(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        Entity me = p.serverLevel().getEntity(s.mob);
        if (me instanceof SPMonster m) m.endSequence();
        SPNetwork.sendTo(p, new Payloads.StartSequence(s.kind.ordinal(), -1, 0));
        MotionManager.clearAction(p, Action.SEQUENCE);
    }

    private static void release(ServerPlayer p, Session s) {
        SPNetwork.sendTo(p, new Payloads.StartSequence(s.kind.ordinal(), -1, 0));
        if (DownedManager.isDowned(p)) MotionManager.setAction(p, Action.DOWNED, 0, true);
        else MotionManager.clearAction(p, Action.SEQUENCE);
    }
}
