package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading and changing a loot table's JSON by path, like "pools.0.entries.2.weight": object keys
 * and array indexes joined by dots. The editor's form works on the table through these, so anything
 * it doesn't know about is left as it was. A key that isn't the form's own, like an enchantment's id,
 * goes through {@link #key} first, as some mods' ids have a dot in them.
 */
public final class JsonPaths {
    private JsonPaths() {
    }

    public static String join(String path, Object part) {
        return path.isEmpty() ? String.valueOf(part) : path + "." + part;
    }

    /**
     * A key as one step of a path, whatever's in it: a dot in it is marked with a backslash, so it
     * isn't taken for where the step ends.
     */
    public static String key(String key) {
        return key.replace("\\", "\\\\").replace(".", "\\.");
    }

    /** A path's steps: split at its dots, other than the ones marked as part of a key. */
    private static String[] steps(String path) {
        List<String> out = new ArrayList<>();
        StringBuilder step = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length()) {
                step.append(path.charAt(++i));
            } else if (c == '.') {
                out.add(step.toString());
                step.setLength(0);
            } else {
                step.append(c);
            }
        }
        out.add(step.toString());
        // Nothing after a last dot is no step, as it has always been here.
        while (out.size() > 1 && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out.toArray(String[]::new);
    }

    /** Where a path's last step starts: after its last dot that isn't part of a key, or at 0 if it's all one step. */
    private static int lastStep(String path) {
        for (int i = path.length() - 1; i >= 0; i--) {
            if (path.charAt(i) == '.') {
                int marks = 0;
                while (i - marks > 0 && path.charAt(i - marks - 1) == '\\') {
                    marks++;
                }
                if (marks % 2 == 0) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /** A step as the key or index it stands for, without the marks on its dots. */
    private static String plain(String step) {
        String[] steps = steps(step);
        return steps.length == 1 ? steps[0] : step;
    }

    /** What's at the path, or null if there's nothing there. */
    public static JsonElement get(JsonElement root, String path) {
        if (path.isEmpty()) {
            return root;
        }
        JsonElement at = root;
        for (String part : steps(path)) {
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

    public static JsonObject object(JsonElement root, String path) {
        return get(root, path) instanceof JsonObject object ? object : null;
    }

    public static JsonArray array(JsonElement root, String path) {
        return get(root, path) instanceof JsonArray array ? array : null;
    }

    public static String string(JsonElement root, String path, String fallback) {
        JsonElement value = get(root, path);
        return value instanceof JsonPrimitive p && p.isString() ? p.getAsString() : fallback;
    }

    public static boolean bool(JsonElement root, String path) {
        JsonElement value = get(root, path);
        return value instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    /** Puts a value at the path, creating objects and arrays on the way. Null removes the key or item. */
    public static void set(JsonElement root, String path, JsonElement value) {
        String[] parts = steps(path);
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

    /** Removes a key or array item. An array left empty is removed too, as the game reads a missing list as empty. */
    public static void remove(JsonElement root, String path) {
        int start = lastStep(path);
        String parentPath = start == 0 ? "" : path.substring(0, start - 1);
        String last = plain(path.substring(start));
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
    public static int append(JsonElement root, String path, JsonElement value) {
        JsonArray array = array(root, path);
        if (array == null) {
            array = new JsonArray();
            set(root, path, array);
        }
        array.add(value);
        return array.size() - 1;
    }

    /** Puts a copy of the list item at the path straight after it. Returns the copy's index, or -1 if there's no such item. */
    public static int duplicate(JsonElement root, String path) {
        int index = indexIn(root, path);
        if (index < 0) {
            return -1;
        }
        JsonArray array = array(root, parent(path));
        // A list has no way to put one in the middle, so the ones after it each move down a place.
        array.add(array.get(array.size() - 1));
        for (int i = array.size() - 2; i > index + 1; i--) {
            array.set(i, array.get(i - 1));
        }
        array.set(index + 1, array.get(index).deepCopy());
        return index + 1;
    }

    /** Moves the list item at the path one place towards the start (-1) or the end (1). Returns where it is now, or -1 if it can't go that way. */
    public static int move(JsonElement root, String path, int by) {
        int index = indexIn(root, path);
        JsonArray array = index < 0 ? null : array(root, parent(path));
        int to = index + by;
        if (array == null || Math.abs(by) != 1 || to < 0 || to >= array.size()) {
            return -1;
        }
        JsonElement moved = array.get(index);
        array.set(index, array.get(to));
        array.set(to, moved);
        return to;
    }

    /**
     * Moves the list item at the path into another list, to stand at {@code to} there, after the others
     * when {@code to} is past its end. The list it leaves stays, even when that leaves it empty, and the
     * one it goes to is made if there isn't one. Returns where it went, or -1 if it can't go.
     */
    public static int moveInto(JsonElement root, String path, String list, int to) {
        int index = indexIn(root, path);
        JsonArray from = index < 0 ? null : array(root, parent(path));
        if (from == null || list.equals(parent(path)) || list.startsWith(path + ".")) {
            return -1;
        }
        JsonArray into = array(root, list);
        if (into == null) {
            into = new JsonArray();
            set(root, list, into);
        }
        JsonElement moved = from.remove(index);
        int at = Math.max(0, Math.min(to, into.size()));
        // Gson's lists can't be put into partway along, so the ones after it move up one.
        into.add(moved);
        for (int i = into.size() - 1; i > at; i--) {
            into.set(i, into.get(i - 1));
        }
        into.set(at, moved);
        return at;
    }

    /**
     * Moves the list item at the path to another place in its list, those between the two each moving
     * one along. Returns where it is now, or -1 if there's no such item or no such place.
     */
    public static int moveTo(JsonElement root, String path, int to) {
        int index = indexIn(root, path);
        JsonArray array = index < 0 ? null : array(root, parent(path));
        if (array == null || to < 0 || to >= array.size()) {
            return -1;
        }
        JsonElement moved = array.get(index);
        int step = to > index ? 1 : -1;
        for (int i = index; i != to; i += step) {
            array.set(i, array.get(i + step));
        }
        array.set(to, moved);
        return to;
    }

    /** How many items the list holding the item at the path has, or 0 if it isn't in one. */
    public static int siblings(JsonElement root, String path) {
        return indexIn(root, path) < 0 ? 0 : array(root, parent(path)).size();
    }

    /** The place in its list of the item at the path, or -1 if the path isn't to an item of a list. */
    public static int indexIn(JsonElement root, String path) {
        int start = lastStep(path);
        String last = path.substring(start);
        JsonArray array = start == 0 ? null : array(root, path.substring(0, start - 1));
        return array != null && isIndex(last) && Integer.parseInt(last) < array.size() ? Integer.parseInt(last) : -1;
    }

    private static String parent(String path) {
        return path.substring(0, lastStep(path) - 1);
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
    public static JsonPrimitive number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return new JsonPrimitive((long) value);
        }
        return new JsonPrimitive(value);
    }
}
