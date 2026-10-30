package com.smileprotocol.fabric.client;

import com.smileprotocol.client.ClientController;
import com.smileprotocol.client.ElevatorBoardRenderer;
import com.smileprotocol.client.ElevatorDoorRenderer;
import com.smileprotocol.client.HudOverlay;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.platform.Services;
import com.smileprotocol.registry.ModBlockEntities;
import com.smileprotocol.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Punto de entrada de cliente en Fabric. */
public final class SmileProtocolFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientController.init();
        for (SPNetwork.Def<?> def : SPNetwork.DEFINITIONS) {
            if (def.toClient()) registerReceiver(def);
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientController.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientController.onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(ClientController::tick);
        // Animaciones del jugador (README 13): solo con PlayerAnimator instalado
        if (Services.PLATFORM.isModLoaded("playeranimator") || Services.PLATFORM.isModLoaded("player-animator")) {
            PalBridge.init();
        }
        HudRenderCallback.EVENT.register((graphics, delta) ->
                HudOverlay.INSTANCE.render(graphics, delta.getGameTimeDeltaPartialTick(true)));

        BlockEntityRenderers.register(ModBlockEntities.ELEVATOR_BOARD.get(), ElevatorBoardRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.ELEVATOR_DOOR.get(), ElevatorDoorRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.VALVE.get(), com.smileprotocol.client.SPGeoRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.FUSE_BOX.get(), com.smileprotocol.client.SPGeoRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.LAB_DOOR.get(), com.smileprotocol.client.SPGeoRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.HIDE.get(), com.smileprotocol.client.SPGeoRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.smileprotocol.registry.ModEntities.BERSERKER.get(), ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "berserker"));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.smileprotocol.registry.ModEntities.SMILER_TEST.get(), ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "smiler_test"));
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ELEVATOR_BUTTON.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.LAB_DOOR.get(), ModBlocks.SECURITY_DOOR.get(),
                ModBlocks.VALVE.get(), ModBlocks.GURNEY.get(), ModBlocks.CURTAIN.get(), ModBlocks.FUSE_BOX.get(),
                ModBlocks.LOCKER.get(), ModBlocks.WARDROBE.get(), ModBlocks.WASTE_CONTAINER.get(),
                ModBlocks.BLOOD_FLOOR.get(), ModBlocks.WALL_DECAL.get(), ModBlocks.WALL_WRITING.get());
    }

    private static <T extends CustomPacketPayload> void registerReceiver(SPNetwork.Def<T> def) {
        ClientPlayNetworking.registerGlobalReceiver(def.type(), (payload, ctx) -> SPNetwork.handleClient(payload));
    }
}
