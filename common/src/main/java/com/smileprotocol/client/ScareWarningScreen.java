package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Aviso de sustos (README 24.2): la primera vez que se entra en una partida en cada sesion de juego.
 * Permite seguir, abrir el menu de accesibilidad o no volver a verlo.
 */
public class ScareWarningScreen extends Screen {

    private static boolean shown;
    private MultiLineLabel body = MultiLineLabel.EMPTY;

    public ScareWarningScreen() {
        super(Component.translatable("smileprotocol.warning.title"));
    }

    /** Lo llama el tick del cliente: se abre una vez al entrar en la primera partida. */
    public static void maybeShow(Minecraft mc) {
        if (shown || !SPConfig.get().scare_warning || mc.screen != null || !ClientState.inSmileProtocol()) return;
        shown = true;
        mc.setScreen(new ScareWarningScreen());
    }

    @Override
    protected void init() {
        body = MultiLineLabel.create(font, Component.translatable("smileprotocol.warning.body"), width - 80);
        int y = height / 2 + 40;
        addRenderableWidget(Button.builder(Component.translatable("smileprotocol.warning.continue"), b -> onClose())
                .bounds(width / 2 - 155, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("smileprotocol.warning.options"),
                b -> Minecraft.getInstance().setScreen(new AccessibilityScreen(null))).bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("smileprotocol.warning.never"), b -> {
            SPConfig.get().scare_warning = false;
            SPConfig.save();
            onClose();
        }).bounds(width / 2 + 55, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFB0161A);
        body.renderCentered(g, width / 2, height / 2 - 40);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
