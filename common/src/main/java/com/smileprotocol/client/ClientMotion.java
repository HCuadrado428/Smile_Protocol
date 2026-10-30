package com.smileprotocol.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionManager;
import com.smileprotocol.motion.MotionStates;
import com.smileprotocol.motion.MotionView;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Movimiento en el cliente (README 12): teclas propias, prediccion del dive y del vault,
 * camara (mirar atras, asomarse, inclinaciones) y suavizado del estado de los demas para las
 * animaciones. El servidor valida y corrige con {@code SyncMotion}.
 */
public final class ClientMotion {

    /** Suavizado por entidad (asomarse y mirar atras de cualquier jugador). */
    public static final class Visual {
        public float lean, prevLean, look, prevLook;
        public int actionAge;
        public Action lastAction = Action.NONE;

        public float lean(float pt) { return Mth.lerp(pt, prevLean, lean); }
        public float look(float pt) { return Mth.lerp(pt, prevLook, look); }
    }

    private static final Map<Integer, Visual> VISUALS = new HashMap<>();

    // Estado local
    private static boolean wasSprinting;
    private static boolean wasShift;
    private static boolean wasJump;
    private static boolean lookToggle, leanLeftToggle, leanRightToggle;
    private static boolean prevLookKey, prevLeftKey, prevRightKey;
    private static int sentLean;
    private static boolean sentLook;

    // Dive predicho
    private static int diveTicks = -1;
    private static Vec3 diveDir = Vec3.ZERO;

    // Vault predicho
    private static int vaultTicks = -1;
    private static Vec3 vaultStart = Vec3.ZERO, vaultEnd = Vec3.ZERO;
    private static double vaultHeight;
    private static boolean vaultWasSprinting;

    // Camara
    private static float leanOffset, prevLeanOffset;
    private static float roll, prevRoll;
    private static float dip, prevDip;

    private ClientMotion() {}

    public static Visual visual(int entityId) {
        return VISUALS.computeIfAbsent(entityId, k -> new Visual());
    }

    /** Paquete del servidor con el estado de un jugador. */
    public static void onSync(Payloads.SyncMotion p) {
        Minecraft mc = Minecraft.getInstance();
        MotionView v = new MotionView(Action.of(p.action()), p.lean(), p.lookBack(), p.detail(), p.lowPose(),
                mc.player != null ? mc.player.tickCount : 0);
        MotionStates.CLIENT.put(p.entityId(), v);
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            // Correccion de la prediccion
            if (v.action() != Action.DIVE) diveTicks = -1;
            if (v.action() != Action.VAULT && vaultTicks >= 0 && vaultTicks < 3) vaultTicks = -1;
        }
    }

    public static void reset() {
        MotionStates.CLIENT.clear();
        VISUALS.clear();
        diveTicks = -1;
        vaultTicks = -1;
        sentLean = 0;
        sentLook = false;
        lookToggle = leanLeftToggle = leanRightToggle = false;
    }

    /** Bloqueo del movimiento del jugador local (vault, escondido...). Lo usa el mixin de KeyboardInput. */
    public static boolean inputLocked() {
        if (!ClientState.inSmileProtocol()) return false;
        if (vaultTicks >= 0) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Action a = MotionStates.view(mc.player).action();
        return a == Action.HIDDEN || a == Action.GRABBED;
    }

    // Teclas ---------------------------------------------------------------------------------

    /** Teclas propias forzadas por la grabacion de animaciones (nombres como key.keyboard.q). */
    public static final java.util.Set<String> TEST_KEYS = new java.util.HashSet<>();

    static boolean isDown(String keyName) {
        if (TEST_KEYS.contains(keyName)) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(keyName);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
    }

    // Tick -------------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        tickVisuals(mc);
        prevLeanOffset = leanOffset;
        prevRoll = roll;
        prevDip = dip;

        if (!ClientState.inSmileProtocol()) {
            leanOffset *= 0.5f;
            roll *= 0.5f;
            dip *= 0.5f;
            return;
        }
        SPConfig.Values cfg = SPConfig.get();
        MotionView own = MotionStates.view(player);

        // Mirar atras y asomarse (mantener o alternar)
        boolean lookKey = isDown(cfg.key_look_back);
        boolean leftKey = isDown(cfg.key_lean_left);
        boolean rightKey = isDown(cfg.key_lean_right);
        if (cfg.look_back_toggle && lookKey && !prevLookKey) lookToggle = !lookToggle;
        if (cfg.lean_toggle && leftKey && !prevLeftKey) { leanLeftToggle = !leanLeftToggle; leanRightToggle = false; }
        if (cfg.lean_toggle && rightKey && !prevRightKey) { leanRightToggle = !leanRightToggle; leanLeftToggle = false; }
        prevLookKey = lookKey;
        prevLeftKey = leftKey;
        prevRightKey = rightKey;
        boolean look = cfg.look_back_toggle ? lookToggle : lookKey;
        boolean left = cfg.lean_toggle ? leanLeftToggle : leftKey;
        boolean right = cfg.lean_toggle ? leanRightToggle : rightKey;
        int lean = left == right ? 0 : left ? -1 : 1;
        boolean busy = own.action() != Action.NONE || diveTicks >= 0 || vaultTicks >= 0;
        if (player.isSprinting() || busy) {
            lean = 0;
            leanLeftToggle = leanRightToggle = false;
        }
        if (own.action() == Action.HIDDEN) look = false;
        if (lean != sentLean || look != sentLook) {
            sentLean = lean;
            sentLook = look;
            SPNetwork.sendToServer(new Payloads.MotionInput(Payloads.MotionInput.STATE, lean, look, BlockPos.ZERO));
            MotionStates.CLIENT.put(player.getId(), new MotionView(own.action(), lean, look, own.detail(), own.lowPose(), own.startTick()));
        }

        // Dive: corriendo + Shift (README 12.3)
        boolean shift = mc.options.keyShift.isDown();
        if (shift && !wasShift && wasSprinting && player.onGround() && !busy) startDive(player);
        wasShift = shift;

        // Vault: Espacio hacia un obstaculo de 1 bloque (README 12.4)
        boolean jump = mc.options.keyJump.isDown();
        if (jump && !wasJump && !busy && player.onGround() && !player.isCrouching() && player.input.forwardImpulse > 0.1f) {
            tryVault(player);
        }
        wasJump = jump;
        wasSprinting = player.isSprinting();

        tickDive(player);
        tickVault(player);
        tickCamera(mc, player, lean);
    }

    private static void startDive(LocalPlayer player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        diveDir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        diveTicks = 0;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(diveDir.x * 0.75, Math.max(v.y, 0.18), diveDir.z * 0.75);
        MotionView own = MotionStates.view(player);
        MotionStates.CLIENT.put(player.getId(), new MotionView(Action.DIVE, 0, own.lookBack(), 0, true, player.tickCount));
        SPNetwork.sendToServer(new Payloads.MotionInput(Payloads.MotionInput.DIVE, 0, sentLook, BlockPos.ZERO));
    }

    private static void tickDive(LocalPlayer player) {
        if (diveTicks < 0) return;
        diveTicks++;
        if (diveTicks <= MotionManager.DIVE_SLIDE) {
            double speed = 0.75 * Math.pow(0.86, diveTicks);
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(diveDir.x * speed, v.y, diveDir.z * speed);
        }
        if (diveTicks >= MotionManager.DIVE_TOTAL) {
            diveTicks = -1;
            MotionView own = MotionStates.view(player);
            if (own.action() == Action.DIVE) {
                MotionStates.CLIENT.put(player.getId(), new MotionView(Action.NONE, 0, own.lookBack(), 0, false, player.tickCount));
            }
        }
    }

    private static void tryVault(LocalPlayer player) {
        Level level = player.level();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        // Eje dominante: el obstaculo se busca en la direccion cardinal mas cercana
        Direction facing = Direction.getNearest((float) dir.x, 0f, (float) dir.z);
        BlockPos feet = player.blockPosition();
        BlockPos obstacle = null;
        for (int i = 1; i <= 2 && obstacle == null; i++) {
            BlockPos cand = feet.relative(facing, i);
            double dist = Math.abs(facing.getAxis().choose(cand.getX() + 0.5 - player.getX(), 0, cand.getZ() + 0.5 - player.getZ()));
            if (dist > 1.6) break;
            if (isVaultableClient(level, cand)) obstacle = cand;
            else if (!level.getBlockState(cand).getCollisionShape(level, cand).isEmpty()) break;
        }
        if (obstacle == null) return;
        BlockPos landing = obstacle.relative(facing);
        if (!free(level, landing) || !free(level, landing.above())) return;
        VoxelShape shape = level.getBlockState(obstacle).getCollisionShape(level, obstacle);
        vaultHeight = shape.max(Direction.Axis.Y);

        vaultStart = player.position();
        Vec3 n = Vec3.atLowerCornerOf(facing.getNormal());
        // Destino: el centro del bloque de aterrizaje en el eje del salto, conservando el desplazamiento lateral
        double tx = facing.getAxis() == Direction.Axis.X ? landing.getX() + 0.5 : player.getX();
        double tz = facing.getAxis() == Direction.Axis.Z ? landing.getZ() + 0.5 : player.getZ();
        vaultEnd = new Vec3(tx + n.x * 0.1, obstacle.getY(), tz + n.z * 0.1);
        vaultTicks = 0;
        vaultWasSprinting = player.isSprinting();
        MotionView own = MotionStates.view(player);
        MotionStates.CLIENT.put(player.getId(), new MotionView(Action.VAULT, 0, own.lookBack(), 0, false, player.tickCount));
        SPNetwork.sendToServer(new Payloads.MotionInput(Payloads.MotionInput.VAULT, 0, sentLook, obstacle));
    }

    private static boolean free(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isVaultableClient(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        VoxelShape shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double top = shape.max(Direction.Axis.Y);
        if (top > 1.01 || top < 0.3) return false;
        if (!SPConfig.get().vault_any_obstacle && !st.is(com.smileprotocol.registry.ModTags.VAULTABLE)) return false;
        return free(level, pos.above()) && free(level, pos.above(2));
    }

    private static void tickVault(LocalPlayer player) {
        if (vaultTicks < 0) return;
        vaultTicks++;
        float t = Math.min(1f, vaultTicks / (float) MotionManager.VAULT_TICKS);
        float e = t * t * (3 - 2 * t);
        double y = Mth.lerp(e, vaultStart.y, vaultEnd.y) + (vaultHeight + 0.45) * Math.sin(Math.PI * Math.min(1, t * 1.1));
        double x = Mth.lerp(e, vaultStart.x, vaultEnd.x);
        double z = Mth.lerp(e, vaultStart.z, vaultEnd.z);
        player.setPos(x, Math.max(y, vaultEnd.y), z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        if (vaultTicks >= MotionManager.VAULT_TICKS) {
            vaultTicks = -1;
            Vec3 dir = vaultEnd.subtract(vaultStart).multiply(1, 0, 1).normalize();
            double speed = vaultWasSprinting ? 0.32 : 0.16;
            player.setDeltaMovement(dir.x * speed, 0, dir.z * speed);
            if (vaultWasSprinting) player.setSprinting(true);
            MotionView own = MotionStates.view(player);
            if (own.action() == Action.VAULT) {
                MotionStates.CLIENT.put(player.getId(), new MotionView(Action.NONE, 0, own.lookBack(), 0, false, player.tickCount));
            }
        }
    }

    // Camara ------------------------------------------------------------------------------------

    private static void tickCamera(Minecraft mc, LocalPlayer player, int lean) {
        // Asomarse: desplazamiento lateral limitado por las paredes y inclinacion (README 12.6)
        float targetOffset = 0;
        if (lean != 0) {
            float max = 0.42f;
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vec3 side = left.scale(lean < 0 ? 1 : -1);
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(side.scale(max + 0.25)),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            float allowed = max;
            if (hit.getType() == HitResult.Type.BLOCK) allowed = (float) Math.max(0, hit.getLocation().distanceTo(eye) - 0.25);
            targetOffset = Math.min(max, allowed) * lean;
        }
        leanOffset += (targetOffset - leanOffset) * 0.35f;

        float targetRoll = (leanOffset / 0.42f) * 15f;
        float targetDip = 0;
        if (diveTicks >= 0) {
            float k = diveTicks < MotionManager.DIVE_SLIDE ? 1f : 1f - (diveTicks - MotionManager.DIVE_SLIDE) / 8f;
            targetRoll += 7f * k;
            targetDip -= 0.15f * k;
        }
        if (vaultTicks >= 0) {
            float t = vaultTicks / (float) MotionManager.VAULT_TICKS;
            targetDip -= 0.25f * (float) Math.sin(Math.PI * t);
            targetRoll -= 4f * (float) Math.sin(Math.PI * t);
        }
        roll += (targetRoll - roll) * 0.3f;
        dip += (targetDip - dip) * 0.35f;
    }

    private static void tickVisuals(Minecraft mc) {
        if (mc.level == null) return;
        VISUALS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Map.Entry<Integer, MotionView> e : MotionStates.CLIENT.entrySet()) {
            Visual v = visual(e.getKey());
            MotionView m = e.getValue();
            v.prevLean = v.lean;
            v.prevLook = v.look;
            v.lean += (m.lean() - v.lean) * 0.3f;
            v.look += ((m.lookBack() ? 1f : 0f) - v.look) * 0.25f;
            if (m.action() != v.lastAction) {
                v.lastAction = m.action();
                v.actionAge = 0;
            } else {
                v.actionAge++;
            }
        }
    }

    // Valores para los mixins de camara -------------------------------------------------------------

    /** Grados que se suman al yaw de la camara (0..180). */
    public static float cameraYawOffset(@Nullable Entity entity, float pt) {
        if (entity == null || !ClientState.inSmileProtocol()) return 0;
        return visual(entity.getId()).look(pt) * 180f;
    }

    /** Desplazamiento lateral de la camara (bloques, negativo = izquierda). */
    public static float cameraLeanOffset(float pt) {
        return Mth.lerp(pt, prevLeanOffset, leanOffset);
    }

    public static float cameraRoll(float pt) {
        return Mth.lerp(pt, prevRoll, roll);
    }

    public static float cameraDip(float pt) {
        return Mth.lerp(pt, prevDip, dip);
    }

    public static boolean isDiving() {
        return diveTicks >= 0;
    }

    public static boolean isVaulting() {
        return vaultTicks >= 0;
    }

    public static float vaultProgress(float pt) {
        return vaultTicks < 0 ? 0 : Math.min(1f, (vaultTicks + pt) / MotionManager.VAULT_TICKS);
    }
}
