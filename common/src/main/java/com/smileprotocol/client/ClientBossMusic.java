package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.entity.Smiley;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;

/**
 * Musica del jefe (docs/SMILERS.md): mientras haya un Smiley cerca peleando, suena el bucle de su
 * fase; al cambiar de fase se funde con el siguiente, y al encerrarlo (o salir de la sala) se apaga.
 * Las apariciones de lejos en las plantas no tienen musica.
 */
public final class ClientBossMusic {

    @Nullable private static Track track;
    private static int phase;

    private ClientBossMusic() {}

    private static final class Track extends AbstractTickableSoundInstance {
        private float target = 1f;

        Track(SoundEvent ev) {
            super(ev, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.001f;
        }

        @Override
        public void tick() {
            volume += (target * SPConfig.get().boss_music_volume - volume) * 0.04f;
            if (target <= 0f && volume < 0.005f) stop();
        }
    }

    /** Fase que esta sonando (0 = nada), para las pruebas. */
    public static int playing() {
        return track != null && !track.isStopped() ? phase : 0;
    }

    public static boolean active() {
        return playing() > 0;
    }

    public static void tick(Minecraft mc) {
        int want = 0;
        if (mc.player != null && mc.level != null && ClientState.inSmileProtocol() && SPConfig.get().boss_music_volume > 0) {
            for (Smiley s : mc.level.getEntitiesOfClass(Smiley.class, mc.player.getBoundingBox().inflate(80))) {
                Smiley.State st = s.state();
                if (st == Smiley.State.CAMEO || st == Smiley.State.SEALED || s.phase() <= 0) continue;
                want = Math.max(want, Math.min(3, s.phase()));
            }
        }
        if (want == phase && (want == 0 || (track != null && !track.isStopped()))) return;
        if (track != null) track.target = 0f;
        track = null;
        phase = want;
        if (want > 0) {
            SoundEvent ev = switch (want) {
                case 1 -> ModSounds.MUSIC_SMILEY_1.get();
                case 2 -> ModSounds.MUSIC_SMILEY_2.get();
                default -> ModSounds.MUSIC_SMILEY_3.get();
            };
            track = new Track(ev);
            mc.getSoundManager().play(track);
        }
    }
}
