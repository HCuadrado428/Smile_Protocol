package com.smileprotocol.interact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Bloque que se usa con el click derecho mantenido dentro de la partida (README 11). El
 * cliente llama a {@link #spec} para el prompt y la barra; el servidor lo vuelve a calcular,
 * cuenta el tiempo y llama a {@link #complete}. Los datos que hagan falta para el prompt tienen
 * que estar sincronizados con el cliente (entidad de bloque).
 */
public interface SPInteractable {

    /** Null si no hay nada que hacer con este bloque ahora mismo. */
    @Nullable
    InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player);

    void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);

    /** Mientras se mantiene (para animar el bloque). */
    default void onProgress(ServerLevel level, BlockPos pos, BlockState state, boolean active) {}

    /** Bloque al que redirigir (la mitad de abajo de una puerta, por ejemplo). */
    default BlockPos root(Level level, BlockPos pos, BlockState state) {
        return pos;
    }
}
