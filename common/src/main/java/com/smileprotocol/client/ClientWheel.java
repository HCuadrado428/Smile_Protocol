package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.player.SPInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Rueda de objetos: mientras se mantiene {@code key_wheel} (Alt) la camara se queda quieta y el
 * raton elige uno de los 4 huecos (arriba 1, derecha 2, abajo 3, izquierda 4) sin mirar la barra;
 * al soltar la tecla se coge ese.
 */
public final class ClientWheel {

    private static boolean open;
    private static double x, y;

    private ClientWheel() {
    }

    public static boolean open() {
        return open;
    }

    public static void tick(Minecraft mc) {
        boolean down = mc.screen == null && ClientState.inSmileProtocol() && ClientMotion.isDown(SPConfig.get().key_wheel);
        if (down && !open) {
            open = true;
            x = y = 0;
        } else if (!down && open) {
            open = false;
            int s = hovered();
            if (s >= 0) ClientController.select(s);
        }
    }

    /** Movimiento del raton mientras esta abierta: mueve el selector en vez de la camara. */
    public static boolean consume(double dx, double dy) {
        if (!open) return false;
        x += dx;
        y += dy;
        double len = Math.hypot(x, y);
        if (len > 120) {
            x *= 120 / len;
            y *= 120 / len;
        }
        return true;
    }

    /** Hueco senalado (o -1 si el raton esta en el centro). */
    public static int hovered() {
        if (Math.hypot(x, y) < 25) return -1;
        if (Math.abs(x) > Math.abs(y)) return x > 0 ? 1 : 3;
        return y < 0 ? 0 : 2;
    }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        if (!open) return;
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0x80FFFFFF);
        int[][] at = {{0, -46}, {46, 0}, {0, 46}, {-46, 0}};
        int hov = hovered();
        for (int i = 0; i < Math.min(4, SPInventory.SIZE); i++) {
            int bx = cx + at[i][0] - 14, by = cy + at[i][1] - 14;
            boolean on = i == hov;
            g.fill(bx - 2, by - 2, bx + 30, by + 30, on ? 0xC08A1418 : 0x90100C0C);
            g.fill(bx, by, bx + 28, by + 28, on ? 0xD0281414 : 0xA0181414);
            ItemStack st = ClientState.slot(i);
            if (!st.isEmpty()) g.renderItem(st, bx + 6, by + 6);
            g.drawString(font, String.valueOf(i + 1), bx + 2, by + 2, i == ClientState.selected() ? 0xFFE0282C : 0xFFB0A898, false);
        }
        // el selector, siguiendo el raton
        int px = cx + (int) (x * 0.4), py = cy + (int) (y * 0.4);
        g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFE0282C);
    }
}
