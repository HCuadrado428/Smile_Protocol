package com.smileprotocol.run;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una partida (README 6.1): su tipo, el ascensor del que salio, la semilla y la secuencia de
 * zonas, y uno o varios grupos que juegan a la vez y de forma independiente.
 */
public final class Run {

    private final String id;
    private final RunType type;
    private final String elevatorId;
    private final long seed;
    private final List<ResourceLocation> sequence;
    private final List<Group> groups = new ArrayList<>();
    private RunState state = RunState.DESCENT;
    private final long startTime;
    private long endTime = -1;
    /** Ya se ha devuelto a todos al lobby y se han liberado las instancias. */
    private boolean closed;

    public Run(String id, RunType type, String elevatorId, long seed, List<ResourceLocation> sequence, long startTime) {
        this.id = id;
        this.type = type;
        this.elevatorId = elevatorId;
        this.seed = seed;
        this.sequence = List.copyOf(sequence);
        this.startTime = startTime;
    }

    public String id() { return id; }
    public RunType type() { return type; }
    public String elevatorId() { return elevatorId; }
    public long seed() { return seed; }
    public List<ResourceLocation> sequence() { return sequence; }
    public List<Group> groups() { return groups; }
    public RunState state() { return state; }
    public void setState(RunState state) { this.state = state; }
    public long startTime() { return startTime; }
    public long endTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public boolean closed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    @Nullable
    public Group group(UUID id) {
        for (Group g : groups) if (g.id().equals(id)) return g;
        return null;
    }

    public boolean allGroupsDone() {
        for (Group g : groups) if (!g.state().isDone()) return false;
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Type", type.name());
        t.putString("Elevator", elevatorId);
        t.putLong("Seed", seed);
        ListTag seq = new ListTag();
        for (ResourceLocation r : sequence) seq.add(StringTag.valueOf(r.toString()));
        t.put("Sequence", seq);
        ListTag gs = new ListTag();
        for (Group g : groups) gs.add(g.save());
        t.put("Groups", gs);
        t.putString("State", state.name());
        t.putLong("Start", startTime);
        t.putLong("End", endTime);
        t.putBoolean("Closed", closed);
        return t;
    }

    public static Run load(CompoundTag t) {
        List<ResourceLocation> seq = new ArrayList<>();
        ListTag s = t.getList("Sequence", Tag.TAG_STRING);
        for (int i = 0; i < s.size(); i++) {
            ResourceLocation r = ResourceLocation.tryParse(s.getString(i));
            if (r != null) seq.add(r);
        }
        RunType type;
        try {
            type = RunType.valueOf(t.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = RunType.MINI;
        }
        Run run = new Run(t.getString("Id"), type, t.getString("Elevator"), t.getLong("Seed"), seq, t.getLong("Start"));
        ListTag gs = t.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < gs.size(); i++) run.groups.add(Group.load(gs.getCompound(i)));
        try {
            run.state = RunState.valueOf(t.getString("State"));
        } catch (IllegalArgumentException e) {
            run.state = RunState.RUNNING;
        }
        run.endTime = t.getLong("End");
        run.closed = t.getBoolean("Closed");
        return run;
    }
}
