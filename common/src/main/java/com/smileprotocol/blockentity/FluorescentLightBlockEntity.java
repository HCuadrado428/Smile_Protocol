package com.smileprotocol.blockentity;

import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.circuit.CircuitListener;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entidad de bloque del fluorescente. En el cliente mantiene la luz dinamica de Veil (README
 * 21.1); en el servidor, si esta ligado a un circuito ({@code Circuit}), se enciende al
 * completarlo y se apaga sin el (README 14.1).
 */
public class FluorescentLightBlockEntity extends SyncedBlockEntity implements CircuitListener {

    private String circuit = "";

    public FluorescentLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUORESCENT_LIGHT.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FluorescentLightBlockEntity be) {
        // El ticker solo se crea en el cliente
        com.smileprotocol.client.light.DynamicLights.tickFluorescent(be);
    }

    public void setCircuit(String circuit) {
        this.circuit = circuit;
        changed();
    }

    @Override
    public String circuitId() {
        return circuit;
    }

    @Override
    public void onCircuit(ServerLevel level, boolean powered) {
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof FluorescentLightBlock)) return;
        // Con corriente se encienden y dejan de parpadear (README 14.1)
        BlockState next = st.setValue(FluorescentLightBlock.LIT, powered).setValue(FluorescentLightBlock.FLICKERS, false);
        if (next != st) level.setBlock(worldPosition, next, Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!circuit.isEmpty()) tag.putString("Circuit", circuit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        circuit = tag.getString("Circuit");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) com.smileprotocol.client.light.DynamicLights.remove(worldPosition);
    }
}
