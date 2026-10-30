package com.smileprotocol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.marker.MarkerManager;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marcadores de equipo en el cliente (README 8.4): R pulsada pone uno de atencion; mantenida abre
 * la rueda (peligro arriba, ir alli a la derecha, despejado abajo, necesito ayuda a la izquierda) y
 * se elige moviendo el raton. Se dibujan con su forma y el color de quien los puso, con la
 * distancia, a traves de las paredes y pegados al borde si quedan fuera de la pantalla.
 */
public final class ClientMarkers {

    public record Marker(int id, int kind, Vec3 pos, int color, String name, boolean self, long until, int duration) {}

    private static final ResourceLocation[] ICONS = {
            SmileProtocol.id("hud/marker_attention"), SmileProtocol.id("hud/marker_danger"), SmileProtocol.id("hud/marker_go"),
            SmileProtocol.id("hud/marker_help"), SmileProtocol.id("hud/marker_clear")};
    /** Orden de la rueda: arriba, derecha, abajo, izquierda. */
    private static final int[] WHEEL = {MarkerManager.DANGER, MarkerManager.GO, MarkerManager.CLEAR, MarkerManager.HELP};
    private static final int HOLD_TICKS = 6;

    private static List<Marker> markers = List.of();
    private static final Set<Integer> known = new HashSet<>();
    private static boolean prevDown;
    private static int held;
    private static boolean wheel;
    private static float yaw0, pitch0, accX, accY;
    private static int choice = -1;
    private static long tick;

    private ClientMarkers() {}

    public static ResourceLocation icon(int kind) {
        return ICONS[Mth.clamp(kind, 0, ICONS.length - 1)];
    }

    public static List<Marker> markers() {
        return markers;
    }

    public static boolean wheelOpen() {
        return wheel;
    }

    public static void reset() {
        markers = List.of();
        known.clear();
        wheel = false;
        prevDown = false;
        held = 0;
    }

    public static void onSync(Payloads.SyncMarkers p) {
        Minecraft mc = Minecraft.getInstance();
        List<Marker> out = new ArrayList<>();
        ListTag list = p.data().getList("m", Tag.TAG_COMPOUND);
        boolean ping = false;
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int id = t.getInt("id");
            boolean self = mc.player != null && t.getUUID("o").equals(mc.player.getUUID());
            out.add(new Marker(id, t.getInt("k"), new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")),
                    t.getInt("c"), t.getString("n"), self, tick + t.getInt("left"), Math.max(1, t.getInt("dur"))));
            ids.add(id);
            if (!known.contains(id) && !self) ping = true;
        }
        known.clear();
        known.addAll(ids);
        markers = out;
        if (ping) mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_MARKER.get(), 1f, 0.6f));
    }

    public static void tick(Minecraft mc) {
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || !ClientState.inSmileProtocol() || ClientState.dead || mc.screen != null) {
            if (wheel) closeWheel(p, false);
            prevDown = false;
            return;
        }
        boolean down = ClientMotion.isDown(SPConfig.get().key_marker);
        if (down) {
            held++;
            if (!wheel && held >= HOLD_TICKS && !ClientState.downed) {
                wheel = true;
                yaw0 = p.getYRot();
                pitch0 = p.getXRot();
                accX = accY = 0;
                choice = -1;
            }
            if (wheel) {
                // el raton elige en la rueda: la vista se queda quieta
                accX = Mth.clamp(accX + Mth.wrapDegrees(p.getYRot() - yaw0), -30, 30);
                accY = Mth.clamp(accY + (p.getXRot() - pitch0), -30, 30);
                p.setYRot(yaw0);
                p.setXRot(pitch0);
                p.yRotO = yaw0;
                p.xRotO = pitch0;
                if (Math.hypot(accX, accY) > 5) {
                    double a = Math.toDegrees(Math.atan2(accX, -accY));          // 0 = arriba, 90 = derecha
                    choice = WHEEL[Math.floorMod((int) Math.round(a / 90.0), 4)];
                }
            }
        } else if (prevDown) {
            if (wheel) {
                closeWheel(p, true);
            } else {
                SPNetwork.sendToServer(new Payloads.PlaceMarker(ClientState.downed ? MarkerManager.HELP : MarkerManager.ATTENTION));
            }
            held = 0;
        }
        prevDown = down;
    }

    private static void closeWheel(LocalPlayer p, boolean place) {
        wheel = false;
        held = 0;
        if (place && choice >= 0) SPNetwork.sendToServer(new Payloads.PlaceMarker(choice));
        choice = -1;
    }

    // Dibujo ------------------------------------------------------------------------------------------

    public static void render(GuiGraphics g, Font font, int w, int h, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double fov = Math.toRadians(mc.options.fov().get());
        double f = 1.0 / Math.tan(fov / 2);
        for (Marker m : markers) {
            long left_ = m.until - tick;
            if (left_ <= 0) continue;
            Vec3 rel = m.pos.subtract(eye);
            double x = rel.x * left.x() + rel.y * left.y() + rel.z * left.z();
            double y = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
            double z = rel.x * look.x() + rel.y * look.y() + rel.z * look.z();
            float sx, sy;
            boolean edge = false;
            if (z > 0.1) {
                sx = (float) (w / 2.0 - x / z * f * h / 2.0);
                sy = (float) (h / 2.0 - y / z * f * h / 2.0);
            } else {
                // detras: al borde de abajo, del lado en que queda
                sx = (float) (w / 2.0 - Math.signum(x) * w * 0.45);
                sy = h - 40;
                edge = true;
            }
            int margin = 14;
            if (sx < margin || sx > w - margin || sy < margin || sy > h - 40) edge = true;
            sx = Mth.clamp(sx, margin, w - margin);
            sy = Mth.clamp(sy, margin, h - 40);
            double dist = rel.length();
            float fade = Mth.clamp(left_ / 40f, 0f, 1f);
            float pop = Mth.clamp((m.duration - left_ + pt) / 6f, 0f, 1f);
            float alpha = fade * (edge ? 0.75f : 1f);
            int size = (int) ((edge ? 12 : 16) * (0.6f + 0.4f * pop) * (m.kind == MarkerManager.HELP && (tick / 6) % 2 == 0 ? 1.15f : 1f));
            int mc0 = display(m.color);
            float r = ((mc0 >> 16) & 255) / 255f, gg = ((mc0 >> 8) & 255) / 255f, b = (mc0 & 255) / 255f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r, gg, b, alpha);
            g.blitSprite(icon(m.kind), (int) sx - size / 2, (int) sy - size / 2, size, size);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            int a = (int) (alpha * 255) << 24;
            String d = Math.round(dist) + "m";
            Component dc = HudOverlay.hud(d);
            g.drawString(font, dc, (int) sx - font.width(dc) / 2, (int) sy + size / 2 + 1, a | 0xC9BFA6, true);
            if (!m.self && !edge) {
                Component nc = HudOverlay.hud(m.name);
                g.drawString(font, nc, (int) sx - font.width(nc) / 2, (int) sy - size / 2 - 10, a | (display(m.color) & 0xFFFFFF), true);
            }
        }
        if (wheel) renderWheel(g, font, w, h);
    }

    private static void renderWheel(GuiGraphics g, Font font, int w, int h) {
        int cx = w / 2, cy = h / 2, rad = 34;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x80C9BFA6);
        int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int kind = WHEEL[i];
            boolean sel = kind == choice;
            int size = sel ? 24 : 16;
            int x = cx + off[i][0] * rad, y = cy + off[i][1] * rad;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(sel ? 1f : 0.75f, sel ? 0.9f : 0.72f, sel ? 0.8f : 0.66f, sel ? 1f : 0.7f);
            g.blitSprite(icon(kind), x - size / 2, y - size / 2, size, size);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (sel) {
                Component label = HudOverlay.hud(Component.translatable("smileprotocol.marker." + MarkerManager.NAMES[kind]));
                int ly = off[i][1] > 0 ? y + size / 2 + 3 : off[i][1] < 0 ? y - size / 2 - 11 : y + size / 2 + 3;
                g.drawString(font, label, x - font.width(label) / 2, ly, 0xFFE0D6BC, true);
            }
        }
    }

    /** Daltonismo (README 24.2): el color de cada companero se cambia por uno de una paleta distinguible. */
    public static int display(int color) {
        String mode = com.smileprotocol.config.SPConfig.get().colorblind;
        if (mode == null || mode.equals("OFF")) return color;
        int[] base = com.smileprotocol.marker.MarkerManager.COLORS;
        int[] pal = mode.equals("TRITANOPIA")
                ? new int[]{0xFF6B6B, 0x6BE0FF, 0xE8E8E8, 0xFF9EC8, 0x2EC4B6, 0xB0B0B0, 0xFF3030, 0x8888FF}
                : new int[]{0xF0E442, 0x56B4E9, 0xE69F00, 0x0072B2, 0xCC79A7, 0xD55E00, 0xFFFFFF, 0xAAAAAA};
        for (int i = 0; i < base.length; i++) if ((base[i] & 0xFFFFFF) == (color & 0xFFFFFF)) return pal[i % pal.length];
        return color;
    }
}
