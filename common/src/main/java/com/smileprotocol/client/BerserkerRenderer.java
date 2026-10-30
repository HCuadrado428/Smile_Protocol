package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Berserker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Berserker con GeckoLib (README 20.2). La sonrisa roja es lo unico que brilla: la pinta la capa
 * emisiva con {@code textures/entity/berserker_glowmask.png} (README 20.1), sin luz de Veil.
 */
public class BerserkerRenderer extends GeoEntityRenderer<Berserker> {

    public BerserkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("berserker"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }
}
