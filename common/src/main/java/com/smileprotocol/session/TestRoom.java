package com.smileprotocol.session;

import com.smileprotocol.dimension.SPDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Sala provisional en la dimension de instancias. Sustituye a las instancias reales hasta
 * la fase 6: sirve para probar la sesion, las restricciones y el HUD, y es adonde bajan
 * los ascensores del lobby por ahora.
 */
public final class TestRoom {

    /** Centro del suelo de la sala. */
    public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    private static final int R = 6;
    private static final int H = 5;

    private TestRoom() {}

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(SPDimension.SP_DIM);
    }

    public static Vec3 spawnPos() {
        return Vec3.atBottomCenterOf(ORIGIN.above());
    }

    /** Construye la sala si no existe. */
    public static void ensureBuilt(ServerLevel level) {
        if (level.getBlockState(ORIGIN).is(Blocks.POLISHED_DEEPSLATE)) return;

        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;

        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int y = 0; y <= H + 1; y++) {
                    BlockPos p = ORIGIN.offset(x, y, z);
                    boolean edge = Math.abs(x) == R || Math.abs(z) == R;
                    BlockState s;
                    if (y == 0) s = floor;
                    else if (y == H + 1) s = (x % 3 == 0 && z % 3 == 0) ? light : wall;
                    else s = edge ? wall : Blocks.AIR.defaultBlockState();
                    level.setBlock(p, s, flags);
                }
            }
        }

        BlockPos signPos = ORIGIN.offset(0, 2, -R + 1);
        level.setBlock(signPos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, Direction.SOUTH), flags);
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = sign.getFrontText()
                    .setMessage(0, Component.literal("SALA DE PRUEBAS"))
                    .setMessage(1, Component.literal("Solo para pruebas"))
                    .setMessage(2, Component.literal("(/spadmin test)"))
                    .setMessage(3, Component.literal("/smileprotocol leave"));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
        }
    }
}
