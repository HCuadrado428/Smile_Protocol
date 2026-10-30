package com.smileprotocol.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.qte.QTEType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Minijuego de escape en el cliente (README 18): lee las teclas y guarda lo que hay que dibujar. */
public final class ClientQTE {

    private static boolean active;
    private static QTEType type = QTEType.MASH;
    private static int total = 1;
    private static long seed;
    private static int target = 1;
    private static int difficulty;
    private static int elapsed;
    private static long elapsedAt;
    private static int progress;
    private static float extra;
    private static boolean prevSpace;
    private static final boolean[] PREV_WASD = new boolean[4];
    private static final int[] WASD = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D};
    /** Resultado del ultimo minijuego para un rotulo breve (millis, exito). */
    private static long endedAt = -1;
    private static boolean lastSuccess;
    private static long lastPressAt;

    private ClientQTE() {}

    public static void start(Payloads.StartQTE p) {
        active = true;
        type = QTEType.values()[Math.max(0, Math.min(QTEType.values().length - 1, p.qteType()))];
        total = Math.max(1, p.ticks());
        seed = p.seed();
        target = Math.max(1, p.target());
        difficulty = p.difficulty();
        elapsed = 0;
        elapsedAt = Util.getMillis();
        progress = 0;
        extra = type == QTEType.HOLD ? 0.3f : 0f;
        prevSpace = true; // la tecla que ya estuviera pulsada no cuenta
        endedAt = -1;
    }

    public static void state(Payloads.QTEState p) {
        elapsed = p.elapsed();
        elapsedAt = Util.getMillis();
        progress = p.progress();
        extra = p.extra();
    }

    public static void end(Payloads.QTEEnd p) {
        if (active) {
            endedAt = Util.getMillis();
            lastSuccess = p.success();
        }
        active = false;
    }

    public static void reset() {
        active = false;
        endedAt = -1;
    }

    public static void tick(Minecraft mc) {
        if (!active || mc.player == null) return;
        long win = mc.getWindow().getWindow();
        boolean space = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_SPACE);
        switch (type) {
            case MASH, TIMING -> {
                if (space && !prevSpace) {
                    SPNetwork.sendToServer(new Payloads.QTEInput(Payloads.QTEInput.PRESS, 0));
                    lastPressAt = Util.getMillis();
                }
            }
            case HOLD -> {
                if (space != prevSpace) SPNetwork.sendToServer(new Payloads.QTEInput(Payloads.QTEInput.HOLD, space ? 1 : 0));
            }
            case SEQUENCE -> {
                for (int i = 0; i < 4; i++) {
                    boolean down = InputConstants.isKeyDown(win, WASD[i]);
                    if (down && !PREV_WASD[i]) {
                        SPNetwork.sendToServer(new Payloads.QTEInput(Payloads.QTEInput.PRESS, i));
                        lastPressAt = Util.getMillis();
                    }
                    PREV_WASD[i] = down;
                }
            }
        }
        prevSpace = space;
    }

    // Para la HUD -------------------------------------------------------------------------------

    public static boolean active() {
        return active;
    }

    public static QTEType type() {
        return type;
    }

    /** Tick actual con interpolacion entre paquetes (para el cursor). */
    public static float time() {
        return elapsed + Math.min(4f, (Util.getMillis() - elapsedAt) / 50f);
    }

    public static float remaining() {
        return Math.max(0f, 1f - time() / total);
    }

    public static int progress() {
        return progress;
    }

    public static int target() {
        return target;
    }

    public static float extra() {
        return extra;
    }

    public static long seed() {
        return seed;
    }

    public static int difficulty() {
        return difficulty;
    }

    public static long sincePress() {
        return Util.getMillis() - lastPressAt;
    }

    /** Milisegundos desde que acabo el ultimo (o -1). */
    public static long sinceEnd() {
        return endedAt < 0 ? -1 : Util.getMillis() - endedAt;
    }

    public static boolean lastSuccess() {
        return lastSuccess;
    }
}
