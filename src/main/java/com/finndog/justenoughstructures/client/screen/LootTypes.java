package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Every kind of entry, function and condition a loot table can use, from the game's own lists so
 * mods' kinds are there too, and what a new one of each starts as.
 */
final class LootTypes {
    static final List<String> TABLE_TYPES = List.of("minecraft:chest", "minecraft:archaeology", "minecraft:generic", "minecraft:entity",
            "minecraft:block", "minecraft:fishing", "minecraft:gift", "minecraft:barter", "minecraft:empty");

    /** The ones people use most, first, in this order; then the rest by name. */
    private static final List<String> ENTRIES_FIRST = List.of("item", "tag", "loot_table", "empty", "dynamic", "alternatives", "group", "sequence");
    private static final List<String> FUNCTIONS_FIRST = List.of("set_count", "enchant_randomly", "enchant_with_levels", "set_enchantments", "set_damage",
            "set_potion", "set_stew_effect", "set_name", "set_lore", "set_nbt", "set_instrument", "exploration_map", "limit_count", "looting_enchant",
            "apply_bonus", "explosion_decay", "furnace_smelt");
    private static final List<String> CONDITIONS_FIRST = List.of("random_chance", "random_chance_with_looting", "killed_by_player",
            "survives_explosion", "inverted", "any_of", "all_of", "table_bonus");

    /** What a new function or condition of each kind starts with, so it loads as it is. */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("function:set_count", "{\"count\": 1}"),
            Map.entry("function:enchant_with_levels", "{\"levels\": 30, \"treasure\": false}"),
            Map.entry("function:set_damage", "{\"damage\": {\"type\": \"minecraft:uniform\", \"min\": 0.5, \"max\": 1}}"),
            Map.entry("function:set_potion", "{\"id\": \"minecraft:healing\"}"),
            Map.entry("function:exploration_map", "{\"destination\": \"minecraft:on_treasure_maps\", \"decoration\": \"red_x\", \"zoom\": 1, \"skip_existing_chunks\": false}"),
            Map.entry("function:set_name", "{\"name\": \"Name\"}"),
            Map.entry("function:set_lore", "{\"lore\": []}"),
            Map.entry("function:set_nbt", "{\"tag\": \"{}\"}"),
            Map.entry("function:limit_count", "{\"limit\": {\"min\": 1, \"max\": 64}}"),
            Map.entry("function:looting_enchant", "{\"count\": 1}"),
            Map.entry("function:set_instrument", "{\"options\": \"#minecraft:goat_horns\"}"),
            Map.entry("function:apply_bonus", "{\"enchantment\": \"minecraft:fortune\", \"formula\": \"minecraft:ore_drops\"}"),
            Map.entry("function:set_loot_table", "{\"name\": \"minecraft:chests/simple_dungeon\", \"type\": \"minecraft:chest\"}"),
            Map.entry("function:set_stew_effect", "{\"effects\": []}"),
            Map.entry("condition:random_chance", "{\"chance\": 0.5}"),
            Map.entry("condition:random_chance_with_looting", "{\"chance\": 0.1, \"looting_multiplier\": 0.01}"),
            Map.entry("condition:inverted", "{\"term\": {\"condition\": \"minecraft:random_chance\", \"chance\": 0.5}}"),
            Map.entry("condition:any_of", "{\"terms\": []}"),
            Map.entry("condition:all_of", "{\"terms\": []}"),
            Map.entry("condition:table_bonus", "{\"enchantment\": \"minecraft:fortune\", \"chances\": [0.1, 0.2]}"),
            Map.entry("condition:weather_check", "{\"raining\": true}"),
            Map.entry("condition:time_check", "{\"value\": {\"min\": 0, \"max\": 12000}}"));

    private LootTypes() {
    }

    static List<String> entryTypes() {
        return ordered(BuiltInRegistries.LOOT_POOL_ENTRY_TYPE, ENTRIES_FIRST);
    }

    static List<String> functionTypes() {
        return ordered(BuiltInRegistries.LOOT_FUNCTION_TYPE, FUNCTIONS_FIRST);
    }

    static List<String> conditionTypes() {
        return ordered(BuiltInRegistries.LOOT_CONDITION_TYPE, CONDITIONS_FIRST);
    }

    private static List<String> ordered(Registry<?> registry, List<String> first) {
        List<String> out = new ArrayList<>();
        for (String path : first) {
            if (registry.containsKey(new ResourceLocation(path))) {
                out.add("minecraft:" + path);
            }
        }
        List<String> rest = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            if (!out.contains(id.toString())) {
                rest.add(id.toString());
            }
        }
        rest.sort(null);
        out.addAll(rest);
        return out;
    }

    /** A new function of this kind, with what it needs filled in. */
    static JsonObject function(String type) {
        JsonObject out = defaults("function:" + shortId(type));
        out.addProperty("function", type);
        return reorder(out, "function");
    }

    static JsonObject condition(String type) {
        JsonObject out = defaults("condition:" + shortId(type));
        out.addProperty("condition", type);
        return reorder(out, "condition");
    }

    /** A new entry of this kind. */
    static JsonObject entry(String type, JsonObject was) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        String kind = shortId(type);
        switch (kind) {
            case "item" -> out.addProperty("name", was != null && was.has("name") && isItemType(was) ? was.get("name").getAsString() : "minecraft:stick");
            case "tag" -> {
                out.addProperty("name", "minecraft:arrows");
                out.addProperty("expand", false);
            }
            case "loot_table" -> out.addProperty("name", "minecraft:chests/simple_dungeon");
            case "dynamic" -> out.addProperty("name", "minecraft:contents");
            case "alternatives", "group", "sequence" -> out.add("children", new JsonArray());
            default -> {
            }
        }
        if (was != null) {
            // What the entry had that every kind has: its weight, quality, functions and conditions.
            for (String key : List.of("weight", "quality", "functions", "conditions")) {
                if (was.has(key) && !List.of("alternatives", "group", "sequence").contains(kind)) {
                    out.add(key, was.get(key).deepCopy());
                }
            }
        }
        return out;
    }

    private static boolean isItemType(JsonObject entry) {
        return entry.has("type") && shortId(entry.get("type").getAsString()).equals("item");
    }

    private static JsonObject defaults(String key) {
        String json = DEFAULTS.get(key);
        return json == null ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject();
    }

    /** The type key first, as the game's own files have it. */
    private static JsonObject reorder(JsonObject object, String first) {
        JsonObject out = new JsonObject();
        out.add(first, object.get(first));
        object.entrySet().forEach(e -> {
            if (!e.getKey().equals(first)) {
                out.add(e.getKey(), e.getValue());
            }
        });
        return out;
    }

    /** "minecraft:set_count" as "set_count"; a mod's own kind keeps its namespace. */
    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    /** A readable name: a translation if there's one, otherwise the id tidied up, with a mod's namespace after it. */
    static String name(String kind, String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return id;
        }
        String fallback = StructureNames.pretty(parsed.getPath());
        if (!parsed.getNamespace().equals("minecraft")) {
            fallback += " (" + StructureNames.mod(parsed.getNamespace()) + ")";
        }
        return Component.translatableWithFallback("screen.justenoughstructures.editor." + kind + "." + parsed.getPath(), fallback).getString();
    }
}
