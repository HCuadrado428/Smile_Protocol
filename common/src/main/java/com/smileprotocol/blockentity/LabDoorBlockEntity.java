package com.smileprotocol.blockentity;

import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.circuit.CircuitListener;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Configuracion de una puerta del laboratorio (README 14.2, 14.3): tipo y su parametro (lock_id,
 * nivel de tarjeta, circuito o grupo de valvulas), si la llave se gasta y si ya se ha abierto.
 * Va en la mitad de abajo.
 */
public class LabDoorBlockEntity extends SyncedBlockEntity implements CircuitListener {

    private DoorType type = DoorType.NORMAL;
    private String param = "";
    private boolean consume;
    private boolean unlocked;

    public LabDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB_DOOR.get(), pos, state);
    }

    public DoorType type() { return type; }
    public String param() { return param; }
    public boolean consume() { return consume; }
    public boolean unlocked() { return unlocked; }

    public int level() {
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public void configure(DoorType type, String param, boolean consume) {
        this.type = type;
        this.param = param;
        this.consume = consume;
        this.unlocked = false;
        changed();
    }

    public void unlock() {
        unlocked = true;
        changed();
    }

    @Override
    public String circuitId() {
        return type == DoorType.ELECTRIC ? param : "";
    }

    @Override
    public void onCircuit(ServerLevel level, boolean powered) {
        if (type != DoorType.ELECTRIC) return;
        LabDoorBlock.setOpenAt(level, worldPosition, powered);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Type", type.name().toLowerCase(Locale.ROOT));
        tag.putString("Param", param);
        tag.putBoolean("Consume", consume);
        tag.putBoolean("Unlocked", unlocked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = DoorType.parse(tag.getString("Type"));
        param = tag.getString("Param");
        consume = tag.getBoolean("Consume");
        unlocked = tag.getBoolean("Unlocked");
    }
}
