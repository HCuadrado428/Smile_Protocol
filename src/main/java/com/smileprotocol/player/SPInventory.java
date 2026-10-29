package com.smileprotocol.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Inventario de 4 huecos de la partida (README 10). Un item por hueco, sin stacks.
 * Vive en la capability del jugador; el inventario real queda vacio mientras dura la partida.
 */
public final class SPInventory {

    public static final int SIZE = 4;

    private final ItemStack[] slots = new ItemStack[SIZE];
    private int selected;

    public SPInventory() {
        clear();
    }

    public ItemStack get(int slot) {
        return slots[slot];
    }

    public void set(int slot, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (copy.getCount() > 1) copy.setCount(1);
        slots[slot] = copy;
    }

    public ItemStack getSelectedStack() {
        return slots[selected];
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int slot) {
        if (slot >= 0 && slot < SIZE) selected = slot;
    }

    /** Primer hueco libre o -1 si esta lleno. */
    public int firstEmpty() {
        for (int i = 0; i < SIZE; i++) if (slots[i].isEmpty()) return i;
        return -1;
    }

    public boolean isFull() {
        return firstEmpty() < 0;
    }

    public void clear() {
        for (int i = 0; i < SIZE; i++) slots[i] = ItemStack.EMPTY;
        selected = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            CompoundTag item = new CompoundTag();
            item.putByte("Slot", (byte) i);
            slots[i].save(item);
            list.add(item);
        }
        tag.put("Items", list);
        tag.putByte("Selected", (byte) selected);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            int slot = item.getByte("Slot");
            if (slot >= 0 && slot < SIZE) slots[slot] = ItemStack.of(item);
        }
        setSelected(tag.getByte("Selected"));
    }
}
