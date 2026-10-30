package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Berserker con GeckoLib (README 20.2). La sonrisa roja es lo unico que brilla: la pinta la capa
 * emisiva con {@code textures/entity/berserker_glowmask.png} (README 20.1), sin luz de Veil.
 */
public class BerserkerRenderer<T extends com.smileprotocol.entity.SPMonster> extends GeoEntityRenderer<T> {

    public BerserkerRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, "berserker");
    }

    /** Otro smiler con el mismo esqueleto y animaciones y su propia textura. */
    public BerserkerRenderer(EntityRendererProvider.Context ctx, String texture) {
        super(ctx, new DefaultedEntityGeoModel<T>(SmileProtocol.id("berserker"), true) {
            @Override
            public net.minecraft.resources.ResourceLocation getTextureResource(T animatable) {
                return SmileProtocol.id("textures/entity/" + texture + ".png");
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }
}
