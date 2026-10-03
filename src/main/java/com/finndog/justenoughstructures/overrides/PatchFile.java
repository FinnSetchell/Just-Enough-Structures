package com.finndog.justenoughstructures.overrides;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A file of changes to templates, each for one spot in one template: {"patches": [...], "removed": [...]}.
 * Every entry names its template and spot the same way, so this can find, replace and set aside
 * entries without knowing what else they hold. A file that can't be read is never written over.
 */
final class PatchFile {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String name;

    PatchFile(String name) {
        this.name = name;
    }

    Path path() {
        return LootOverrides.folder().resolve(name);
    }

    /** The entries in use, in the order they were saved. Throws if the file can't be read. */
    JsonArray entries() throws IOException {
        return read().getAsJsonArray("patches");
    }

    /** Saves an entry, replacing any for the same spot. */
    void save(ResourceLocation template, BlockPos pos, JsonObject entry) throws IOException {
        JsonObject json = read();
        JsonArray kept = new JsonArray();
        for (JsonElement element : json.getAsJsonArray("patches")) {
            if (!at(element, template, pos)) {
                kept.add(element);
            }
        }
        entry.addProperty("saved", Instant.now().toString());
        kept.add(entry);
        json.add("patches", kept);
        write(json);
    }

    /** Moves the entry for a spot to the list of removed ones. False if there wasn't one. */
    boolean remove(ResourceLocation template, BlockPos pos) throws IOException {
        JsonObject json = read();
        JsonArray kept = new JsonArray();
        JsonArray removed = json.getAsJsonArray("removed");
        boolean found = false;
        for (JsonElement element : json.getAsJsonArray("patches")) {
            if (at(element, template, pos)) {
                JsonObject gone = element.getAsJsonObject().deepCopy();
                gone.addProperty("removed", Instant.now().toString());
                removed.add(gone);
                found = true;
            } else {
                kept.add(element);
            }
        }
        if (found) {
            json.add("patches", kept);
            write(json);
        }
        return found;
    }

    static JsonArray pos(BlockPos pos) {
        JsonArray out = new JsonArray();
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        return out;
    }

    /** The spot an entry is for, or null if it doesn't say. */
    static BlockPos pos(JsonObject entry) {
        JsonArray pos = entry.getAsJsonArray("pos");
        return pos.size() == 3 ? new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()) : null;
    }

    private static boolean at(JsonElement element, ResourceLocation template, BlockPos pos) {
        try {
            JsonObject entry = element.getAsJsonObject();
            return template.equals(ResourceLocation.tryParse(entry.get("template").getAsString())) && pos.equals(pos(entry));
        } catch (RuntimeException e) {
            // Nonsense in the file is kept as it is.
            return false;
        }
    }

    private JsonObject read() throws IOException {
        Path file = path();
        JsonObject json = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
        if (!json.has("patches") || !json.get("patches").isJsonArray()) {
            json.add("patches", new JsonArray());
        }
        if (!json.has("removed") || !json.get("removed").isJsonArray()) {
            json.add("removed", new JsonArray());
        }
        return json;
    }

    private void write(JsonObject json) throws IOException {
        Path file = path();
        Files.createDirectories(file.getParent());
        Files.writeString(file, PRETTY.toJson(json));
    }
}
