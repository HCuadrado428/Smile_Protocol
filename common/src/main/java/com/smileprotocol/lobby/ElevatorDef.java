package com.smileprotocol.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Definicion persistente de un ascensor del lobby: su cabina (region donde se cuentan los
 * jugadores), sus puertas, su boton, su pantalla y el punto donde se deja a quien sale.
 */
public record ElevatorDef(String id, ElevatorType type, String name, BoundingBox cabin,
                          List<BlockPos> doors, @Nullable BlockPos button, @Nullable BlockPos board,
                          BlockPos exit) {

    /** Caja de la cabina en coordenadas de entidad (incluye el bloque superior). */
    public AABB cabinAABB() {
        return AABB.of(cabin);
    }

    public boolean contains(Vec3 pos) {
        return cabinAABB().contains(pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Type", type.name());
        tag.putString("Name", name);
        tag.putIntArray("Cabin", LobbyData.boxToArray(cabin));
        ListTag doorList = new ListTag();
        for (BlockPos d : doors) doorList.add(LobbyData.writePos(d));
        tag.put("Doors", doorList);
        if (button != null) tag.put("Button", LobbyData.writePos(button));
        if (board != null) tag.put("Board", LobbyData.writePos(board));
        tag.put("Exit", LobbyData.writePos(exit));
        return tag;
    }

    public static ElevatorDef load(CompoundTag tag) {
        List<BlockPos> doors = new ArrayList<>();
        ListTag doorList = tag.getList("Doors", Tag.TAG_COMPOUND);
        for (int i = 0; i < doorList.size(); i++) doors.add(LobbyData.readPos(doorList.getCompound(i)));
        ElevatorType type;
        try {
            type = ElevatorType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = ElevatorType.MINI;
        }
        return new ElevatorDef(
                tag.getString("Id"), type, tag.getString("Name"),
                LobbyData.boxFromArray(tag.getIntArray("Cabin")),
                List.copyOf(doors),
                tag.contains("Button") ? LobbyData.readPos(tag.getCompound("Button")) : null,
                tag.contains("Board") ? LobbyData.readPos(tag.getCompound("Board")) : null,
                LobbyData.readPos(tag.getCompound("Exit")));
    }
}
