package com.smileprotocol.difficulty;

import net.minecraft.nbt.CompoundTag;

/**
 * Multiplicadores de dificultad de un grupo (README 6.3, punto 3). Se fijan al empezar la
 * partida segun el tamano del grupo y no cambian aunque luego alguien muera.
 *
 * @param fuses    fusibles por circuito
 * @param mobs     mobs por zona
 * @param qteTime  tiempo del QTE (1.4 = +40 %)
 * @param minFuses minimo de fusibles por circuito tras escalar
 */
public record DifficultyProfile(int groupSize, double fuses, double mobs, double qteTime, int minFuses) {

    public static final DifficultyProfile BASE = new DifficultyProfile(4, 1, 1, 1, 1);

    /** Fusibles de un circuito con {@code base} fusibles en la plantilla. */
    public int scaleFuses(int base) {
        if (base <= 0) return 0;
        return Math.max(minFuses, (int) Math.round(base * fuses));
    }

    /** Mobs de una zona con {@code base} puntos de aparicion. Nunca mas que los puntos que haya. */
    public int scaleMobs(int base, int available) {
        if (base <= 0) return 0;
        return Math.min(available, Math.max(1, (int) Math.round(base * mobs)));
    }

    /** Ticks del QTE a partir de los de base. */
    public int scaleQte(int baseTicks) {
        return (int) Math.round(baseTicks * qteTime);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Size", groupSize);
        t.putDouble("Fuses", fuses);
        t.putDouble("Mobs", mobs);
        t.putDouble("Qte", qteTime);
        t.putInt("MinFuses", minFuses);
        return t;
    }

    public static DifficultyProfile load(CompoundTag t) {
        if (!t.contains("Size")) return BASE;
        return new DifficultyProfile(t.getInt("Size"), t.getDouble("Fuses"), t.getDouble("Mobs"),
                t.getDouble("Qte"), Math.max(0, t.getInt("MinFuses")));
    }

    public String describe() {
        return groupSize + "j fus×" + fuses + " mobs×" + mobs + " qte×" + qteTime;
    }
}
