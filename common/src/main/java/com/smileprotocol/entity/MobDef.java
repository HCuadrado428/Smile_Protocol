package com.smileprotocol.entity;

import com.google.gson.JsonObject;

/**
 * Ficha de un mob ({@code data/smileprotocol/mobs/*.json}, README 20.2). Todas las cifras son
 * propuestas a ajustar en pruebas; se releen tras {@code /reload}.
 *
 * @param hearingMultiplier multiplica el radio de cada ruido
 * @param minNoise          radio efectivo minimo para que reaccione (bloques)
 * @param idleBehavior      WANDER o STAND
 * @param retargetRatio     cuanto mas fuerte tiene que ser un ruido para cambiar de objetivo
 * @param searchTime        ticks que se queda escuchando tras golpear
 * @param windup            ticks de aviso antes del golpe
 * @param strikeRadius      radio del golpe (bloques)
 * @param recovery          ticks de recuperacion tras el golpe
 * @param walkSpeed         velocidad al merodear (multiplicador de la base)
 * @param runSpeed          velocidad al ir hacia un ruido
 * @param onCatch           DOWN, GRAB_QTE o INSTANT_KILL
 * @param execution         remata a los derribados
 * @param opensDoors        abre las puertas normales del laboratorio
 */
public record MobDef(String id, float hearingMultiplier, float minNoise, String idleBehavior, float retargetRatio,
                     int searchTime, int windup, float strikeRadius, int recovery, double walkSpeed, double runSpeed,
                     String onCatch, boolean execution, boolean opensDoors, boolean checksHidingSpots) {

    public static MobDef berserkerDefaults() {
        return new MobDef("berserker", 1.5f, 5f, "WANDER", 1.5f, 120, 12, 2.5f, 30, 0.55, 1.35, "DOWN", true, true, false);
    }

    static MobDef parse(String id, JsonObject o) {
        MobDef d = berserkerDefaults();
        JsonObject senses = obj(o, "senses"), hearing = obj(senses, "hearing"), ai = obj(o, "ai"),
                strike = obj(o, "strike"), speed = obj(o, "speed");
        return new MobDef(id,
                f(hearing, "multiplier", d.hearingMultiplier), f(hearing, "min_noise", d.minNoise),
                s(ai, "idle_behavior", d.idleBehavior), f(ai, "retarget_loudness_ratio", d.retargetRatio),
                i(ai, "search_time", d.searchTime),
                i(strike, "windup_ticks", d.windup), f(strike, "radius", d.strikeRadius), i(strike, "recovery_ticks", d.recovery),
                f(speed, "walk", (float) d.walkSpeed), f(speed, "run", (float) d.runSpeed),
                s(o, "on_catch", d.onCatch), b(o, "execution", d.execution), b(ai, "opens_doors", d.opensDoors),
                b(ai, "checks_hiding_spots", d.checksHidingSpots));
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static String s(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    private static boolean b(JsonObject o, String k, boolean d) { return o.has(k) ? o.get(k).getAsBoolean() : d; }
}
