package com.smileprotocol.neoforge;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.command.SPCommands;
import com.smileprotocol.event.SPEvents;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Punto de entrada en NeoForge: registros, red y ciclo de vida hacia el codigo comun. */
@Mod(SmileProtocol.MODID)
public final class SmileProtocolNeoForge {

    public SmileProtocolNeoForge(IEventBus modBus) {
        SmileProtocol.init();
        NeoForgePlatform.registerAll(modBus);
        modBus.addListener(SmileProtocolNeoForge::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> SPEvents.onServerStarting(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SPEvents.onServerStopped(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) SPEvents.onLogin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) SPEvents.onLogout(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) SPEvents.onChangedDimension(p, e.getTo());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) SPEvents.onRespawn(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SPEvents.onServerTick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> SPCommands.register(e.getDispatcher()));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (SPNetwork.Def<?> def : SPNetwork.DEFINITIONS) register(registrar, def);
    }

    private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, SPNetwork.Def<T> def) {
        if (def.toClient()) {
            registrar.playToClient(def.type(), def.codec(), (payload, ctx) -> SPNetwork.handleClient(payload));
        } else {
            registrar.playToServer(def.type(), def.codec(), (payload, ctx) -> {
                if (ctx.player() instanceof ServerPlayer p) def.serverHandler().accept(payload, p);
            });
        }
    }
}
