package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Shader de tension (README 21): un {@link PostChain} propio
 * ({@code shaders/post/tension.json}) con aberracion, distorsion, desaturacion, grano y vineta que
 * suben con un smiler cerca, durante un QTE o una secuencia, derribado y con poca vida. Se aplica
 * justo despues de dibujar el mundo (GameRendererMixin), antes de la HUD. Sin tension no se procesa.
 */
public final class ClientTension {

    private static final ResourceLocation CHAIN = SmileProtocol.id("shaders/post/tension.json");
    @Nullable private static PostChain chain;
    private static boolean failed;
    private static int width, height;
    private static float level, prev;

    private ClientTension() {}

    public static float level(float pt) {
        return Mth.lerp(pt, prev, level);
    }

    /** Cada tick: lo que deberia sentirse ahora, con subida rapida y bajada lenta. */
    public static void tick(Minecraft mc) {
        prev = level;
        float target = 0f;
        if (mc.player != null && ClientState.inSmileProtocol() && !ClientState.dead && SPConfig.get().tension_shader) {
            double best = Double.MAX_VALUE;
            for (SPMonster m : mc.level.getEntitiesOfClass(SPMonster.class, mc.player.getBoundingBox().inflate(18))) {
                best = Math.min(best, m.distanceTo(mc.player));
            }
            if (best < 18) target = (float) Mth.clamp(1.0 - (best - 3) / 15.0, 0, 1) * 0.85f;
            if (ClientQTE.active() || ClientSequence.active()) target = 1f;
            if (ClientState.downed) target = Math.max(target, 0.55f);
            float hp = mc.player.getHealth() / mc.player.getMaxHealth();
            if (hp < 0.35f) target = Math.max(target, (0.35f - hp) / 0.35f * 0.5f);
            target *= SPConfig.get().tension_strength;
        }
        level += (target - level) * (target > level ? 0.25f : 0.05f);
        if (level < 0.003f) level = 0f;
    }

    /** Despues del mundo y antes de la HUD. */
    public static void render(float pt) {
        float k = level(pt);
        if (k <= 0.004f || failed) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                width = height = -1;
            }
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            float pulse = (float) Math.pow(Math.max(0, Math.sin((mc.level.getGameTime() + pt) * (0.25 + 0.25 * k))), 6);
            for (PostPass pass : ((PostChainAccessor) chain).smileprotocol$passes()) {
                pass.getEffect().safeGetUniform("Intensity").set(k);
                pass.getEffect().safeGetUniform("Pulse").set(pulse);
            }
            chain.process(pt);
            mc.getMainRenderTarget().bindWrite(false);
        } catch (Exception e) {
            failed = true;
            SmileProtocol.LOGGER.error("Shader de tension desactivado", e);
            close();
        }
    }

    /** Al recargar recursos o salir: se vuelve a crear la proxima vez. */
    public static void close() {
        if (chain != null) chain.close();
        chain = null;
    }

    public static void reset() {
        level = prev = 0f;
        close();
        failed = false;
    }
}
