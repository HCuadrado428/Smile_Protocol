package com.smileprotocol.client;

import com.smileprotocol.entity.Ahorcado;
import com.smileprotocol.entity.SPMonster;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

/**
 * Tu cuerpo nota que hay algo cerca antes que tu (docs/MIEDO.md): un latido que se acelera y, muy
 * cerca, la respiracion contenida. No dice donde esta: solo que esta. Calla durante agarres,
 * secuencias y con sustos suaves (accesibilidad) baja de volumen.
 */
public final class ClientFear {

    private static int beatIn, breathIn;

    private ClientFear() {}

    /** Distancia al monstruo de verdad mas cercano (los munecos y lo invisible no cuentan... salvo el Apagon). */
    public static double nearest(Minecraft mc, double radius) {
        double best = Double.MAX_VALUE;
        for (SPMonster m : mc.level.getEntitiesOfClass(SPMonster.class, mc.player.getBoundingBox().inflate(radius))) {
            if (!m.isAlive() || m instanceof Ahorcado a && a.isDecoy()) continue;
            best = Math.min(best, m.distanceTo(mc.player));
        }
        return best;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !ClientState.inSmileProtocol() || ClientState.dead
                || ClientQTE.active() || ClientSequence.active()) {
            beatIn = breathIn = 0;
            return;
        }
        double d = nearest(mc, 14);
        if (d > 14) {
            beatIn = Math.min(beatIn, 10);
            return;
        }
        float k = (float) Mth.clamp(1 - (d - 3) / 11, 0, 1);     // 0 lejos, 1 encima
        float vol = com.smileprotocol.config.SPConfig.get().soft_scares ? 0.5f : 1f;
        if (--beatIn <= 0) {
            beatIn = Math.round(Mth.lerp(k, 24, 9));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PLAYER_HEARTBEAT.get(), Mth.lerp(k, 0.9f, 1.15f),
                    Mth.lerp(k, 0.25f, 0.8f) * vol));
        }
        if (d < 8 && !mc.player.isSprinting() && --breathIn <= 0) {
            breathIn = 55 + mc.level.random.nextInt(30);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PLAYER_PANT.get(), 0.8f, 0.25f * vol));
        }
    }
}
