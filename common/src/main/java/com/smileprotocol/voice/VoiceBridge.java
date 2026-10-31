package com.smileprotocol.voice;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Lo que el mod necesita de Simple Voice Chat (README 7.4, 17.2). Lo implementa el plugin
 * {@link SPVoicePlugin}, el unico sitio que toca la API de voz: si el mod de voz no esta
 * instalado nunca se carga y {@link VoiceManager} funciona sin el.
 */
public interface VoiceBridge {

    /** Mete al jugador en el grupo de voz de espectadores de su partida, o lo saca (null). */
    void setSpectatorGroup(ServerPlayer player, @Nullable String runId);

    /** Partida del grupo de espectadores en el que esta (null si no esta en ninguno nuestro). */
    @Nullable String spectatorRun(ServerPlayer player);

    /** Fin de una transmision de walkie: cierra los canales de radio del emisor. */
    void walkieEnd(java.util.UUID sender);

    /** Tiene el mod de voz instalado y conectado. */
    boolean connected(ServerPlayer player);

    /**
     * El Imitador: reproduce en {@code pos} una frase grabada (paquetes opus de 20 ms), deformada (algo mas
     * grave y entrecortada), audible a {@code distance} bloques. Devuelve false si no se pudo.
     */
    boolean playMimic(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos, java.util.List<byte[]> clip,
                      float distance);
}
