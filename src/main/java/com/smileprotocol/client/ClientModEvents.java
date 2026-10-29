package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de cliente del bus del mod: overlays y renderers. */
@Mod.EventBusSubscriber(modid = SmileProtocol.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", HudOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_BOARD.get(), ElevatorBoardRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_DOOR.get(), ElevatorDoorRenderer::new);
    }
}
