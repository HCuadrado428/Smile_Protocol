package com.smileprotocol.entity;

import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.downed.DeathManager;
import com.smileprotocol.downed.DownedManager;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.motion.Flashlights;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModSounds;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.sequence.SequenceManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El Gemelo: copia a un jugador del grupo (su piel y su nombre) y anda entre ellos como uno mas, sin
 * linterna. Se acerca pero guarda la distancia y se queda mirando. Se delata si alguien se le acerca
 * mucho, si le enfocan con la linterna un par de segundos o si pasa demasiado rato: se le ve la cara de
 * verdad, chilla y corre hacia el mas cercano. Al alcanzarlo lo derriba y se desvanece. Uno por planta.
 */
public class Gemelo extends SmilerBase {

    public enum State { STALK, REVEAL, CHASE }

    private static final EntityDataAccessor<Optional<UUID>> COPY =
            SynchedEntityData.defineId(Gemelo.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Gemelo.class, EntityDataSerializers.BYTE);

    @Nullable private ServerPlayer target;
    private int timer;
    private int stalkTicks;
    private int lit;
    private int repath;

    public Gemelo(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(COPY, Optional.empty());
        b.define(STATE, (byte) 0);
    }

    @Override
    public String defId() {
        return "gemelo";
    }

    public State state() {
        return State.values()[Math.floorMod(entityData.get(STATE), State.values().length)];
    }

    private void setState(State s) {
        entityData.set(STATE, (byte) s.ordinal());
        timer = 0;
    }

    /** Jugador al que copia (piel y nombre), o null si aun no ha elegido. */
    @Nullable
    public UUID copied() {
        return entityData.get(COPY).orElse(null);
    }

    public boolean revealed() {
        return state() != State.STALK;
    }

    /** Copia a un jugador: su piel (en el cliente) y su nombre encima de la cabeza. */
    public void copy(ServerPlayer p) {
        entityData.set(COPY, Optional.of(p.getUUID()));
        setCustomName(Component.literal(p.getGameProfile().getName()));
        setCustomNameVisible(true);
        SPDebug.log("gemelo copia a " + p.getGameProfile().getName());
    }

    /** Pruebas: delatarse ya. */
    public void forceReveal() {
        if (state() == State.STALK) reveal(null);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || sequence() != null) return;
        timer++;
        List<ServerPlayer> players = players(level);
        if (copied() == null) {
            if (players.isEmpty()) return;
            // copia a alguien del grupo de esa planta (en solitario, al propio jugador)
            ServerPlayer near = players.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            Group g = near == null ? null : RunManager.groupOf(near);
            List<ServerPlayer> pool = new ArrayList<>();
            if (g != null) for (UUID id : g.activeMembers()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) pool.add(p);
            }
            if (pool.isEmpty()) pool.addAll(players);
            copy(pool.get(getRandom().nextInt(pool.size())));
        }
        switch (state()) {
            case STALK -> stalk(level, players);
            case REVEAL -> {
                getNavigation().stop();
                if (target != null) getLookControl().setLookAt(target, 60, 60);
                if (timer >= 24) setState(State.CHASE);
            }
            case CHASE -> chase(level, players);
        }
    }

    private void stalk(ServerLevel level, List<ServerPlayer> players) {
        ServerPlayer near = players.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (near == null) {
            getNavigation().stop();
            return;
        }
        double d = distanceTo(near);
        getLookControl().setLookAt(near, 20, 20);
        if (d < 24) stalkTicks++;
        // se delata: alguien muy cerca, le enfocan con la linterna o pasa demasiado rato
        if (d < SPConfig.get().gemelo_reveal_distance) {
            reveal(near);
            return;
        }
        boolean litNow = false;
        for (ServerPlayer p : players) {
            if (!Flashlights.isOn(p) || distanceTo(p) > 9 || !p.hasLineOfSight(this)) continue;
            Vec3 to = getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
            if (p.getViewVector(1f).dot(to) > 0.93) litNow = true;
        }
        lit = litNow ? lit + 1 : Math.max(0, lit - 1);
        if (lit >= 40 || stalkTicks >= SPConfig.get().gemelo_patience * 20) {
            reveal(near);
            return;
        }
        // acercarse hasta unos 9 bloques y quedarse mirando; si se le acercan, se aparta despacio
        if (--repath > 0) return;
        repath = 20;
        if (d > 11) {
            getNavigation().moveTo(near, 0.9);
        } else if (d < 7) {
            Vec3 away = position().subtract(near.position()).normalize().scale(5);
            Vec3 to = position().add(away);
            getNavigation().moveTo(to.x, to.y, to.z, 0.8);
        } else {
            getNavigation().stop();
        }
    }

    private void reveal(@Nullable ServerPlayer near) {
        target = near;
        setState(State.REVEAL);
        setCustomNameVisible(false);
        setCustomName(null);
        level().playSound(null, blockPosition(), ModSounds.SMILEY_SCREAM.get(), SoundSource.HOSTILE, 1.4f, 1.5f);
        if (level() instanceof ServerLevel sl) {
            for (ServerPlayer p : players(sl)) {
                if (distanceTo(p) < 14 && p.hasLineOfSight(this)) SPNetwork.sendTo(p, new Payloads.PlayJumpscare(0, 0.5f));
            }
        }
        SPDebug.log("gemelo se delata" + (near != null ? " ante " + near.getGameProfile().getName() : ""));
    }

    private void chase(ServerLevel level, List<ServerPlayer> players) {
        if (target == null || !valid(target)) {
            target = players.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (target == null) {
                if (timer > 200) vanish(level);
                return;
            }
        }
        if (--repath <= 0) {
            repath = 5;
            getNavigation().moveTo(target, 1.55);
        }
        if (distanceTo(target) < 1.7 && hasLineOfSight(target)) {
            DownedManager.down(target, "gemelo");
            vanish(level);
            return;
        }
        if (timer > SPConfig.get().gemelo_chase_seconds * 20) vanish(level);
    }

    /** Se desvanece en humo (no vuelve en esta planta). */
    public void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, blockPosition(), ModSounds.SMILEY_SMEAR.get(), SoundSource.HOSTILE, 1f, 1.3f);
        discard();
    }

    private List<ServerPlayer> players(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48), this::valid);
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && SPPlayerData.inSP(p) && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && !DeathManager.isDead(p) && !DownedManager.isDowned(p) && !HideManager.isHidden(p)
                && !SequenceManager.inSequence(p);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID c = copied();
        if (c != null) tag.putUUID("Copy", c);
        tag.putByte("State", entityData.get(STATE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Copy")) entityData.set(COPY, Optional.of(tag.getUUID("Copy")));
        entityData.set(STATE, tag.getByte("State"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // se dibuja con el modelo de jugador (GemeloRenderer), no con GeckoLib
    }
}
