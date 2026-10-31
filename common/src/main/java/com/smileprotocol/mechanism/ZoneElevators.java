package com.smileprotocol.mechanism;

import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.config.SPConfig;
import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.dimension.SPDimension;
import com.smileprotocol.hide.HideManager;
import com.smileprotocol.instance.InstanceManager;
import com.smileprotocol.instance.ZonePlacement;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import com.smileprotocol.player.SPInventory;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunManager;
import com.smileprotocol.session.SPHeal;
import com.smileprotocol.session.SPSessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ascensores de salida de las zonas (README 6.5). Funcionan cuando su circuito {@code exit} esta
 * encendido (o siempre, si la zona no tiene ese circuito). En cuanto un jugador vivo entra en la
 * cabina empieza la cuenta atras de gracia; el ascensor sale cuando estan todos o se acaba. Al
 * salir todos aparecen en la cabina de la zona siguiente, curados, y a los que estaban heridos o
 * fuera se les retiran los objetos ({@code elevator_strip}).
 */
public final class ZoneElevators {

    private static final String EXIT_CIRCUIT = "exit";
    private static final int UPDATE_EVERY = 5;

    static final class State {
        int zoneIndex = -1;
        boolean powered;
        boolean hasCircuit;
        boolean doorsOpen;
        int countdown = -1;
        int ride = -1;
        boolean moved;
        final Set<UUID> insideAtDeparture = new HashSet<>();
        final Set<UUID> hurtAtDeparture = new HashSet<>();
        final Set<UUID> showing = new HashSet<>();
        /** Planta siguiente que se esta construyendo durante el viaje. */
        @javax.annotation.Nullable com.smileprotocol.instance.InstanceManager.PlaceJob job;
        /** Posicion de cada uno dentro de la cabina al salir (dx, dy, dz, giro), para dejarlos igual al llegar. */
        final Map<UUID, double[]> offsets = new HashMap<>();
        int arriveAt = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ZoneElevators() {}

    public static void onCircuit(ServerLevel level, BlockPos near, String circuit, boolean powered) {
        if (!EXIT_CIRCUIT.equals(circuit) || !SPDimension.isSP(level)) return;
        Group g = InstanceManager.groupAt(level, near);
        if (g == null) return;
        State s = STATES.get(g.id());
        if (s != null) s.zoneIndex = -1; // recalcular
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(SPDimension.SP_DIM);
        if (level == null) return;
        boolean update = server.getTickCount() % UPDATE_EVERY == 0;
        Set<UUID> alive = new HashSet<>();
        for (Run run : RunManager.openRuns(server)) {
            for (Group g : run.groups()) {
                if (g.state().isDone() || g.current() == null) continue;
                alive.add(g.id());
                State s = STATES.computeIfAbsent(g.id(), k -> new State());
                if (s.ride >= 0) tickRide(server, level, g, s);
                else if (update) update(server, level, g, s);
            }
        }
        STATES.keySet().retainAll(alive);
    }

    private static void update(MinecraftServer server, ServerLevel level, Group g, State s) {
        ZonePlacement z = g.current();
        BoundingBox cabin = z.exitCabin();
        if (cabin == null) return;

        // Nueva zona: recalcula el circuito y cierra las puertas
        if (s.zoneIndex != g.zoneIndex()) {
            s.zoneIndex = g.zoneIndex();
            BlockPos center = cabin.getCenter();
            s.hasCircuit = CircuitManager.hasCircuit(level, center, EXIT_CIRCUIT);
            s.powered = !s.hasCircuit || CircuitManager.isPowered(level, center, EXIT_CIRCUIT);
            s.doorsOpen = !s.powered;
            s.countdown = -1;
        }
        // Laboratorio Cero: el ascensor no ha llegado todavia (README de Smiley)
        if (com.smileprotocol.entity.SmileyFight.holdsElevator(server, g)) {
            setDoors(level, z, s, false);
            hideHud(server, s);
            return;
        }
        setDoors(level, z, s, s.powered);
        if (!s.powered) {
            hideHud(server, s);
            return;
        }

        List<ServerPlayer> players = playing(server, g);
        List<ServerPlayer> inside = new ArrayList<>();
        BoundingBox area = cabin.inflatedBy(1);
        for (ServerPlayer p : players) {
            if (area.isInside(p.blockPosition()) && !HideManager.isHidden(p)) inside.add(p);
        }
        if (inside.isEmpty()) {
            if (s.countdown >= 0) s.countdown = -1;
            hideHud(server, s);
            return;
        }
        if (s.countdown < 0) {
            s.countdown = SPConfig.get().elevator_grace * 20;
            level.playSound(null, cabin.getCenter(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 0.8f);
        } else {
            s.countdown -= UPDATE_EVERY;
        }

        // la mision de la planta tiene que estar hecha (solo queda escapar)
        if (!com.smileprotocol.mission.MissionManager.readyToLeave(server, g)) {
            s.countdown = -1;
            Payloads.SyncReady wait = new Payloads.SyncReady(true, Component.translatable("smileprotocol.zone_elevator.title"),
                    inside.size(), players.size(), 0, Component.translatable("smileprotocol.mission.pending"), "");
            s.showing.clear();
            for (ServerPlayer p : players) {
                SPNetwork.sendTo(p, wait);
                s.showing.add(p.getUUID());
            }
            return;
        }
        boolean all = inside.size() >= players.size();
        int secs = Math.max(0, (s.countdown + 19) / 20);
        Component status = all ? Component.translatable("smileprotocol.zone_elevator.departing")
                : Component.translatable("smileprotocol.zone_elevator.waiting", secs);
        Payloads.SyncReady hud = new Payloads.SyncReady(true, Component.translatable("smileprotocol.zone_elevator.title"),
                inside.size(), players.size(), all ? 0 : secs, status, "");
        s.showing.clear();
        for (ServerPlayer p : players) {
            SPNetwork.sendTo(p, hud);
            s.showing.add(p.getUUID());
        }
        if (all || s.countdown <= 0) depart(server, level, g, s, players, inside);
    }

    private static List<ServerPlayer> playing(MinecraftServer server, Group g) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p) && g.id().equals(SPPlayerData.of(p).getGroupId())) out.add(p);
        }
        return out;
    }

    private static void depart0(MinecraftServer server, Group g) {
        com.smileprotocol.mission.MissionManager.onDepart(server, g);
    }

    /** Centro del suelo de la cabina de salida. */
    private static net.minecraft.world.phys.Vec3 cabinCenter(ZonePlacement z) {
        BoundingBox b = z.exitCabin();
        if (b == null) return z.entry();
        return new net.minecraft.world.phys.Vec3((b.minX() + b.maxX() + 1) / 2.0, b.minY(), (b.minZ() + b.maxZ() + 1) / 2.0);
    }

    /**
     * Sale el ascensor: los de dentro se quedan en la cabina, que baja de verdad (tiembla, zumba, la luz
     * parpadea) mientras se construye la planta siguiente. Los que se quedaron fuera lo ven todo negro.
     */
    private static void depart(MinecraftServer server, ServerLevel level, Group g, State s,
                               List<ServerPlayer> players, List<ServerPlayer> inside) {
        depart0(server, g);
        ZonePlacement z = g.current();
        setDoors(level, z, s, false);
        s.insideAtDeparture.clear();
        s.hurtAtDeparture.clear();
        s.offsets.clear();
        net.minecraft.world.phys.Vec3 c = cabinCenter(z);
        for (ServerPlayer p : inside) {
            s.insideAtDeparture.add(p.getUUID());
            s.offsets.put(p.getUUID(), new double[]{p.getX() - c.x, p.getY() - c.y, p.getZ() - c.z, p.getYRot(), p.getXRot()});
        }
        for (ServerPlayer p : players) if (p.getHealth() < p.getMaxHealth()) s.hurtAtDeparture.add(p.getUUID());
        s.ride = 0;
        s.moved = false;
        s.arriveAt = -1;
        s.countdown = -1;
        hideHud(server, s);
        // la planta siguiente se empieza a construir ahora (si no estaba ya)
        int next = g.zoneIndex() + 1;
        s.job = null;
        if (next <= g.lastIndex() && (g.next() == null || g.next().index() != next)) {
            s.job = com.smileprotocol.instance.InstanceManager.beginPlace(level, g, next, 1 - z.sub());
        }
        BlockPos center = BlockPos.containing(c);
        level.playSound(null, center, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1f, 0.5f);
        level.playSound(null, center, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9f, 0.6f);
        // la luz de la cabina parpadea durante el viaje
        BoundingBox cab = z.exitCabin();
        if (cab != null) {
            for (BlockPos lp : BlockPos.betweenClosed(cab.minX() - 1, cab.maxY(), cab.minZ() - 1, cab.maxX() + 1, cab.maxY() + 2, cab.maxZ() + 1)) {
                var st = level.getBlockState(lp);
                if (st.getBlock() instanceof com.smileprotocol.block.FluorescentLightBlock) {
                    level.setBlock(lp, st.setValue(com.smileprotocol.block.FluorescentLightBlock.FLICKERS, true), 3);
                }
            }
        }
        int total = Math.max(2, SPConfig.get().elevator_ride_time) * 20;
        for (ServerPlayer p : players) {
            if (!s.insideAtDeparture.contains(p.getUUID())) SPNetwork.sendTo(p, new Payloads.ScreenFade(15, total + 40, 20));
        }
        com.smileprotocol.labevent.LabActions.scare(inside, com.smileprotocol.labevent.LabActions.SCARE_SHAKE, 0.6f);
        SPDebug.log(g.label() + ": ascensor de la zona " + g.zoneIndex() + " sale con " + inside.size() + "/" + players.size()
                + (s.job != null ? " (construyendo la zona " + next + ")" : ""));
    }

    /** El viaje: la cabina baja, se construye la planta y, cuando esta y ha pasado el tiempo minimo, se llega. */
    private static void tickRide(MinecraftServer server, ServerLevel level, Group g, State s) {
        s.ride++;
        int total = Math.max(2, SPConfig.get().elevator_ride_time) * 20;
        if (s.job != null && !s.job.blocksDone()) {
            com.smileprotocol.instance.InstanceManager.stepPlace(level, s.job, Math.max(1, SPConfig.get().zone_build_slices_per_tick));
        }
        List<ServerPlayer> riders = new ArrayList<>();
        for (UUID id : s.insideAtDeparture) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p)) riders.add(p);
        }
        ZonePlacement z = g.current();
        BlockPos center = BlockPos.containing(cabinCenter(z));
        if (s.arriveAt < 0) {
            // zumbido del motor, traqueteo y algun golpe de los cables
            if (s.ride % 40 == 1) level.playSound(null, center, SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.35f, 0.45f);
            if (s.ride % 25 == 0) com.smileprotocol.labevent.LabActions.scare(riders, com.smileprotocol.labevent.LabActions.SCARE_SHAKE, 0.12f);
            if (s.ride % 97 == 50) level.playSound(null, center, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.5f, 0.5f);
            if (s.ride % 10 == 0) {
                float built = s.job == null ? 1f : s.job.progress();
                int pct = Math.round(Math.min(built, Math.min(1f, s.ride / (float) total)) * 100);
                Component bar = Component.translatable("smileprotocol.zone_elevator.riding", g.zoneIndex() + 2, pct);
                for (ServerPlayer p : riders) p.displayClientMessage(bar, true);
            }
            boolean ready = s.job == null || s.job.blocksDone();
            if (s.ride >= total && ready) {
                // frena: golpe y un fundido cortito para cambiar de cabina sin que se note
                if (s.job != null) {
                    ZonePlacement p = com.smileprotocol.instance.InstanceManager.finishPlace(level, s.job);
                    g.setNext(p);
                    s.job = null;
                }
                level.playSound(null, center, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 0.5f);
                com.smileprotocol.labevent.LabActions.scare(riders, com.smileprotocol.labevent.LabActions.SCARE_SHAKE, 0.5f);
                for (ServerPlayer p : riders) SPNetwork.sendTo(p, new Payloads.ScreenFade(4, 6, 10));
                s.arriveAt = s.ride + 5;
            }
            return;
        }
        if (s.ride >= s.arriveAt && !s.moved) {
            s.moved = true;
            arrive(server, level, g, s);
            s.ride = -1;
        }
    }

    /** Pone a cada uno en la cabina de llegada donde estaba en la de salida (la cabina gira media vuelta). */
    private static void placeRiders(MinecraftServer server, ServerLevel level, Group g, State s) {
        ZonePlacement z = g.current();
        if (z == null) return;
        net.minecraft.world.phys.Vec3 e = z.entry();
        for (Map.Entry<UUID, double[]> en : s.offsets.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !SPPlayerData.inSP(p) || !g.isActive(p.getUUID())) continue;
            double[] o = en.getValue();
            double dx = net.minecraft.util.Mth.clamp(-o[0], -2.2, 2.2), dz = net.minecraft.util.Mth.clamp(-o[2], -2.2, 2.2);
            com.smileprotocol.session.SPSessionManager.teleport(p, level, new net.minecraft.world.phys.Vec3(e.x + dx, e.y, e.z + dz),
                    (float) o[3] + 180f, (float) o[4]);
        }
        level.playSound(null, BlockPos.containing(e), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.2f);
    }

    /** Llegada a la zona siguiente: curar, penalizar, teletransportar (README 6.5, puntos 3 a 7). */
    private static void arrive(MinecraftServer server, ServerLevel level, Group g, State s) {
        String mode = SPConfig.get().elevator_strip.toUpperCase(Locale.ROOT);
        // Los muertos del grupo vuelven en la cabina (README 6.5, punto 3): antes de cambiar de zona,
        // para que los lleve con los demas
        for (UUID id : new ArrayList<>(g.members().keySet())) {
            if (!g.isDead(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && SPPlayerData.inSP(p)) com.smileprotocol.downed.DeathManager.reviveAtElevator(p);
        }
        for (UUID id : g.activeMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !SPPlayerData.inSP(p)) continue;
            HideManager.forceExit(p, false);
            com.smileprotocol.downed.DownedManager.clear(p);
            boolean outside = !s.insideAtDeparture.contains(id);
            boolean hurt = s.hurtAtDeparture.contains(id);
            boolean strip = switch (mode) {
                case "ALL" -> true;
                case "REVIVED_ONLY" -> outside;
                default -> outside || hurt;
            };
            if (strip) stripItems(p);
            SPHeal.heal(p, p.getMaxHealth());
            // temblor de la cabina al frenar
            com.smileprotocol.labevent.LabActions.scare(List.of(p), com.smileprotocol.labevent.LabActions.SCARE_SHAKE, 0.5f);
        }
        int next = g.zoneIndex() + 1;
        Component result = RunManager.setZone(server, g, next);
        // El rotulo de la planta lo pone InstanceManager al llegar (FloorTitle)
        SPDebug.log(result.getString());
        if (!g.state().isDone()) placeRiders(server, level, g, s);
    }

    /** Quita curaciones y objetos; se conserva el equipo base (linterna y walkie, README 6.5). */
    private static void stripItems(ServerPlayer p) {
        SPInventory inv = SPPlayerData.of(p).getSPInventory();
        boolean any = false;
        for (int i = 0; i < SPInventory.SIZE; i++) {
            ItemStack st = inv.get(i);
            if (st.isEmpty() || isBaseEquipment(st)) continue;
            inv.set(i, ItemStack.EMPTY);
            any = true;
        }
        if (any) {
            SPSessionManager.sync(p);
            p.displayClientMessage(Component.translatable("smileprotocol.zone_elevator.stripped").withStyle(ChatFormatting.RED), false);
        }
    }

    public static boolean isBaseEquipment(ItemStack st) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return path.equals("flashlight") || path.equals("walkie");
    }

    private static void setDoors(ServerLevel level, ZonePlacement z, State s, boolean open) {
        if (s.doorsOpen == open) return;
        s.doorsOpen = open;
        List<BlockPos> doors = z.marker("exit_door");
        for (BlockPos d : doors) ElevatorDoorBlock.setOpen(level, d, open);
        if (!doors.isEmpty()) {
            level.playSound(null, doors.get(0), open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 0.8f, 0.7f);
        }
    }

    private static void hideHud(MinecraftServer server, State s) {
        if (s.showing.isEmpty()) return;
        for (UUID id : s.showing) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) SPNetwork.sendTo(p, Payloads.SyncReady.hidden());
        }
        s.showing.clear();
    }

    public static List<String> debugLines(Group g) {
        State s = STATES.get(g.id());
        if (s == null) return List.of();
        return List.of(" ascensor salida: " + (s.powered ? "§acon corriente" : "§csin corriente")
                + (s.countdown >= 0 ? " §7cuenta " + (s.countdown / 20) + "s" : "") + (s.ride >= 0 ? " §7en viaje" : ""));
    }
}
