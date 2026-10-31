package com.smileprotocol.run;

import com.smileprotocol.difficulty.DifficultyProfile;
import com.smileprotocol.instance.ZonePlacement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un grupo de jugadores dentro de una partida (README 6.3). Juega en su propio hueco de la
 * dimension de instancias, con su propia copia de las zonas.
 */
public final class Group {

    private final UUID id;
    private final String runId;
    /** Numero dentro de la partida (1, 2...). */
    private final int number;
    private final Map<UUID, MemberStatus> members = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final List<ResourceLocation> sequence;
    private final DifficultyProfile difficulty;
    private final int slot;

    private GroupState state = GroupState.INTRO;
    private int zoneIndex;
    @Nullable private ZonePlacement current;
    @Nullable private ZonePlacement next;
    /** Tiempo de juego desde el que no queda ningun miembro activo conectado (-1 = alguno lo esta). */
    private long offlineSince = -1;
    private long endTime = -1;
    /** Dificultad y modificadores elegidos en el ascensor. */
    private com.smileprotocol.difficulty.RunRules rules = com.smileprotocol.difficulty.RunRules.DEFAULT;

    public Group(UUID id, String runId, int number, List<ResourceLocation> sequence, DifficultyProfile difficulty, int slot) {
        this.id = id;
        this.runId = runId;
        this.number = number;
        this.sequence = List.copyOf(sequence);
        this.difficulty = difficulty;
        this.slot = slot;
    }

    public UUID id() { return id; }
    public String runId() { return runId; }
    public int number() { return number; }
    /** Nombre corto para comandos: {@code R3.1} = partida R3, grupo 1. */
    public String label() { return runId + "." + number; }
    public List<ResourceLocation> sequence() { return sequence; }
    public DifficultyProfile difficulty() { return difficulty; }
    public int slot() { return slot; }
    public com.smileprotocol.difficulty.RunRules rules() { return rules; }

    // Mision de la planta en curso (se guarda para sobrevivir a un reinicio del servidor)
    private int missionZone = -1;
    private int missionStep;
    private final java.util.Set<String> missionFacts = new java.util.LinkedHashSet<>();

    public int missionZone() { return missionZone; }
    public int missionStep() { return missionStep; }
    public java.util.Set<String> missionFacts() { return missionFacts; }

    /** Guarda el progreso de la mision. Devuelve true si ha cambiado. */
    public boolean setMission(int zone, int step, java.util.Set<String> facts) {
        if (zone == missionZone && step == missionStep && facts.equals(missionFacts)) return false;
        missionZone = zone;
        missionStep = step;
        missionFacts.clear();
        missionFacts.addAll(facts);
        return true;
    }
    public void setRules(com.smileprotocol.difficulty.RunRules rules) { this.rules = rules; }

    public void addMember(UUID player, String name) {
        members.put(player, MemberStatus.ACTIVE);
        names.put(player, name);
    }

    public Map<UUID, MemberStatus> members() { return members; }
    public String name(UUID player) { return names.getOrDefault(player, player.toString().substring(0, 8)); }
    public boolean isMember(UUID player) { return members.containsKey(player); }

    public boolean isActive(UUID player) { return members.get(player) == MemberStatus.ACTIVE; }

    /** Sigue en la partida: vivo o muerto espectando (README 9.3). */
    public boolean isInRun(UUID player) {
        MemberStatus st = members.get(player);
        return st == MemberStatus.ACTIVE || st == MemberStatus.DEAD;
    }

    public boolean isDead(UUID player) { return members.get(player) == MemberStatus.DEAD; }

    public void setStatus(UUID player, MemberStatus status) {
        if (members.containsKey(player)) members.put(player, status);
    }

    public List<UUID> activeMembers() {
        List<UUID> l = new ArrayList<>();
        members.forEach((k, v) -> { if (v == MemberStatus.ACTIVE) l.add(k); });
        return l;
    }

    public GroupState state() { return state; }
    public void setState(GroupState state) { this.state = state; }

    public int zoneIndex() { return zoneIndex; }
    public void setZoneIndex(int zoneIndex) { this.zoneIndex = zoneIndex; }
    public int lastIndex() { return sequence.size() - 1; }

    @Nullable public ZonePlacement current() { return current; }
    public void setCurrent(@Nullable ZonePlacement p) { this.current = p; }
    @Nullable public ZonePlacement next() { return next; }
    public void setNext(@Nullable ZonePlacement p) { this.next = p; }

    public long offlineSince() { return offlineSince; }
    public void setOfflineSince(long t) { this.offlineSince = t; }

    public long endTime() { return endTime; }
    public void setEndTime(long t) { this.endTime = t; }

    /** Estado legible: «zona 2», «pasillo», «final»... */
    public String stateLabel() {
        return switch (state) {
            case INTRO -> "pasillo";
            case ZONE -> "zona " + zoneIndex;
            case FINAL -> "final";
            case COMPLETED -> "completado";
            case ELIMINATED -> "eliminado";
        };
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Run", runId);
        t.putInt("Number", number);
        ListTag m = new ListTag();
        members.forEach((uuid, status) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", uuid);
            e.putString("Status", status.name());
            e.putString("Name", name(uuid));
            m.add(e);
        });
        t.put("Members", m);
        ListTag seq = new ListTag();
        for (ResourceLocation r : sequence) seq.add(StringTag.valueOf(r.toString()));
        t.put("Sequence", seq);
        t.put("Difficulty", difficulty.save());
        t.putString("Rules", rules.encode());
        if (missionZone >= 0) {
            CompoundTag mission = new CompoundTag();
            mission.putInt("Zone", missionZone);
            mission.putInt("Step", missionStep);
            ListTag facts = new ListTag();
            for (String fact : missionFacts) facts.add(StringTag.valueOf(fact));
            mission.put("Facts", facts);
            t.put("Mission", mission);
        }
        t.putInt("Slot", slot);
        t.putString("State", state.name());
        t.putInt("ZoneIndex", zoneIndex);
        if (current != null) t.put("Current", current.save());
        if (next != null) t.put("Next", next.save());
        t.putLong("OfflineSince", offlineSince);
        t.putLong("EndTime", endTime);
        return t;
    }

    public static Group load(CompoundTag t) {
        List<ResourceLocation> seq = new ArrayList<>();
        ListTag s = t.getList("Sequence", Tag.TAG_STRING);
        for (int i = 0; i < s.size(); i++) {
            ResourceLocation r = ResourceLocation.tryParse(s.getString(i));
            if (r != null) seq.add(r);
        }
        Group g = new Group(t.getUUID("Id"), t.getString("Run"), t.getInt("Number"), seq,
                DifficultyProfile.load(t.getCompound("Difficulty")), t.getInt("Slot"));
        ListTag m = t.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < m.size(); i++) {
            CompoundTag e = m.getCompound(i);
            UUID uuid = e.getUUID("Id");
            g.names.put(uuid, e.getString("Name"));
            MemberStatus st;
            try {
                st = MemberStatus.valueOf(e.getString("Status"));
            } catch (IllegalArgumentException ex) {
                st = MemberStatus.ACTIVE;
            }
            g.members.put(uuid, st);
        }
        try {
            g.state = GroupState.valueOf(t.getString("State"));
        } catch (IllegalArgumentException ex) {
            g.state = GroupState.INTRO;
        }
        g.zoneIndex = t.getInt("ZoneIndex");
        g.rules = com.smileprotocol.difficulty.RunRules.decode(t.getString("Rules"));
        if (t.contains("Mission")) {
            CompoundTag mission = t.getCompound("Mission");
            g.missionZone = mission.getInt("Zone");
            g.missionStep = mission.getInt("Step");
            ListTag facts = mission.getList("Facts", Tag.TAG_STRING);
            for (int k = 0; k < facts.size(); k++) g.missionFacts.add(facts.getString(k));
        }
        if (t.contains("Current")) g.current = ZonePlacement.load(t.getCompound("Current"));
        if (t.contains("Next")) g.next = ZonePlacement.load(t.getCompound("Next"));
        g.offlineSince = t.getLong("OfflineSince");
        g.endTime = t.contains("EndTime") ? t.getLong("EndTime") : -1;
        return g;
    }
}
