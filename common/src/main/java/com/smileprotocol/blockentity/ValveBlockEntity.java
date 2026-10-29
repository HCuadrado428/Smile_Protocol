package com.smileprotocol.blockentity;

import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Valvula (README 14.4): su grupo, su orden dentro del grupo (0 = cualquiera) y si hay que
 * girarla a la vez que las demas del grupo ({@code Coop}).
 */
public class ValveBlockEntity extends AnimatedBlockEntity {

    private String group = "";
    private int order;
    private boolean coop;
    /** Momento (tiempo de juego) en que se termino de girar, para la comprobacion «a la vez». */
    private long turnedAt = -1;

    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE.get(), pos, state);
    }

    @Override
    protected boolean targetOpen() {
        BlockState st = getBlockState();
        return st.hasProperty(com.smileprotocol.block.ValveBlock.TURNED) && st.getValue(com.smileprotocol.block.ValveBlock.TURNED);
    }

    @Override
    protected float speed() {
        return 1f / 20f;
    }

    public String group() { return group; }
    public int order() { return order; }
    public boolean coop() { return coop; }
    public long turnedAt() { return turnedAt; }

    public void configure(String group, int order, boolean coop) {
        this.group = group;
        this.order = Math.max(0, order);
        this.coop = coop;
        changed();
    }

    public void setTurnedAt(long t) {
        turnedAt = t;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Group", group);
        tag.putInt("Order", order);
        tag.putBoolean("Coop", coop);
        tag.putLong("TurnedAt", turnedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        group = tag.getString("Group");
        order = tag.getInt("Order");
        coop = tag.getBoolean("Coop");
        turnedAt = tag.contains("TurnedAt") ? tag.getLong("TurnedAt") : -1;
    }
}
