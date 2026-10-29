package com.smileprotocol.run;

import com.smileprotocol.config.SPConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Formacion de grupos (README 6.3, punto 1). */
public final class GroupManager {

    private GroupManager() {}

    /**
     * Tamanos de grupo para {@code n} jugadores con un minimo de {@code min} por grupo:
     * {@code ⌊n/min⌋} grupos y los sobrantes repartidos de uno en uno. Lista vacia si
     * {@code n < min}. Ejemplos con min 4: 9 → [5, 4], 11 → [6, 5], 19 → [5, 5, 5, 4].
     */
    public static List<Integer> groupSizes(int n, int min) {
        if (min <= 0 || n < min) return List.of();
        int g = n / min;
        int base = n / g;
        int extra = n % g;
        List<Integer> sizes = new ArrayList<>(g);
        for (int i = 0; i < g; i++) sizes.add(i < extra ? base + 1 : base);
        return sizes;
    }

    /** Un mini ascensor: un grupo con todos los de la cabina. */
    public static List<List<ServerPlayer>> mini(List<ServerPlayer> players) {
        return List.of(List.copyOf(players));
    }

    /**
     * El mega ascensor: reparte a todos los listos. Si el administrador ha fijado grupos, se
     * respetan los que tengan al menos {@code min} jugadores listos; el resto se reparte al
     * azar con {@link #groupSizes}. Si sobran menos de {@code min}, se suman de uno en uno a
     * los grupos mas pequenos: nadie juega solo ni en un grupo de menos de {@code min}.
     */
    public static List<List<ServerPlayer>> mega(List<ServerPlayer> ready, List<List<UUID>> fixed, RandomSource rnd) {
        int min = SPConfig.get().min_group_size;
        Map<UUID, ServerPlayer> byId = new LinkedHashMap<>();
        for (ServerPlayer p : ready) byId.put(p.getUUID(), p);

        List<List<ServerPlayer>> groups = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        boolean useFixed = !"RANDOM".equalsIgnoreCase(SPConfig.get().group_mode) || !fixed.isEmpty();
        if (useFixed) {
            for (List<UUID> f : fixed) {
                List<ServerPlayer> g = new ArrayList<>();
                for (UUID u : f) {
                    ServerPlayer p = byId.get(u);
                    if (p != null && used.add(u)) g.add(p);
                }
                if (g.size() >= min) groups.add(g);
                else for (ServerPlayer p : g) used.remove(p.getUUID());
            }
        }

        List<ServerPlayer> rest = new ArrayList<>();
        for (ServerPlayer p : ready) if (!used.contains(p.getUUID())) rest.add(p);
        shuffle(rest, rnd);

        List<Integer> sizes = groupSizes(rest.size(), min);
        int i = 0;
        for (int size : sizes) {
            groups.add(new ArrayList<>(rest.subList(i, i + size)));
            i += size;
        }
        // Sobrantes (solo si no llegaban a un grupo propio): al grupo mas pequeno, de uno en uno
        for (; i < rest.size(); i++) {
            if (groups.isEmpty()) break;
            List<ServerPlayer> smallest = groups.get(0);
            for (List<ServerPlayer> g : groups) if (g.size() < smallest.size()) smallest = g;
            smallest.add(rest.get(i));
        }
        return groups;
    }

    private static <T> void shuffle(List<T> list, RandomSource rnd) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }
}
