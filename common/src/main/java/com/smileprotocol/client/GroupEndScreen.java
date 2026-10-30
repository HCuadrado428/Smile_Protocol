package com.smileprotocol.client;

import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Pantalla del final del grupo (README 6.6, 6.7). GAME OVER: estatica de television, letras rojas
 * que tiemblan, la planta en la que cayo el grupo, y los botones «Volver al lobby» y «Seguir
 * mirando» (espectar a otros grupos). Escapados: lo mismo en tono frio.
 */
public class GroupEndScreen extends Screen {

    private final Payloads.GroupEnd end;
    private final long opened = Util.getMillis();
    private final Random rng = new Random();

    public GroupEndScreen(Payloads.GroupEnd end) {
        super(Component.translatable(end.eliminated() ? "smileprotocol.end.game_over" : "smileprotocol.end.escaped"));
        this.end = end;
    }

    public static void open(Payloads.GroupEnd end) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(end.eliminated() ? ModSounds.EVENT_STING.get()
                : net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 1f, end.eliminated() ? 0.6f : 1f));
        mc.setScreen(new GroupEndScreen(end));
    }

    @Override
    protected void init() {
        int bw = 150, y = height / 2 + 50;
        addRenderableWidget(Button.builder(Component.translatable("smileprotocol.end.lobby"), b -> {
            SPNetwork.sendToServer(new Payloads.ReturnLobby(true));
            onClose();
        }).bounds(width / 2 - bw - 6, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(end.eliminated() ? "smileprotocol.end.watch" : "smileprotocol.end.stay"),
                b -> onClose()).bounds(width / 2 + 6, y, bw, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        float t = (Util.getMillis() - opened) / 1000f;
        g.fill(0, 0, width, height, 0xE0000000);
        // estatica: bloques de ruido que cambian cada frame, mas fuertes al principio
        float k = Mth.clamp(1.2f - t * 0.25f, 0.25f, 1f);
        for (int i = 0; i < 900 * k; i++) {
            int x = rng.nextInt(width), y = rng.nextInt(height), s = 1 + rng.nextInt(3);
            int v = rng.nextInt(120);
            g.fill(x, y, x + s, y + s, (int) (60 * k) << 24 | v << 16 | v << 8 | v);
        }
        // banda de interferencia que baja
        int band = (int) ((t * 90) % (height + 40)) - 20;
        g.fill(0, band, width, band + 6, 0x30FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        float t = (Util.getMillis() - opened) / 1000f;
        int color = end.eliminated() ? 0xFFB01418 : 0xFF8FB0B0;
        int jx = end.eliminated() ? (int) (Mth.sin(t * 31) * 2 + (rng.nextInt(9) == 0 ? rng.nextInt(9) - 4 : 0)) : 0;
        Component title = HudOverlay.scrawl(getTitle());
        g.pose().pushPose();
        g.pose().translate(width / 2f + jx, height / 2f - 50, 0);
        g.pose().scale(3.2f, 3.2f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, color, false);
        g.pose().popPose();
        Component where = HudOverlay.hud(Component.translatable(end.eliminated() ? "smileprotocol.end.fell" : "smileprotocol.end.made_it",
                end.zone(), end.zoneIndex() + 1, end.zones() + 1));
        Component time = HudOverlay.hud(Component.translatable("smileprotocol.end.time", end.seconds() / 60, String.format("%02d", end.seconds() % 60)));
        g.drawString(font, where, width / 2 - font.width(where) / 2, height / 2 + 4, 0xFFC9BFA6, true);
        g.drawString(font, time, width / 2 - font.width(time) / 2, height / 2 + 18, 0xFF8C8270, true);
        if (end.eliminated()) {
            Component hint = HudOverlay.hud(Component.translatable("smileprotocol.end.no_retry"));
            g.drawString(font, hint, width / 2 - font.width(hint) / 2, height / 2 + 32, 0xFF6A6050, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
