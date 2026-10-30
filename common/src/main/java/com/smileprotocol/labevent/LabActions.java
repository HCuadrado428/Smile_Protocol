package com.smileprotocol.labevent;

import com.google.gson.JsonObject;
import com.smileprotocol.block.FluorescentLightBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalogo unico de acciones de los eventos de laboratorio y de las misiones (README 14.7, 19):
 * sustos, luces, alarmas, sonidos, puertas, circuitos, ruido que atrae mobs, mobs, mensajes de
 * radio, efectos de camara y dano ambiental. Las acciones que duran (parpadeo, apagon, alarma)
 * se programan en {@link LabEventManager}.
 */
public final class LabActions {

    /** Tipos de susto que llegan al cliente ({@code PlayJumpscare}). */
    public static final int SCARE_FACE = 0, SCARE_FLASH = 1, SCARE_SHAKE = 2, SCARE_AUDIO = 3;

    private LabActions() {}

    /** Contexto de una accion: donde, quien la provoco y a quien afecta. */
    public record Ctx(ServerLevel level, ZonePlacement zone, BlockPos pos, @Nullable ServerPlayer trigger,
                      List<ServerPlayer> targets, List<ServerPlayer> group) {}

    /** Ejecuta una accion. Devuelve true si es una amenaza real (mob o ruido). */
    public static boolean run(Ctx c, JsonObject a) {
        String type = s(a, "type", "").toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "jumpscare" -> jumpscare(c, a);
            case "light_flicker" -> LabEventManager.schedule(c, "flicker", i(a, "ticks", 60), s(a, "circuit", ""));
            case "blackout" -> LabEventManager.schedule(c, "blackout", i(a, "ticks", 200), s(a, "circuit", ""));
            case "lights_off", "lights_on" -> setLights(c, "lights_on".equals(type), s(a, "circuit", ""), 1f);
            case "alarm" -> LabEventManager.schedule(c, "alarm", i(a, "ticks", 60), "");
            case "sound" -> sound(c, s(a, "id", s(a, "sound", "")), a.has("ambient") && a.get("ambient").getAsBoolean(), f(a, "volume", 1.2f));
            case "lock_door", "unlock_door" -> doors(c, "lock_door".equals(type), s(a, "door", ""));
            case "door_slam" -> slam(c);
            case "set_circuit", "power" -> CircuitManager.notify(c.level(), c.zone().box().getCenter(), s(a, "circuit", "exit"),
                    !a.has("powered") || a.get("powered").getAsBoolean());
            case "noise_burst" -> {
                NoiseSystem.emit(c.level(), Vec3.atCenterOf(c.pos()), f(a, "radius", 14f), NoiseType.EVENT, null);
                return true;
            }
            case "spawn_mob" -> {
                spawnMob(c, s(a, "mob", "berserker"));
                return true;
            }
            case "message", "radio_message" -> {
                Component msg = Component.translatable(s(a, "text", "")).withStyle(
                        "radio_message".equals(type) ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
                for (ServerPlayer p : c.targets()) p.displayClientMessage(msg, false);
                if ("radio_message".equals(type)) sound(c, "minecraft:block.note_block.didgeridoo", true, 0.4f);
            }
            case "camera_effect" -> scare(c.targets(), SCARE_SHAKE, f(a, "strength", 1f));
            case "ambient_damage" -> {
                float dmg = f(a, "amount", 4f);
                for (ServerPlayer p : c.targets()) p.hurt(c.level().damageSources().magic(), dmg);
            }
            case "start_sequence" -> SPDebug.log("evento: start_sequence aun no tiene uso (necesita un mob)");
            default -> SPDebug.log("evento: accion desconocida " + type);
        }
        return false;
    }

    // Sustos -------------------------------------------------------------------------------------

    private static void jumpscare(Ctx c, JsonObject a) {
        String kind = s(a, "kind", "visual").toLowerCase(java.util.Locale.ROOT);
        List<ServerPlayer> who = new ArrayList<>();
        for (ServerPlayer p : c.targets()) if (LabEventManager.budget(p)) who.add(p);
        if (who.isEmpty()) return;
        switch (kind) {
            case "audio" -> {
                for (ServerPlayer p : who) {
                    SPNetwork.sendTo(p, new Payloads.PlayJumpscare(SCARE_AUDIO, 1f));
                }
            }
            case "flash" -> scare(who, SCARE_FLASH, f(a, "strength", 1f));
            case "peek" -> peek(c, who);
            case "environment" -> {
                slam(c);
                setLights(c, false, "", 0.5f);
                LabEventManager.schedule(c, "relight", 60, "");
            }
            default -> scare(who, SCARE_FACE, f(a, "strength", 1f));
        }
    }

    public static void scare(List<ServerPlayer> who, int kind, float strength) {
        for (ServerPlayer p : who) SPNetwork.sendTo(p, new Payloads.PlayJumpscare(kind, strength));
    }

    /** Un smiler se asoma un instante a cierta distancia, delante del jugador, y desaparece. No ataca. */
    private static void peek(Ctx c, List<ServerPlayer> who) {
        ServerPlayer p = who.get(0);
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        for (int d = 12; d >= 6; d -= 2) {
            Vec3 at = p.position().add(look.scale(d)).add(look.z * (d % 4 == 0 ? 2 : -2), 0, -look.x * (d % 4 == 0 ? 2 : -2));
            BlockPos bp = BlockPos.containing(at);
            if (!c.level().getBlockState(bp).isAir() || !c.level().getBlockState(bp.above()).isAir()
                    || c.level().getBlockState(bp.below()).isAir() || !c.zone().box().isInside(bp)) continue;
            var mob = com.smileprotocol.registry.ModEntities.SMILER_TEST.get().create(c.level());
            if (mob == null) return;
            float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z));
            mob.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, yaw, 0);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setNoAi(true);
            mob.setSilent(true);
            c.level().addFreshEntity(mob);
            LabEventManager.despawnLater(mob, 25);
            c.level().playSound(null, bp, ModSounds.BERSERKER_BREATH.get(), SoundSource.HOSTILE, 0.8f, 1.3f);
            return;
        }
    }

    // Entorno ------------------------------------------------------------------------------------

    /** Enciende o apaga los fluorescentes de la planta (o de un circuito); {@code part} = fraccion. */
    public static void setLights(Ctx c, boolean on, String circuit, float part) {
        CircuitManager.forEachBlockEntity(c.level(), c.zone().box(), be -> {
            if (!(be instanceof FluorescentLightBlockEntity fl)) return;
            if (!circuit.isEmpty() && !circuit.equals(fl.circuitId())) return;
            if (part < 1f && c.level().random.nextFloat() > part) return;
            BlockState st = fl.getBlockState();
            if (st.hasProperty(FluorescentLightBlock.LIT) && st.getValue(FluorescentLightBlock.LIT) != on) {
                c.level().setBlock(fl.getBlockPos(), st.setValue(FluorescentLightBlock.LIT, on), 3);
            }
        });
    }

    private static void sound(Ctx c, String id, boolean ambient, float volume) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        SoundEvent snd = rl == null ? null : BuiltInRegistries.SOUND_EVENT.get(rl);
        if (snd == null) return;
        if (ambient) {
            for (ServerPlayer p : c.targets()) c.level().playSound(null, p.blockPosition(), snd, SoundSource.AMBIENT, volume, 1f);
        } else {
            c.level().playSound(null, c.pos(), snd, SoundSource.BLOCKS, volume, 1f);
        }
    }

    private static void doors(Ctx c, boolean lock, String param) {
        CircuitManager.forEachBlockEntity(c.level(), c.zone().box(), be -> {
            if (!(be instanceof LabDoorBlockEntity d) || !d.param().equals(param)) return;
            if (lock) {
                com.smileprotocol.block.LabDoorBlock.setOpenAt(c.level(), d.getBlockPos(), false);
                d.configure(DoorType.LOCKED, param, false);
            } else {
                d.unlock();
                com.smileprotocol.block.LabDoorBlock.setOpenAt(c.level(), d.getBlockPos(), true);
            }
        });
    }

    /** La puerta abierta mas cercana se cierra de golpe. */
    private static void slam(Ctx c) {
        BlockPos[] best = {null};
        double[] bd = {64 * 64};
        CircuitManager.forEachBlockEntity(c.level(), c.zone().box(), be -> {
            if (!(be instanceof LabDoorBlockEntity d)) return;
            BlockState st = d.getBlockState();
            if (!st.hasProperty(DoorBlock.OPEN) || !st.getValue(DoorBlock.OPEN)) return;
            double dist = d.getBlockPos().distSqr(c.pos());
            if (dist < bd[0]) {
                bd[0] = dist;
                best[0] = d.getBlockPos();
            }
        });
        if (best[0] == null) return;
        com.smileprotocol.block.LabDoorBlock.setOpenAt(c.level(), best[0], false);
        c.level().playSound(null, best[0], net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.6f);
        c.level().playSound(null, best[0], net.minecraft.sounds.SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 1f, 0.7f);
    }

    private static void spawnMob(Ctx c, String id) {
        List<BlockPos> spawns = c.zone().mobSpawns();
        if (spawns.isEmpty()) return;
        BlockPos best = spawns.get(0);
        double bd = -1;
        for (BlockPos b : spawns) {
            double d = Double.MAX_VALUE;
            for (ServerPlayer p : c.group()) d = Math.min(d, p.blockPosition().distSqr(b));
            if (d > bd) {
                bd = d;
                best = b;
            }
        }
        var type = switch (id) {
            case "smiler_test" -> com.smileprotocol.registry.ModEntities.SMILER_TEST.get();
            case "smiler_kill" -> com.smileprotocol.registry.ModEntities.SMILER_KILL.get();
            case "ahorcado" -> com.smileprotocol.registry.ModEntities.AHORCADO.get();
            case "apagon" -> com.smileprotocol.registry.ModEntities.APAGON.get();
            case "rastreador" -> com.smileprotocol.registry.ModEntities.RASTREADOR.get();
            case "paciente" -> com.smileprotocol.registry.ModEntities.PACIENTE.get();
            case "smiley" -> com.smileprotocol.registry.ModEntities.SMILEY.get();
            default -> com.smileprotocol.registry.ModEntities.BERSERKER.get();
        };
        com.smileprotocol.entity.MobManager.spawn(c.level(), type, best, c.level().random);
    }

    static String s(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
}
