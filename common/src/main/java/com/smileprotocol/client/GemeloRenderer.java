package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Gemelo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * El Gemelo se dibuja con el modelo de jugador y la piel de quien copia (ancho o fino segun su piel).
 * Al delatarse: la cabeza tiembla, la piel se oscurece y encima sale su cara de verdad (cuencas negras
 * y una sonrisa que llega a las orejas).
 */
public class GemeloRenderer extends LivingEntityRenderer<Gemelo, PlayerModel<Gemelo>> {

    private static final ResourceLocation FACE = SmileProtocol.id("textures/entity/gemelo_face.png");
    private final PlayerModel<Gemelo> wide;
    private final PlayerModel<Gemelo> slim;

    public GemeloRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new TwinModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new TwinModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new Face(this));
    }

    private static PlayerSkin skin(Gemelo g) {
        UUID id = g.copied();
        if (id == null) return DefaultPlayerSkin.getDefaultSkin();
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    @Override
    public ResourceLocation getTextureLocation(Gemelo g) {
        return skin(g).texture();
    }

    @Override
    public void render(Gemelo g, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = skin(g).model() == PlayerSkin.Model.SLIM ? slim : wide;
        model.setAllVisible(true);
        super.render(g, yaw, pt, pose, buffers, light);
    }

    /** Modelo de jugador cuya cabeza tiembla cuando el gemelo se ha delatado. */
    private static final class TwinModel extends PlayerModel<Gemelo> {
        TwinModel(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(Gemelo g, float limb, float limbAmt, float age, float headYaw, float headPitch) {
            super.setupAnim(g, limb, limbAmt, age, headYaw, headPitch);
            if (!g.revealed()) return;
            float t = age * 1.7f;
            head.xRot += Mth.sin(t * 3.1f) * 0.12f;
            head.zRot = Mth.sin(t * 2.3f) * 0.25f;
            rightArm.xRot = -1.4f + Mth.sin(t) * 0.1f;          // brazos por delante, para agarrar
            leftArm.xRot = -1.4f + Mth.cos(t) * 0.1f;
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }

    @Override
    protected void scale(Gemelo g, PoseStack pose, float pt) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);           // como el jugador
    }

    @Override
    protected boolean shouldShowName(Gemelo g) {
        return !g.revealed() && g.hasCustomName() && g.isCustomNameVisible();
    }

    @Override
    protected float getFlipDegrees(Gemelo g) {
        return 0f;
    }

    /** Cara de verdad al delatarse, con la cabeza temblando. */
    private static final class Face extends RenderLayer<Gemelo, PlayerModel<Gemelo>> {
        Face(RenderLayerParent<Gemelo, PlayerModel<Gemelo>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Gemelo g, float limb, float limbAmt, float pt,
                           float age, float headYaw, float headPitch) {
            if (!g.revealed()) return;
            PlayerModel<Gemelo> m = getParentModel();
            pose.pushPose();
            m.head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(FACE)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
