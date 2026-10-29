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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * HUD de terror (README 8.1). Nada de monitores limpios: brochazos oscuros de fondo, un corazon
 * anatomico que late, el pulso trazado en rojo sobre la mancha, la vida como un trazo de sangre,
 * letra de maquina de escribir (Special Elite) y garabatos (Rock Salt), grano de pelicula y una
 * vineta que se llena de venas cuando queda poca vida.
 * <ul>
 *   <li>Dentro de la partida: pulso y vida, bateria de la linterna, los 4 huecos, punto de mira,
 *   avisos de interaccion y los escondites.</li>
 *   <li>En el lobby: la nota del ascensor con «X/Y listos» y la cuenta atras.</li>
 *   <li>Siempre: el fundido a negro y, para operadores, el panel de depuracion.</li>
 * </ul>
 */
public final class HudOverlay {

    public static final HudOverlay INSTANCE = new HudOverlay();

    private static ResourceLocation sprite(String n) {
        return SmileProtocol.id("hud/" + n);
    }

    private static final ResourceLocation PANEL = sprite("panel");
    private static final ResourceLocation SMEAR = sprite("smear");
    private static final ResourceLocation SMEAR_SMALL = sprite("smear_small");
    private static final ResourceLocation SLOT = sprite("slot");
    private static final ResourceLocation SLOT_SELECTED = sprite("slot_selected");
    private static final ResourceLocation HEART = sprite("heart");
    private static final ResourceLocation BULB = sprite("bulb");
    private static final ResourceLocation PIP_ON = sprite("pip_on");
    private static final ResourceLocation PIP_OFF = sprite("pip_off");
    private static final ResourceLocation VIGNETTE = sprite("vignette");
    private static final ResourceLocation BLOOD_VIGNETTE = sprite("blood_vignette");
    private static final ResourceLocation GRAIN = sprite("grain");
    private static final ResourceLocation BAR_BLOOD = sprite("bar_blood");
    private static final ResourceLocation BAR_EMPTY = sprite("bar_empty");
    private static final ResourceLocation BAR_LIGHT = sprite("bar_light");
    private static final ResourceLocation BAR_LIGHT_EMPTY = sprite("bar_light_empty");
    private static final ResourceLocation TAPE = sprite("tape");
    private static final ResourceLocation NOTE = sprite("note");

    /** Fuentes: maquina de escribir para todo y garabato para los numeros grandes. */
    public static final ResourceLocation FONT_HUD = SmileProtocol.id("hud");
    public static final ResourceLocation FONT_SCRAWL = SmileProtocol.id("scrawl");

    private static final int BONE = 0xFFC9BFA6;
    private static final int BONE_DIM = 0xFF8C8270;
    private static final int BLOOD = 0xFFB0161A;
    private static final int BLOOD_BRIGHT = 0xFFE0282C;
    private static final int SICK = 0xFFC8B45A;
    private static final int INK = 0xFF231C16;
    private static final int READY = 0xFF8FB070;

    /** Nombre del item al cambiar de hueco: se muestra 2 s y se desvanece. */
    private int lastSelected = -1;
    private long selectedAt;
    private static final Random RNG = new Random();

    private HudOverlay() {}

    public static MutableComponent hud(Component c) {
        return c.copy().withStyle(s -> s.withFont(FONT_HUD));
    }

    public static MutableComponent hud(String s) {
        return hud(Component.literal(s));
    }

    public static MutableComponent scrawl(Component c) {
        return c.copy().withStyle(s -> s.withFont(FONT_SCRAWL));
    }

    /** Lo llama cada loader al dibujar el HUD, por encima de todo. */
    public void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        RenderSystem.enableBlend();
        boolean inGame = ClientState.inSmileProtocol() && mc.player != null;
        if (inGame) {
            renderVignette(g, mc.player, w, h);
            renderGrain(g, w, h);
        }

        if (!mc.options.hideGui) {
            if (inGame) {
                if (ClientHide.hidden()) {
                    renderHide(g, font, w, h, partialTick);
                } else {
                    renderCrosshair(g, w, h);
                    renderInteraction(g, font, w, h);
                }
                renderVitals(g, font, mc.player, h);
                renderBattery(g, font, mc.player, h);
                renderSlots(g, font, w, h);
            }
            Payloads.SyncReady ready = ClientState.ready;
            if (ready != null) renderReady(g, font, ready, w);
            renderDebug(g, font);
        }

        float fade = ClientState.fadeAlpha(partialTick);
        if (fade > 0f) g.fill(0, 0, w, h, Mth.clamp((int) (fade * 255), 0, 255) << 24);
    }

    // Vineta y grano -----------------------------------------------------------------------

    private static void renderVignette(GuiGraphics g, Player p, int w, int h) {
        float frac = healthFraction(p);
        RenderSystem.setShaderColor(0f, 0f, 0f, 0.85f);
        g.blitSprite(VIGNETTE, 0, 0, w, h);
        if (frac < 0.6f) {
            float beat = heartbeat(frac);
            float k = (0.6f - frac) / 0.6f;
            float alpha = Mth.clamp(k * (0.45f + 0.35f * beat), 0f, 0.95f);
            // las venas entran un poco mas con cada latido
            int grow = (int) (beat * 6 * k);
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            g.blitSprite(BLOOD_VIGNETTE, -grow, -grow, w + grow * 2, h + grow * 2);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Grano de pelicula: el mismo sprite desplazado al azar cada fotograma, muy tenue. */
    private static void renderGrain(GuiGraphics g, int w, int h) {
        int t = 128;
        int ox = -RNG.nextInt(t), oy = -RNG.nextInt(t);
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.035f);
        for (int y = oy; y < h; y += t) {
            for (int x = ox; x < w; x += t) g.blitSprite(GRAIN, x, y, t, t);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Punto de mira -----------------------------------------------------------------

    private static void renderCrosshair(GuiGraphics g, int w, int h) {
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0x60000000);
        g.fill(cx, cy, cx + 1, cy + 1, 0xC0C9BFA6);
    }

    // Interaccion (README 8.1: prompt y barra de progreso) -------------------------------------

    private static void renderInteraction(GuiGraphics g, Font font, int w, int h) {
        int cx = w / 2, y = h / 2 + 14;
        Payloads.InteractState st = ClientInteraction.state();
        if (st.active()) {
            Component prompt = hud(st.prompt());
            g.drawString(font, prompt, cx - font.width(prompt) / 2, y, BONE, true);
            int bw = 96, bx = cx - bw / 2, by = y + 12;
            float k = st.total() <= 0 ? 1f : Mth.clamp(st.progress() / (float) st.total(), 0f, 1f);
            g.blitSprite(BAR_EMPTY, bx, by, bw, 6);
            if (st.waiting()) RenderSystem.setShaderColor(0.8f, 0.75f, 0.5f, 1f);
            bar(g, st.waiting() ? BAR_LIGHT : BAR_BLOOD, st.waiting() ? 64 : 100, st.waiting() ? 6 : 8, bx, by, bw, 6, k);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (st.waiting()) {
                Component wait = hud(Component.translatable("smileprotocol.interact.waiting"));
                g.drawString(font, wait, cx - font.width(wait) / 2, by + 9, SICK, true);
            }
            return;
        }
        com.smileprotocol.interact.InteractionSpec spec = ClientInteraction.spec();
        if (spec == null) return;
        if (spec.blocked()) {
            Component c = hud(spec.prompt());
            g.drawString(font, c, cx - font.width(c) / 2, y, BONE_DIM, true);
            return;
        }
        Component key = hud(Component.translatable(spec.ticks() > 0 ? "smileprotocol.interact.hold" : "smileprotocol.interact.press"));
        Component prompt = hud(spec.prompt());
        int kw = font.width(key) + 10;
        int total = kw + 5 + font.width(prompt);
        int x = cx - total / 2;
        g.blitSprite(TAPE, x, y - 3, kw, 13);
        g.drawString(font, key, x + 5, y, INK, false);
        g.drawString(font, prompt, x + kw + 5, y, BONE, true);
    }

    // Escondite (README 15.4: rendijas, aliento) -------------------------------------------------

    private static void renderHide(GuiGraphics g, Font font, int w, int h, float pt) {
        Payloads.SyncHide st = ClientHide.state();
        switch (st.overlay()) {
            case "crack" -> {
                int gap = Math.max(10, w / 9), cx = w / 2;
                g.fill(0, 0, cx - gap / 2, h, 0xF4040303);
                g.fill(cx + gap / 2, 0, w, h, 0xF4040303);
                g.fillGradient(cx - gap / 2, 0, cx + gap / 2, h / 4, 0xE0040303, 0x00040303);
                g.fillGradient(cx - gap / 2, h * 3 / 4, cx + gap / 2, h, 0x00040303, 0xE0040303);
            }
            case "low" -> {
                g.fill(0, 0, w, h * 2 / 5, 0xF4050404);
                g.fillGradient(0, h * 2 / 5, w, h * 3 / 5, 0xF4050404, 0x30050404);
                g.fillGradient(0, h - h / 8, w, h, 0x00050404, 0xC0050404);
                // la sabana manchada cuelga por delante
                g.fillGradient(0, h * 2 / 5 - 4, w, h * 2 / 5 + 10, 0x00000000, 0x40400808);
            }
            case "cloth" -> {
                g.fill(0, 0, w, h, 0x58586850);
                g.fillGradient(0, 0, w, h / 3, 0x80201810, 0x00201810);
            }
            default -> {
                int slits = 6, sh = Math.max(3, h / 70), gap = Math.max(sh * 2, h / 22);
                int band = slits * sh + (slits - 1) * gap;
                int top = h / 2 - band / 2;
                int sx0 = w / 2 - w / 3, sx1 = w / 2 + w / 3;
                g.fill(0, 0, w, top, 0xF6030303);
                g.fill(0, top + band, w, h, 0xF6030303);
                g.fill(0, top, sx0, top + band, 0xF6030303);
                g.fill(sx1, top, w, top + band, 0xF6030303);
                for (int i = 0; i < slits - 1; i++) {
                    int y0 = top + i * (sh + gap) + sh;
                    g.fill(sx0, y0, sx1, y0 + gap, 0xF6030303);
                }
                g.fill(sx0, top, sx1, top + band, 0x22000000);
            }
        }

        int cx = w / 2, y = h - 100;
        if (!st.partial()) {
            float b = ClientHide.breath(pt);
            int bw = 120, bx = cx - bw / 2;
            Component label = hud(Component.translatable(st.holding() ? "smileprotocol.hud.holding_breath" : "smileprotocol.hud.breath"));
            // el texto tiembla cuando se esta aguantando la respiracion
            int jx = st.holding() ? RNG.nextInt(3) - 1 : 0;
            g.drawString(font, label, cx - font.width(label) / 2 + jx, y - 12, st.holding() ? SICK : BONE_DIM, true);
            g.blitSprite(BAR_LIGHT_EMPTY, bx, y, bw, 6);
            if (b < 0.25f) RenderSystem.setShaderColor(1f, 0.35f, 0.3f, 1f);
            bar(g, BAR_LIGHT, 64, 6, bx, y, bw, 6, b);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        Component hint = hud(Component.translatable(st.partial() ? "smileprotocol.hud.hide_hint_partial" : "smileprotocol.hud.hide_hint"));
        g.drawString(font, hint, cx - font.width(hint) / 2, y + 10, BONE_DIM, true);
    }

    /** Dibuja la parte llena de una barra (sprite recortado a la fraccion {@code k}). */
    private static void bar(GuiGraphics g, ResourceLocation sprite, int tw, int th, int x, int y, int w, int h, float k) {
        int fw = Math.round(w * Mth.clamp(k, 0f, 1f));
        if (fw <= 0) return;
        // Se estira el sprite al ancho completo y se recorta: el borde del trazo queda al final
        g.enableScissor(x, y - 2, x + fw, y + h + 2);
        g.blitSprite(sprite, x, y, w, h);
        g.disableScissor();
    }

    // Pulso y vida ------------------------------------------------------------------

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
        int pw = 150, ph = 42;
        int px = 4, py = h - ph - 4;
        g.blitSprite(SMEAR, px, py, pw, ph);

        // Corazon que late (y tiembla con poca vida)
        float beat = heartbeat(frac);
        float scale = 1.25f + 0.25f * beat;
        int shake = low ? RNG.nextInt(3) - 1 : 0;
        g.pose().pushPose();
        g.pose().translate(px + 19 + shake, py + 21, 0);
        g.pose().scale(scale, scale, 1f);
        g.blitSprite(HEART, -8, -8, 16, 16);
        g.pose().popPose();

        // Rotulo y porcentaje
        int tx = px + 36;
        g.drawString(font, hud(Component.translatable("smileprotocol.hud.vitals")), tx, py + 7, BONE_DIM, false);
        Component pct = hud(Math.round(frac * 100) + "%");
        g.drawString(font, pct, px + pw - 12 - font.width(pct), py + 7, low ? BLOOD_BRIGHT : BONE, false);

        // Pulso: los ultimos 2 segundos, lo mas reciente a la derecha, con algun salto de senal
        int ex = tx, ey = py + 17, ew = pw - 50, eh = 10;
        int prev = Integer.MIN_VALUE;
        long glitch = Util.getMillis() / 90;
        for (int x = 0; x < ew; x++) {
            double ago = (ew - 1 - x) / (double) ew * 2.0;
            double v = p.getHealth() <= 0 ? 0 : ecg(beatPhase(frac, ago));
            int y = ey + eh / 2 - (int) Math.round(v * (eh / 2.0));
            if (low && ((x * 31 + glitch * 17) % 53) == 0) y += RNG.nextInt(5) - 2;
            int a = 50 + (int) (205 * (x / (float) ew));
            int c = (a << 24) | ((low ? BLOOD_BRIGHT : BLOOD) & 0xFFFFFF);
            if (prev == Integer.MIN_VALUE) prev = y;
            g.fill(ex + x, Math.min(prev, y), ex + x + 1, Math.max(prev, y) + 1, c);
            prev = y;
        }
        g.fill(ex + ew - 2, prev - 1, ex + ew + 1, prev + 2, BLOOD_BRIGHT);

        // Vida: trazo de sangre
        int bx = tx, by = py + ph - 13, bw = pw - 50;
        g.blitSprite(BAR_EMPTY, bx, by, bw, 6);
        if (low && beat > 0.5f) RenderSystem.setShaderColor(1.3f, 1.1f, 1.1f, 1f);
        bar(g, BAR_BLOOD, 100, 8, bx, by, bw, 6, frac);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Bateria de la linterna: solo con la linterna encendida (y un momento al apagarla) --------

    private static void renderBattery(GuiGraphics g, Font font, Player p, int h) {
        int id = p.getId();
        boolean on = com.smileprotocol.client.light.Flashlight.isOn(id);
        long since = com.smileprotocol.client.light.Flashlight.sinceToggle();
        if (!on && since > 2500) return;
        float alpha = on ? 1f : Mth.clamp(1f - (since - 1500) / 1000f, 0f, 1f);
        if (alpha <= 0.02f) return;
        float b = com.smileprotocol.client.light.Flashlight.battery(id);
        boolean flick = b < 0.10f && (Util.getMillis() / 170) % 3 == 0;
        int a = (int) (alpha * (flick ? 0.35f : 1f) * 255) << 24;
        Component label = hud(Component.translatable(on ? "smileprotocol.hud.flashlight" : "smileprotocol.hud.flashlight_off"));
        Component pct = hud(Math.round(b * 100) + "%");
        int barW = 60;
        int pw = 10 + 12 + 6 + font.width(label) + 6 + barW + 6 + font.width(pct) + 12, ph = 22;
        int px = 4, py = h - 42 - 4 - ph + 2;
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blitSprite(SMEAR_SMALL, px, py, pw, ph);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha * (flick ? 0.35f : 1f));
        g.blitSprite(BULB, px + 10, py + 5, 12, 12);
        int x = px + 28;
        g.drawString(font, label, x, py + 7, a | (BONE_DIM & 0xFFFFFF), false);
        x += font.width(label) + 6;
        g.blitSprite(BAR_LIGHT_EMPTY, x, py + 9, barW, 5);
        if (b < 0.2f) RenderSystem.setShaderColor(1f, 0.45f, 0.35f, alpha);
        bar(g, BAR_LIGHT, 64, 6, x, py + 9, barW, 5, b);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        x += barW + 6;
        g.drawString(font, pct, x, py + 7, a | ((b < 0.2f ? BLOOD_BRIGHT : SICK) & 0xFFFFFF), false);
    }

    // Huecos ---------------------------------------------------------------------------

    private void renderSlots(GuiGraphics g, Font font, int w, int h) {
        int size = 24, gap = 5;
        int total = SPInventory.SIZE * size + (SPInventory.SIZE - 1) * gap;
        int x0 = (w - total) / 2;
        int y = h - size - 6;
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
            g.pose().translate(x + 3, y + 2, 200);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, hud(String.valueOf(i + 1)), 0, 0, active ? BLOOD_BRIGHT : BONE_DIM, false);
            g.pose().popPose();
        }

        // Nombre del item del hueco activo, al cambiar
        ItemStack current = ClientState.slot(sel);
        long age = Util.getMillis() - selectedAt;
        if (!current.isEmpty() && age < 2000) {
            int alpha = age < 1500 ? 255 : (int) (255 * (1 - (age - 1500) / 500f));
            if (alpha > 8) {
                Component name = hud(current.getHoverName());
                g.drawString(font, name, (w - font.width(name)) / 2, y - 13, (alpha << 24) | (BONE & 0xFFFFFF), true);
            }
        }
    }

    // Ascensor del lobby: una nota de papel ----------------------------------------------------

    private static void renderReady(GuiGraphics g, Font font, Payloads.SyncReady r, int w) {
        boolean pips = r.total() > 0 && r.total() <= 8;
        Component title = hud(r.title());
        Component status = hud(r.status());
        int bw = Math.max(190, Math.max(font.width(status), font.width(title)) + 40);
        int bh = pips ? 76 : 64;
        int x = (w - bw) / 2, y = 6;
        boolean counting = r.countdown() >= 0;
        g.blitSprite(NOTE, x, y, bw, bh);

        g.drawString(font, title, (w - font.width(title)) / 2, y + 9, INK, false);

        Component big = scrawl(counting ? Component.literal(String.valueOf(r.countdown()))
                : Component.translatable("smileprotocol.hud.ready", r.ready(), r.total()));
        int color = counting ? 0xFF8A1010 : (r.total() > 0 && r.ready() >= r.total() ? 0xFF2E4A22 : 0xFF5A1410);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 22, 0);
        float s = counting ? 2.0f : 1.5f;
        g.pose().scale(s, s, 1f);
        g.drawString(font, big, -font.width(big) / 2, 0, color, false);
        g.pose().popPose();

        int sy = y + 50;
        if (pips) {
            int pw = r.total() * 10 - 3;
            for (int i = 0; i < r.total(); i++) {
                g.blitSprite(i < r.ready() ? PIP_ON : PIP_OFF, (w - pw) / 2 + i * 10, sy, 7, 7);
            }
            sy += 11;
        }
        g.drawString(font, status, (w - font.width(status)) / 2, sy, 0xFF3A3026, false);
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
