package com.smileprotocol.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mision de una planta ({@code data/smileprotocol/missions/<planta>.json}, README 19): una lista de
 * pasos en orden. Cada paso es un objetivo y puede disparar eventos al completarse.
 *
 * <p>Tipos de objetivo: {@code FIND_ITEM} (item, count), {@code OPEN_DOOR} (param de la puerta o
 * "forced" para las atascadas), {@code INSERT_FUSES} (circuit), {@code TURN_VALVES} (group),
 * {@code POWER} (circuit), {@code REACH} (marker), {@code SURVIVE} (seconds) y {@code ESCAPE}.
 * Eventos: {@code message}, {@code sound}, {@code spawn_mob} (mob), {@code lights_off},
 * {@code lights_on} y {@code power} (circuit).
 */
public record MissionDef(String id, List<Step> steps) {

    public record Step(String type, String arg, int count, String text, List<Event> events) {}

    public record Event(String type, String arg) {}

    static MissionDef parse(String id, JsonObject o) {
        List<Step> steps = new ArrayList<>();
        JsonArray arr = o.has("steps") ? o.getAsJsonArray("steps") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject s = e.getAsJsonObject();
            String type = str(s, "type", "ESCAPE").toUpperCase(Locale.ROOT);
            String arg = switch (type) {
                case "FIND_ITEM" -> str(s, "item", "");
                case "OPEN_DOOR" -> str(s, "door", "");
                case "INSERT_FUSES", "POWER" -> str(s, "circuit", "exit");
                case "TURN_VALVES" -> str(s, "group", "");
                case "REACH" -> str(s, "marker", "");
                default -> "";
            };
            int count = s.has("count") ? s.get("count").getAsInt() : s.has("seconds") ? s.get("seconds").getAsInt() : 1;
            List<Event> events = new ArrayList<>();
            if (s.has("on_complete")) {
                for (JsonElement ev : s.getAsJsonArray("on_complete")) {
                    JsonObject eo = ev.getAsJsonObject();
                    String et = str(eo, "type", "message").toLowerCase(Locale.ROOT);
                    String ea = eo.has("text") ? eo.get("text").getAsString() : eo.has("mob") ? eo.get("mob").getAsString()
                            : eo.has("sound") ? eo.get("sound").getAsString() : eo.has("circuit") ? eo.get("circuit").getAsString() : "";
                    events.add(new Event(et, ea));
                }
            }
            steps.add(new Step(type, arg, Math.max(1, count), str(s, "text", ""), events));
        }
        return new MissionDef(id, steps);
    }

    /** Sin fichero: dar corriente al ascensor de salida y escapar. */
    static MissionDef fallback(String id) {
        return new MissionDef(id, List.of(new Step("INSERT_FUSES", "exit", 1, "", List.of()),
                new Step("ESCAPE", "", 1, "", List.of())));
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }
}
