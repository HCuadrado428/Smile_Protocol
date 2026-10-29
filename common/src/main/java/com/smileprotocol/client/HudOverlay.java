package com.smileprotocol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.player.SPInventory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * HUD propio con aspecto de monitor medico de laboratorio (README 8.1).
 * <ul>
 *   <li>Dentro de la partida: constantes vitales (corazon que late, electrocardiograma y barra
 *   de vida), los 4 huecos, un punto de mira minimo y una vineta oscura que se vuelve roja con
 *   poca vida.</li>
 *   <li>En el lobby: el panel del ascensor con «X/Y listos», indicadores y cuenta atras.</li>
 *   <li>Siempre: el fundido a negro y, para operadores, el panel de depuracion.</li>
 * </ul>
 */
public final class HudOverlay {

    public static final HudOverlay INSTANCE = new HudOverlay();

    private static final ResourceLocation PANEL = SmileProtocol.id("hud/panel");
    private static final ResourceLocation PANEL_ALERT = SmileProtocol.id("hud/panel_alert");
    private static final ResourceLocation SLOT = SmileProtocol.id("hud/slot");
    private static final ResourceLocation SLOT_SELECTED = SmileProtocol.id("hud/slot_selected");
    private static final ResourceLocation HEART = SmileProtocol.id("hud/heart");
    private static final ResourceLocation PIP_ON = SmileProtocol.id("hud/pip_on");
    private static final ResourceLocation PIP_OFF = SmileProtocol.id("hud/pip_off");
    private static final ResourceLocation VIGNETTE = SmileProtocol.id("hud/vignette");

    private static final int ACCENT = 0xFF56E0C8;
    private static final int DANGER = 0xFFE22C3A;
    private static final int TEXT = 0xFFDCE8E4;
    private static final int MUTED = 0xFF82968F;
    private static final int AMBER = 0xFFFFB020;
    private static final int READY = 0xFF46D27A;
    private static final int WAITING = 0xFFF0E040;

    /** Nombre del item al cambiar de hueco: se muestra 2 s y se desvanece. */
    private int lastSelected = -1;
    private long selectedAt;

    private HudOverlay() {}

    /** Lo llama cada loader al dibujar el HUD, por encima de todo. */
    public void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        RenderSystem.enableBlend();
        boolean inGame = ClientState.inSmileProtocol() && mc.player != null;
        if (inGame) renderVignette(g, mc.player, w, h);

        if (!mc.options.hideGui) {
            if (inGame) {
                if (ClientHide.hidden()) {
                    renderHide(g, font, w, h, partialTick);
                } else {
                    renderCrosshair(g, w, h);
                    renderInteraction(g, font, w, h);
                }
                renderVitals(g, font, mc.player, h);
                renderSlots(g, font, w, h);
            }
            Payloads.SyncReady ready = ClientState.ready;
            if (ready != null) renderReady(g, font, ready, w);
            renderDebug(g, font);
        }

        float fade = ClientState.fadeAlpha(partialTick);
        if (fade > 0f) g.fill(0, 0, w, h, Mth.clamp((int) (fade * 255), 0, 255) << 24);
    }

    // Vineta ---------------------------------------------------------------------

    private static void renderVignette(GuiGraphics g, Player p, int w, int h) {
        float frac = healthFraction(p);
        RenderSystem.setShaderColor(0f, 0f, 0f, 0.55f);
        g.blitSprite(VIGNETTE, 0, 0, w, h);
        if (frac < 0.35f) {
            float beat = heartbeat(frac);
            float alpha = (0.35f - frac) / 0.35f * (0.35f + 0.25f * beat);
            RenderSystem.setShaderColor(0.75f, 0.02f, 0.05f, alpha);
            g.blitSprite(VIGNETTE, 0, 0, w, h);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Punto de mira -----------------------------------------------------------------

    private static void renderCrosshair(GuiGraphics g, int w, int h) {
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0x70000000);
        g.fill(cx, cy, cx + 1, cy + 1, 0xE0F0F0F0);
    }

    // Interaccion (README 8.1: prompt y barra de progreso) -------------------------------------

    private static void renderInteraction(GuiGraphics g, Font font, int w, int h) {
        int cx = w / 2, y = h / 2 + 12;
        Payloads.InteractState st = ClientInteraction.state();
        if (st.active()) {
            Component prompt = st.prompt();
            g.drawString(font, prompt, cx - font.width(prompt) / 2, y, TEXT, true);
            int bw = 92, bx = cx - bw / 2, by = y + 12;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xC0000000);
            float k = st.total() <= 0 ? 1f : Mth.clamp(st.progress() / (float) st.total(), 0f, 1f);
            int col = st.waiting() ? AMBER : ACCENT;
            g.fill(bx, by, bx + Math.round(bw * k), by + 4, col);
            g.fill(bx, by, bx + Math.round(bw * k), by + 1, 0x60FFFFFF);
            if (st.waiting()) {
                Component wait = Component.translatable("smileprotocol.interact.waiting");
                g.drawString(font, wait, cx - font.width(wait) / 2, by + 8, AMBER, true);
            }
            return;
        }
        com.smileprotocol.interact.InteractionSpec spec = ClientInteraction.spec();
        if (spec == null) return;
        if (spec.blocked()) {
            g.drawString(font, spec.prompt(), cx - font.width(spec.prompt()) / 2, y, 0xFF9AA6A2, true);
            return;
        }
        Component key = Component.translatable(spec.ticks() > 0 ? "smileprotocol.interact.hold" : "smileprotocol.interact.press");
        int kw = font.width(key) + 6;
        int total = kw + 4 + font.width(spec.prompt());
        int x = cx - total / 2;
        g.fill(x, y - 2, x + kw, y + 10, 0xB0000000);
        g.renderOutline(x, y - 2, kw, 12, ACCENT);
        g.drawString(font, key, x + 3, y, ACCENT, false);
        g.drawString(font, spec.prompt(), x + kw + 4, y, TEXT, true);
    }

    // Escondite (README 15.4: rendijas, aliento) -------------------------------------------------

    private static void renderHide(GuiGraphics g, Font font, int w, int h, float pt) {
        Payloads.SyncHide st = ClientHide.state();
        switch (st.overlay()) {
            case "crack" -> {
                int gap = Math.max(10, w / 9), cx = w / 2;
                g.fill(0, 0, cx - gap / 2, h, 0xF0050505);
                g.fill(cx + gap / 2, 0, w, h, 0xF0050505);
                g.fillGradient(cx - gap / 2, 0, cx + gap / 2, h / 4, 0xE0050505, 0x00050505);
                g.fillGradient(cx - gap / 2, h * 3 / 4, cx + gap / 2, h, 0x00050505, 0xE0050505);
            }
            case "low" -> {
                g.fill(0, 0, w, h * 2 / 5, 0xF0060606);
                g.fillGradient(0, h * 2 / 5, w, h * 3 / 5, 0xF0060606, 0x30060606);
                g.fillGradient(0, h - h / 8, w, h, 0x00060606, 0xC0060606);
            }
            case "cloth" -> {
                g.fill(0, 0, w, h, 0x50B8A88A);
                g.fillGradient(0, 0, w, h / 3, 0x70302820, 0x00302820);
            }
            default -> {
                // Rendijas de taquilla: bandas horizontales en el centro
                int slits = 6, sh = Math.max(3, h / 70), gap = Math.max(sh * 2, h / 22);
                int band = slits * sh + (slits - 1) * gap;
                int top = h / 2 - band / 2;
                int sx0 = w / 2 - w / 3, sx1 = w / 2 + w / 3;
                g.fill(0, 0, w, top, 0xF4040404);
                g.fill(0, top + band, w, h, 0xF4040404);
                g.fill(0, top, sx0, top + band, 0xF4040404);
                g.fill(sx1, top, w, top + band, 0xF4040404);
                for (int i = 0; i < slits - 1; i++) {
                    int y0 = top + i * (sh + gap) + sh;
                    g.fill(sx0, y0, sx1, y0 + gap, 0xF4040404);
                }
                g.fill(sx0, top, sx1, top + band, 0x22000000);
            }
        }

        // Aliento (no en ocultacion parcial)
        int cx = w / 2, y = h - 70;
        if (!st.partial()) {
            float b = ClientHide.breath(pt);
            int bw = 120, bx = cx - bw / 2;
            Component label = Component.translatable(st.holding() ? "smileprotocol.hud.holding_breath" : "smileprotocol.hud.breath");
            g.drawString(font, label, cx - font.width(label) / 2, y - 11, st.holding() ? AMBER : MUTED, true);
            g.fill(bx - 1, y - 1, bx + bw + 1, y + 5, 0xC0000000);
            int col = b < 0.25f ? DANGER : st.holding() ? AMBER : ACCENT;
            g.fill(bx, y, bx + Math.round(bw * b), y + 4, col);
        }
        Component hint = Component.translatable(st.partial() ? "smileprotocol.hud.hide_hint_partial" : "smileprotocol.hud.hide_hint");
        g.drawString(font, hint, cx - font.width(hint) / 2, y + 9, 0xFF8A9A94, true);
    }

    // Constantes vitales ------------------------------------------------------------------

    private static float healthFraction(Player p) {
        return p.getMaxHealth() <= 0 ? 0f : Mth.clamp(p.getHealth() / p.getMaxHealth(), 0f, 1f);
    }

    /** Latidos por segundo: 70 ppm con la vida llena, hasta 150 con poca vida. */
    private static double beatsPerSecond(float frac) {
        return (70 + (1 - frac) * 80) / 60.0;
    }

    /** Fase actual del latido (0 a 1). */
    private static double beatPhase(float frac, double secondsAgo) {
        double t = Util.getMillis() / 1000.0 - secondsAgo;
        double ph = (t * beatsPerSecond(frac)) % 1.0;
        return ph < 0 ? ph + 1 : ph;
    }

    /** Intensidad del latido (0 a 1), maxima en el complejo QRS. */
    private static float heartbeat(float frac) {
        double ph = beatPhase(frac, 0);
        return (float) Math.exp(-Math.pow((ph - 0.31) * 12, 2));
    }

    /** Forma del electrocardiograma en una fase del latido (-0.45 a 1). */
    private static double ecg(double p) {
        if (p > 0.10 && p < 0.18) return 0.15 * Math.sin((p - 0.10) / 0.08 * Math.PI);
        if (p > 0.26 && p < 0.29) return -0.25;
        if (p >= 0.29 && p < 0.33) return 1.0;
        if (p >= 0.33 && p < 0.36) return -0.45;
        if (p > 0.50 && p < 0.66) return 0.25 * Math.sin((p - 0.50) / 0.16 * Math.PI);
        return 0.0;
    }

    private static void renderVitals(GuiGraphics g, Font font, Player p, int h) {
        float frac = healthFraction(p);
        boolean low = frac <= 0.3f;
        int pw = 142, ph = 36;
        int px = 8, py = h - ph - 8;
        g.blitSprite(low ? PANEL_ALERT : PANEL, px, py, pw, ph);

        // Corazon que late
        float beat = heartbeat(frac);
        float scale = 1f + 0.18f * beat;
        g.pose().pushPose();
        g.pose().translate(px + 13, py + 18, 0);
        g.pose().scale(scale, scale, 1f);
        g.blitSprite(HEART, -6, -6, 13, 12);
        g.pose().popPose();

        // Rotulo y porcentaje
        int tx = px + 26;
        g.drawString(font, Component.translatable("smileprotocol.hud.vitals"), tx, py + 5, MUTED, false);
        String pct = Math.round(frac * 100) + "%";
        g.drawString(font, pct, px + pw - 7 - font.width(pct), py + 5, low ? DANGER : TEXT, false);

        // Electrocardiograma: los ultimos 2 segundos, lo mas reciente a la derecha
        int ex = tx, ey = py + 15, ew = pw - 33, eh = 10;
        int color = low ? DANGER : ACCENT;
        int prev = Integer.MIN_VALUE;
        for (int x = 0; x < ew; x++) {
            double ago = (ew - 1 - x) / (double) ew * 2.0;
            double v = p.getHealth() <= 0 ? 0 : ecg(beatPhase(frac, ago));
            int y = ey + eh / 2 - (int) Math.round(v * (eh / 2.0));
            int a = 60 + (int) (195 * (x / (float) ew));
            int c = (a << 24) | (color & 0xFFFFFF);
            if (prev == Integer.MIN_VALUE) prev = y;
            g.fill(ex + x, Math.min(prev, y), ex + x + 1, Math.max(prev, y) + 1, c);
            prev = y;
        }
        // Cabeza del trazo
        g.fill(ex + ew - 2, prev - 1, ex + ew + 1, prev + 2, color);

        // Barra segmentada (10 segmentos)
        int bx = tx, by = py + ph - 9, seg = (ew - 9) / 10;
        float filled = frac * 10f;
        for (int i = 0; i < 10; i++) {
            int x0 = bx + i * (seg + 1);
            g.fill(x0, by, x0 + seg, by + 4, 0xFF2A1012);
            float k = Mth.clamp(filled - i, 0f, 1f);
            if (k > 0f) {
                int on = low && beat > 0.5f ? 0xFFFF4A58 : DANGER;
                g.fill(x0, by, x0 + Math.max(1, Math.round(seg * k)), by + 4, on);
                g.fill(x0, by, x0 + Math.max(1, Math.round(seg * k)), by + 1, 0x60FFFFFF);
            }
        }
    }

    // Huecos ---------------------------------------------------------------------------

    private void renderSlots(GuiGraphics g, Font font, int w, int h) {
        int size = 24, gap = 4;
        int total = SPInventory.SIZE * size + (SPInventory.SIZE - 1) * gap;
        int x0 = (w - total) / 2;
        int y = h - size - 8;
        int sel = ClientState.selected();
        if (sel != lastSelected) {
            lastSelected = sel;
            selectedAt = Util.getMillis();
        }

        for (int i = 0; i < SPInventory.SIZE; i++) {
            int x = x0 + i * (size + gap);
            boolean active = i == sel;
            g.blitSprite(active ? SLOT_SELECTED : SLOT, x, y, size, size);
            ItemStack stack = ClientState.slot(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 4, y + 4);
                g.renderItemDecorations(font, stack, x + 4, y + 4);
            }
            g.pose().pushPose();
            g.pose().translate(x + 3, y + 3, 200);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(font, String.valueOf(i + 1), 0, 0, active ? 0xFFBEFFF0 : MUTED, false);
            g.pose().popPose();
        }

        // Nombre del item del hueco activo, al cambiar
        ItemStack current = ClientState.slot(sel);
        long age = Util.getMillis() - selectedAt;
        if (!current.isEmpty() && age < 2000) {
            int alpha = age < 1500 ? 255 : (int) (255 * (1 - (age - 1500) / 500f));
            if (alpha > 8) {
                Component name = current.getHoverName();
                g.drawString(font, name, (w - font.width(name)) / 2, y - 12, (alpha << 24) | (TEXT & 0xFFFFFF), true);
            }
        }
    }

    // Ascensor del lobby -------------------------------------------------------------------

    private static void renderReady(GuiGraphics g, Font font, Payloads.SyncReady r, int w) {
        boolean pips = r.total() > 0 && r.total() <= 8;
        int bw = Math.max(176, Math.max(font.width(r.status()), font.width(r.title())) + 28);
        int bh = pips ? 62 : 52;
        int x = (w - bw) / 2, y = 8;
        boolean counting = r.countdown() >= 0;
        g.blitSprite(PANEL, x, y, bw, bh);

        Component title = r.title().copy();
        g.drawString(font, title, (w - font.width(title)) / 2, y + 7, ACCENT, false);

        Component big = counting ? Component.literal(String.valueOf(r.countdown()))
                : Component.translatable("smileprotocol.hud.ready", r.ready(), r.total());
        int color = counting ? AMBER : (r.total() > 0 && r.ready() >= r.total() ? READY : WAITING);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 19, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, big, -font.width(big) / 2, 0, color, true);
        g.pose().popPose();

        int sy = y + 41;
        if (pips) {
            int pw = r.total() * 9 - 2;
            for (int i = 0; i < r.total(); i++) {
                g.blitSprite(i < r.ready() ? PIP_ON : PIP_OFF, (w - pw) / 2 + i * 9, sy, 7, 7);
            }
            sy += 11;
        }
        g.drawString(font, r.status(), (w - font.width(r.status())) / 2, sy, 0xFFB4C4BE, false);
    }

    // Depuracion ------------------------------------------------------------------------------

    private static void renderDebug(GuiGraphics g, Font font) {
        if (ClientState.debugLines.isEmpty()) return;
        int maxW = 0;
        for (String l : ClientState.debugLines) maxW = Math.max(maxW, font.width(l));
        g.blitSprite(PANEL, 2, 2, maxW + 16, ClientState.debugLines.size() * 10 + 12);
        int y = 8;
        for (String l : ClientState.debugLines) {
            g.drawString(font, l, 9, y, 0xFFE0E0E0, false);
            y += 10;
        }
    }
}
