package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.smileprotocol.SmileProtocol;
import com.smileprotocol.block.FuseBoxBlock;
import com.smileprotocol.block.ValveBlock;
import com.smileprotocol.blockentity.AnimatedBlockEntity;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.HideBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Renderer GeckoLib de los mecanismos y escondites (README 14, 15). El modelo se elige por el id
 * del bloque ({@code geo/block/<id>.geo.json}) y las piezas moviles se giran desde codigo:
 * <ul>
 *   <li>{@code leaf}: hoja de puerta, sobre su bisagra (izquierda o derecha);</li>
 *   <li>{@code door}: puerta de taquilla o armario; {@code lid}: tapa del contenedor;</li>
 *   <li>{@code cloth}: la cortina se mece; {@code wheel}: rueda de la valvula;</li>
 *   <li>{@code fuse0..3}, {@code lamp_on/off}: fusibles puestos y piloto de la caja.</li>
 * </ul>
 * Todas las piezas moviles son simetricas en X, asi que el giro se calcula en el espacio del
 * bloque (origen en el centro de la base, +Z hacia el frente) sin depender del modelo.
 */
public class SPGeoRenderer<T extends AnimatedBlockEntity> extends GeoBlockRenderer<T> {

    /** Bisagra de la hoja cerrada (pegada a la cara de atras, grosor 3/16). Ver vanilla DoorBlock. */
    private static final float DOOR_PIVOT = (1f - 3f / 16f) / 2f;

    public SPGeoRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new Model<>());
    }

    /** Modelo por id de bloque. */
    static final class Model<T extends AnimatedBlockEntity> extends GeoModel<T> {
        private static String name(AnimatedBlockEntity be) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
        }

        @Override
        public ResourceLocation getModelResource(T be) {
            return SmileProtocol.id("geo/block/" + name(be) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(T be) {
            return SmileProtocol.id("textures/block/" + name(be) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(T be) {
            return SmileProtocol.id("animations/block/" + name(be) + ".animation.json");
        }
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
    }

    @Override
    protected Direction getFacing(T be) {
        BlockState st = be.getBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return st.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return Direction.SOUTH;
    }

    /** Solo lo que ocupa dos bloques (puertas, camilla, taquillas) necesita dibujarse aunque su bloque no se vea. */
    @Override
    public boolean shouldRenderOffScreen(T be) {
        return be instanceof LabDoorBlockEntity || be instanceof HideBlockEntity;
    }

    /** Rendimiento: los muebles y mecanismos pequenos no se dibujan de lejos (no se ven a oscuras). */
    @Override
    public int getViewDistance() {
        return 48;
    }

    /** NeoForge: la caja de recorte cubre las piezas de dos bloques (puertas, camilla). */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(T be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.5);
    }

    @Override
    public void renderRecursively(PoseStack pose, T be, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, boolean isReRender, float pt, int light, int overlay, int colour) {
        String name = bone.getName();
        BlockState st = be.getBlockState();
        float open = be.openAmount(pt);
        boolean pushed = false;

        switch (name) {
            case "leaf" -> {
                if (be instanceof LabDoorBlockEntity door && open > 0f) {
                    boolean right = door.hingeRight();
                    float px = right ? -DOOR_PIVOT : DOOR_PIVOT, pz = -DOOR_PIVOT;
                    pose.pushPose();
                    pushed = true;
                    pose.translate(px, 0, pz);
                    pose.mulPose(Axis.YP.rotationDegrees((right ? -90f : 90f) * open));
                    pose.translate(-px, 0, -pz);
                }
            }
            case "door" -> {
                if (open > 0f) {
                    pose.pushPose();
                    pushed = true;
                    pose.translate(7f / 16f, 0, 0.5f);
                    pose.mulPose(Axis.YP.rotationDegrees(100f * open));
                    pose.translate(-7f / 16f, 0, -0.5f);
                }
            }
            case "lid" -> {
                if (open > 0f) {
                    pose.pushPose();
                    pushed = true;
                    pose.translate(0, 26f / 16f, -7.5f / 16f);
                    pose.mulPose(Axis.XP.rotationDegrees(-75f * open));
                    pose.translate(0, -26f / 16f, 7.5f / 16f);
                }
            }
            case "cloth" -> {
                if (be.getLevel() != null && be instanceof HideBlockEntity) {
                    float t = (be.getLevel().getGameTime() + pt) * 0.05f + be.getBlockPos().asLong() % 7;
                    float sway = (float) Math.sin(t) * 2.5f + open * 18f;
                    pose.pushPose();
                    pushed = true;
                    pose.translate(0, 31f / 16f, 0);
                    pose.mulPose(Axis.XP.rotationDegrees(sway));
                    pose.translate(0, -31f / 16f, 0);
                }
            }
            case "wheel" -> {
                if (be instanceof ValveBlockEntity) {
                    if (!st.getValue(ValveBlock.WHEEL)) return;
                    float angle = 270f * open;
                    if (st.getValue(ValveBlock.TURNING) && be.getLevel() != null) {
                        angle = ((be.getLevel().getGameTime() + pt) * 9f) % 360f;
                    }
                    pose.pushPose();
                    pushed = true;
                    pose.translate(0, 0.5f, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(angle));
                    pose.translate(0, -0.5f, 0);
                }
            }
            // Muebles que se registran
            case "slide" -> bone.setPosZ(open * 5f);
            case "swing_l" -> bone.setRotY(open * 1.75f);
            case "swing_r" -> bone.setRotY(-open * 1.75f);
            case "flip" -> bone.setRotX(open * 1.9f);
            case "full" -> {
                if (st.hasProperty(com.smileprotocol.block.SearchableFurnitureBlock.OPEN)
                        && st.getValue(com.smileprotocol.block.SearchableFurnitureBlock.OPEN)) return;
            }
            case "empty" -> {
                if (st.hasProperty(com.smileprotocol.block.SearchableFurnitureBlock.OPEN)
                        && !st.getValue(com.smileprotocol.block.SearchableFurnitureBlock.OPEN)) return;
            }
            case "json_open" -> {
                return;
            }
            case "lamp_on", "lamp_off" -> {
                if (be instanceof FuseBoxBlockEntity && st.hasProperty(FuseBoxBlock.POWERED)) {
                    boolean on = st.getValue(FuseBoxBlock.POWERED);
                    if (on != name.equals("lamp_on")) return;
                }
            }
            default -> {
                if (name.startsWith("fuse") && name.length() == 5 && be instanceof FuseBoxBlockEntity
                        && st.hasProperty(FuseBoxBlock.FILLED)) {
                    int i = name.charAt(4) - '0';
                    if (i >= st.getValue(FuseBoxBlock.FILLED)) return;
                }
            }
        }
        super.renderRecursively(pose, be, bone, renderType, buffers, buffer, isReRender, pt, light, overlay, colour);
        if (pushed) pose.popPose();
    }
}
