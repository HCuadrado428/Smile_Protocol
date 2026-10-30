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
    private static final ResourceLocation[] SPLATTER = {sprite("splatter0"), sprite("splatter1"), sprite("splatter2"), sprite("splatter3")};

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
            if (!ClientSelfTest.recording()) renderGrain(g, w, h);
        }

        if (!mc.options.hideGui) {
            if (inGame && ClientState.dead) {
                renderDead(g, font, w, h);
            } else if (inGame && ClientSequence.active()) {
                renderSequence(g, w, h, partialTick);
            } else if (inGame) {
                if (ClientQTE.active()) {
                    renderQTE(g, font, w, h);
                } else if (ClientState.downed) {
                    renderDowned(g, font, w, h);
                } else if (ClientHide.hidden()) {
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

    /** Minijuego de escape (README 18): titulo garabateado, tiempo en sangre y el control de cada tipo. */
    private static void renderQTE(GuiGraphics g, Font font, int w, int h) {
        com.smileprotocol.qte.QTEType type = ClientQTE.type();
        float t = ClientQTE.time();
        long ms = Util.getMillis();
        // pantalla: latido rojo en los bordes y un temblor leve
        float beat = (float) Math.exp(-Math.pow(((ms / 1000.0 * 2.2) % 1.0 - 0.3) * 10, 2));
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.35f + 0.3f * beat);
        g.blitSprite(BLOOD_VIGNETTE, -8, -8, w + 16, h + 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        int shake = ClientQTE.sincePress() < 90 ? RNG.nextInt(3) - 1 : 0;
        int cx = w / 2 + shake, y = h / 2 - 54;
        String key = type.name().toLowerCase(java.util.Locale.ROOT);
        Component title = scrawl(Component.translatable("smileprotocol.qte." + key));
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFC01A1E, false);
        g.pose().popPose();
        // tiempo restante
        int bw = 170, bx = cx - bw / 2, by = y + 26;
        g.blitSprite(BAR_EMPTY, bx, by, bw, 5);
        bar(g, BAR_BLOOD, 100, 8, bx, by, bw, 5, ClientQTE.remaining());
        int wy = by + 14;
        switch (type) {
            case MASH -> {
                float k = ClientQTE.progress() / (float) ClientQTE.target();
                g.blitSprite(BAR_LIGHT_EMPTY, bx, wy, bw, 9);
                bar(g, BAR_LIGHT, 64, 6, bx, wy, bw, 9, k);
                Component sp = hud(Component.translatable("smileprotocol.qte.key_space"));
                int kw = font.width(sp) + 12, kx = cx - kw / 2 + (ClientQTE.sincePress() < 80 ? 0 : 0);
                int ky = wy + 16 + (ClientQTE.sincePress() < 80 ? 1 : 0);
                g.blitSprite(TAPE, kx, ky, kw, 14);
                g.drawString(font, sp, kx + 6, ky + 3, INK, false);
            }
            case TIMING -> {
                int tw = bw, tx = bx, ty = wy;
                g.blitSprite(BAR_LIGHT_EMPTY, tx, ty, tw, 10);
                float zone = com.smileprotocol.qte.QTEType.timingZone(ClientQTE.seed(), ClientQTE.progress());
                float zw = com.smileprotocol.qte.QTEType.timingWidth(ClientQTE.difficulty());
                int zx0 = tx + Math.round((zone - zw / 2) * tw), zx1 = tx + Math.round((zone + zw / 2) * tw);
                g.fill(zx0, ty - 1, zx1, ty + 11, 0xA0C9BFA6);
                int cur = tx + Math.round(com.smileprotocol.qte.QTEType.timingCursor(t) * tw);
                g.fill(cur - 1, ty - 4, cur + 1, ty + 14, BLOOD_BRIGHT);
                pips(g, cx, ty + 18, com.smileprotocol.qte.QTEType.TIMING_HITS, ClientQTE.progress());
                int misses = Math.round(ClientQTE.extra());
                for (int i = 0; i < com.smileprotocol.qte.QTEType.TIMING_MISSES; i++) {
                    Component x = hud(i < misses ? "X" : "·");
                    g.drawString(font, x, cx - 8 + i * 12, ty + 30, i < misses ? BLOOD_BRIGHT : BONE_DIM, false);
                }
            }
            case SEQUENCE -> {
                String[] names = {"W", "A", "S", "D"};
                int n = com.smileprotocol.qte.QTEType.SEQUENCE_LENGTH, kw = 18, gap = 6;
                int x0 = cx - (n * kw + (n - 1) * gap) / 2;
                for (int i = 0; i < n; i++) {
                    int k = com.smileprotocol.qte.QTEType.sequenceKey(ClientQTE.seed(), i);
                    boolean done = i < ClientQTE.progress(), cur = i == ClientQTE.progress();
                    int kx = x0 + i * (kw + gap), ky = wy + (cur ? -2 : 0);
                    if (!done) {
                        RenderSystem.setShaderColor(1f, 1f, 1f, cur ? 1f : 0.55f);
                        g.blitSprite(TAPE, kx, ky, kw, 16);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    }
                    Component c = hud(names[k]);
                    g.drawString(font, c, kx + (kw - font.width(c)) / 2, ky + 4, done ? 0x60C9BFA6 : cur ? 0xFF8A1010 : INK, false);
                    if (done) g.fill(kx + 2, ky + 8, kx + kw - 2, ky + 9, BLOOD);
                }
            }
            case HOLD -> {
                int tw = bw, tx = bx, ty = wy;
                g.blitSprite(BAR_LIGHT_EMPTY, tx, ty, tw, 10);
                float zone = com.smileprotocol.qte.QTEType.holdZone(ClientQTE.seed(), t);
                float zw = com.smileprotocol.qte.QTEType.HOLD_ZONE;
                g.fill(tx + Math.round((zone - zw / 2) * tw), ty - 1, tx + Math.round((zone + zw / 2) * tw), ty + 11, 0xA0C9BFA6);
                int nx = tx + Math.round(ClientQTE.extra() * tw);
                g.fill(nx - 1, ty - 4, nx + 1, ty + 14, BLOOD_BRIGHT);
                float k = ClientQTE.progress() / (float) ClientQTE.target();
                g.blitSprite(BAR_EMPTY, bx + 30, ty + 18, bw - 60, 5);
                bar(g, BAR_BLOOD, 100, 8, bx + 30, ty + 18, bw - 60, 5, k);
            }
        }
        Component hint = hud(Component.translatable("smileprotocol.qte." + key + "_hint"));
        g.drawString(font, hint, cx - font.width(hint) / 2, wy + 44, BONE, true);
    }

    private static void pips(GuiGraphics g, int cx, int y, int n, int on) {
        int pw = n * 10 - 3;
        for (int i = 0; i < n; i++) g.blitSprite(i < on ? PIP_ON : PIP_OFF, cx - pw / 2 + i * 10, y, 7, 7);
    }

    /** Derribado: todo se oscurece y enrojece, y una barra marca lo que queda antes de desangrarse. */
    private static void renderDowned(GuiGraphics g, Font font, int w, int h) {
        float beat = (float) Math.exp(-Math.pow(((Util.getMillis() / 1000.0 * 1.6) % 1.0 - 0.3) * 10, 2));
        // (sin g.fill antes: el lote de fill cambia el estado y la vineta salia opaca)
        RenderSystem.setShaderColor(0.25f, 0f, 0f, 0.6f);
        g.blitSprite(VIGNETTE, 0, 0, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.3f + 0.25f * beat);
        g.blitSprite(BLOOD_VIGNETTE, -6, -6, w + 12, h + 12);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        int cx = w / 2, y = h / 2 - 30;
        Component title = scrawl(Component.translatable("smileprotocol.downed.title"));
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFB01418, false);
        g.pose().popPose();
        int bw = 140, bx = cx - bw / 2, by = y + 30;
        g.blitSprite(BAR_EMPTY, bx, by, bw, 7);
        bar(g, BAR_BLOOD, 100, 8, bx, by, bw, 7, ClientState.bleed);
        Component hint = hud(Component.translatable("smileprotocol.downed.hint"));
        g.drawString(font, hint, cx - font.width(hint) / 2, by + 12, BONE_DIM, true);
        if (ClientState.revive >= 0f) {
            // un companero le esta reanimando
            int ry = by + 30;
            Component r = hud(Component.translatable("smileprotocol.downed.being_revived"));
            g.drawString(font, r, cx - font.width(r) / 2, ry, READY, true);
            g.blitSprite(BAR_LIGHT_EMPTY, bx + 20, ry + 11, bw - 40, 5);
            bar(g, BAR_LIGHT, 64, 6, bx + 20, ry + 11, bw - 40, 5, ClientState.revive);
        }
    }

    /**
     * Remate o muerte instantanea (README 9.3): rojo que late con cada golpe, cada vez mas oscuro,
     * y fundido a negro al final. Sin textos: solo lo que se ve.
     */
    private static void renderSequence(GuiGraphics g, int w, int h, float pt) {
        float hit = ClientSequence.impact(pt);
        float prog = ClientSequence.progress(pt);
        RenderSystem.setShaderColor(0.2f, 0f, 0f, 0.55f + 0.35f * prog);
        g.blitSprite(VIGNETTE, -20, -20, w + 40, h + 40);
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(0.25f + 0.75f * hit + 0.3f * prog, 0f, 1f));
        g.blitSprite(BLOOD_VIGNETTE, -10, -10, w + 20, h + 20);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // sangre en la pantalla: cada mancha resbala despacio hacia abajo
        int now = ClientSequence.ageTicks();
        for (ClientSequence.Splat sp : ClientSequence.splats()) {
            float life = now - sp.born() + pt;
            float sy = sp.y() + sp.drip() * life;
            float alpha = Mth.clamp(1.15f - life / 120f, 0.55f, 1f);
            int size = (int) (sp.size() * (1f + Math.min(life, 4f) * 0.04f));
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            g.blitSprite(SPLATTER[sp.variant()], (int) (sp.x() * w) - size / 2, (int) (sy * h) - size / 2, size, (int) (size * 1.15f));
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        float black = Mth.clamp((prog - 0.8f) / 0.2f, 0f, 1f);
        // cabeza arrancada: se va a negro en un momento
        com.smileprotocol.sequence.SequenceKind kind = ClientSequence.kind();
        if (kind.decap >= 0) black = Math.max(black, Mth.clamp((ClientSequence.age(pt) - kind.decap) / 5f, 0f, 1f));
        int a = Mth.clamp((int) (black * 255) + (int) (hit * 70), 0, 255);
        if (a > 0) g.fill(0, 0, w, h, a << 24 | (black < 0.5f ? 0x200000 : 0));
    }

    /** Muerto: rotulo y a quien se esta mirando (README 9.3). */
    private static void renderDead(GuiGraphics g, Font font, int w, int h) {
        RenderSystem.setShaderColor(0f, 0f, 0f, 0.7f);
        g.blitSprite(VIGNETTE, 0, 0, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Component title = scrawl(Component.translatable("smileprotocol.downed.dead_title"));
        g.pose().pushPose();
        g.pose().translate(w / 2f, 18, 0);
        g.pose().scale(1.8f, 1.8f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFB01418, false);
        g.pose().popPose();
        Minecraft mc = Minecraft.getInstance();
        Component line = mc.getCameraEntity() instanceof Player other && other != mc.player
                ? Component.translatable("smileprotocol.downed.watching", other.getDisplayName())
                : Component.translatable("smileprotocol.downed.no_one");
        Component l1 = hud(line), l2 = hud(Component.translatable("smileprotocol.downed.dead_hint"));
        g.drawString(font, l1, w / 2 - font.width(l1) / 2, h - 44, BONE, true);
        g.drawString(font, l2, w / 2 - font.width(l2) / 2, h - 32, BONE_DIM, true);
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
