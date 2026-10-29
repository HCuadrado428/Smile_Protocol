package com.smileprotocol.fabric;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.command.SPCommands;
import com.smileprotocol.event.SPEvents;
import com.smileprotocol.network.SPNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Punto de entrada en Fabric: registros, red y ciclo de vida hacia el codigo comun. */
public final class SmileProtocolFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SmileProtocol.init();
        // Fuerza el registro del attachment de los datos del jugador
        FabricPlatform.PLAYER_DATA.identifier();

        for (SPNetwork.Def<?> def : SPNetwork.DEFINITIONS) registerPayload(def);

        ServerLifecycleEvents.SERVER_STARTING.register(SPEvents::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(SPEvents::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SPEvents.onLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPEvents.onLogout(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                SPEvents.onChangedDimension(player, destination.dimension()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SPEvents.onRespawn(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(SPEvents::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, env) -> SPCommands.register(dispatcher));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void registerPayload(SPNetwork.Def<T> def) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = def.codec();
        if (def.toClient()) {
            PayloadTypeRegistry.playS2C().register(def.type(), codec);
        } else {
            PayloadTypeRegistry.playC2S().register(def.type(), codec);
            ServerPlayNetworking.registerGlobalReceiver(def.type(), (payload, ctx) -> def.serverHandler().accept(payload, ctx.player()));
        }
    }
}
