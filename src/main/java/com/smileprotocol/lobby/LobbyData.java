package com.smileprotocol.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos persistentes del lobby (overworld): modo, punto de aparicion, region protegida
 * y ascensores (README 4.2, 5.2, 6.2).
 */
public final class LobbyData extends SavedData {

    private static final String NAME = "smileprotocol_lobby";

    private LobbyMode mode = LobbyMode.FREE;
    @Nullable private BlockPos spawn;
    private float spawnYaw;
    @Nullable private BoundingBox region;
    private final Map<String, ElevatorDef> elevators = new LinkedHashMap<>();

    public static LobbyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LobbyData::load, LobbyData::new, NAME);
    }

    public LobbyMode getMode() { return mode; }
    public void setMode(LobbyMode mode) { this.mode = mode; setDirty(); }

    @Nullable public BlockPos getSpawn() { return spawn; }
    public float getSpawnYaw() { return spawnYaw; }
    public void setSpawn(BlockPos pos, float yaw) { spawn = pos.immutable(); spawnYaw = yaw; setDirty(); }

    @Nullable public BoundingBox getRegion() { return region; }
    public void setRegion(@Nullable BoundingBox box) { region = box; setDirty(); }

    /** True si la posicion esta dentro de la region protegida del lobby. */
    public boolean inRegion(Vec3 pos) {
        return region != null && region.isInside(BlockPos.containing(pos));
    }

    public boolean inRegion(BlockPos pos) {
        return region != null && region.isInside(pos);
    }

    public Collection<ElevatorDef> getElevators() { return Collections.unmodifiableCollection(elevators.values()); }

    @Nullable public ElevatorDef getElevator(String id) { return elevators.get(id); }

    public void putElevator(ElevatorDef def) { elevators.put(def.id(), def); setDirty(); }

    public void removeElevator(String id) { elevators.remove(id); setDirty(); }

    public void clearElevators() { elevators.clear(); setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("Mode", mode.name());
        if (spawn != null) {
            tag.put("Spawn", NbtUtils.writeBlockPos(spawn));
            tag.putFloat("SpawnYaw", spawnYaw);
        }
        if (region != null) tag.putIntArray("Region", boxToArray(region));
        ListTag list = new ListTag();
        for (ElevatorDef e : elevators.values()) list.add(e.save());
        tag.put("Elevators", list);
        return tag;
    }

    public static LobbyData load(CompoundTag tag) {
        LobbyData d = new LobbyData();
        try {
            d.mode = LobbyMode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            d.mode = LobbyMode.FREE;
        }
        if (tag.contains("Spawn")) {
            d.spawn = NbtUtils.readBlockPos(tag.getCompound("Spawn"));
            d.spawnYaw = tag.getFloat("SpawnYaw");
        }
        if (tag.contains("Region")) d.region = boxFromArray(tag.getIntArray("Region"));
        ListTag list = tag.getList("Elevators", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ElevatorDef e = ElevatorDef.load(list.getCompound(i));
            d.elevators.put(e.id(), e);
        }
        return d;
    }

    static int[] boxToArray(BoundingBox b) {
        return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    static BoundingBox boxFromArray(int[] a) {
        if (a.length < 6) return new BoundingBox(0, 0, 0, 0, 0, 0);
        return new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]);
    }
}
