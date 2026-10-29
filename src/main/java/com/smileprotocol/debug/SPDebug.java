package com.smileprotocol.debug;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.lobby.LobbyData;
import com.smileprotocol.lobby.LobbyManager;
import com.smileprotocol.network.DebugDataPacket;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModCapabilities;
import com.smileprotocol.compat.ModCheck;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Modo depuracion basico (README 24.1). Solo para operadores: los datos solo se envian a
 * quien lo activa. Ademas escribe un registro {@code sp-debug.log} en la carpeta del servidor.
 */
public final class SPDebug {

    private static final Set<UUID> ENABLED = new HashSet<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SPDebug() {}

    public static boolean isEnabled(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    public static void setEnabled(ServerPlayer player, boolean on) {
        if (on) ENABLED.add(player.getUUID());
        else {
            ENABLED.remove(player.getUUID());
            SPNetwork.sendTo(player, new DebugDataPacket(List.of()));
        }
    }

    /** Cada segundo: envia las lineas de depuracion a los operadores que lo tienen activo. */
    public static void tick(MinecraftServer server) {
        if (ENABLED.isEmpty()) return;
        for (UUID id : Set.copyOf(ENABLED)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (!p.hasPermissions(2)) {
                setEnabled(p, false);
                continue;
            }
            SPNetwork.sendTo(p, new DebugDataPacket(lines(server, p)));
        }
    }

    private static List<String> lines(MinecraftServer server, ServerPlayer p) {
        List<String> l = new ArrayList<>();
        SPPlayerData d = ModCapabilities.get(p);
        l.add("§cSmile Protocol §7(debug)");
        l.add("inSP: " + d.inSP() + "  guardado: " + d.hasSaved());
        l.add("dimension: " + p.level().dimension().location() + (SPDimension.isSP(p.level()) ? " §a(instancias)" : ""));
        l.add("grupo: " + (d.getGroupId() == null ? "-" : d.getGroupId().toString().substring(0, 8)));
        l.add("hueco activo: " + (d.getSPInventory().getSelected() + 1));
        l.add("mods: " + ModCheck.describe(p));
        LobbyData lobby = LobbyData.get(server);
        l.add("lobby: modo " + lobby.getMode() + (lobby.inRegion(p.position()) ? " §a(dentro)" : ""));
        l.addAll(LobbyManager.debugLines(server));
        return l;
    }

    /** Escribe una linea en sp-debug.log. */
    public static void log(String message) {
        String line = "[" + LocalDateTime.now().format(TIME) + "] " + message + System.lineSeparator();
        Path file = FMLPaths.GAMEDIR.get().resolve("sp-debug.log");
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SmileProtocol.LOGGER.debug("No se pudo escribir sp-debug.log", e);
        }
    }
}
