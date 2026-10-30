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
 * @param qteType           minijuego al agarrar: MASH, TIMING, SEQUENCE o HOLD (README 18)
 * @param qteTicks          duracion base del minijuego (se escala por el grupo)
 * @param qteFail           DOWN o DEATH si se falla
 * @param sightRange        alcance de la vista (0 = ciego)
 * @param executionSeq      secuencia de remate ({@code SequenceKind}, README 9.3)
 * @param killSeq           secuencia de muerte instantanea
 * @param canEnterVents     cabe por los conductos (README 12.9)
 * @param causesInterference distorsiona los walkies cercanos (README 17.3)
 */
public record MobDef(String id, float hearingMultiplier, float minNoise, String idleBehavior, float retargetRatio,
                     int searchTime, int windup, float strikeRadius, int recovery, double walkSpeed, double runSpeed,
                     String onCatch, boolean execution, boolean opensDoors, boolean checksHidingSpots,
                     String qteType, int qteTicks, String qteFail, float sightRange, String executionSeq, String killSeq,
                     boolean canEnterVents, boolean causesInterference) {

    public static MobDef berserkerDefaults() {
        return new MobDef("berserker", 1.5f, 5f, "WANDER", 1.5f, 120, 12, 2.5f, 30, 0.55, 1.35, "DOWN", true, true, false,
                "MASH", 100, "DOWN", 0f, "BERSERKER_EXECUTION", "SMILER_KILL", false, false);
    }

    static MobDef parse(String id, JsonObject o) {
        MobDef d = berserkerDefaults();
        JsonObject senses = obj(o, "senses"), hearing = obj(senses, "hearing"), ai = obj(o, "ai"),
                strike = obj(o, "strike"), speed = obj(o, "speed"), qte = obj(o, "qte"), vision = obj(senses, "vision"),
                seq = obj(o, "sequences");
        return new MobDef(id,
                f(hearing, "multiplier", d.hearingMultiplier), f(hearing, "min_noise", d.minNoise),
                s(ai, "idle_behavior", d.idleBehavior), f(ai, "retarget_loudness_ratio", d.retargetRatio),
                i(ai, "search_time", d.searchTime),
                i(strike, "windup_ticks", d.windup), f(strike, "radius", d.strikeRadius), i(strike, "recovery_ticks", d.recovery),
                f(speed, "walk", (float) d.walkSpeed), f(speed, "run", (float) d.runSpeed),
                s(o, "on_catch", d.onCatch), b(o, "execution", d.execution), b(ai, "opens_doors", d.opensDoors),
                b(ai, "checks_hiding_spots", d.checksHidingSpots),
                s(qte, "type", d.qteType), i(qte, "ticks", d.qteTicks), s(o, "qte_fail_result", d.qteFail),
                b(vision, "enabled", false) ? f(vision, "range", 20f) : 0f,
                s(seq, "execution", d.executionSeq), s(seq, "kill", d.killSeq),
                b(o, "can_enter_vents", d.canEnterVents), b(o, "causes_interference", d.causesInterference));
    }

    public com.smileprotocol.sequence.SequenceKind executionSequence() {
        return seq(executionSeq, com.smileprotocol.sequence.SequenceKind.BERSERKER_EXECUTION);
    }

    public com.smileprotocol.sequence.SequenceKind killSequence() {
        return seq(killSeq, com.smileprotocol.sequence.SequenceKind.SMILER_KILL);
    }

    private static com.smileprotocol.sequence.SequenceKind seq(String name, com.smileprotocol.sequence.SequenceKind d) {
        try {
            return com.smileprotocol.sequence.SequenceKind.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            return d;
        }
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static String s(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    private static boolean b(JsonObject o, String k, boolean d) { return o.has(k) ? o.get(k).getAsBoolean() : d; }
}
