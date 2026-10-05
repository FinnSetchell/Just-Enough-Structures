package com.finndog.justenoughstructures.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 26.3 rewrote how loot tables are written. A table, pool or entry has a {@code modifier}, one or a
 * list, where it had a list of {@code functions}, and a {@code condition} where it had a list of
 * {@code conditions}. Each names its kind as {@code type} rather than {@code function} or
 * {@code condition}, and a reference to an item modifier or a predicate is just its id.
 *
 * <p>The loot table editor works in the older shape, so tables are turned into it on the way in
 * and back on the way out. Before 26.3 both leave a table as it is.
 */
public final class LootFormat {
    //? if >=26.3 {
    /*private static final boolean MODIFIERS = true;
    *///?} else {
    private static final boolean MODIFIERS = false;
    //?}
    private static final String REFERENCE = "minecraft:reference";

    private LootFormat() {
    }

    /** The table in the shape the editor works in. The table itself is left alone. */
    public static JsonObject forEditing(JsonObject table) {
        return MODIFIERS && table != null ? editable(table.deepCopy()) : table;
    }

    /** The table as the game reads it. The table itself is left alone. */
    public static JsonObject forGame(JsonObject table) {
        return MODIFIERS && table != null ? playable(table.deepCopy()) : table;
    }

    // ------------------------------------------------------------------ to the editor's shape

    /** A table, pool or entry, and everything in it. Changed in place. */
    private static JsonObject editable(JsonObject holder) {
        JsonElement modifier = holder.remove("modifier");
        if (modifier != null) {
            JsonArray functions = new JsonArray();
            for (JsonElement function : modifier.isJsonArray() ? modifier.getAsJsonArray() : List.of(modifier)) {
                functions.add(editableFunction(function));
            }
            holder.add("functions", functions);
        }
        JsonElement condition = holder.remove("condition");
        if (condition != null) {
            JsonArray conditions = new JsonArray();
            conditions.add(editableCondition(condition));
            holder.add("conditions", conditions);
        }
        for (String key : List.of("pools", "entries", "children")) {
            if (holder.get(key) instanceof JsonArray list) {
                list.forEach(e -> {
                    if (e.isJsonObject()) {
                        editable(e.getAsJsonObject());
                    }
                });
            }
        }
        // A loot_table entry can hold its table right there.
        if (holder.get("value") instanceof JsonObject inline) {
            editable(inline);
        }
        return holder;
    }

    private static JsonElement editableFunction(JsonElement function) {
        if (function.isJsonPrimitive()) {
            JsonObject reference = new JsonObject();
            reference.addProperty("function", REFERENCE);
            reference.add("name", function);
            return reference;
        }
        if (!(function instanceof JsonObject object)) {
            return function;
        }
        rename(object, "type", "function");
        JsonElement condition = object.remove("condition");
        if (condition != null) {
            JsonArray conditions = new JsonArray();
            conditions.add(editableCondition(condition));
            object.add("conditions", conditions);
        }
        return object;
    }

    private static JsonElement editableCondition(JsonElement condition) {
        if (condition.isJsonPrimitive()) {
            JsonObject reference = new JsonObject();
            reference.addProperty("condition", REFERENCE);
            reference.add("name", condition);
            return reference;
        }
        if (!(condition instanceof JsonObject object)) {
            return condition;
        }
        rename(object, "type", "condition");
        // any_of, all_of and inverted hold more conditions.
        if (object.get("terms") instanceof JsonArray terms) {
            replaceAll(terms, LootFormat::editableCondition);
        }
        if (object.has("term")) {
            object.add("term", editableCondition(object.get("term")));
        }
        return object;
    }

    // ------------------------------------------------------------------ to the game's shape

    private static JsonObject playable(JsonObject holder) {
        JsonElement functions = holder.remove("functions");
        if (functions instanceof JsonArray list && !list.isEmpty()) {
            replaceAll(list, LootFormat::playableFunction);
            holder.add("modifier", list.size() == 1 ? list.get(0) : list);
        }
        JsonElement conditions = holder.remove("conditions");
        if (conditions instanceof JsonArray list && !list.isEmpty()) {
            holder.add("condition", playableConditions(list));
        }
        for (String key : List.of("pools", "entries", "children")) {
            if (holder.get(key) instanceof JsonArray list) {
                list.forEach(e -> {
                    if (e.isJsonObject()) {
                        playable(e.getAsJsonObject());
                    }
                });
            }
        }
        if (holder.get("value") instanceof JsonObject inline) {
            playable(inline);
        }
        return holder;
    }

    private static JsonElement playableFunction(JsonElement function) {
        if (!(function instanceof JsonObject object)) {
            return function;
        }
        if (isReference(object, "function")) {
            return object.get("name");
        }
        rename(object, "function", "type");
        JsonElement conditions = object.remove("conditions");
        if (conditions instanceof JsonArray list && !list.isEmpty()) {
            object.add("condition", playableConditions(list));
        }
        return object;
    }

    /** One condition, or all of them as one when there are several. */
    private static JsonElement playableConditions(JsonArray conditions) {
        replaceAll(conditions, LootFormat::playableCondition);
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        JsonObject all = new JsonObject();
        all.addProperty("type", "minecraft:all_of");
        all.add("terms", conditions);
        return all;
    }

    private static JsonElement playableCondition(JsonElement condition) {
        if (!(condition instanceof JsonObject object)) {
            return condition;
        }
        if (isReference(object, "condition")) {
            return object.get("name");
        }
        rename(object, "condition", "type");
        if (object.get("terms") instanceof JsonArray terms) {
            replaceAll(terms, LootFormat::playableCondition);
        }
        if (object.has("term")) {
            object.add("term", playableCondition(object.get("term")));
        }
        return object;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isReference(JsonObject object, String typeKey) {
        if (!(object.get(typeKey) instanceof JsonPrimitive type) || !(object.get("name") instanceof JsonPrimitive)) {
            return false;
        }
        String kind = type.getAsString();
        return kind.equals(REFERENCE) || kind.equals("reference");
    }

    /** Moves a key to the front under its new name, as the game writes a kind first. */
    private static void rename(JsonObject object, String from, String to) {
        JsonElement value = object.remove(from);
        if (value == null) {
            return;
        }
        List<Map.Entry<String, JsonElement>> rest = new ArrayList<>(object.entrySet());
        rest.forEach(e -> object.remove(e.getKey()));
        object.add(to, value);
        rest.forEach(e -> object.add(e.getKey(), e.getValue()));
    }

    private static void replaceAll(JsonArray array, java.util.function.UnaryOperator<JsonElement> change) {
        for (int i = 0; i < array.size(); i++) {
            array.set(i, change.apply(array.get(i)));
        }
    }
}
