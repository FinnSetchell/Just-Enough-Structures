package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How the browser was left: spin, markers, ground, maximise, the Info tab's details and the loot
 * sort order carry over to the next time it's opened, even after a restart. Saved as the player
 * uses it, the way JEI keeps its own state, so there's no settings screen for these.
 */
public final class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean spin = true;
    public static boolean markers = true;
    public static boolean ground = true;
    public static boolean maximised;
    public static boolean details;
    public static boolean rarestFirst;
    private static boolean loaded;

    private ClientState() {
    }

    private static Path file() {
        return JustEnoughStructures.configDir().resolve("state.json");
    }

    /** Reads the saved state the first time the browser opens. */
    public static void load() {
        if (!loaded) {
            loaded = true;
            read(file());
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
            spin = flag(json, "spin", spin);
            markers = flag(json, "markers", markers);
            ground = flag(json, "ground", ground);
            maximised = flag(json, "maximised", maximised);
            details = flag(json, "details", details);
            rarestFirst = flag(json, "rarest_first", rarestFirst);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't read {}, keeping the defaults", file, e);
        }
    }

    public static void write(Path file) {
        JsonObject json = new JsonObject();
        json.addProperty("spin", spin);
        json.addProperty("markers", markers);
        json.addProperty("ground", ground);
        json.addProperty("maximised", maximised);
        json.addProperty("details", details);
        json.addProperty("rarest_first", rarestFirst);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json));
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save {}", file, e);
        }
    }

    private static boolean flag(JsonObject json, String key, boolean fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }
}
