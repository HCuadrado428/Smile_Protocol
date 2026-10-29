package com.smileprotocol.client;

import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Escondite en el cliente (README 15.4): vista limitada a un cono, superposicion de rendijas,
 * aguantar la respiracion con Shift y salir con el click derecho.
 */
public final class ClientHide {

    private static Payloads.SyncHide state = Payloads.SyncHide.none();
    private static boolean sentHolding;
    private static boolean prevUse;
    private static float prevBreath = 1f, breath = 1f;

    private ClientHide() {}

    public static Payloads.SyncHide state() {
        return state;
    }

    public static boolean hidden() {
        return state.hidden();
    }

    public static float breath(float pt) {
        return Mth.lerp(pt, prevBreath, breath);
    }

    public static void onSync(Payloads.SyncHide s) {
        boolean entering = s.hidden() && !state.hidden();
        state = s;
        if (entering) {
            sentHolding = false;
            prevUse = true; // el click que lo metio no lo saca
        }
    }

    public static void reset() {
        state = Payloads.SyncHide.none();
    }

    public static void tick(Minecraft mc) {
        prevBreath = breath;
        breath += (state.breath() - breath) * 0.5f;
        if (!state.hidden() || mc.player == null) return;
        boolean holding = mc.options.keyShift.isDown() && mc.screen == null;
        if (holding != sentHolding) {
            sentHolding = holding;
            SPNetwork.sendToServer(new Payloads.HideInput(holding, false));
        }
        boolean use = mc.options.keyUse.isDown() && mc.screen == null;
        if (use && !prevUse) SPNetwork.sendToServer(new Payloads.HideInput(false, true));
        prevUse = use;
        clamp(mc.player);
    }

    /** Giro limitado desde dentro (README 15.4). Lo llama tambien el mixin de giro del raton. */
    public static void clamp(Entity e) {
        if (!state.hidden()) return;
        float half = state.cone() / 2f;
        float rel = Mth.wrapDegrees(e.getYRot() - state.yaw());
        float clamped = Mth.clamp(rel, -half, half);
        if (clamped != rel) {
            float y = state.yaw() + clamped;
            e.setYRot(y);
            e.yRotO = y;
        }
        float pitch = Mth.clamp(e.getXRot(), -35f, 35f);
        if (pitch != e.getXRot()) {
            e.setXRot(pitch);
            e.xRotO = pitch;
        }
    }
}
