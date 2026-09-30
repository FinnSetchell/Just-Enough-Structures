package com.finndog.justenoughstructures.overrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges a dev's edit of a loot table with the mod's newer version of it, for when a mod updates a
 * table that's been overridden. What only one side changed is taken from that side. Where both
 * changed the same thing, the dev's version is kept and it counts as a conflict to look at.
 *
 * <p>Lists of entries are matched up by their {@code name} where every entry has a different one,
 * so a mod adding or moving an item doesn't make every entry after it look changed.
 */
public final class JsonMerge {
    private JsonMerge() {
    }

    /** The merged table, and how many places both sides changed, where the dev's version was kept. */
    public record Result(JsonElement merged, int conflicts) {
    }

    public static Result merge(JsonElement base, JsonElement mine, JsonElement theirs) {
        int[] conflicts = new int[1];
        JsonElement merged = merge(base, mine, theirs, conflicts);
        return new Result(merged, conflicts[0]);
    }

    /** Null stands for "not there". */
    private static JsonElement merge(JsonElement base, JsonElement mine, JsonElement theirs, int[] conflicts) {
        if (Objects.equals(mine, theirs)) {
            return copy(mine);
        }
        if (Objects.equals(base, mine)) {
            return copy(theirs);
        }
        if (Objects.equals(base, theirs)) {
            return copy(mine);
        }
        if (mine != null && theirs != null && mine.isJsonObject() && theirs.isJsonObject()) {
            JsonObject b = base != null && base.isJsonObject() ? base.getAsJsonObject() : new JsonObject();
            return mergeObjects(b, mine.getAsJsonObject(), theirs.getAsJsonObject(), conflicts);
        }
        if (mine != null && theirs != null && mine.isJsonArray() && theirs.isJsonArray()) {
            JsonArray b = base != null && base.isJsonArray() ? base.getAsJsonArray() : new JsonArray();
            return mergeArrays(b, mine.getAsJsonArray(), theirs.getAsJsonArray(), conflicts);
        }
        conflicts[0]++;
        return copy(mine);
    }

    private static JsonObject mergeObjects(JsonObject base, JsonObject mine, JsonObject theirs, int[] conflicts) {
        Set<String> keys = new LinkedHashSet<>(theirs.keySet());
        keys.addAll(mine.keySet());
        keys.addAll(base.keySet());
        JsonObject out = new JsonObject();
        for (String key : keys) {
            JsonElement merged = merge(base.get(key), mine.get(key), theirs.get(key), conflicts);
            if (merged != null) {
                out.add(key, merged);
            }
        }
        return out;
    }

    private static JsonArray mergeArrays(JsonArray base, JsonArray mine, JsonArray theirs, int[] conflicts) {
        Map<String, JsonElement> baseByName = byName(base);
        Map<String, JsonElement> mineByName = byName(mine);
        Map<String, JsonElement> theirsByName = byName(theirs);
        JsonArray out = new JsonArray();
        if (baseByName != null && mineByName != null && theirsByName != null) {
            // Their order, then anything only the dev added, in the dev's order.
            List<String> names = new ArrayList<>(theirsByName.keySet());
            for (String name : mineByName.keySet()) {
                if (!theirsByName.containsKey(name)) {
                    names.add(name);
                }
            }
            for (String name : names) {
                JsonElement merged = merge(baseByName.get(name), mineByName.get(name), theirsByName.get(name), conflicts);
                if (merged != null) {
                    out.add(merged);
                }
            }
            return out;
        }
        if (mine.size() == theirs.size() && (base.size() == mine.size() || base.isEmpty())) {
            for (int i = 0; i < mine.size(); i++) {
                JsonElement b = i < base.size() ? base.get(i) : null;
                JsonElement merged = merge(b, mine.get(i), theirs.get(i), conflicts);
                if (merged != null) {
                    out.add(merged);
                }
            }
            return out;
        }
        conflicts[0]++;
        return mine.deepCopy();
    }

    /** The array's objects by their names, or null if they don't all have a different one. */
    private static Map<String, JsonElement> byName(JsonArray array) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("name") || !element.getAsJsonObject().get("name").isJsonPrimitive()) {
                return null;
            }
            String name = element.getAsJsonObject().get("name").getAsString();
            if (!seen.add(name)) {
                return null;
            }
            out.put(name, element);
        }
        return out;
    }

    private static JsonElement copy(JsonElement element) {
        return element == null ? null : element.deepCopy();
    }
}
