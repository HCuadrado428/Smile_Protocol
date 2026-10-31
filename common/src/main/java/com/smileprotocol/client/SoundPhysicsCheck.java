package com.smileprotocol.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Compatibilidad con Sound Physics Remastered: al entrar, mira su configuracion y avisa si esta apagado
 * o si no procesa la voz (el eco de las salas y las voces que se oyen ahogadas tras las paredes forman
 * parte del juego). Los ajustes recomendados estan en {@code docs/sound_physics/soundphysics.properties}.
 */
public final class SoundPhysicsCheck {

    private static boolean done;

    private SoundPhysicsCheck() {}

    public static void reset() {
        done = false;
    }

    public static void tick(Minecraft mc) {
        if (done || mc.player == null) return;
        done = true;
        Path file = mc.gameDirectory.toPath().resolve("config/sound_physics_remastered/soundphysics.properties");
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
        } catch (Exception e) {
            return;
        }
        if ("false".equalsIgnoreCase(p.getProperty("enabled", "true").trim())) {
            mc.player.displayClientMessage(Component.translatable("smileprotocol.soundphysics.disabled").withStyle(ChatFormatting.GOLD), false);
        } else if ("false".equalsIgnoreCase(p.getProperty("simple_voice_chat_integration", "true").trim())) {
            mc.player.displayClientMessage(Component.translatable("smileprotocol.soundphysics.voice").withStyle(ChatFormatting.GOLD), false);
        }
    }
}
