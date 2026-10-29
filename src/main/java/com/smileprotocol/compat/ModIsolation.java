package com.smileprotocol.compat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.registry.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.List;

/**
 * Capa 3 del aislamiento, parte de servidor (README 7.3): entidades y comandos de otros mods
 * dentro de la partida. La parte de cliente (HUD, pantallas, teclas) esta en
 * {@code client.ClientIsolation}.
 */
public final class ModIsolation {

    private ModIsolation() {}

    public static boolean isOwn(ResourceLocation id) {
        return id != null && SmileProtocol.MODID.equals(id.getNamespace());
    }

    /** En la dimension de instancias solo entran jugadores y entidades del mod. */
    public static boolean blockEntityJoin(EntityJoinLevelEvent event) {
        if (!SPDimension.isSP(event.getLevel())) return false;
        Entity e = event.getEntity();
        if (e instanceof Player) return false;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return !isOwn(type);
    }

    /** Dentro de la partida, un jugador sin permisos solo puede usar los comandos permitidos. */
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (!ModCapabilities.inSP(player) || source.hasPermission(2)) return;

        String root = rootName(parse.getContext());
        List<? extends String> allowed = SPConfig.PLAYER_COMMANDS.get();
        if (root == null || !allowed.contains(root)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("smileprotocol.isolation.command_blocked")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static String rootName(CommandContextBuilder<CommandSourceStack> ctx) {
        List<ParsedCommandNode<CommandSourceStack>> nodes = ctx.getNodes();
        if (nodes.isEmpty()) return null;
        return nodes.get(0).getNode().getName();
    }
}
