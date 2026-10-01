package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * How the browser was left: spin, markers, ground, maximise, the Info tab's details, the loot
 * sort order and the player's favourite structures carry over to the next time it's opened, even
 * after a restart. Saved as the player uses it, the way JEI keeps its own state.
 */
public final class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        JsonObject json = new JsonObject();
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
            Files.writeString(file, GSON.toJson(json));
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

    private static boolean flag(JsonObject json, String key, boolean fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }
}
