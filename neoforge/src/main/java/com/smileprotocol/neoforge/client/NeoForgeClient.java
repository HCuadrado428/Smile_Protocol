package com.smileprotocol.neoforge.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.client.ClientController;
import com.smileprotocol.client.ClientIsolation;
import com.smileprotocol.client.ClientState;
import com.smileprotocol.client.ElevatorBoardRenderer;
import com.smileprotocol.client.ElevatorDoorRenderer;
import com.smileprotocol.client.HudOverlay;
import com.smileprotocol.platform.Services;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/** Cliente en NeoForge: HUD, renderers y eventos hacia el codigo comun. */
public final class NeoForgeClient {

    /** Capas vanilla que siguen visibles dentro de la partida (README 8.1). */
    private static final Set<ResourceLocation> VANILLA_ALLOWED = Set.of(
            VanillaGuiLayers.CHAT, VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.TITLE,
            VanillaGuiLayers.TAB_LIST, VanillaGuiLayers.CAMERA_OVERLAYS);

    /** PlayerAnimator instalado: animaciones del jugador (README 13). */
    private static final boolean PAL = Services.PLATFORM.isModLoaded("playeranimator")
            || Services.PLATFORM.isModLoaded("player-animator");

    private NeoForgeClient() {}

    @EventBusSubscriber(modid = SmileProtocol.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            ClientController.init();
            // Menu de accesibilidad en la lista de mods (README 24.2)
            net.neoforged.fml.ModList.get().getModContainerById(SmileProtocol.MODID).ifPresent(c -> c.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (mc, parent) -> new com.smileprotocol.client.AccessibilityScreen(parent)));
            if (PAL) PalBridge.init();
        }

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(SmileProtocol.id("hud"), (graphics, delta) ->
                    HudOverlay.INSTANCE.render(graphics, delta.getGameTimeDeltaPartialTick(true)));
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_BOARD.get(), ElevatorBoardRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_DOOR.get(), ElevatorDoorRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.VALVE.get(), com.smileprotocol.client.SPGeoRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.FUSE_BOX.get(), com.smileprotocol.client.SPGeoRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.LAB_DOOR.get(), com.smileprotocol.client.SPGeoRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.HIDE.get(), com.smileprotocol.client.SPGeoRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.FURNITURE.get(), com.smileprotocol.client.SPGeoRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.BERSERKER.get(), ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "berserker"));
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.SMILER_TEST.get(),
                    ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "smiler_test"));
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.SMILER_KILL.get(),
                    ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "smiler_kill"));
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.PACIENTE.get(), com.smileprotocol.client.PacienteRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.IMITADOR.get(), com.smileprotocol.client.ImitadorRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.RASTREADOR.get(),
                    ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "rastreador", "rastreador"));
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.AHORCADO.get(),
                    ctx -> new com.smileprotocol.client.BerserkerRenderer<>(ctx, "ahorcado", "ahorcado"));
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.APAGON.get(), com.smileprotocol.client.ApagonRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.SMILEY.get(), com.smileprotocol.client.SmileyRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.FLARE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
            event.registerEntityRenderer(com.smileprotocol.registry.ModEntities.THROWN_ITEM.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        }
    }

    @EventBusSubscriber(modid = SmileProtocol.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            ClientController.tick(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            ClientController.onJoin();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientController.onLeave();
        }

        @SubscribeEvent
        public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
            if (ClientState.inSmileProtocol() && !ClientIsolation.isHudLayerAllowed(event.getName(), VANILLA_ALLOWED)) {
                event.setCanceled(true);
            }
        }
    }
}
