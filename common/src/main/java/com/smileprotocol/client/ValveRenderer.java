package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.smileprotocol.block.ValveBlock;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rueda de la valvula (README 14.4): gira mientras alguien la mantiene y queda girada al
 * terminar. Se dibuja con el modelo del objeto «rueda de valvula».
 */
public class ValveRenderer implements BlockEntityRenderer<ValveBlockEntity> {

    private ItemStack wheel;

    public ValveRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ValveBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof ValveBlock) || !st.getValue(ValveBlock.WHEEL)) return;
        if (wheel == null) wheel = new ItemStack(ModItems.VALVE_WHEEL.get());
        Direction facing = st.getValue(ValveBlock.FACING);
        float angle;
        if (st.getValue(ValveBlock.TURNED)) angle = 270f;
        else if (st.getValue(ValveBlock.TURNING) && be.getLevel() != null) angle = ((be.getLevel().getGameTime() + pt) * 9f) % 360f;
        else angle = 0f;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, -0.04);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.scale(0.8f, 0.8f, 0.8f);
        Minecraft.getInstance().getItemRenderer().renderStatic(wheel, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
