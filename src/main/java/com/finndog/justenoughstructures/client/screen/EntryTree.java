package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A pool's entries in the editor's list order: each entry, followed by its nested entries at any
 * depth. A nested entry is found by its path from the pool's top-level entry, like "children.1" or
 * "children.1.children.0". No screen dependencies, so game tests can use it.
 */
public final class EntryTree {
    /** The kinds of entry that hold other entries. */
    private static final List<String> HOLDERS = List.of("alternatives", "group", "sequence");

    /**
     * One row of the list. {@code entry} is the index of the top-level entry it is or sits in;
     * {@code child} is the path from there, empty for the top-level entry; {@code depth} is how deep
     * it's nested; {@code object} is the entry's JSON.
     */
    public record Node(int entry, String child, int depth, JsonObject object) {
    }

    private EntryTree() {
    }

    /** A pool's entries in the order they're listed, each followed by those inside it. Null gives none. */
    public static List<Node> of(JsonArray entries) {
        List<Node> out = new ArrayList<>();
        for (int e = 0; entries != null && e < entries.size(); e++) {
            add(out, e, "", 0, entries.get(e));
        }
        return out;
    }

    private static void add(List<Node> out, int entry, String child, int depth, JsonElement element) {
        JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        out.add(new Node(entry, child, depth, object));
        if (holdsEntries(object) && object.get("children") instanceof JsonArray children) {
            for (int c = 0; c < children.size(); c++) {
                add(out, entry, inside(child, c), depth + 1, children.get(c));
            }
        }
    }

    /** Whether an entry is of a kind that holds others. */
    public static boolean holdsEntries(JsonObject entry) {
        JsonElement type = entry.get("type");
        return type != null && type.isJsonPrimitive() && HOLDERS.contains(LootTypes.shortId(type.getAsString()));
    }

    /** Returns an entry's path in the table. */
    public static String path(int pool, int entry, String child) {
        String path = "pools." + pool + ".entries." + entry;
        return child.isEmpty() ? path : path + "." + child;
    }

    /** Returns the path of the entry containing this one, or empty if that's the top-level entry. */
    public static String parent(String child) {
        int cut = child.lastIndexOf(".children.");
        return cut < 0 ? "" : child.substring(0, cut);
    }

    /** Returns the path to this entry's sibling at {@code index}. */
    public static String beside(String child, int index) {
        return child.substring(0, child.lastIndexOf('.') + 1) + index;
    }

    /**
     * Returns the index in its list where a row dragged to {@code y} would land. {@code tops} and
     * {@code bottoms} give each item's drawn span, including its nested rows; {@code dragged} is the
     * dragged item's index, or -1. It lands after every other item whose middle is above {@code y}.
     */
    public static int dropIndex(int[] tops, int[] bottoms, int dragged, int y) {
        int above = 0;
        for (int i = 0; i < tops.length; i++) {
            if (i != dragged && (tops[i] + bottoms[i]) / 2 < y) {
                above++;
            }
        }
        return above;
    }

    /** Returns the path to this entry's child at {@code index}. */
    public static String inside(String child, int index) {
        return (child.isEmpty() ? "children." : child + ".children.") + index;
    }
}
