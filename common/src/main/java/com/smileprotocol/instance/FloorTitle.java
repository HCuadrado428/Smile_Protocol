package com.smileprotocol.instance;

import com.smileprotocol.zone.ZoneCatalog;
import com.smileprotocol.zone.ZoneDef;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Rotulo al llegar a una planta: «PLANTA -2» y el nombre de la planta (README 6.5). */
public final class FloorTitle {

    /** Fuentes de la HUD (assets/smileprotocol/font): garabato de sangre y maquina de escribir. */
    private static final ResourceLocation SCRAWL = ResourceLocation.fromNamespaceAndPath("smileprotocol", "scrawl");
    private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath("smileprotocol", "hud");

    private FloorTitle() {}

    /** Nombre visible de una planta. */
    public static Component zoneName(net.minecraft.server.MinecraftServer server, ResourceLocation zoneId) {
        ZoneDef def = ZoneCatalog.get(server, zoneId);
        return Component.literal(def != null ? def.name() : zoneId.getPath());
    }

    public static void show(ServerPlayer p, int index, ResourceLocation zoneId) {
        ZoneDef def = ZoneCatalog.get(p.server, zoneId);
        String name = def != null ? def.name() : zoneId.getPath();
        p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 30));
        p.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("smileprotocol.floor.title", index + 1)
                        .withStyle(s -> s.withFont(SCRAWL).withColor(0x9A1414))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(name)
                .withStyle(s -> s.withFont(HUD).withColor(0xC9BFA6))));
    }
}
