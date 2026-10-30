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
import net.minecraft.server.level.ServerLevel;
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
        Vec3 anchor;
        final Vec3 mobPos;
        final float mobYaw;
        final float victimYaw;
        int age;

        Session(UUID mob, SequenceKind kind, Vec3 anchor, Vec3 mobPos, float mobYaw, float victimYaw) {
            this.mob = mob;
            this.kind = kind;
            this.anchor = anchor;
            this.mobPos = mobPos;
            this.mobYaw = mobYaw;
            this.victimYaw = victimYaw;
        }

        /** Hacia donde mira la victima (horizontal). */
        Vec3 facing() {
            return Vec3.directionFromRotation(0, victimYaw);
        }

        /** Donde sangra: cuello o cara (hacia la cabeza) o espalda. */
        Vec3 woundAt(int where) {
            Vec3 f = facing();
            double lift = kind.lift(age);
            if (kind.prone) {
                double fwd = where == SequenceKind.BACK ? 0.3 : 0.8;
                double up = where == SequenceKind.BACK ? 0.3 : (kind == SequenceKind.SMILER_EXECUTION && age < 46 ? 0.6 : 0.3);
                return anchor.add(f.x * fwd, up + lift, f.z * fwd);
            }
            double up = where == SequenceKind.FACE ? 1.55 : 1.35;
            return anchor.add(f.x * 0.22, up + lift, f.z * 0.22);
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
        com.smileprotocol.item.UseManager.stop(p, true);

        // El mob se gira hacia la victima y la victima queda a su distancia, delante
        float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - mob.getX()), p.getZ() - mob.getZ()));
        mob.getNavigation().stop();
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        Vec3 anchor = new Vec3(mob.getX() + dir.x * kind.distance, p.getY(), mob.getZ() + dir.z * kind.distance);
        float victimYaw = kind.facesMob ? yaw + 180f : yaw;

        Session s = new Session(mob.getUUID(), kind, anchor, mob.position(), yaw, victimYaw);
        SESSIONS.put(p.getUUID(), s);
        p.setSprinting(false);
        p.setDeltaMovement(Vec3.ZERO);
        MotionManager.setAction(p, Action.SEQUENCE, kind.ordinal(), kind.prone);
        com.smileprotocol.session.SPSessionManager.teleport(p, p.serverLevel(), anchor, victimYaw, kind.prone ? 5f : -25f);
        mob.startSequence(kind, p);
        SPNetwork.sendTo(p, new Payloads.StartSequence(kind.ordinal(), mob.getId(), kind.ticks));
        if (kind == SequenceKind.SMILEY_KILL || kind == SequenceKind.SMILEY_DRAG) {
            p.serverLevel().playSound(null, mob.blockPosition(), ModSounds.SMILEY_WHISPER.get(), SoundSource.HOSTILE, 1.6f, 0.6f);
        }
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
            // Victima anclada (sin tocar su giro: la camara la lleva el cliente) y mob quieto; si la
            // arrastra, la victima va tumbada detras del mob y el mob anda
            if (s.kind.drag) {
                Vec3 back = Vec3.directionFromRotation(0, mob.getYRot()).scale(-s.kind.distance);
                s.anchor = new Vec3(mob.getX() + back.x, mob.getY(), mob.getZ() + back.z);
            }
            Vec3 at = s.anchor.add(0, s.kind.lift(s.age), 0);
            if (p.position().distanceToSqr(at) > 0.0004) {
                p.connection.teleport(at.x, at.y, at.z, 0f, 0f, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
            }
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            if (!s.kind.drag) {
                mob.getNavigation().stop();
                mob.setDeltaMovement(Vec3.ZERO);
                if (mob.position().distanceToSqr(s.mobPos) > 0.01) mob.moveTo(s.mobPos.x, s.mobPos.y, s.mobPos.z, s.mobYaw, 0f);
                mob.setYRot(s.mobYaw);
                mob.setYBodyRot(s.mobYaw);
            }
            ServerLevel level = p.serverLevel();
            Vec3 up = new Vec3(0, 1, 0);
            for (int t : s.kind.impacts) {
                if (s.age != t) continue;
                boolean bite = s.kind == SequenceKind.SMILER_KILL && t < s.kind.impacts[2];
                level.playSound(null, p.blockPosition(), bite ? ModSounds.MOB_BITE.get() : ModSounds.MOB_CRUNCH.get(),
                        SoundSource.HOSTILE, 1.4f, 0.9f + 0.05f * (t % 3));
                if (s.kind == SequenceKind.BERSERKER_EXECUTION) {
                    level.playSound(null, p.blockPosition(), ModSounds.BERSERKER_IMPACT.get(), SoundSource.HOSTILE, 1.0f, 1.1f);
                }
                level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
                int where = s.kind == SequenceKind.BERSERKER_EXECUTION ? SequenceKind.BACK
                        : s.kind == SequenceKind.SMILER_KILL ? SequenceKind.FACE : SequenceKind.NECK;
                Gore.burst(level, s.woundAt(where), s.facing().scale(-1).add(up), 1.0f);
                if (s.kind.prone || t >= s.kind.impacts[s.kind.impacts.length - 1]) Gore.pool(level, s.anchor, 3, 1);
            }
            if (s.kind.isTear(s.age)) {
                level.playSound(null, p.blockPosition(), ModSounds.MOB_TEAR.get(), SoundSource.HOSTILE, 1.5f, 0.9f);
                level.playSound(null, p.blockPosition(), ModSounds.MOB_SQUELCH.get(), SoundSource.HOSTILE, 1.2f, 1f);
                Gore.burst(level, s.woundAt(s.kind.fountainAt), s.facing().add(up), 2.2f);
                Gore.pool(level, s.anchor, 6, 2);
            }
            if (s.age >= s.kind.fountain[0] && s.age <= s.kind.fountain[1]) {
                // chorro que se va agotando
                float k = 1f - (s.age - s.kind.fountain[0]) / (float) (s.kind.fountain[1] - s.kind.fountain[0] + 1);
                Gore.fountain(level, s.woundAt(s.kind.fountainAt), s.facing(), 0.4f + 0.8f * k);
                if (s.age % 12 == 0) level.playSound(null, p.blockPosition(), ModSounds.MOB_SQUELCH.get(), SoundSource.HOSTILE, 0.6f * k + 0.2f, 1.1f);
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
