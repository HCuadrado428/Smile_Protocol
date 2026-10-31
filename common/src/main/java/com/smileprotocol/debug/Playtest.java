package com.smileprotocol.debug;

import com.smileprotocol.ServerRef;
import com.smileprotocol.SmileProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sesion de prueba con jugadores (docs/PLAYTEST.md): mientras esta activa, todo lo que se apunta en el
 * registro de depuracion va tambien a {@code smileprotocol-playtest/<fecha>.log}, junto con las notas de
 * los jugadores ({@code /sp nota}) y, al terminar, un resumen con lo que ha pasado (partidas, derribos por
 * smiler, muertes, grupos que escapan...).
 */
public final class Playtest {

    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static Path file;
    private static long startedAt;
    private static final Map<String, Integer> COUNTS = new TreeMap<>();
    private static int notes;

    private Playtest() {}

    public static boolean active() {
        return file != null;
    }

    public static Path start(MinecraftServer server, String by) {
        if (file != null) stop(server);
        Path dir = server.getServerDirectory().resolve("smileprotocol-playtest");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            SmileProtocol.LOGGER.warn("Playtest: no se pudo crear la carpeta", e);
        }
        file = dir.resolve(LocalDateTime.now().format(FILE) + ".log");
        startedAt = System.currentTimeMillis();
        COUNTS.clear();
        notes = 0;
        write("=== Sesion de prueba de Smile Protocol, empezada por " + by + " ===");
        write("Jugadores: " + String.join(", ", server.getPlayerList().getPlayerNamesArray()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            write("  " + p.getGameProfile().getName() + ": mods " + com.smileprotocol.compat.ModCheck.describe(p));
        }
        return file;
    }

    /** Termina y escribe el resumen. Devuelve el fichero (o null si no habia sesion). */
    public static Path stop(MinecraftServer server) {
        if (file == null) return null;
        long mins = (System.currentTimeMillis() - startedAt) / 60000;
        write("=== Resumen (" + mins + " min, " + notes + " notas) ===");
        COUNTS.forEach((k, v) -> write("  " + k + ": " + v));
        Path f = file;
        file = null;
        return f;
    }

    /** Suma uno a un contador del resumen ("derribo por berserker", "grupo escapado"...). */
    public static void count(String what) {
        if (file == null) return;
        COUNTS.merge(what, 1, Integer::sum);
    }

    /** Nota de un jugador: con su posicion, su planta y lo que lleva. */
    public static void note(ServerPlayer p, String text) {
        if (file == null) return;
        notes++;
        var g = com.smileprotocol.run.RunManager.groupOf(p);
        String zone = g == null || g.current() == null ? "lobby" : g.current().zoneId().getPath() + " (planta " + (g.zoneIndex() + 1) + ")";
        write("NOTA de " + p.getGameProfile().getName() + " en " + zone + " " + p.blockPosition().toShortString() + ": " + text);
    }

    /** Lo llama SPDebug.log: todo lo que pasa, con hora. */
    static void record(String line) {
        if (file != null) write(line);
    }

    private static synchronized void write(String line) {
        if (file == null) return;
        try {
            Files.writeString(file, "[" + LocalDateTime.now().format(TIME) + "] " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SmileProtocol.LOGGER.debug("Playtest: no se pudo escribir", e);
        }
    }

    static MinecraftServer server() {
        return ServerRef.get();
    }
}
