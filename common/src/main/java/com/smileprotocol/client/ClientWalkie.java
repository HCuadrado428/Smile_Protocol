package com.smileprotocol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Walkie en el cliente (README 17.3): B mantenida transmite (si el walkie esta en el hueco activo
 * y encendido) y la HUD dice si esta encendido, si transmites y a quien se oye.
 */
public final class ClientWalkie {

    private static final ResourceLocation ICON = SmileProtocol.id("hud/walkie");
    private static Payloads.SyncWalkie state = Payloads.SyncWalkie.NONE;
    private static boolean sent;
    private static long rxSince;

    private ClientWalkie() {}

    public static Payloads.SyncWalkie state() {
        return state;
    }

    public static void onSync(Payloads.SyncWalkie s) {
        if (!s.rx().isEmpty() && state.rx().isEmpty()) rxSince = Util.getMillis();
        state = s;
    }

    public static void reset() {
        state = Payloads.SyncWalkie.NONE;
        sent = false;
    }

    public static void tick(Minecraft mc) {
        boolean want = mc.player != null && ClientState.inSmileProtocol() && !ClientState.dead && mc.screen == null
                && state.has() && state.on() && ClientMotion.isDown(SPConfig.get().key_walkie);
        if (want != sent) {
            sent = want;
            SPNetwork.sendToServer(new Payloads.WalkieInput(want));
        }
    }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        Payloads.SyncWalkie s = state;
        boolean listening = !s.rx().isEmpty();
        if (!s.has() && !listening) return;
        long now = Util.getMillis();
        boolean noise = s.interference() && (now / 90) % 3 != 0;
        Component label;
        int color;
        if (s.tx()) {
            label = Component.translatable("smileprotocol.hud.walkie_tx");
            color = (now / 400) % 2 == 0 ? 0xFFE0282C : 0xFFB0161A;
        } else if (listening) {
            label = Component.translatable("smileprotocol.hud.walkie_rx", s.rx());
            color = 0xFF8FB070;
        } else if (s.on()) {
            label = Component.translatable("smileprotocol.hud.walkie_on");
            color = 0xFF8C8270;
        } else {
            label = Component.translatable("smileprotocol.hud.walkie_off");
            color = 0xFF4A443A;
        }
        Component text = HudOverlay.hud(label);
        int tw = font.width(text);
        int x = w - tw - 30, y = h - 22;
        float shake = noise ? (float) Math.sin(now * 0.07) * 1.5f : 0f;
        RenderSystem.enableBlend();
        float r = ((color >> 16) & 255) / 255f, gg = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        RenderSystem.setShaderColor(r, gg, b, noise ? 0.6f : 1f);
        int bounce = listening && !s.tx() ? (int) (Math.sin((now - rxSince) * 0.02) * 1.5) : 0;
        g.blitSprite(ICON, x - 18 + (int) shake, y - 3 + bounce, 14, 14);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.drawString(font, text, x + (int) shake, y, color, true);
    }
}
