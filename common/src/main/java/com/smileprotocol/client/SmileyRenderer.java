package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Smiley;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Smiley (docs/SMILERS.md): sombra pura; solo brillan el ojo, la sonrisa tallada, los dientes y la
 * garganta. Fundido en la pared no se dibuja.
 * <p>
 * La mirada se suma a la animacion (no la pisa, como haria GeckoLib con {@code turnsHead}) y se reparte
 * entre el cuello y la cabeza: asi puede seguirte con la cabeza girada del todo mientras anda hacia
 * otro lado, y la animacion sigue ladeandola o echandola atras.
 */
public class SmileyRenderer extends GeoEntityRenderer<Smiley> {

    public SmileyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("smiley"), false) {
            @Override
            public void setCustomAnimations(Smiley mob, long instanceId, AnimationState<Smiley> state) {
                super.setCustomAnimations(mob, instanceId, state);
                EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
                if (d == null) return;
                float yaw = Mth.wrapDegrees(d.netHeadYaw()) * Mth.DEG_TO_RAD;
                float pitch = Mth.clamp(d.headPitch(), -40f, 40f) * Mth.DEG_TO_RAD;
                var neck = getAnimationProcessor().getBone("neck2");
                var head = getAnimationProcessor().getBone("head");
                if (neck != null) neck.setRotY(neck.getRotY() + yaw * 0.35f);
                if (head != null) {
                    head.setRotY(head.getRotY() + yaw * 0.65f);
                    head.setRotX(head.getRotX() + pitch * 0.7f);
                }
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(Smiley mob, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (mob.isInvisible()) return;
        super.render(mob, yaw, pt, pose, buffers, light);
    }
}
