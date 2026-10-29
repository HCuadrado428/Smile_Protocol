package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sonidos del mod. Cada uno necesita su entrada en {@code assets/smileprotocol/sounds.json}
 * y su .ogg (provisional, generado por script, hasta tener el definitivo).
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SmileProtocol.MODID);

    private ModSounds() {}

    /** Registra un sonido con rango variable (posicional). */
    static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SmileProtocol.id(name)));
    }
}
