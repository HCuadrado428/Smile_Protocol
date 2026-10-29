package com.smileprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.smileprotocol.block.ElevatorBoardBlock;
import com.smileprotocol.blockentity.ElevatorBoardBlockEntity;
import com.smileprotocol.network.SyncLobbyBoardsPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Dibuja el texto de la pantalla de un ascensor (README 5.2): el nombre arriba, pequeno, y el
 * estado debajo en su color. El texto se escala para caber en el bloque.
 */
public class ElevatorBoardRenderer implements BlockEntityRenderer<ElevatorBoardBlockEntity> {

    /** Ancho util de la pantalla en bloques. */
    private static final float WIDTH = 0.88f;

    private final Font font;

    public ElevatorBoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ElevatorBoardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Direction facing = be.getBlockState().getValue(ElevatorBoardBlock.FACING);
        SyncLobbyBoardsPacket.Board board = ClientState.board(be.getBlockPos());

        Component title = board != null ? board.title() : Component.literal("—");
        Component status = board != null ? board.status() : Component.empty();
        int color = board != null ? board.color() : 0x606060;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Cara delantera de la pantalla (el panel ocupa 2/16 desde la pared)
        pose.translate(0, 0, -0.5 + 2.0 / 16.0 + 0.004);

        drawLine(pose, buffers, title, 0.18f, 0.0055f, 0xFF9FB3AA);
        drawLine(pose, buffers, status, -0.06f, 0.0095f, 0xFF000000 | color);
        pose.popPose();
    }

    private void drawLine(PoseStack pose, MultiBufferSource buffers, Component text, float y, float maxScale, int color) {
        int w = font.width(text);
        if (w == 0) return;
        float scale = Math.min(maxScale, WIDTH / w);
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.scale(scale, -scale, scale);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(text, -w / 2f, -font.lineHeight / 2f, color, false, m, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
