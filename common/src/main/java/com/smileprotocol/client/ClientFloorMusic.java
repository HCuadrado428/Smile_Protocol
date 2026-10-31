package com.smileprotocol.client;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Musica de ambiente de cada planta (tools/floor_music.py): un bucle muy bajo que se funde al cambiar
 * de planta, baja cuando aprieta la tension y se calla mientras suena el jefe.
 */
public final class ClientFloorMusic {

    private static String floor = "";
    @Nullable private static Track track;
    private static String playing = "";

    private ClientFloorMusic() {}

    private static final class Track extends AbstractTickableSoundInstance {
        float target = 1f;

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
            volume += (target - volume) * 0.02f;
            if (target <= 0f && volume < 0.004f) stop();
        }
    }

    public static void setFloor(String zone) {
        floor = zone == null ? "" : zone;
    }

    public static String floor() {
        return floor;
    }

    /** Planta cuya musica suena ahora ("" si ninguna), para las pruebas. */
    public static String playing() {
        return track != null && !track.isStopped() ? playing : "";
    }

    public static void tick(Minecraft mc) {
        float vol = SPConfig.get().floor_music_volume;
        boolean in = mc.player != null && ClientState.inSmileProtocol() && vol > 0 && !floor.isEmpty();
        String want = in && ModSounds.FLOOR_MUSIC.containsKey(floor) ? floor : "";
        if (!want.equals(playing) || (track != null && track.isStopped())) {
            if (track != null) track.target = 0f;
            track = null;
            playing = want;
            if (!want.isEmpty()) {
                Supplier<SoundEvent> ev = ModSounds.FLOOR_MUSIC.get(want);
                track = new Track(ev.get());
                mc.getSoundManager().play(track);
            }
        }
        if (track != null) {
            float tension = ClientTension.level(1f);
            track.target = ClientBossMusic.active() ? 0.02f : vol * (1f - 0.5f * tension);
        }
    }

    public static void reset() {
        if (track != null) track.target = 0f;
        track = null;
        playing = "";
        floor = "";
    }
}
