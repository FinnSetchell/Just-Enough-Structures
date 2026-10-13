package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A pool's entries as the editor lists them: each one, and after it the entries inside it when
 * it's a kind that holds entries, however deep those go. An entry inside another is found by the
 * path down to it from its pool's entry, like "children.1" or "children.1.children.0". Plain, with
 * nothing of the screen's in it, so the game tests can run it.
 */
public final class EntryTree {
    /** The kinds of entry that hold other entries. */
    private static final List<String> HOLDERS = List.of("alternatives", "group", "sequence");

    /**
     * One entry in the list: which of the pool's entries it is or is inside, the path from that one
     * down to it, empty for that one itself, how many entries it's inside, and the entry.
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

    /** Where in its table an entry is. */
    public static String path(int pool, int entry, String child) {
        String path = "pools." + pool + ".entries." + entry;
        return child.isEmpty() ? path : path + "." + child;
    }

    /** The path to the entry this one is inside, which is empty when that's the pool's own entry. */
    public static String parent(String child) {
        int cut = child.lastIndexOf(".children.");
        return cut < 0 ? "" : child.substring(0, cut);
    }

    /** The path to another of the entries this one is among, by its place among them. */
    public static String beside(String child, int index) {
        return child.substring(0, child.lastIndexOf('.') + 1) + index;
    }

    /**
     * Where in its list a row dragged to {@code y} would land. {@code tops} and {@code bottoms} are where
     * each thing in the list is drawn, with the rows of whatever's inside it; {@code dragged} is the one
     * being dragged. It lands after every other one whose middle is above where it has been taken.
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

    /** The path to an entry inside this one, by its place among those. */
    public static String inside(String child, int index) {
        return (child.isEmpty() ? "children." : child + ".children.") + index;
    }
}
