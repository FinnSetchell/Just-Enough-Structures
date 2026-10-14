package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.FileFormat;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
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
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * How the browser was left: spin, markers, ground, maximise, the Info tab's details, the loot
 * sort order and the player's favourite structures carry over to the next time it's opened, even
 * after a restart. Saved as the player uses it, the way JEI keeps its own state. The player's own
 * notes on structures are kept the same way, in a file of their own.
 */
public final class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean spin = true;
    public static boolean markers = true;
    public static boolean ground = true;
    public static boolean maximised;
    public static boolean details;
    public static boolean rarestFirst;
    /** For players allowed Pack tools who'd rather not see it: no button, no popup icons, no marks on edited tables. */
    public static boolean hidePackTools;
    /** Structure ids the player starred, shown at the top of the list in every world that has them. */
    public static final Set<String> favourites = new LinkedHashSet<>();
    /**
     * What the player wrote about each structure, by its id. They have a file of their own, as a
     * version from before them would write the state file again without them.
     */
    public static final Map<String, String> notes = new LinkedHashMap<>();
    /** The most a player can write about one structure: a few pages of a book. */
    public static final int NOTE_LIMIT = 2000;
    private static boolean loaded;
    /** Files a newer version saved, which this one neither reads nor writes over. */
    private static final Set<Path> newerFiles = new HashSet<>();

    private ClientState() {
    }

    private static Path file() {
        return JustEnoughStructures.configDir().resolve("state.json");
    }

    private static Path notesFile() {
        return JustEnoughStructures.configDir().resolve("notes.json");
    }

    /** Reads the saved state and notes the first time the browser opens. */
    public static void load() {
        if (!loaded) {
            loaded = true;
            read(file());
            readNotes(notesFile());
        }
    }

    public static void save() {
        write(file());
    }

    /** Anything missing or unreadable keeps the value it had. */
    public static void read(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
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

    /** Stars or unstars a structure, and saves straight away. */
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

    /** Keeps what the player wrote about a structure, or forgets it when it's blank, and saves if it changed. */
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

    /** Notes as they're kept: nothing when they're blank, no spaces or empty lines at the end, and no longer than the limit. */
    public static String kept(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String kept = text.stripTrailing();
        return kept.length() > NOTE_LIMIT ? kept.substring(0, NOTE_LIMIT) : kept;
    }

    /** Anything missing or unreadable keeps the notes there were. Notes on something that isn't an id are skipped. */
    public static void readNotes(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
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
