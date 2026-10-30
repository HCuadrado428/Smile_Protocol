package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Subtitulos de sonido (accesibilidad, README 24.2): los ruidos importantes de la partida (pasos de
 * los mobs, sus gritos y susurros, puertas, eventos, el walkie...) salen como texto corto con una
 * flecha que apunta hacia donde suenan, a la derecha de la pantalla. Solo los sonidos del mod y las
 * puertas; se quita con {@code subtitles: false}.
 */
public final class ClientCaptions implements SoundEventListener {

    private record Caption(Component text, @Nullable Vec3 pos, long at) {}

    private static final List<Caption> LIST = new ArrayList<>();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static boolean registered;

    private ClientCaptions() {}

    public static void register(Minecraft mc) {
        if (registered) return;
        registered = true;
        mc.getSoundManager().addListener(new ClientCaptions());
    }

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents events, float range) {
        if (!SPConfig.get().subtitles || !ClientState.inSmileProtocol() || events.getSubtitle() == null) return;
        var id = sound.getLocation();
        if (!id.getNamespace().equals("smileprotocol") && !id.getPath().contains("door")) return;
        if (id.getPath().startsWith("music") || id.getPath().startsWith("ambient")) return;
        Vec3 pos = sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE ? null
                : new Vec3(sound.getX(), sound.getY(), sound.getZ());
        long now = Util.getMillis();
        LIST.removeIf(c -> c.text().equals(events.getSubtitle()));
        LIST.add(new Caption(events.getSubtitle(), pos, now));
        while (LIST.size() > 6) LIST.remove(0);
    }

    public static void reset() {
        LIST.clear();
    }

    public static void render(GuiGraphics g, Font font, int w, int h) {
        if (LIST.isEmpty() || !SPConfig.get().subtitles) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = Util.getMillis();
        Iterator<Caption> it = LIST.iterator();
        while (it.hasNext()) if (now - it.next().at() > 3000) it.remove();
        int y = h - 70;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = mc.player.getYRot();
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Caption c = LIST.get(i);
            float age = (now - c.at()) / 3000f;
            int a = (int) (Mth.clamp(1.4f - age * 1.4f, 0f, 1f) * 220);
            if (a < 8) continue;
            String arrow = "•";
            if (c.pos() != null) {
                double dx = c.pos().x - eye.x, dz = c.pos().z - eye.z;
                if (dx * dx + dz * dz > 1.5) {
                    float ang = (float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
                    int k = Math.floorMod(Math.round(Mth.wrapDegrees(ang) / 45f), 8);
                    arrow = ARROWS[k];
                }
            }
            Component line = Component.literal(arrow + " ").append(c.text());
            int lw = font.width(line);
            int x = w - lw - 10;
            g.fill(x - 3, y - 2, x + lw + 3, y + 10, (a / 2) << 24);
            g.drawString(font, line, x, y, (a << 24) | 0xE0D6BC, false);
            y -= 13;
        }
    }
}
