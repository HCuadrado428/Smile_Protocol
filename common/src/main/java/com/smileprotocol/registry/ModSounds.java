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
    public static final Supplier<SoundEvent> MOB_CRUNCH = register("mob.crunch");
    public static final Supplier<SoundEvent> MOB_BITE = register("mob.bite");
    public static final Supplier<SoundEvent> MOB_TEAR = register("mob.tear");
    public static final Supplier<SoundEvent> MOB_SQUELCH = register("mob.squelch");
    public static final Supplier<SoundEvent> PLAYER_REVIVE = register("player.revive");
    public static final Supplier<SoundEvent> EVENT_SURGE = register("event.surge");
    public static final Supplier<SoundEvent> EVENT_ALARM = register("event.alarm");
    public static final Supplier<SoundEvent> EVENT_STING = register("event.sting");
    public static final Supplier<SoundEvent> EVENT_KNOCK = register("event.knock");
    public static final Supplier<SoundEvent> EVENT_SCREAM = register("event.scream");
    public static final Supplier<SoundEvent> WALKIE_CLICK = register("walkie.click");
    public static final Supplier<SoundEvent> WALKIE_CRACKLE = register("walkie.crackle");
    public static final Supplier<SoundEvent> UI_MARKER = register("ui.marker");
    public static final Supplier<SoundEvent> AMBIENT_DRONE = register("ambient.drone");
    public static final Supplier<SoundEvent> AMBIENT_CREAK = register("ambient.creak");
    public static final Supplier<SoundEvent> AMBIENT_DRIP = register("ambient.drip");
    public static final Supplier<SoundEvent> PACIENTE_CRACK = register("paciente.crack");
    public static final Supplier<SoundEvent> RASTREADOR_SCREECH = register("rastreador.screech");
    public static final Supplier<SoundEvent> RASTREADOR_PANT = register("rastreador.pant");
    public static final Supplier<SoundEvent> RASTREADOR_SCRATCH = register("rastreador.scratch");

    private ModSounds() {}

    private static Supplier<SoundEvent> register(String name) {
        return Services.PLATFORM.register(BuiltInRegistries.SOUND_EVENT, name,
                () -> SoundEvent.createVariableRangeEvent(SmileProtocol.id(name)));
    }

    public static void init() {}
}
