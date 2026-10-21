package dev.echo.ai;

import com.google.gson.*;
import java.util.*;

/** Deliberately small, closed protocol. No commands, scripts, selectors or arbitrary NBT. */
public record Plan(String reply, List<Step> steps) {
    public enum Kind { FOLLOW, STAY, GOTO, MINE, CRAFT, PLACE, EXPLORE, DELIVER }
    public record Step(Kind kind, String item, int count, int x, int y, int z) { }
    public static Plan parse(String json) {
        if (json == null || json.length() > 24000) throw new IllegalArgumentException("Plan exceeds limit");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String reply = root.has("reply") ? root.get("reply").getAsString() : "On it.";
        if (reply.length() > 600) throw new IllegalArgumentException("Reply exceeds limit");
        JsonArray actions = root.getAsJsonArray("steps");
        if (actions == null || actions.size() > 32) throw new IllegalArgumentException("Expected at most 32 steps");
        List<Step> steps = new ArrayList<>();
        for (JsonElement e : actions) {
            JsonObject a = e.getAsJsonObject();
            Kind kind = Kind.valueOf(a.get("action").getAsString().toUpperCase(Locale.ROOT));
            String item = a.has("item") ? a.get("item").getAsString() : "minecraft:air";
            if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || item.length() > 128) throw new IllegalArgumentException("Invalid item id");
            int count = number(a, "count", 1);
            if (count < 1 || count > 64) throw new IllegalArgumentException("Count must be 1..64");
            int x = number(a, "x", 0), y = number(a, "y", 0), z = number(a, "z", 0);
            if (Math.abs((long)x) > 29999984 || Math.abs((long)z) > 29999984 || y < -64 || y > 319) throw new IllegalArgumentException("Coordinate outside supported overworld bounds");
            steps.add(new Step(kind, item, count, x, y, z));
        }
        return new Plan(reply, List.copyOf(steps));
    }
    private static int number(JsonObject a, String key, int fallback) {
        if (!a.has(key)) return fallback;
        return a.get(key).getAsBigDecimal().intValueExact();
    }
    public static Plan single(Kind kind) { return new Plan("On it.", List.of(new Step(kind, "minecraft:air", 1, 0, 0, 0))); }
}
