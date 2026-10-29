package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Capabilities del mod. Por ahora solo {@link SPPlayerData} en los jugadores. */
public final class ModCapabilities {

    public static final Capability<SPPlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation PLAYER_DATA_ID = SmileProtocol.id("player_data");

    private ModCapabilities() {}

    /** Bus del mod. */
    public static void onRegister(RegisterCapabilitiesEvent event) {
        event.register(SPPlayerData.class);
    }

    /** Bus de Forge. */
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            Provider provider = new Provider();
            event.addCapability(PLAYER_DATA_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    /** Bus de Forge: conserva los datos al morir o al volver del End (README 5.3). */
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        original.getCapability(PLAYER_DATA).ifPresent(old ->
                event.getEntity().getCapability(PLAYER_DATA).ifPresent(now -> now.copyFrom(old)));
        original.invalidateCaps();
    }

    /** Datos del jugador. Todo jugador tiene la capability, asi que nunca es null en la practica. */
    public static SPPlayerData get(Player player) {
        return player.getCapability(PLAYER_DATA).orElseGet(SPPlayerData::new);
    }

    public static boolean inSP(Player player) {
        return player.getCapability(PLAYER_DATA).map(SPPlayerData::inSP).orElse(false);
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final SPPlayerData data = new SPPlayerData();
        private final LazyOptional<SPPlayerData> optional = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_DATA.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serialize();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.deserialize(tag);
        }

        void invalidate() {
            optional.invalidate();
        }
    }
}
