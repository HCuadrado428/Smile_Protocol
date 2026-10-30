package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Smiley;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Smiley (docs/SMILERS.md): silueta negra; solo brillan el ojo y la sonrisa. Fundido en la pared no se dibuja. */
public class SmileyRenderer extends GeoEntityRenderer<Smiley> {

    public SmileyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("smiley"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(Smiley mob, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (mob.isInvisible()) return;
        super.render(mob, yaw, pt, pose, buffers, light);
    }
}
