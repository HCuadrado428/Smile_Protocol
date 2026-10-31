package com.smileprotocol.score;

import com.smileprotocol.debug.SPDebug;
import com.smileprotocol.difficulty.RunRules;
import com.smileprotocol.run.Group;
import com.smileprotocol.run.GroupState;
import com.smileprotocol.run.MemberStatus;
import com.smileprotocol.run.Run;
import com.smileprotocol.run.RunType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puntuacion, records y logros. Al terminar un grupo se calcula su puntuacion (plantas superadas, huida,
 * tiempo y supervivientes, por el multiplicador de las reglas), se apunta en la tabla si entra y se
 * actualizan las estadisticas y los logros de cada jugador. El tutorial no puntua (pero da un logro).
 */
public final class ScoreManager {

    /** Logros, en el orden en que se listan. */
    public static final List<String> ACHIEVEMENTS = List.of(
            "graduate", "first_escape", "untouched", "nightmare", "daredevil", "veteran", "speedrun", "last_standing",
            "electrician", "hide_seek", "tamer", "clockmaker");

    /** Jugadores derribados alguna vez en la partida en curso (para «sin un rasguno»). */
    private static final Set<UUID> DOWNED_THIS_RUN = new HashSet<>();
    /** Puntuacion de cada grupo ya terminado (la pantalla del final la lee). */
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private ScoreManager() {}

    // Calculo -------------------------------------------------------------------------------------

    /**
     * 1000 por planta superada, 3000 por escapar, hasta 3000 mas por rapidez (menos de 25 min) y 500 por
     * superviviente, todo por el multiplicador de las reglas.
     */
    public static int score(int zonesCleared, boolean escaped, int seconds, int survivors, RunRules rules) {
        double s = zonesCleared * 1000.0;
        if (escaped) {
            s += 3000 + Math.max(0, 1500 - seconds) * 2 + survivors * 500;
        }
        return (int) Math.round(s * rules.scoreFactor());
    }

    public static int lastScore(UUID groupId) {
        return LAST.getOrDefault(groupId, 0);
    }

    // Eventos -------------------------------------------------------------------------------------

    /** Lo llama {@code RunManager} al terminar un grupo (escapado o eliminado). Devuelve la puntuacion. */
    public static int onGroupFinished(MinecraftServer server, Run run, Group g, GroupState state, int seconds) {
        ScoreData data = ScoreData.get(server);
        boolean escaped = state == GroupState.COMPLETED;
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : g.members().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) online.add(p);
        }
        if (run.type() == RunType.TUTORIAL) {
            if (escaped) for (ServerPlayer p : online) unlock(p, "graduate");
            for (UUID id : g.members().keySet()) DOWNED_THIS_RUN.remove(id);
            return 0;
        }
        int survivors = escaped ? g.activeMembers().size() : 0;
        int cleared = escaped ? g.lastIndex() + 1 : g.zoneIndex();
        int score = score(cleared, escaped, seconds, survivors, g.rules());
        LAST.put(g.id(), score);

        List<String> names = new ArrayList<>();
        for (UUID id : g.members().keySet()) names.add(g.name(id));
        int place = data.add(new ScoreData.Entry(score, names, g.zoneIndex(), g.lastIndex(), seconds, g.rules().encode(), escaped,
                System.currentTimeMillis()));

        for (Map.Entry<UUID, MemberStatus> e : g.members().entrySet()) {
            ScoreData.Stats st = data.stats(e.getKey());
            st.name = g.name(e.getKey());
            st.runs++;
            boolean alive = e.getValue() == MemberStatus.ACTIVE;
            if (escaped && alive) st.escapes++;
            if (e.getValue() == MemberStatus.DEAD) st.deaths++;
            st.best = Math.max(st.best, score);
        }
        data.setDirty();

        for (ServerPlayer p : online) {
            UUID id = p.getUUID();
            ScoreData.Stats st = data.stats(id);
            boolean alive = g.isActive(id);
            if (escaped && alive) {
                unlock(p, "first_escape");
                if (!DOWNED_THIS_RUN.contains(id)) unlock(p, "untouched");
                if (g.rules().level() == RunRules.Level.NIGHTMARE) unlock(p, "nightmare");
                if (g.rules().modifiers().size() >= 3) unlock(p, "daredevil");
                if (st.escapes >= 10) unlock(p, "veteran");
                if (seconds < 15 * 60) unlock(p, "speedrun");
                if (g.members().size() > 1 && g.activeMembers().size() == 1) unlock(p, "last_standing");
            }
            p.sendSystemMessage(Component.translatable("smileprotocol.score.final", score).withStyle(ChatFormatting.GOLD));
        }
        for (UUID id : g.members().keySet()) DOWNED_THIS_RUN.remove(id);

        if (place > 0) {
            Component msg = Component.translatable("smileprotocol.score.record", String.join(", ", names), score, place)
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            server.getPlayerList().broadcastSystemMessage(msg, false);
            refreshBoard(server);
        }
        SPDebug.log(g.label() + " puntuacion " + score + (place > 0 ? " (puesto " + place + ")" : ""));
        return score;
    }

    public static void onDowned(ServerPlayer p) {
        DOWNED_THIS_RUN.add(p.getUUID());
    }

    public static void onFuseInserted(ServerPlayer p) {
        ScoreData.Stats st = bump(p, s -> s.fuses++);
        if (st.fuses >= 25) unlock(p, "electrician");
    }

    public static void onHide(ServerPlayer p) {
        ScoreData.Stats st = bump(p, s -> s.hides++);
        if (st.hides >= 50) unlock(p, "hide_seek");
    }

    public static void onStun(ServerPlayer p) {
        ScoreData.Stats st = bump(p, s -> s.stuns++);
        if (st.stuns >= 5) unlock(p, "tamer");
    }

    public static void onDecoy(ServerPlayer p) {
        ScoreData.Stats st = bump(p, s -> s.decoys++);
        if (st.decoys >= 10) unlock(p, "clockmaker");
    }

    private static ScoreData.Stats bump(ServerPlayer p, java.util.function.Consumer<ScoreData.Stats> f) {
        ScoreData data = ScoreData.get(p.server);
        ScoreData.Stats st = data.stats(p.getUUID());
        st.name = p.getGameProfile().getName();
        f.accept(st);
        data.setDirty();
        return st;
    }

    /** Desbloquea un logro (una vez): aviso al jugador, sonido y mensaje a todos. */
    public static boolean unlock(ServerPlayer p, String id) {
        ScoreData data = ScoreData.get(p.server);
        ScoreData.Stats st = data.stats(p.getUUID());
        st.name = p.getGameProfile().getName();
        if (!st.achievements.add(id)) return false;
        data.setDirty();
        Component name = Component.translatable("smileprotocol.achievement." + id);
        p.displayClientMessage(Component.translatable("smileprotocol.achievement.toast", name).withStyle(ChatFormatting.GOLD), true);
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1f);
        p.server.getPlayerList().broadcastSystemMessage(Component.translatable("smileprotocol.achievement.broadcast",
                p.getDisplayName(), name.copy().withStyle(s -> s.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("smileprotocol.achievement." + id + ".desc"))))).withStyle(ChatFormatting.GREEN), false);
        SPDebug.log(p.getGameProfile().getName() + " logro " + id);
        return true;
    }

    // Tablon del lobby ------------------------------------------------------------------------------

    /** El tablon tiene carteles sin actualizar (estaban en chunks sin cargar). */
    private static boolean boardDirty = true;

    /** Cada dos segundos: si quedo algo por escribir en el tablon y ya esta cargado, se escribe. */
    public static void tick(MinecraftServer server) {
        if (boardDirty && server.getTickCount() % 40 == 0) refreshBoard(server);
    }

    /** Reescribe los carteles del tablon con la tabla actual. */
    public static void refreshBoard(MinecraftServer server) {
        boardDirty = false;
        ScoreData data = ScoreData.get(server);
        ServerLevel level = server.overworld();
        List<BlockPos> signs = data.board();
        for (int i = 0; i < signs.size(); i++) {
            BlockPos pos = signs.get(i);
            if (!level.isLoaded(pos)) {
                boardDirty = true;
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            String[] lines = i < data.top().size() ? boardLines(i + 1, data.top().get(i)) : new String[]{(i + 1) + ".", "---", "", ""};
            SignText text = sign.getFrontText();
            for (int k = 0; k < 4; k++) text = text.setMessage(k, Component.literal(lines[k]));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static String[] boardLines(int place, ScoreData.Entry e) {
        String names = String.join(", ", e.names());
        if (names.length() > 15) names = names.substring(0, 14) + "…";
        String time = String.format("%d:%02d", e.seconds() / 60, e.seconds() % 60);
        RunRules r = RunRules.decode(e.rules());
        String level = switch (r.level()) {
            case EASY -> "Fácil";
            case NIGHTMARE -> "Pesadilla";
            default -> "Normal";
        };
        if (!r.modifiers().isEmpty()) level += " +" + r.modifiers().size();
        return new String[]{place + ". " + e.score() + " pts", names,
                (e.escaped() ? "Escapan " : "Planta " + e.zone() + " ") + time, level};
    }

    // Comandos ---------------------------------------------------------------------------------------

    public static List<Component> recordLines(MinecraftServer server) {
        List<Component> l = new ArrayList<>();
        ScoreData data = ScoreData.get(server);
        l.add(Component.translatable("smileprotocol.score.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (data.top().isEmpty()) l.add(Component.translatable("smileprotocol.score.empty").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < data.top().size(); i++) {
            String[] s = boardLines(i + 1, data.top().get(i));
            l.add(Component.literal(s[0] + " · " + String.join(", ", data.top().get(i).names()) + " · " + s[2] + " · " + s[3])
                    .withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
        return l;
    }

    public static List<Component> achievementLines(ServerPlayer p) {
        List<Component> l = new ArrayList<>();
        ScoreData.Stats st = ScoreData.get(p.server).stats(p.getUUID());
        l.add(Component.translatable("smileprotocol.achievement.title", st.achievements.size(), ACHIEVEMENTS.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        l.add(Component.translatable("smileprotocol.score.stats", st.runs, st.escapes, st.deaths, st.best).withStyle(ChatFormatting.GRAY));
        for (String id : ACHIEVEMENTS) {
            boolean has = st.achievements.contains(id);
            l.add(Component.literal(has ? "✔ " : "✘ ").withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("smileprotocol.achievement." + id).withStyle(has ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("smileprotocol.achievement." + id + ".desc").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return l;
    }
}
