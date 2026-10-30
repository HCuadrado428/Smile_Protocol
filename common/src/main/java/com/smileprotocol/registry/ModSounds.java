package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/** Sonidos propios (provisionales, generados por tools/sounds.py). */
public final class ModSounds {

    public static final Supplier<SoundEvent> BERSERKER_STEP = register("berserker.step");
    public static final Supplier<SoundEvent> BERSERKER_BREATH = register("berserker.breath");
    public static final Supplier<SoundEvent> BERSERKER_LISTEN = register("berserker.listen");
    public static final Supplier<SoundEvent> BERSERKER_SNIFF = register("berserker.sniff");
    public static final Supplier<SoundEvent> BERSERKER_ROAR = register("berserker.roar");
    public static final Supplier<SoundEvent> BERSERKER_IMPACT = register("berserker.impact");
    public static final Supplier<SoundEvent> PLAYER_GROAN = register("player.groan");
    public static final Supplier<SoundEvent> PLAYER_HEARTBEAT = register("player.heartbeat");
    public static final Supplier<SoundEvent> MOB_GRAB = register("mob.grab");

    private ModSounds() {}

    private static Supplier<SoundEvent> register(String name) {
        return Services.PLATFORM.register(BuiltInRegistries.SOUND_EVENT, name,
                () -> SoundEvent.createVariableRangeEvent(SmileProtocol.id(name)));
    }

    public static void init() {}
}
