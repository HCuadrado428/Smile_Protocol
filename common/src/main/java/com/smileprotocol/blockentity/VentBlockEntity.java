package com.smileprotocol.blockentity;

import com.smileprotocol.block.VentFanBlock;
import com.smileprotocol.block.VentGrilleBlock;
import com.smileprotocol.circuit.CircuitListener;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Circuito de una rejilla sellada o de un ventilador de conducto (README 12.9): la rejilla se
 * desbloquea al encenderse su circuito. El ventilador gira mientras su circuito tiene corriente
 * y se para al cortarse; con {@code Brake} es al reves (el circuito es su freno y se para al
 * encenderlo).
 */
public class VentBlockEntity extends SyncedBlockEntity implements CircuitListener {

    private String circuit = "";
    private boolean brake;

    public VentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT.get(), pos, state);
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
        BlockState next = st;
        if (st.getBlock() instanceof VentGrilleBlock && powered) next = st.setValue(VentGrilleBlock.SEALED, false);
        if (st.getBlock() instanceof VentFanBlock) next = st.setValue(VentFanBlock.RUNNING, powered != brake);
        if (next != st) level.setBlock(worldPosition, next, Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!circuit.isEmpty()) tag.putString("Circuit", circuit);
        if (brake) tag.putBoolean("Brake", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        circuit = tag.getString("Circuit");
        brake = tag.getBoolean("Brake");
    }
}
