package com.smileprotocol.zone;

import com.smileprotocol.config.SPConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Secuencia de zonas de una partida (README 6.5): pasillo inicial, {@code intermediate_zones}
 * zonas del catalogo sin repetir y con dificultad creciente, y la sala final. Con
 * {@code avoid_repeat_last} se evita repetir las intermedias de la partida anterior.
 */
public final class ZoneSequence {

    private ZoneSequence() {}

    public static List<ResourceLocation> generate(MinecraftServer server, long seed, List<ResourceLocation> last) {
        SPConfig.Values cfg = SPConfig.get();
        List<ResourceLocation> pool = ZoneCatalog.pool(server);
        int k = Math.min(cfg.intermediate_zones, pool.size());

        List<ResourceLocation> best = pick(server, pool, k, RandomSource.create(seed));
        if (cfg.avoid_repeat_last && !last.isEmpty()) {
            List<ResourceLocation> lastMid = middle(last);
            // Varios intentos para que cambie respecto a la anterior (si el catalogo da para ello)
            for (int i = 1; i <= 16 && best.equals(lastMid); i++) {
                best = pick(server, pool, k, RandomSource.create(seed + i * 7919L));
            }
        }

        List<ResourceLocation> seq = new ArrayList<>();
        ResourceLocation start = ResourceLocation.tryParse(cfg.start_zone);
        ResourceLocation end = ResourceLocation.tryParse(cfg.final_zone);
        if (start != null) seq.add(start);
        seq.addAll(best);
        if (end != null) seq.add(end);
        return seq;
    }

    private static List<ResourceLocation> pick(MinecraftServer server, List<ResourceLocation> pool, int k, RandomSource rnd) {
        List<ResourceLocation> shuffled = new ArrayList<>(pool);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            ResourceLocation t = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, t);
        }
        List<ResourceLocation> chosen = new ArrayList<>(shuffled.subList(0, k));
        // Dificultad creciente (orden estable: a igual dificultad queda el orden aleatorio)
        chosen.sort(Comparator.comparingInt(id -> {
            ZoneDef d = ZoneCatalog.get(server, id);
            return d == null ? 0 : d.difficulty();
        }));
        return chosen;
    }

    /** Las intermedias de una secuencia completa (sin pasillo ni sala final). */
    private static List<ResourceLocation> middle(List<ResourceLocation> seq) {
        if (seq.size() <= 2) return List.of();
        return seq.subList(1, seq.size() - 1);
    }
}
