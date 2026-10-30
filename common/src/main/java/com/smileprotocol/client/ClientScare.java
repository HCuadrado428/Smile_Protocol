package com.smileprotocol.client;

import com.smileprotocol.labevent.LabActions;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

/**
 * Sustos locales (README 14.7): la cara de Smiley que aparece un instante a pantalla completa con un
 * golpe de sonido, un destello, una sacudida de camara o solo el sonido (precedido de un silencio
 * breve). Con "sustos suaves" (accesibilidad, fase 22) se rebajaran.
 */
public final class ClientScare {

    private static int kind = -1;
    private static int age;
    private static float strength;
    private static long lastFlash;

    private ClientScare() {}

    public static void play(Payloads.PlayJumpscare p) {
        kind = p.kind();
        strength = p.strength();
        age = 0;
        Minecraft mc = Minecraft.getInstance();
        // Accesibilidad: con sustos suaves, menos volumen; sin destellos, como mucho un fogonazo cada 3 s
        var cfg = com.smileprotocol.config.SPConfig.get();
        float vol = cfg.soft_scares ? 0.35f : 1f;
        long now = net.minecraft.Util.getMillis();
        if (cfg.reduce_flashes && (kind == LabActions.SCARE_FLASH || kind == LabActions.SCARE_FACE) && now - lastFlash < 3000) {
            kind = LabActions.SCARE_AUDIO;
        }
        if (kind == LabActions.SCARE_FLASH || kind == LabActions.SCARE_FACE) lastFlash = now;
        if (kind == LabActions.SCARE_FACE || kind == LabActions.SCARE_AUDIO) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EVENT_STING.get(), 1.4f, vol));
        } else if (kind == LabActions.SCARE_FLASH) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EVENT_SURGE.get(), 0.8f, vol));
        }
    }

    public static void tick() {
        if (kind < 0) return;
        if (++age > 40) kind = -1;
    }

    public static void reset() {
        kind = -1;
    }

    /** Cara a pantalla completa: 0-1 (aparece de golpe, tiembla y se va en medio segundo). */
    public static float face(float pt) {
        if (kind != LabActions.SCARE_FACE) return 0f;
        float t = age + pt;
        return t < 2 ? 1f : Mth.clamp(1f - (t - 9f) / 5f, 0f, 1f);
    }

    public static float faceScale(float pt) {
        float t = age + pt;
        return 1.05f + 0.25f * Mth.clamp(t / 14f, 0f, 1f);
    }

    public static float flash(float pt) {
        if (kind != LabActions.SCARE_FLASH && kind != LabActions.SCARE_FACE) return 0f;
        float t = age + pt;
        return kind == LabActions.SCARE_FACE ? Mth.clamp(1f - t / 3f, 0f, 1f) * 0.5f
                : Mth.clamp(1f - t / 10f, 0f, 1f) * (0.6f + 0.4f * Mth.sin(t * 2.5f)) * strength;
    }

    /** Grados de sacudida de la camara. */
    public static float shake(float pt) {
        if (kind < 0 || kind == LabActions.SCARE_AUDIO) return 0f;
        float t = age + pt;
        float k = Mth.clamp(1f - t / (kind == LabActions.SCARE_SHAKE ? 30f : 12f), 0f, 1f) * strength;
        return k * (kind == LabActions.SCARE_SHAKE ? 3.5f : 5f) * com.smileprotocol.config.SPConfig.get().camera_shake;
    }

    public static float time(float pt) {
        return age + pt;
    }
}
