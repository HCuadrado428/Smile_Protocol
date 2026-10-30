package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

/**
 * Ambiente del laboratorio (README 21): zumbido en bucle que se apaga a ratos (silencios
 * deliberados) y baja cuando aprieta la tension, y crujidos y goteos sueltos a unos metros, por
 * los que Sound Physics Remastered pone el eco de cada sala.
 */
public final class ClientAmbience {

    private static final RandomSource RNG = RandomSource.create();
    @Nullable private static Drone drone;
    private static int nextOneShot = 200;
    private static int silence;
    private static int nextSilence = 1800;

    private ClientAmbience() {}

    private static final class Drone extends AbstractTickableSoundInstance {
        private float target;

        Drone() {
            super(ModSounds.AMBIENT_DRONE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.001f;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.03f;
            if (target <= 0f && volume < 0.005f) stop();
        }

        void fadeOut() {
            target = 0f;
        }
    }

    public static void tick(Minecraft mc) {
        boolean in = mc.player != null && mc.level != null && ClientState.inSmileProtocol() && SPConfig.get().ambience;
        if (!in) {
            if (drone != null) drone.fadeOut();
            drone = null;
            return;
        }
        if (drone == null || drone.isStopped()) {
            drone = new Drone();
            mc.getSoundManager().play(drone);
        }
        // silencios: de vez en cuando el zumbido se va unos segundos
        if (silence > 0) silence--;
        else if (--nextSilence <= 0) {
            silence = 200 + RNG.nextInt(300);
            nextSilence = 1800 + RNG.nextInt(2400);
        }
        float tension = ClientTension.level(1f);
        drone.target = silence > 0 ? 0f : 0.32f * (1f - 0.6f * tension);

        if (--nextOneShot <= 0) {
            nextOneShot = 400 + RNG.nextInt(800);
            if (silence > 0 && RNG.nextBoolean()) return;
            double a = RNG.nextDouble() * Math.PI * 2, d = 7 + RNG.nextDouble() * 12;
            double x = mc.player.getX() + Math.cos(a) * d, z = mc.player.getZ() + Math.sin(a) * d;
            double y = mc.player.getY() + 1 + RNG.nextDouble() * 2;
            boolean creak = RNG.nextFloat() < 0.6f;
            mc.level.playLocalSound(x, y, z, creak ? ModSounds.AMBIENT_CREAK.get() : ModSounds.AMBIENT_DRIP.get(),
                    SoundSource.AMBIENT, creak ? 0.7f : 0.5f, Mth.nextFloat(RNG, 0.8f, 1.15f), false);
        }
    }

    public static void reset() {
        if (drone != null) drone.fadeOut();
        drone = null;
        silence = 0;
    }
}
