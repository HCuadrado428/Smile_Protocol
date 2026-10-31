package com.smileprotocol.item;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Senuelos: el despertador que se deja en el suelo. Suena cada segundo y medio durante un rato y hace
 * ruido como una lata (los smilers van a mirar). Se ve como el propio objeto en el suelo (una entidad de
 * visualizacion de objeto, sin colision) y desaparece al acabar o al limpiar la planta.
 */
public final class Decoys {

    /** Cada cuanto suena (ticks). */
    public static final int RING_EVERY = 30;
    /** Radio del ruido de cada timbrazo. */
    public static final float NOISE = 18f;

    private static final class Decoy {
        final ServerLevel level;
        final Vec3 pos;
        final long until;
        final Entity display;

        Decoy(ServerLevel level, Vec3 pos, long until, Entity display) {
            this.level = level;
            this.pos = pos;
            this.until = until;
            this.display = display;
        }
    }

    private static final List<Decoy> ACTIVE = new ArrayList<>();

    private Decoys() {}

    /** Deja un despertador a los pies del jugador, un poco por delante, sonando {@code ticks}. */
    public static boolean place(ServerPlayer p, int ticks) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 at = new Vec3(p.getX() + look.x * 0.7, Math.floor(p.getY()) + 0.01, p.getZ() + look.z * 0.7);
        return place(level, at, ticks, p.getYRot()) != null;
    }

    public static Entity place(ServerLevel level, Vec3 at, int ticks, float yaw) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:item_display");
        t.put("item", new ItemStack(ModItems.DECOY.get()).save(level.registryAccess()));
        t.putString("item_display", "ground");
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0.18f, 0));
        tr.put("scale", floats(0.75f, 0.75f, 0.75f));
        t.put("transformation", tr);
        Entity e = EntityType.create(t, level).orElse(null);
        if (e == null) return null;
        e.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(e);
        ACTIVE.add(new Decoy(level, at, level.getGameTime() + ticks, e));
        level.playSound(null, e.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.4f);
        SPDebug.log("senuelo en " + e.blockPosition().toShortString() + " durante " + ticks + " ticks");
        return e;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    public static int active() {
        return ACTIVE.size();
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Decoy> it = ACTIVE.iterator(); it.hasNext(); ) {
            Decoy d = it.next();
            long now = d.level.getGameTime();
            if (d.display.isRemoved() || now >= d.until) {
                if (!d.display.isRemoved()) {
                    d.level.playSound(null, d.display.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 0.8f);
                    d.display.discard();
                }
                it.remove();
                continue;
            }
            if (now % RING_EVERY == 0) {
                // dos timbrazos seguidos, como un despertador de campanas
                d.level.playSound(null, d.display.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.4f, 1.6f);
                d.level.playSound(null, d.display.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.4f, 1.9f);
                NoiseSystem.emit(d.level, d.pos, NOISE, NoiseType.ITEM, null);
                d.display.setYRot(d.display.getYRot() + (now / RING_EVERY % 2 == 0 ? 12 : -12));
            }
        }
    }
}
