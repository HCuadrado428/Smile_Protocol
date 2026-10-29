package com.smileprotocol.compat;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Capa 3 del aislamiento, parte de servidor (README 7.3): entidades y comandos de otros mods
 * dentro de la partida. La parte de cliente esta en {@code client.ClientIsolation}.
 */
public final class ModIsolation {

    private ModIsolation() {}

    public static boolean isOwn(ResourceLocation id) {
        return id != null && SmileProtocol.MODID.equals(id.getNamespace());
    }

    /** En la dimension de instancias solo entran jugadores y entidades del mod. */
    public static boolean blocksEntity(ServerLevel level, Entity e) {
        if (!SPDimension.isSP(level) || e instanceof Player) return false;
        return !isOwn(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /** Dentro de la partida, un jugador sin permisos solo puede usar los comandos permitidos. */
    public static boolean blocksCommand(CommandSourceStack source, String root) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        if (!SPPlayerData.inSP(player) || source.hasPermission(2)) return false;
        if (root != null && SPConfig.get().player_commands.contains(root)) return false;
        player.sendSystemMessage(Component.translatable("smileprotocol.isolation.command_blocked").withStyle(ChatFormatting.RED));
        return true;
    }
}
