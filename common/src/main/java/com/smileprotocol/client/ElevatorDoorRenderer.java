package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.blockentity.ElevatorDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Dibuja la hoja de puerta con GeckoLib ({@code geo/block/elevator_door.geo.json}) y la
 * desliza de lado segun lo abierta que este. El eje X local apunta hacia
 * {@code FACING.getCounterClockWise()}, que es como se mide {@link ElevatorDoorBlockEntity#getSlide()}.
 */
public class ElevatorDoorRenderer extends GeoBlockRenderer<ElevatorDoorBlockEntity> {

    private static final String LEAF = "leaf";

    public ElevatorDoorRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new DefaultedBlockGeoModel<>(SmileProtocol.id("elevator_door")));
    }

    /** SOUTH sin girar; el resto gira de modo que X local = FACING en sentido antihorario. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
    }

    /** Sin recorte por la caja del bloque: la hoja se sale de ella al deslizarse. */
    @Override
    public boolean shouldRenderOffScreen(ElevatorDoorBlockEntity be) {
        return true;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ElevatorDoorBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        if (!LEAF.equals(bone.getName())) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, colour);
            return;
        }
        float offset = animatable.getSlide() * animatable.openAmount(partialTick);
        poseStack.pushPose();
        poseStack.translate(offset, 0, 0);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
        poseStack.popPose();
    }
}
