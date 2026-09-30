package com.finndog.justenoughstructures.overrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * The simple edits the loot table editor offers, made straight on the table's JSON: pools, how
 * many times they roll, item entries, their weights and how many of the item they give. Anything
 * else in the table, like another mod's functions or conditions, is left exactly as it was.
 */
public final class TableDraft {
    private static final String SET_COUNT = "minecraft:set_count";

    private TableDraft() {
    }

    /** A whole-number range, like a pool's rolls or an entry's count. */
    public record Range(int min, int max) {
    }

    public static JsonArray pools(JsonObject table) {
        if (!table.has("pools") || !table.get("pools").isJsonArray()) {
            table.add("pools", new JsonArray());
        }
        return table.getAsJsonArray("pools");
    }

    public static List<JsonObject> poolList(JsonObject table) {
        return objects(pools(table));
    }

    public static JsonObject addPool(JsonObject table) {
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", new JsonArray());
        pools(table).add(pool);
        return pool;
    }

    public static void removePool(JsonObject table, int index) {
        JsonArray pools = pools(table);
        if (index >= 0 && index < pools.size()) {
            pools.remove(index);
        }
    }

    public static JsonArray entries(JsonObject pool) {
        if (!pool.has("entries") || !pool.get("entries").isJsonArray()) {
            pool.add("entries", new JsonArray());
        }
        return pool.getAsJsonArray("entries");
    }

    public static List<JsonObject> entryList(JsonObject pool) {
        return objects(entries(pool));
    }

    public static JsonObject addItem(JsonObject pool, String item) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        entry.addProperty("weight", 1);
        entries(pool).add(entry);
        return entry;
    }

    public static void removeEntry(JsonObject pool, int index) {
        JsonArray entries = entries(pool);
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
        }
    }

    /** The entry's type without the minecraft namespace, like "item", "tag" or "loot_table". */
    public static String type(JsonObject entry) {
        String type = string(entry, "type", "minecraft:item");
        return type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
    }

    public static boolean isItem(JsonObject entry) {
        return type(entry).equals("item");
    }

    public static String name(JsonObject entry) {
        return string(entry, "name", "");
    }

    public static void setName(JsonObject entry, String name) {
        entry.addProperty("name", name);
    }

    public static int weight(JsonObject entry) {
        return integer(entry.get("weight"), 1);
    }

    public static void setWeight(JsonObject entry, int weight) {
        if (weight == 1) {
            entry.remove("weight");
        } else {
            entry.addProperty("weight", Math.max(0, weight));
        }
    }

    /** How many times the pool rolls, or null when it's a formula the simple form can't show. */
    public static Range rolls(JsonObject pool) {
        return range(pool.get("rolls"));
    }

    public static void setRolls(JsonObject pool, Range rolls) {
        pool.add("rolls", toJson(rolls));
    }

    /**
     * How many of the item the entry gives, from its set_count function, or 1 to 1 without one.
     * Null when the count is a formula or depends on a condition.
     */
    public static Range count(JsonObject entry) {
        JsonObject function = setCount(entry);
        if (function == null) {
            return new Range(1, 1);
        }
        if (function.has("conditions") || function.has("add")) {
            return null;
        }
        return range(function.get("count"));
    }

    public static void setCount(JsonObject entry, Range count) {
        JsonObject function = setCount(entry);
        if (count.min() == 1 && count.max() == 1) {
            if (function != null) {
                JsonArray functions = entry.getAsJsonArray("functions");
                functions.remove(function);
                if (functions.isEmpty()) {
                    entry.remove("functions");
                }
            }
            return;
        }
        if (function == null) {
            if (!entry.has("functions") || !entry.get("functions").isJsonArray()) {
                entry.add("functions", new JsonArray());
            }
            function = new JsonObject();
            function.addProperty("function", SET_COUNT);
            entry.getAsJsonArray("functions").add(function);
        }
        function.add("count", toJson(count));
    }

    /** How many functions and conditions the entry has that the simple form doesn't show. */
    public static int others(JsonObject entry) {
        int functions = entry.has("functions") && entry.get("functions").isJsonArray() ? entry.getAsJsonArray("functions").size() : 0;
        if (setCount(entry) != null) {
            functions--;
        }
        int conditions = entry.has("conditions") && entry.get("conditions").isJsonArray() ? entry.getAsJsonArray("conditions").size() : 0;
        return functions + conditions;
    }

    private static JsonObject setCount(JsonObject entry) {
        if (!entry.has("functions") || !entry.get("functions").isJsonArray()) {
            return null;
        }
        for (JsonElement element : entry.getAsJsonArray("functions")) {
            if (element.isJsonObject()) {
                String function = string(element.getAsJsonObject(), "function", "");
                if (function.equals(SET_COUNT) || function.equals("set_count")) {
                    return element.getAsJsonObject();
                }
            }
        }
        return null;
    }

    /** A number, or a uniform range written either way the game accepts. Anything else is null. */
    private static Range range(JsonElement value) {
        if (value == null) {
            return new Range(1, 1);
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            int n = (int) Math.round(value.getAsDouble());
            return new Range(n, n);
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String type = string(object, "type", "minecraft:uniform");
            if ((type.equals("minecraft:uniform") || type.equals("uniform")) && isNumber(object.get("min")) && isNumber(object.get("max"))) {
                return new Range((int) Math.round(object.get("min").getAsDouble()), (int) Math.round(object.get("max").getAsDouble()));
            }
            if ((type.equals("minecraft:constant") || type.equals("constant")) && isNumber(object.get("value"))) {
                int n = (int) Math.round(object.get("value").getAsDouble());
                return new Range(n, n);
            }
        }
        return null;
    }

    private static JsonElement toJson(Range range) {
        int min = Math.min(range.min(), range.max());
        int max = Math.max(range.min(), range.max());
        if (min == max) {
            return new JsonPrimitive(min);
        }
        JsonObject uniform = new JsonObject();
        uniform.addProperty("type", "minecraft:uniform");
        uniform.addProperty("min", min);
        uniform.addProperty("max", max);
        return uniform;
    }

    private static boolean isNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static int integer(JsonElement value, int fallback) {
        return isNumber(value) ? (int) Math.round(value.getAsDouble()) : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                out.add(element.getAsJsonObject());
            }
        }
        return out;
    }
}
