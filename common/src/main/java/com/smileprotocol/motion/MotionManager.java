package com.smileprotocol.motion;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Movimiento y sigilo en el servidor (README 12): valida las peticiones del cliente (dive,
 * vault, asomarse, mirar atras), lleva la accion de cada jugador, emite el ruido de sus
 * movimientos y lo sincroniza a los jugadores cercanos para las animaciones.
 * <p>
 * El movimiento lo predice el cliente, como en vanilla; aqui solo se comprueba que sea
 * plausible (cooldown, que este corriendo, que exista el obstaculo).
 */
public final class MotionManager {

    public static final int DIVE_SLIDE = 16;
    public static final int DIVE_TOTAL = 24;
    public static final int VAULT_TICKS = 10;
    /** Lo que tarda en levantarse tras la reanimacion (README 9.3). */
    public static final int GETUP_TICKS = 26;

    /** Estado de un jugador. Solo en memoria: al reconectarse vuelve a NONE. */
    static final class State {
        Action action = Action.NONE;
        int actionTicks;
        int detail;
        boolean lowPose;
        int lean;
        boolean lookBack;
        int diveCooldown;
        int vaultCooldown;
        int lastSprintTick = -100;
        Vec3 lastPos;
        boolean wasOnGround = true;
        float lastFall;
        double stride;
        boolean inVent;
        MotionView synced = MotionView.NONE;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private MotionManager() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static MotionView view(Player p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return MotionView.NONE;
        return new MotionView(s.action, s.lean, s.lookBack, s.detail, s.lowPose, s.actionTicks);
    }

    public static Action action(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? Action.NONE : s.action;
    }

    /** Lean actual (-1, 0, 1). Para la vista de los mobs desde la cabeza asomada (fase 11). */
    public static int lean(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : s.lean;
    }

    /**
     * Otros sistemas (interaccion, escondites) fijan la accion principal. {@code NONE} la libera
     * solo si era esa misma accion.
     */
    public static void setAction(ServerPlayer p, Action action, int detail, boolean lowPose) {
        State s = state(p);
        s.action = action;
        s.detail = detail;
        s.lowPose = lowPose;
        s.actionTicks = 0;
        if (action != Action.NONE) s.lean = 0;
        sync(p, s, true);
    }

    public static void clearAction(ServerPlayer p, Action action) {
        State s = state(p);
        if (s.action != action) return;
        setAction(p, Action.NONE, 0, false);
    }

    // Peticiones del cliente --------------------------------------------------------------

    public static void onInput(Payloads.MotionInput in, ServerPlayer p) {
        if (in.kind() == Payloads.MotionInput.SPECTATE) {
            // muerto: cambia de companero al que mira (lean = direccion)
            if (SPPlayerData.inSP(p)) com.smileprotocol.downed.DeathManager.cycle(p, in.lean() < 0 ? -1 : 1);
            return;
        }
        if (!SPPlayerData.inSP(p) || p.isSpectator()) return;
        State s = state(p);
        switch (in.kind()) {
            case Payloads.MotionInput.DIVE -> tryDive(p, s);
            case Payloads.MotionInput.VAULT -> tryVault(p, s, in.obstacle());
            default -> {
                int lean = Integer.signum(in.lean());
                // Asomarse: de pie, agachado o andando despacio, nunca corriendo ni en otra accion (README 12.6)
                if (lean != 0 && (p.isSprinting() || p.isVisuallyCrawling() || s.action != Action.NONE)) lean = 0;
                boolean look = in.lookBack() && s.action != Action.HIDDEN;
                if (lean != s.lean || look != s.lookBack) {
                    s.lean = lean;
                    s.lookBack = look;
                    sync(p, s, false);
                }
            }
        }
    }

    private static void tryDive(ServerPlayer p, State s) {
        boolean recentlySprinting = p.tickCount - s.lastSprintTick <= 10;
        if (s.action != Action.NONE || s.diveCooldown > 0 || !recentlySprinting || !p.onGround() || p.isVisuallyCrawling()) {
            sync(p, s, true); // corrige la prediccion del cliente
            return;
        }
        s.action = Action.DIVE;
        s.actionTicks = 0;
        s.lowPose = true;
        s.lean = 0;
        s.diveCooldown = SPConfig.get().dive_cooldown;
        NoiseSystem.emit(p.serverLevel(), p.position(), SPConfig.get().noise_dive, NoiseType.DIVE, p);
        sync(p, s, true);
    }

    private static void tryVault(ServerPlayer p, State s, BlockPos obstacle) {
        boolean ok = s.action == Action.NONE && s.vaultCooldown <= 0 && !p.isVisuallyCrawling()
                && obstacle.distToCenterSqr(p.position()) < 3.5 * 3.5
                && isVaultable(p.serverLevel(), obstacle, p);
        if (!ok) {
            sync(p, s, true);
            return;
        }
        s.action = Action.VAULT;
        s.actionTicks = 0;
        s.lowPose = false;
        s.lean = 0;
        s.vaultCooldown = VAULT_TICKS + 6;
        NoiseSystem.emit(p.serverLevel(), p.position(), SPConfig.get().noise_vault, NoiseType.VAULT, p);
        sync(p, s, true);
    }

    /**
     * Obstaculo de 1 bloque como mucho, con dos bloques libres encima (README 12.4). Con
     * {@code vault_any_obstacle = false} solo sirven los del tag {@code smileprotocol:vaultable}.
     */
    public static boolean isVaultable(ServerLevel level, BlockPos pos, Player p) {
        BlockState st = level.getBlockState(pos);
        VoxelShape shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double top = shape.max(net.minecraft.core.Direction.Axis.Y);
        if (top > 1.01 || top < 0.3) return false;
        if (!SPConfig.get().vault_any_obstacle && !st.is(com.smileprotocol.registry.ModTags.VAULTABLE)) return false;
        return level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty();
    }

    // Tick ----------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!SPPlayerData.inSP(p)) {
                State old = STATES.remove(p.getUUID());
                if (old != null && old.synced != MotionView.NONE) broadcast(p, MotionView.NONE);
                continue;
            }
            tickPlayer(p, state(p));
        }
    }

    private static void tickPlayer(ServerPlayer p, State s) {
        if (s.diveCooldown > 0) s.diveCooldown--;
        if (s.vaultCooldown > 0) s.vaultCooldown--;
        if (p.isSprinting()) s.lastSprintTick = p.tickCount;
        s.actionTicks++;

        if (s.action == Action.DIVE && s.actionTicks >= DIVE_TOTAL) {
            s.action = Action.NONE;
            s.lowPose = false;
            sync(p, s, true);
        } else if (s.action == Action.VAULT && s.actionTicks >= VAULT_TICKS + 2) {
            s.action = Action.NONE;
            sync(p, s, true);
        } else if (s.action == Action.GETUP && s.actionTicks >= GETUP_TICKS) {
            s.action = Action.NONE;
            s.lowPose = false;
            sync(p, s, true);
        }
        if (s.lean != 0 && p.isSprinting()) {
            s.lean = 0;
            sync(p, s, false);
        }

        // Ruido de los pasos (README 12.1)
        Vec3 pos = p.position();
        if (s.lastPos == null) s.lastPos = pos;
        double dx = pos.x - s.lastPos.x, dz = pos.z - s.lastPos.z;
        double moved = Math.sqrt(dx * dx + dz * dz);
        s.lastPos = pos;
        SPConfig.Values cfg = SPConfig.get();
        // Conductos de ventilacion (README 12.9): el metal retumba
        boolean vent = p.isVisuallyCrawling() && inDuct(p);
        if (vent != s.inVent) {
            s.inVent = vent;
            if (vent) com.smileprotocol.mechanism.LabEvents.fire(p.serverLevel(), p.blockPosition(), p, "vent_entered", "");
        }
        if (p.onGround() && s.action != Action.HIDDEN && moved > 0.01 && moved < 2) {
            s.stride += moved;
            boolean crawl = s.action == Action.DOWNED;
            double strideLen = crawl ? 0.8 : p.isSprinting() ? 2.2 : p.isCrouching() || p.isVisuallyCrawling() ? 1.2 : 1.7;
            if (s.stride >= strideLen) {
                s.stride = 0;
                float r = crawl ? cfg.noise_crawl : p.isCrouching() || p.isVisuallyCrawling() ? cfg.noise_crouch_walk : p.isSprinting() ? cfg.noise_sprint : cfg.noise_walk;
                if (vent) r *= cfg.noise_vent_mult;
                NoiseType t = crawl ? NoiseType.CRAWL : p.isSprinting() ? NoiseType.SPRINT : NoiseType.FOOTSTEP;
                NoiseSystem.emit(p.serverLevel(), pos, r, t, p);
            }
        }
        // Caidas de mas de 3 bloques: mucho ruido (README 12.8)
        if (!s.wasOnGround && p.onGround() && s.lastFall > 3f) {
            NoiseSystem.emit(p.serverLevel(), pos, cfg.noise_land, NoiseType.LAND, p);
        }
        s.lastFall = p.fallDistance;
        s.wasOnGround = p.onGround();
    }

    /** Esta dentro de un conducto: pisa chapa de conducto. */
    public static boolean inDuct(Player p) {
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(below).getBlock())
                .getPath().equals("lab_vent_duct");
    }

    private static void sync(ServerPlayer p, State s, boolean force) {
        MotionView v = new MotionView(s.action, s.lean, s.lookBack, s.detail, s.lowPose, 0);
        if (!force && v.equals(s.synced)) return;
        s.synced = v;
        broadcast(p, v);
    }

    /** A el mismo y a los jugadores cercanos de su nivel. */
    private static void broadcast(ServerPlayer p, MotionView v) {
        Payloads.SyncMotion packet = new Payloads.SyncMotion(p.getId(), v.action().ordinal(), v.lean(), v.lookBack(),
                v.detail(), v.lowPose());
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p || o.distanceToSqr(p) < 96 * 96) SPNetwork.sendTo(o, packet);
        }
    }

    /** Al entrar alguien en el radio, que vea el estado de los demas (simplificado: al conectarse). */
    public static void onLogout(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }
}
