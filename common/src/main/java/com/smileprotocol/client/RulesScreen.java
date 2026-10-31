package com.smileprotocol.client;

import com.smileprotocol.difficulty.RunRules;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Reglas de la partida: se abre con F7 dentro de la cabina de un ascensor pequeno. Nivel de dificultad
 * (Facil, Normal, Pesadilla) y modificadores. Al aceptar se mandan al servidor, que quita el «listo» a
 * todos los de la cabina para que vuelvan a aceptar con las reglas nuevas.
 */
public class RulesScreen extends Screen {

    private RunRules rules;
    private final RunRules initial;

    public RulesScreen(RunRules current) {
        super(Component.translatable("smileprotocol.rules.title"));
        this.rules = current;
        this.initial = current;
    }

    /** Las reglas que tiene ahora la cabina en la que esta el jugador, o null si no se pueden elegir. */
    public static RunRules cabinRules() {
        Payloads.SyncReady r = ClientState.ready;
        if (r == null || r.rules() == null || r.rules().isEmpty()) return null;
        return RunRules.decode(r.rules());
    }

    @Override
    protected void init() {
        int x1 = width / 2 - 155, x2 = width / 2 + 5, y = 44, bw = 150;
        addRenderableWidget(CycleButton.<RunRules.Level>builder(l -> Component.translatable("smileprotocol.rules.level." + l.name().toLowerCase()))
                .withValues(List.of(RunRules.Level.values())).withInitialValue(rules.level())
                .withTooltip(l -> Tooltip.create(Component.translatable("smileprotocol.rules.level." + l.name().toLowerCase() + ".tip")))
                .create(width / 2 - 100, y, 200, 20, Component.translatable("smileprotocol.rules.level"), (b, v) -> rules = rules.withLevel(v)));
        y += 30;
        List<String> mods = RunRules.ALL;
        for (int i = 0; i < mods.size(); i++) {
            String m = mods.get(i);
            int x = i % 2 == 0 ? x1 : x2;
            addRenderableWidget(CycleButton.onOffBuilder(rules.has(m))
                    .withTooltip(v -> Tooltip.create(Component.translatable("smileprotocol.rules.mod." + m + ".tip")))
                    .create(x, y, bw, 20, Component.translatable("smileprotocol.rules.mod." + m), (b, v) -> {
                        if (v != rules.has(m)) rules = rules.toggle(m);
                    }));
            if (i % 2 == 1) y += 24;
        }
        if (mods.size() % 2 == 1) y += 24;
        y += 18;
        addRenderableWidget(Button.builder(Component.translatable("smileprotocol.access.title"),
                b -> Minecraft.getInstance().setScreen(new AccessibilityScreen(this))).bounds(x1, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x2, y, bw, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFE0D6BC);
        g.drawCenteredString(font, Component.translatable("smileprotocol.rules.note"), width / 2, 28, 0xFF9A9080);
        int score = (int) Math.round(rules.scoreFactor() * 100);
        g.drawCenteredString(font, Component.translatable("smileprotocol.rules.score", score), width / 2, height - 24, 0xFFC8B070);
    }

    @Override
    public void onClose() {
        if (!rules.equals(initial) && Minecraft.getInstance().getConnection() != null) {
            SPNetwork.sendToServer(new Payloads.SetRules(rules.encode()));
        }
        Minecraft.getInstance().setScreen(null);
    }
}
