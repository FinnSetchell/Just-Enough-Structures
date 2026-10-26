package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.FileFormat;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.TrailingCommas;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Browser settings that carry over to the next time it's opened, even after a restart: spin,
 * markers, ground, maximise, the Info tab's details, the loot sort order and the player's favourite
 * structures, and which chest loot tabs the recipe viewers show. Saved as the player uses the
 * browser, as JEI saves its state. The player's notes on structures are saved the same way, in a
 * separate file.
 */
public final class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean spin = true;
    public static boolean markers = true;
    public static boolean ground = true;
    public static boolean maximised;
    public static boolean details;
    public static boolean rarestFirst;
    /** Hides Pack tools for players who can use it but don't want to see it: no button, no popup icons, no marks on edited tables. */
    public static boolean hidePackTools;
    /** Which chest loot tabs the recipe viewers show when another mod has one too. Set through {@code ChestLootTabs.choose}. */
    public static volatile ChestLoot chestLoot = ChestLoot.BOTH;
    /** Structure ids the player starred, shown at the top of the list in every world that has them. */
    public static final Set<String> favourites = new LinkedHashSet<>();
    /**
     * Player notes by structure id. Kept in notes.json, not state.json, because older versions rewrite
     * state.json and would drop them.
     */
    public static final Map<String, String> notes = new LinkedHashMap<>();
    /** Maximum note length, in characters. */
    public static final int NOTE_LIMIT = 2000;
    private static boolean loaded;
    /** Files saved by a newer JES, which this version neither reads nor overwrites. */
    private static final Set<Path> newerFiles = new HashSet<>();

    private ClientState() {
    }

    private static Path file() {
        return JustEnoughStructures.configDir().resolve("state.json");
    }

    private static Path notesFile() {
        return JustEnoughStructures.configDir().resolve("notes.json");
    }

    /** Which chest loot tabs the recipe viewers show. */
    public enum ChestLoot {
        /** JES's Found in structures and other mods' chest loot tabs, as without the setting. */
        BOTH,
        /** Found in structures, with other mods' chest loot tabs hidden. */
        JUST_ENOUGH_STRUCTURES,
        /** Other mods' chest loot tabs, without Found in structures. */
        OTHER_MODS;

        /** How it's written in state.json. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The value written as {@code key}, or null if there's none. */
        public static ChestLoot of(String key) {
            for (ChestLoot value : values()) {
                if (value.key().equals(key)) {
                    return value;
                }
            }
            return null;
        }
    }

    /**
     * Reads the saved state and notes the first time the browser, the settings screen or a recipe
     * viewer needs them. Viewers load on threads of their own, so only one reads at a time.
     */
    public static synchronized void load() {
        if (!loaded) {
            loaded = true;
            read(file());
            readNotes(notesFile());
        }
    }

    public static void save() {
        write(file());
    }

    /** Reads the state file. A missing or unreadable value keeps its current one. */
    public static void read(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(TrailingCommas.strip(Files.readString(file)), JsonObject.class);
            if (FileFormat.newer(json)) {
                newerFiles.add(file);
            }
            FileFormat.check(json, file);
            spin = flag(json, "spin", spin);
            markers = flag(json, "markers", markers);
            ground = flag(json, "ground", ground);
            maximised = flag(json, "maximised", maximised);
            details = flag(json, "details", details);
            rarestFirst = flag(json, "rarest_first", rarestFirst);
            hidePackTools = flag(json, "hide_pack_tools", hidePackTools);
            if (json != null && json.has("chest_loot_in_viewers") && json.get("chest_loot_in_viewers").isJsonPrimitive()) {
                ChestLoot saved = ChestLoot.of(json.get("chest_loot_in_viewers").getAsString());
                chestLoot = saved != null ? saved : chestLoot;
            }
            if (json != null && json.has("favourites") && json.get("favourites").isJsonArray()) {
                favourites.clear();
                for (JsonElement id : json.getAsJsonArray("favourites")) {
                    if (id.isJsonPrimitive() && ResourceLocation.tryParse(id.getAsString()) != null) {
                        favourites.add(id.getAsString());
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            JesLog.debug("Couldn't read {}, keeping the defaults", file, e);
        }
    }

    public static void write(Path file) {
        if (newerFiles.contains(file)) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty(FileFormat.KEY, FileFormat.CURRENT);
        json.addProperty("spin", spin);
        json.addProperty("markers", markers);
        json.addProperty("ground", ground);
        json.addProperty("maximised", maximised);
        json.addProperty("details", details);
        json.addProperty("rarest_first", rarestFirst);
        json.addProperty("hide_pack_tools", hidePackTools);
        json.addProperty("chest_loot_in_viewers", chestLoot.key());
        JsonArray starred = new JsonArray();
        favourites.forEach(starred::add);
        json.add("favourites", starred);
        try {
            Files.createDirectories(file.getParent());
            SafeFiles.write(file, GSON.toJson(json));
        } catch (IOException e) {
            JesLog.debug("Couldn't save {}", file, e);
        }
    }

    /** Stars or unstars a structure, and saves at once. */
    public static void toggleFavourite(ResourceLocation id) {
        if (!favourites.remove(id.toString())) {
            favourites.add(id.toString());
        }
        save();
    }

    public static boolean isFavourite(ResourceLocation id) {
        return favourites.contains(id.toString());
    }

    /** What the player wrote about a structure, or an empty string. */
    public static String note(ResourceLocation id) {
        return notes.getOrDefault(id.toString(), "");
    }

    /** Sets the player's note on a structure, or removes it when blank, and saves if it changed. */
    public static void setNote(ResourceLocation id, String text) {
        String kept = kept(text);
        if (kept.equals(note(id))) {
            return;
        }
        if (kept.isEmpty()) {
            notes.remove(id.toString());
        } else {
            notes.put(id.toString(), kept);
        }
        writeNotes(notesFile());
    }

    /** Normalises a note: empty if blank, trailing whitespace stripped, and cut to {@link #NOTE_LIMIT} characters. */
    public static String kept(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String kept = text.stripTrailing();
        return kept.length() > NOTE_LIMIT ? kept.substring(0, NOTE_LIMIT) : kept;
    }

    /** Reads the notes file. If it's missing or unreadable, the current notes stay. Notes whose key isn't an id are skipped. */
    public static void readNotes(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(TrailingCommas.strip(Files.readString(file)), JsonObject.class);
            if (FileFormat.newer(json)) {
                newerFiles.add(file);
            }
            FileFormat.check(json, file);
            if (json != null && json.has("notes") && json.get("notes").isJsonObject()) {
                notes.clear();
                for (Map.Entry<String, JsonElement> note : json.getAsJsonObject("notes").entrySet()) {
                    if (note.getValue().isJsonPrimitive() && ResourceLocation.tryParse(note.getKey()) != null) {
                        String text = kept(note.getValue().getAsString());
                        if (!text.isEmpty()) {
                            notes.put(note.getKey(), text);
                        }
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            JesLog.debug("Couldn't read {}, keeping the notes as they were", file, e);
        }
    }

    public static void writeNotes(Path file) {
        if (newerFiles.contains(file)) {
            return;
        }
        JsonObject written = new JsonObject();
        notes.forEach(written::addProperty);
        JsonObject json = new JsonObject();
        json.addProperty(FileFormat.KEY, FileFormat.CURRENT);
        json.add("notes", written);
        try {
            Files.createDirectories(file.getParent());
            SafeFiles.write(file, GSON.toJson(json));
        } catch (IOException e) {
            JesLog.warnOnce("notes-unsaved", "Couldn't save the notes written in the browser to {}", file, e);
        }
    }

    private static boolean flag(JsonObject json, String key, boolean fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }
}
