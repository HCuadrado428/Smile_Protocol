package com.smileprotocol.client;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.entity.Imitador;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** El Imitador: modelo GeckoLib; la sonrisa (capa emisiva) brilla un poco en la oscuridad. */
public class ImitadorRenderer extends GeoEntityRenderer<Imitador> {

    public ImitadorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SmileProtocol.id("imitador"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.4f;
    }
}
