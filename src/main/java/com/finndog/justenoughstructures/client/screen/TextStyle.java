package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Set;

/**
 * Text the game shows, like an item's name or a line of its lore, as far as the loot editor's
 * boxes go: the words, a colour, and whether it's bold or italic. That's written as plain text
 * when it has no colour or style, and otherwise as the plainest text component that says so, like
 * {"text": "Fang", "color": "gold", "italic": false}. Plain, with nothing of the screen's in it, so
 * the game tests can run it.
 */
public final class TextStyle {
    /** The game's sixteen named colours for text, in its own order. */
    public static final List<String> COLOURS = List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final Set<String> KEYS = Set.of("text", "color", "bold", "italic");

    private TextStyle() {
    }

    /**
     * Whether the boxes can show all there is to this text: none at all, plain text, or a component
     * with nothing but its words, a colour, bold and italic. Anything more is left to be edited as JSON.
     */
    public static boolean simple(JsonElement value) {
        if (value == null) {
            return true;
        }
        if (value instanceof JsonPrimitive plain) {
            return plain.isString();
        }
        if (!(value instanceof JsonObject component) || !(component.get("text") instanceof JsonPrimitive)) {
            return false;
        }
        for (String key : component.keySet()) {
            JsonElement set = component.get(key);
            boolean fits = switch (key) {
                case "text", "color" -> set instanceof JsonPrimitive p && p.isString();
                case "bold", "italic" -> set instanceof JsonPrimitive p && p.isBoolean();
                default -> false;
            };
            if (!KEYS.contains(key) || !fits) {
                return false;
            }
        }
        return true;
    }

    /** The words. */
    public static String text(JsonElement value) {
        return value == null ? "" : value instanceof JsonObject component ? component.get("text").getAsString() : value.getAsString();
    }

    /** The colour's name, or nothing for the game's own colour for it. */
    public static String colour(JsonElement value) {
        return value instanceof JsonObject component && component.has("color") ? component.get("color").getAsString() : "";
    }

    /** Whether it's bold or italic, by that key: yes, no, or null where it's left to the game, which slants names and lore. */
    public static Boolean flag(JsonElement value, String key) {
        return value instanceof JsonObject component && component.has(key) ? component.get(key).getAsBoolean() : null;
    }

    /** The colour a named colour is drawn in, or grey for one that isn't one of the sixteen. */
    public static int rgb(String colour) {
        int index = COLOURS.indexOf(colour);
        return index < 0 ? 0x6D6D6D : RGB[index];
    }

    /**
     * The text with its words, its colour or one of its styles changed, by that key, or taken off
     * with null. Left with words alone, it's plain text again.
     */
    public static JsonElement with(JsonElement value, String key, JsonElement to) {
        JsonObject out = new JsonObject();
        out.addProperty("text", text(value));
        if (value instanceof JsonObject component) {
            for (String kept : List.of("color", "bold", "italic")) {
                if (component.has(kept)) {
                    out.add(kept, component.get(kept));
                }
            }
        }
        if (to == null) {
            out.remove(key);
        } else {
            out.add(key, to);
        }
        return out.size() == 1 ? new JsonPrimitive(out.get("text").getAsString()) : out;
    }
}
