package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Reading and changing a loot table's JSON by path, like "pools.0.entries.2.weight": object keys
 * and array indexes joined by dots. The editor's form works on the table through these, so anything
 * it doesn't know about is left as it was.
 */
final class JsonPaths {
    private JsonPaths() {
    }

    static String join(String path, Object part) {
        return path.isEmpty() ? String.valueOf(part) : path + "." + part;
    }

    /** What's at the path, or null if there's nothing there. */
    static JsonElement get(JsonElement root, String path) {
        if (path.isEmpty()) {
            return root;
        }
        JsonElement at = root;
        for (String part : path.split("\\.")) {
            if (at instanceof JsonObject object) {
                at = object.get(part);
            } else if (at instanceof JsonArray array && isIndex(part) && Integer.parseInt(part) < array.size()) {
                at = array.get(Integer.parseInt(part));
            } else {
                return null;
            }
        }
        return at;
    }

    static JsonObject object(JsonElement root, String path) {
        return get(root, path) instanceof JsonObject object ? object : null;
    }

    static JsonArray array(JsonElement root, String path) {
        return get(root, path) instanceof JsonArray array ? array : null;
    }

    static String string(JsonElement root, String path, String fallback) {
        JsonElement value = get(root, path);
        return value instanceof JsonPrimitive p && p.isString() ? p.getAsString() : fallback;
    }

    static boolean bool(JsonElement root, String path) {
        JsonElement value = get(root, path);
        return value instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    /**
     * Puts a value at the path, making any objects or arrays on the way. Null takes the key away,
     * and an array left empty by that goes too, as the game reads a missing list as an empty one.
     */
    static void set(JsonElement root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonElement at = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            JsonElement next = child(at, part);
            if (next == null || next.isJsonNull()) {
                if (value == null) {
                    return;
                }
                next = isIndex(parts[i + 1]) ? new JsonArray() : new JsonObject();
                put(at, part, next);
            }
            at = next;
        }
        put(at, parts[parts.length - 1], value);
    }

    /** Takes away what's at the path: a key from an object, or an item from an array. */
    static void remove(JsonElement root, String path) {
        int dot = path.lastIndexOf('.');
        String parentPath = dot < 0 ? "" : path.substring(0, dot);
        String last = dot < 0 ? path : path.substring(dot + 1);
        JsonElement parent = get(root, parentPath);
        if (parent instanceof JsonObject object) {
            object.remove(last);
        } else if (parent instanceof JsonArray array && isIndex(last) && Integer.parseInt(last) < array.size()) {
            array.remove(Integer.parseInt(last));
            if (array.isEmpty() && !parentPath.isEmpty()) {
                remove(root, parentPath);
            }
        }
    }

    /** Adds to the end of the list at the path, making the list if there isn't one. Returns its index. */
    static int append(JsonElement root, String path, JsonElement value) {
        JsonArray array = array(root, path);
        if (array == null) {
            array = new JsonArray();
            set(root, path, array);
        }
        array.add(value);
        return array.size() - 1;
    }

    private static JsonElement child(JsonElement at, String part) {
        if (at instanceof JsonObject object) {
            return object.get(part);
        }
        if (at instanceof JsonArray array && isIndex(part) && Integer.parseInt(part) < array.size()) {
            return array.get(Integer.parseInt(part));
        }
        return null;
    }

    private static void put(JsonElement at, String part, JsonElement value) {
        if (at instanceof JsonObject object) {
            if (value == null) {
                object.remove(part);
            } else {
                object.add(part, value);
            }
        } else if (at instanceof JsonArray array && isIndex(part)) {
            int index = Integer.parseInt(part);
            if (value == null) {
                if (index < array.size()) {
                    array.remove(index);
                }
            } else if (index < array.size()) {
                array.set(index, value);
            } else {
                array.add(value);
            }
        }
    }

    private static boolean isIndex(String part) {
        return !part.isEmpty() && part.chars().allMatch(Character::isDigit);
    }

    /** A number as JSON, whole numbers without a decimal point. */
    static JsonPrimitive number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return new JsonPrimitive((long) value);
        }
        return new JsonPrimitive(value);
    }
}
