package com.smileprotocol.client;

import com.smileprotocol.network.SyncReadyPacket;
import com.smileprotocol.player.SPInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD propio (README 8.1). Dentro de la partida: barra de vida, 4 huecos y punto de mira
 * minimo. En el lobby: el contador «X/Y listos» del ascensor. Siempre: el fundido a negro y,
 * para operadores, el modo depuracion.
 */
public final class HudOverlay implements IGuiOverlay {

    public static final HudOverlay INSTANCE = new HudOverlay();

    // Paleta de laboratorio
    private static final int PANEL = 0xB0101614;
    private static final int PANEL_EDGE = 0xFF2A3A34;
    private static final int TEXT = 0xFFD8E4DE;
    private static final int MUTED = 0xFF8FA39A;
    private static final int HEALTH = 0xFFB3202B;
    private static final int HEALTH_LOW = 0xFFE03040;
    private static final int SLOT_ACTIVE = 0xFFD8E4DE;

    private HudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        if (!mc.options.hideGui) {
            if (ClientState.inSmileProtocol() && mc.player != null) {
                renderCrosshair(g, width, height);
                renderHealth(g, mc.player, height);
                renderSlots(g, font, width, height);
            }
            SyncReadyPacket ready = ClientState.ready;
            if (ready != null) renderReady(g, font, ready, width);
            renderDebug(g, font);
        }

        float fade = ClientState.fadeAlpha(partialTick);
        if (fade > 0f) {
            int a = Mth.clamp((int) (fade * 255), 0, 255);
            g.fill(0, 0, width, height, a << 24);
        }
    }

    private static void renderCrosshair(GuiGraphics g, int w, int h) {
        int cx = w / 2, cy = h / 2;
        g.fill(cx, cy, cx + 1, cy + 1, 0xC0FFFFFF);
    }

    private static void renderHealth(GuiGraphics g, Player p, int h) {
        int x = 12, y = h - 18, bw = 92, bh = 6;
        float frac = p.getMaxHealth() <= 0 ? 0 : Mth.clamp(p.getHealth() / p.getMaxHealth(), 0f, 1f);
        boolean low = frac <= 0.3f;
        int color = HEALTH;
        if (low) {
            // Late cuando queda poca vida
            float pulse = (Mth.sin((p.tickCount + Minecraft.getInstance().getFrameTime()) * 0.4f) + 1f) * 0.5f;
            color = pulse > 0.5f ? HEALTH_LOW : HEALTH;
        }
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, PANEL_EDGE);
        g.fill(x, y, x + bw, y + bh, PANEL);
        g.fill(x, y, x + Math.round(bw * frac), y + bh, color);
        // Marcas cada 25 %
        for (int i = 1; i < 4; i++) {
            int mx = x + bw * i / 4;
            g.fill(mx, y, mx + 1, y + bh, 0x60000000);
        }
    }

    private static void renderSlots(GuiGraphics g, Font font, int w, int h) {
        int size = 22, gap = 4;
        int total = SPInventory.SIZE * size + (SPInventory.SIZE - 1) * gap;
        int x0 = (w - total) / 2;
        int y = h - size - 8;
        for (int i = 0; i < SPInventory.SIZE; i++) {
            int x = x0 + i * (size + gap);
            boolean active = i == ClientState.selected();
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, active ? SLOT_ACTIVE : PANEL_EDGE);
            g.fill(x, y, x + size, y + size, PANEL);
            ItemStack stack = ClientState.slot(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 3, y + 3);
                g.renderItemDecorations(font, stack, x + 3, y + 3);
            }
            g.drawString(font, String.valueOf(i + 1), x + 2, y + 1, active ? TEXT : MUTED, false);
        }
    }

    private static void renderReady(GuiGraphics g, Font font, SyncReadyPacket r, int w) {
        Component count = Component.translatable("smileprotocol.hud.ready", r.ready(), r.total());
        int boxW = Math.max(160, Math.max(font.width(r.status()), font.width(r.title())) + 20);
        int x = (w - boxW) / 2, y = 10, boxH = 44;
        g.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, PANEL_EDGE);
        g.fill(x, y, x + boxW, y + boxH, PANEL);
        g.drawCenteredString(font, r.title(), w / 2, y + 5, MUTED);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 16, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        int color = r.countdown() >= 0 ? 0xFFFFB020 : (r.ready() >= r.total() && r.total() > 0 ? 0xFF40D070 : 0xFFF0E040);
        g.drawCenteredString(font, count, 0, 0, color);
        g.pose().popPose();
        g.drawCenteredString(font, r.status(), w / 2, y + 32, TEXT);
    }

    private static void renderDebug(GuiGraphics g, Font font) {
        if (ClientState.debugLines.isEmpty()) return;
        int y = 4;
        int maxW = 0;
        for (String l : ClientState.debugLines) maxW = Math.max(maxW, font.width(l));
        g.fill(2, 2, 8 + maxW, 6 + ClientState.debugLines.size() * 10, 0x90000000);
        for (String l : ClientState.debugLines) {
            g.drawString(font, l, 5, y, 0xFFE0E0E0, false);
            y += 10;
        }
    }
}
