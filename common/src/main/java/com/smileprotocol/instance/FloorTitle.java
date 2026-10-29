package com.smileprotocol.instance;

import com.smileprotocol.zone.ZoneCatalog;
import com.smileprotocol.zone.ZoneDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Rotulo al llegar a una planta: «PLANTA -2» y el nombre de la planta (README 6.5). */
public final class FloorTitle {

    private FloorTitle() {}

    public static void show(ServerPlayer p, int index, ResourceLocation zoneId) {
        ZoneDef def = ZoneCatalog.get(p.server, zoneId);
        String name = def != null ? def.name() : zoneId.getPath();
        p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 30));
        p.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("smileprotocol.floor.title", index + 1).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(name).withStyle(ChatFormatting.DARK_AQUA)));
    }
}
