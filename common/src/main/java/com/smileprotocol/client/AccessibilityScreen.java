package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu de accesibilidad (README 24.2): subtitulos, daltonismo, sacudida de camara, destellos, sustos
 * suaves, pulsar en vez de mantener, minijuego asistido y tamano de la HUD. Se abre con
 * {@code key_accessibility} (F7) o desde la lista de mods; se guarda al cerrar.
 */
public class AccessibilityScreen extends Screen {

    @Nullable private final Screen parent;

    public AccessibilityScreen(@Nullable Screen parent) {
        super(Component.translatable("smileprotocol.access.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        SPConfig.Values c = SPConfig.get();
        int x1 = width / 2 - 155, x2 = width / 2 + 5, y = 40, bw = 150;
        addRenderableWidget(toggle(x1, y, bw, "subtitles", () -> c.subtitles, v -> c.subtitles = v));
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("smileprotocol.access.colorblind." + v.toLowerCase()))
                .withValues(List.of("OFF", "DEUTERANOPIA", "PROTANOPIA", "TRITANOPIA")).withInitialValue(c.colorblind)
                .create(x2, y, bw, 20, Component.translatable("smileprotocol.access.colorblind"), (b, v) -> c.colorblind = v));
        y += 24;
        addRenderableWidget(slider(x1, y, bw, "camera_shake", c.camera_shake, 0f, 1f, v -> c.camera_shake = v, true));
        addRenderableWidget(slider(x2, y, bw, "hud_scale", c.hud_scale, 0.75f, 1.5f, v -> c.hud_scale = v, false));
        y += 24;
        addRenderableWidget(toggle(x1, y, bw, "reduce_flashes", () -> c.reduce_flashes, v -> c.reduce_flashes = v));
        addRenderableWidget(toggle(x2, y, bw, "soft_scares", () -> c.soft_scares, v -> c.soft_scares = v));
        y += 24;
        addRenderableWidget(toggle(x1, y, bw, "scare_warning", () -> c.scare_warning, v -> c.scare_warning = v));
        addRenderableWidget(toggle(x2, y, bw, "qte_assist", () -> c.qte_assist, v -> c.qte_assist = v));
        y += 24;
        addRenderableWidget(toggle(x1, y, bw, "hold_toggle", () -> c.hold_toggle, v -> c.hold_toggle = v));
        addRenderableWidget(toggle(x2, y, bw, "look_back_toggle", () -> c.look_back_toggle, v -> c.look_back_toggle = v));
        y += 24;
        addRenderableWidget(toggle(x1, y, bw, "lean_toggle", () -> c.lean_toggle, v -> c.lean_toggle = v));
        addRenderableWidget(toggle(x2, y, bw, "tension_shader", () -> c.tension_shader, v -> c.tension_shader = v));
        y += 34;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, y, 200, 20).build());
    }

    private static CycleButton<Boolean> toggle(int x, int y, int w, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
        return CycleButton.onOffBuilder(get.get())
                .withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("smileprotocol.access." + key + ".tip")))
                .create(x, y, w, 20, Component.translatable("smileprotocol.access." + key), (b, v) -> set.accept(v));
    }

    private static AbstractSliderButton slider(int x, int y, int w, String key, float value, float min, float max,
                                               Consumer<Float> set, boolean percent) {
        return new AbstractSliderButton(x, y, w, 20, Component.empty(), (value - min) / (max - min)) {
            {
                updateMessage();
            }

            float current() {
                return Mth.lerp((float) this.value, min, max);
            }

            @Override
            protected void updateMessage() {
                String v = percent ? Math.round(current() * 100) + " %" : String.format(java.util.Locale.ROOT, "%.2fx", current());
                setMessage(Component.translatable("smileprotocol.access." + key).append(": " + v));
            }

            @Override
            protected void applyValue() {
                set.accept(current());
            }
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 16, 0xFFE0D6BC);
    }

    @Override
    public void onClose() {
        SPConfig.save();
        if (Minecraft.getInstance().getConnection() != null) {
            SPNetwork.sendToServer(new Payloads.SetAccessibility(SPConfig.get().qte_assist));
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
