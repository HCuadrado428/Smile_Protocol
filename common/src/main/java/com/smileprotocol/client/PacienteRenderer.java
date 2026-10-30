package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Paciente;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * El Paciente (docs/SMILERS.md): sin ojo; la sonrisa grande (hueso {@code grin}, capa emisiva)
 * se abre desde el centro cuanto mas cerca esta de quien lo mira, y a mas de 16 bloques no se ve.
 */
public class PacienteRenderer extends GeoEntityRenderer<Paciente> {

    public PacienteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("paciente"), true) {
            @Override
            public void setCustomAnimations(Paciente mob, long instanceId, AnimationState<Paciente> state) {
                super.setCustomAnimations(mob, instanceId, state);
                var bone = getAnimationProcessor().getBone("grin");
                if (bone == null) return;
                var player = Minecraft.getInstance().player;
                double d = player == null ? 99 : player.distanceTo(mob);
                float k = (float) Mth.clamp(1 - (d - 2.5) / 13.5, 0, 1);
                k = k * k * (3 - 2 * k);
                bone.setScaleX(0.25f + 0.95f * k);
                bone.setScaleY(Math.max(0.01f, k));
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.45f;
    }
}
