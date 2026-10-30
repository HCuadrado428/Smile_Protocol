package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Apagon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Apagon (docs/SMILERS.md): crece a oscuras y se encoge a la luz (tamano sincronizado). */
public class ApagonRenderer extends GeoEntityRenderer<Apagon> {

    public ApagonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("apagon"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    @Override
    public void render(Apagon mob, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = mob.size(pt);
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.render(mob, yaw, pt, pose, buffers, light);
    }
}
