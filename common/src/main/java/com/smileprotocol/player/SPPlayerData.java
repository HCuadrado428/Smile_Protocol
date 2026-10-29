package com.smileprotocol.player;

import com.mojang.serialization.Codec;
import com.smileprotocol.ServerRef;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Estado persistente del jugador para Smile Protocol (README 5.3).
 * <p>
 * Va adjunto a la entidad del jugador (data attachment de Fabric o NeoForge), asi que se
 * guarda en el mismo fichero y en el mismo momento que el inventario real: si el servidor
 * se cae despues de vaciarlo, el inventario original no se pierde.
 */
public final class SPPlayerData {

    /** Serializacion para los attachments de los dos loaders. */
    public static final Codec<SPPlayerData> CODEC = CompoundTag.CODEC.xmap(SPPlayerData::fromTag, SPPlayerData::toTag);

    private boolean inSP;
    @Nullable private UUID groupId;

    private final SPInventory spInventory = new SPInventory();

    /** Estado guardado al bajar. {@code saved == false} si no hay nada que restaurar. */
    private boolean saved;
    private ListTag savedInventory = new ListTag();
    private int savedGameMode;
    @Nullable private ResourceKey<Level> savedDimension;
    private Vec3 savedPos = Vec3.ZERO;
    private float savedYRot;
    private float savedXRot;
    private float savedHealth;
    private int savedFood;
    private float savedSaturation;

    public static SPPlayerData of(Player player) {
        return Services.PLATFORM.playerData(player);
    }

    public static boolean inSP(Player player) {
        return of(player).inSP();
    }

    public boolean inSP() { return inSP; }
    public void setInSP(boolean inSP) { this.inSP = inSP; }

    @Nullable public UUID getGroupId() { return groupId; }
    public void setGroupId(@Nullable UUID groupId) { this.groupId = groupId; }

    public SPInventory getSPInventory() { return spInventory; }

    public boolean hasSaved() { return saved; }

    public void saveState(ListTag inventory, int gameMode, ResourceKey<Level> dim, Vec3 pos,
                          float yRot, float xRot, float health, int food, float saturation) {
        this.saved = true;
        this.savedInventory = inventory;
        this.savedGameMode = gameMode;
        this.savedDimension = dim;
        this.savedPos = pos;
        this.savedYRot = yRot;
        this.savedXRot = xRot;
        this.savedHealth = health;
        this.savedFood = food;
        this.savedSaturation = saturation;
    }

    public void clearSaved() {
        saved = false;
        savedInventory = new ListTag();
        savedDimension = null;
    }

    public ListTag getSavedInventory() { return savedInventory; }
    public int getSavedGameMode() { return savedGameMode; }
    @Nullable public ResourceKey<Level> getSavedDimension() { return savedDimension; }
    public Vec3 getSavedPos() { return savedPos; }
    public float getSavedYRot() { return savedYRot; }
    public float getSavedXRot() { return savedXRot; }
    public float getSavedHealth() { return savedHealth; }
    public int getSavedFood() { return savedFood; }
    public float getSavedSaturation() { return savedSaturation; }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("InSP", inSP);
        if (groupId != null) tag.putUUID("Group", groupId);
        tag.put("SPInventory", spInventory.save(ServerRef.registries()));
        tag.putBoolean("Saved", saved);
        if (saved) {
            CompoundTag s = new CompoundTag();
            s.put("Inventory", savedInventory);
            s.putInt("GameMode", savedGameMode);
            if (savedDimension != null) s.putString("Dimension", savedDimension.location().toString());
            s.putDouble("X", savedPos.x);
            s.putDouble("Y", savedPos.y);
            s.putDouble("Z", savedPos.z);
            s.putFloat("YRot", savedYRot);
            s.putFloat("XRot", savedXRot);
            s.putFloat("Health", savedHealth);
            s.putInt("Food", savedFood);
            s.putFloat("Saturation", savedSaturation);
            tag.put("SavedState", s);
        }
        return tag;
    }

    public static SPPlayerData fromTag(CompoundTag tag) {
        SPPlayerData d = new SPPlayerData();
        d.inSP = tag.getBoolean("InSP");
        d.groupId = tag.hasUUID("Group") ? tag.getUUID("Group") : null;
        d.spInventory.load(tag.getCompound("SPInventory"), ServerRef.registries());
        d.saved = tag.getBoolean("Saved");
        if (d.saved) {
            CompoundTag s = tag.getCompound("SavedState");
            d.savedInventory = s.getList("Inventory", Tag.TAG_COMPOUND);
            d.savedGameMode = s.getInt("GameMode");
            ResourceLocation dim = s.contains("Dimension") ? ResourceLocation.tryParse(s.getString("Dimension")) : null;
            d.savedDimension = dim != null ? ResourceKey.create(Registries.DIMENSION, dim) : null;
            d.savedPos = new Vec3(s.getDouble("X"), s.getDouble("Y"), s.getDouble("Z"));
            d.savedYRot = s.getFloat("YRot");
            d.savedXRot = s.getFloat("XRot");
            d.savedHealth = s.getFloat("Health");
            d.savedFood = s.getInt("Food");
            d.savedSaturation = s.getFloat("Saturation");
        }
        return d;
    }

    /** Copia profunda (al morir o cambiar de entidad). */
    public SPPlayerData copy() {
        return fromTag(toTag());
    }
}
