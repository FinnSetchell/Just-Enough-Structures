package com.finndog.justenoughstructures.overrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>Lists of entries are matched up by what each entry is, its kind and the item, tag or table
 * it names, so a mod adding or moving an item doesn't make every entry after it look changed. The
 * second and later of the same thing are told apart by their order.
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
        Map<String, JsonElement> baseByName = byKey(base);
        Map<String, JsonElement> mineByName = byKey(mine);
        Map<String, JsonElement> theirsByName = byKey(theirs);
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

    /**
     * The array's objects by what each one is, or null if they can't all be told apart. An entry is
     * its kind and the item, tag or table it names, and the second and later of the same get their
     * place among those after it, so a Nothing entry, or an item that's there twice, has a key of its
     * own. Anything else is its name, which has to be different from the others'.
     */
    private static Map<String, JsonElement> byKey(JsonArray array) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String key;
            if (object.get("type") instanceof JsonPrimitive kind) {
                key = kind.getAsString() + " " + named(object);
            } else if (object.get("name") instanceof JsonPrimitive name && !seen.containsKey(name.getAsString())) {
                key = name.getAsString();
            } else {
                return null;
            }
            int count = seen.merge(key, 1, Integer::sum);
            out.put(count == 1 ? key : key + " #" + count, element);
        }
        return out;
    }

    /** What an entry names: its item, or its table or tag under whichever key this version of the game has for those. */
    private static String named(JsonObject entry) {
        for (String key : List.of("name", "value", "items")) {
            if (entry.get(key) instanceof JsonPrimitive named) {
                return named.getAsString();
            }
        }
        return "";
    }

    private static JsonElement copy(JsonElement element) {
        return element == null ? null : element.deepCopy();
    }
}
