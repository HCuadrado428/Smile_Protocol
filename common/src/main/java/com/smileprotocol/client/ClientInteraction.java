package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.interact.InteractionManager;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.motion.Action;
import com.smileprotocol.motion.MotionStates;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Interaccion en el cliente (README 11, 11.1): busca lo interactuable del punto de mira (objetos
 * del suelo y bloques del mod), muestra el prompt y avisa al servidor al empezar y al soltar el
 * click derecho. El progreso de la barra lo manda el servidor.
 */
public final class ClientInteraction {

    private static final double REACH = 3.6;

    /** Lo que hay en el punto de mira. */
    public record Target(boolean entity, BlockPos pos, int entityId) {}

    @Nullable private static Target target;
    @Nullable private static InteractionSpec spec;
    @Nullable private static Target sent;
    private static boolean needRelease;
    private static Payloads.InteractState state = Payloads.InteractState.idle();
    private static boolean prevDrop;
    private static boolean prevLight;

    private ClientInteraction() {}

    @Nullable public static InteractionSpec spec() { return spec; }
    public static Payloads.InteractState state() { return state; }

    public static void onState(Payloads.InteractState s) {
        if (state.active() && !s.active()) needRelease = true;
        state = s;
        if (!s.active()) sent = null;
    }

    public static void reset() {
        target = null;
        spec = null;
        sent = null;
        state = Payloads.InteractState.idle();
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !ClientState.inSmileProtocol()) {
            if (sent != null) stop();
            target = null;
            spec = null;
            return;
        }
        boolean useDown = mc.options.keyUse.isDown() && mc.screen == null;
        if (!useDown) needRelease = false;

        // Soltar el objeto activo (G)
        boolean drop = ClientMotion.isDown(SPConfig.get().key_drop);
        if (drop && !prevDrop && !ClientState.slot(ClientState.selected()).isEmpty()) {
            SPNetwork.sendToServer(new Payloads.DropItem(ClientState.selected()));
        }
        prevDrop = drop;

        // Linterna (F): va siempre equipada; sin bateria no enciende
        boolean light = ClientMotion.isDown(SPConfig.get().key_flashlight);
        if (light && !prevLight) {
            boolean on = !com.smileprotocol.client.light.Flashlight.isOn(player.getId());
            if (!on || com.smileprotocol.client.light.Flashlight.battery(player.getId()) > 0f) {
                com.smileprotocol.client.light.Flashlight.sync(player.getId(), on,
                        com.smileprotocol.client.light.Flashlight.battery(player.getId()));
                SPNetwork.sendToServer(new Payloads.ToggleFlashlight(on));
            }
        }
        prevLight = light;

        Action action = MotionStates.view(player).action();
        if (action == Action.HIDDEN || action == Action.DIVE || action == Action.VAULT || action == Action.DOWNED) {
            if (sent != null) stop();
            target = null;
            spec = null;
            return;
        }

        findTarget(mc, player);
        boolean canStart = target != null && spec != null && !spec.blocked() && !needRelease;
        if (useDown && canStart) {
            if (sent == null || !sent.equals(target)) {
                sent = target;
                SPNetwork.sendToServer(new Payloads.Interact(target.entity() ? Payloads.Interact.ENTITY : Payloads.Interact.BLOCK,
                        target.pos(), target.entityId()));
            }
        } else if (sent != null && (!useDown || !sent.equals(target))) {
            stop();
        }
    }

    private static void stop() {
        sent = null;
        SPNetwork.sendToServer(new Payloads.Interact(Payloads.Interact.STOP, BlockPos.ZERO, 0));
    }

    private static void findTarget(Minecraft mc, LocalPlayer player) {
        target = null;
        spec = null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        double blockDist = Double.MAX_VALUE;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            blockDist = bh.getLocation().distanceTo(eye);
        }

        // Objetos del suelo (README 10: se recogen mirandolos)
        Vec3 end = eye.add(look.scale(REACH));
        AABB search = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        ItemEntity best = null;
        double bestDist = Math.min(REACH, blockDist);
        for (ItemEntity e : player.level().getEntitiesOfClass(ItemEntity.class, search)) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best != null) {
            target = new Target(true, best.blockPosition(), best.getId());
            spec = InteractionManager.pickupSpec(best.getItem(), inventoryFull());
            return;
        }

        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK && blockDist <= REACH + 0.8) {
            BlockPos pos = bh.getBlockPos();
            BlockState st = player.level().getBlockState(pos);
            if (st.getBlock() instanceof SPInteractable it) {
                BlockPos root = it.root(player.level(), pos, st);
                InteractionSpec s = it.spec(player.level(), root, player.level().getBlockState(root), player);
                if (s != null) {
                    target = new Target(false, root, 0);
                    spec = s;
                }
            }
        }
    }

    private static boolean inventoryFull() {
        for (int i = 0; i < SPInventory.SIZE; i++) if (ClientState.slot(i).isEmpty()) return false;
        return true;
    }
}
