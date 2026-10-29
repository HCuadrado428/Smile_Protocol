package com.smileprotocol.blockentity;

import com.smileprotocol.block.FuseBoxBlock;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Caja de fusibles (README 14.1): su circuito, cuantas ranuras tiene en la plantilla
 * ({@code Slots}), cuantas hacen falta tras escalar por el tamano del grupo ({@code Required}),
 * los fusibles puestos y, si exige uno concreto, de que tipo ({@code Accepts}).
 */
public class FuseBoxBlockEntity extends SyncedBlockEntity {

    public static final String BLOWN = "blown";

    private String circuit = "";
    private int slots = 2;
    private int required = -1;
    private String accepts = "";
    private final List<String> fuses = new ArrayList<>();

    public FuseBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUSE_BOX.get(), pos, state);
    }

    public String circuit() { return circuit; }
    public int slots() { return slots; }
    public int required() { return required < 0 ? slots : required; }
    public String accepts() { return accepts; }
    public List<String> fuses() { return fuses; }

    public void configure(String circuit, int slots) {
        this.circuit = circuit;
        this.slots = Math.max(1, Math.min(4, slots));
        this.required = -1;
        changed();
        updateState();
    }

    public void setRequired(int required) {
        this.required = Math.max(1, Math.min(4, required));
        while (fuses.size() > this.required) fuses.remove(fuses.size() - 1);
        changed();
        updateState();
    }

    public void setAccepts(String type) {
        this.accepts = type;
        changed();
    }

    public boolean isFull() {
        return fuses.size() >= required();
    }

    /** Completa y sin fundidos: enciende su parte del circuito. */
    public boolean isComplete() {
        return isFull() && !fuses.contains(BLOWN);
    }

    public void insert(String type) {
        if (isFull()) return;
        fuses.add(type);
        changed();
        updateState();
    }

    public String removeLast() {
        if (fuses.isEmpty()) return "";
        String t = fuses.remove(fuses.size() - 1);
        changed();
        updateState();
        return t;
    }

    public void setPoweredVisual(boolean powered) {
        if (level == null) return;
        BlockState st = getBlockState();
        if (st.hasProperty(FuseBoxBlock.POWERED) && st.getValue(FuseBoxBlock.POWERED) != powered) {
            level.setBlock(worldPosition, st.setValue(FuseBoxBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    private void updateState() {
        if (level == null || level.isClientSide) return;
        BlockState st = getBlockState();
        if (!st.hasProperty(FuseBoxBlock.FILLED)) return;
        int shown = Math.min(4, fuses.size());
        if (st.getValue(FuseBoxBlock.FILLED) != shown) {
            level.setBlock(worldPosition, st.setValue(FuseBoxBlock.FILLED, shown), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Circuit", circuit);
        tag.putInt("Slots", slots);
        tag.putInt("Required", required);
        tag.putString("Accepts", accepts);
        ListTag list = new ListTag();
        for (String f : fuses) list.add(StringTag.valueOf(f));
        tag.put("Fuses", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        circuit = tag.getString("Circuit");
        slots = tag.contains("Slots") ? Math.max(1, Math.min(4, tag.getInt("Slots"))) : 2;
        required = tag.contains("Required") ? tag.getInt("Required") : -1;
        accepts = tag.getString("Accepts");
        fuses.clear();
        ListTag list = tag.getList("Fuses", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) fuses.add(list.getString(i));
    }
}
