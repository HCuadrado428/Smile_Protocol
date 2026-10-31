package com.smileprotocol.difficulty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de una partida: el nivel de dificultad y los modificadores que se eligen en la cabina de un
 * ascensor pequeno (F7 dentro de la cabina). El mega usa las de la configuracion. Se guardan en el grupo
 * y se mandan al cliente como texto: {@code NORMAL} o {@code NIGHTMARE|fast_mobs,no_revive}.
 */
public record RunRules(Level level, Set<String> modifiers) {

    public enum Level {
        /** Menos smilers, mas tiempo en los minijuegos, algun fusible menos. */
        EASY(0.7, 1.3, 0.8, 0.8),
        NORMAL(1, 1, 1, 1),
        /** Mas smilers y mas rapidos, menos tiempo en los minijuegos. */
        NIGHTMARE(1.4, 0.8, 1.15, 1.25);

        public final double mobs, qte, speed, score;

        Level(double mobs, double qte, double speed, double score) {
            this.mobs = mobs;
            this.qte = qte;
            this.speed = speed;
            this.score = score;
        }
    }

    /** Monstruos un 20 % mas rapidos. */
    public static final String FAST_MOBS = "fast_mobs";
    /** La mitad mas de smilers en cada planta. */
    public static final String MORE_MOBS = "more_mobs";
    /** La linterna gasta el doble de bateria. */
    public static final String WEAK_BATTERY = "weak_battery";
    /** Sin reanimacion: quien cae, muere. */
    public static final String NO_REVIVE = "no_revive";
    /** Los muebles casi nunca tienen nada y no hay objetos sueltos por el suelo. */
    public static final String SCARCE_LOOT = "scarce_loot";

    /** Todos los modificadores, en el orden en que salen en la pantalla. */
    public static final List<String> ALL = List.of(FAST_MOBS, MORE_MOBS, WEAK_BATTERY, NO_REVIVE, SCARCE_LOOT);

    public static final RunRules DEFAULT = new RunRules(Level.NORMAL, Set.of());

    public RunRules {
        Set<String> clean = new LinkedHashSet<>();
        for (String m : ALL) if (modifiers.contains(m)) clean.add(m);
        modifiers = Collections.unmodifiableSet(clean);
    }

    public boolean has(String modifier) {
        return modifiers.contains(modifier);
    }

    public boolean isDefault() {
        return level == Level.NORMAL && modifiers.isEmpty();
    }

    public RunRules withLevel(Level l) {
        return new RunRules(l, modifiers);
    }

    public RunRules toggle(String modifier) {
        Set<String> s = new LinkedHashSet<>(modifiers);
        if (!s.remove(modifier)) s.add(modifier);
        return new RunRules(level, s);
    }

    /** Multiplicador de mobs total (nivel y «mas smilers»). */
    public double mobFactor() {
        return level.mobs * (has(MORE_MOBS) ? 1.5 : 1);
    }

    /** Multiplicador de velocidad de los monstruos. */
    public double speedFactor() {
        return level.speed * (has(FAST_MOBS) ? 1.2 : 1);
    }

    /** Multiplicador del gasto de la linterna. */
    public double batteryDrain() {
        return has(WEAK_BATTERY) ? 2 : 1;
    }

    /** Multiplicador de la puntuacion: cada modificador suma un 15 %. */
    public double scoreFactor() {
        return level.score * (1 + 0.15 * modifiers.size());
    }

    /** El perfil del grupo con el nivel y los modificadores aplicados. */
    public DifficultyProfile apply(DifficultyProfile base) {
        double fuses = level == Level.EASY ? base.fuses() * 0.8 : base.fuses();
        return new DifficultyProfile(base.groupSize(), fuses, base.mobs() * mobFactor(), base.qteTime() * level.qte,
                base.minFuses());
    }

    public String encode() {
        return modifiers.isEmpty() ? level.name() : level.name() + "|" + String.join(",", modifiers);
    }

    public static RunRules decode(String s) {
        if (s == null || s.isBlank()) return DEFAULT;
        String[] parts = s.split("\\|", 2);
        Level level;
        try {
            level = Level.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            level = Level.NORMAL;
        }
        Set<String> mods = new LinkedHashSet<>();
        if (parts.length > 1) {
            for (String m : parts[1].split(",")) mods.add(m.trim().toLowerCase(Locale.ROOT));
        }
        return new RunRules(level, mods);
    }

    /** Claves de idioma para el resumen corto: el nivel y los modificadores. */
    public List<String> summaryKeys() {
        List<String> l = new ArrayList<>();
        l.add("smileprotocol.rules.level." + level.name().toLowerCase(Locale.ROOT));
        for (String m : modifiers) l.add("smileprotocol.rules.mod." + m);
        return l;
    }
}
