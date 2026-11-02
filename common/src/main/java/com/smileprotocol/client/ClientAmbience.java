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
    /** Pasos lejanos que se alejan (quedan por sonar) y por donde van. */
    private static int steps;
    private static double[] stepPos = new double[4];

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
            silence = 300 + RNG.nextInt(500);          // silencios mas largos
            nextSilence = 1500 + RNG.nextInt(2000);
        }
        float tension = ClientTension.level(1f);
        drone.target = silence > 0 || ClientBossMusic.active() ? 0f : 0.32f * (1f - 0.6f * tension);

        if (steps > 0 && mc.player.tickCount % 7 == 0) {
            steps--;
            int i = 5 - steps;
            double px = stepPos[0] + Math.cos(stepPos[3]) * i * 1.3, pz = stepPos[2] + Math.sin(stepPos[3]) * i * 1.3;
            mc.level.playLocalSound(px, stepPos[1], pz, net.minecraft.sounds.SoundEvents.STONE_STEP, SoundSource.AMBIENT,
                    0.5f - i * 0.07f, 0.85f, false);
        }
        if (--nextOneShot <= 0) {
            nextOneShot = 400 + RNG.nextInt(800);
            if (silence > 0 && RNG.nextBoolean()) return;
            double a = RNG.nextDouble() * Math.PI * 2, d = 7 + RNG.nextDouble() * 12;
            double x = mc.player.getX() + Math.cos(a) * d, z = mc.player.getZ() + Math.sin(a) * d;
            double y = mc.player.getY() + 1 + RNG.nextDouble() * 2;
            float r = RNG.nextFloat();
            if (r < 0.45f) {
                mc.level.playLocalSound(x, y, z, ModSounds.AMBIENT_CREAK.get(), SoundSource.AMBIENT, 0.7f, Mth.nextFloat(RNG, 0.8f, 1.15f), false);
            } else if (r < 0.7f) {
                mc.level.playLocalSound(x, y, z, ModSounds.AMBIENT_DRIP.get(), SoundSource.AMBIENT, 0.5f, Mth.nextFloat(RNG, 0.8f, 1.15f), false);
            } else {
                // algo lejos que no es ningun monstruo: una puerta que se cierra, pasos que se alejan,
                // un objeto que cae, metal que se asienta
                double far = 18 + RNG.nextDouble() * 12;
                double fx = mc.player.getX() + Math.cos(a) * far, fz = mc.player.getZ() + Math.sin(a) * far;
                int k = RNG.nextInt(4);
                if (k == 0) {
                    mc.level.playLocalSound(fx, y, fz, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, SoundSource.AMBIENT, 0.6f, 0.7f, false);
                } else if (k == 1) {
                    steps = 5;
                    stepPos = new double[]{fx, y, fz, a + 0.4};
                } else if (k == 2) {
                    mc.level.playLocalSound(fx, y, fz, net.minecraft.sounds.SoundEvents.METAL_FALL, SoundSource.AMBIENT, 0.7f, 0.6f, false);
                } else {
                    mc.level.playLocalSound(fx, y, fz, net.minecraft.sounds.SoundEvents.CHAIN_BREAK, SoundSource.AMBIENT, 0.6f, 0.45f, false);
                }
            }
        }
    }

    public static void reset() {
        if (drone != null) drone.fadeOut();
        drone = null;
        silence = 0;
    }
}
