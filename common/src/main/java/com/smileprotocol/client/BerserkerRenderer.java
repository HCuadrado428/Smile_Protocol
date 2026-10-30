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
        // Remate del smiler (README 9.3): la cabeza arrancada de la victima en la mano derecha
        addRenderLayer(new software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<>(this,
                (bone, mob) -> "hand_r".equals(bone.getName()) ? severedHead(mob) : null, (bone, mob) -> null));
        this.shadowRadius = 0.6f;
    }

    private static net.minecraft.world.item.ItemStack cachedHead = net.minecraft.world.item.ItemStack.EMPTY;
    private static int cachedFor = -1;

    @javax.annotation.Nullable
    private static net.minecraft.world.item.ItemStack severedHead(com.smileprotocol.entity.SPMonster mob) {
        com.smileprotocol.sequence.SequenceKind k = mob.sequence();
        if (k == null || k.decap < 0 || mob.sequenceAge(0) < k.decap + 1) return null;
        int id = mob.sequenceVictim();
        if (id != cachedFor || cachedHead.isEmpty()) {
            net.minecraft.world.entity.Entity e = net.minecraft.client.Minecraft.getInstance().level == null ? null
                    : net.minecraft.client.Minecraft.getInstance().level.getEntity(id);
            if (!(e instanceof net.minecraft.world.entity.player.Player p)) return null;
            cachedHead = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            cachedHead.set(net.minecraft.core.component.DataComponents.PROFILE,
                    new net.minecraft.world.item.component.ResolvableProfile(p.getGameProfile()));
            cachedFor = id;
        }
        return cachedHead;
    }
}
