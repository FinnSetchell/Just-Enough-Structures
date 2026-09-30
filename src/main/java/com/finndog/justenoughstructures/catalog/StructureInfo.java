package com.finndog.justenoughstructures.catalog;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * What a mod or modpack says about one of its structures, from a datapack file at
 * {@code data/<namespace>/justenoughstructures/structures/<path>.json} for the structure
 * {@code <namespace>:<path>}. Every field is optional:
 *
 * <pre>
 * {
 *   "notes": "Shown in an Author's notes section. A plain string or any text component.",
 *   "author": "Who the notes are from",
 *   "hide_loot_locations": true
 * }
 * </pre>
 *
 * @param notes             text for the Info tab, or null
 * @param author            who wrote the notes, or null
 * @param hideLootLocations keep where the loot is out of the preview
 */
public record StructureInfo(Component notes, String author, boolean hideLootLocations) {
    public static final StructureInfo NONE = new StructureInfo(null, null, false);
    public static final String DIRECTORY = JustEnoughStructures.MOD_ID + "/structures";

    private static volatile Map<ResourceLocation, StructureInfo> loaded = Map.of();

    public static StructureInfo forStructure(ResourceLocation id) {
        return loaded.getOrDefault(id, NONE);
    }

    public StructureInfo hidingLoot() {
        return hideLootLocations ? this : new StructureInfo(notes, author, true);
    }

    /** Reads one file's contents. Throws on anything that isn't what the format above describes. */
    public static StructureInfo parse(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new JsonParseException("expected an object");
        }
        JsonObject object = json.getAsJsonObject();
        Component notes = null;
        if (object.has("notes")) {
            notes = Component.Serializer.fromJson(object.get("notes"));
            if (notes == null) {
                throw new JsonParseException("notes should be a string or a text component");
            }
        }
        String author = null;
        if (object.has("author")) {
            JsonElement value = object.get("author");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("author should be a string");
            }
            author = value.getAsString();
        }
        boolean hide = false;
        if (object.has("hide_loot_locations")) {
            JsonElement value = object.get("hide_loot_locations");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("hide_loot_locations should be true or false");
            }
            hide = value.getAsBoolean();
        }
        return new StructureInfo(notes, author, hide);
    }

    /** Loads every structure's file on server start and /reload. Each loader registers it its own way. */
    public static class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, StructureInfo> out = new HashMap<>();
            files.forEach((id, json) -> {
                try {
                    out.put(id, parse(json));
                } catch (RuntimeException e) {
                    JustEnoughStructures.LOGGER.warn("Ignoring {}/{}.json in {}: {}", DIRECTORY, id.getPath(), id.getNamespace(), e.getMessage());
                }
            });
            loaded = Map.copyOf(out);
        }
    }
}
