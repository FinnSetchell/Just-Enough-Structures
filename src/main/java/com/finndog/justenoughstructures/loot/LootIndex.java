package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Which loot tables each structure uses and which items each table can give. Built by capturing
 * every structure, so it only knows about loot that structures actually place.
 */
public record LootIndex(Map<ResourceLocation, Set<ResourceLocation>> tablesByStructure,
                        Map<ResourceLocation, Set<ResourceLocation>> itemsByTable) {

    /** Seeds captured per structure. Different seeds pick different pieces, so more seeds find more loot. */
    private static final int SEEDS = 3;

    /**
     * Captures every structure and reads every loot table they use. {@code progress} gets the number
     * of structures done so far; {@code cancelled} is checked between structures.
     */
    public static LootIndex build(MinecraftServer server, IntConsumer progress, BooleanSupplier cancelled) {
        return build(server, new ArrayList<>(server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()), progress, cancelled);
    }

    public static LootIndex build(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        int done = 0;
        for (ResourceLocation id : ids) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            Set<ResourceLocation> found = new TreeSet<>();
            for (int i = 0; i < SEEDS; i++) {
                try {
                    CaptureResult result = StructureCapture.capture(server, id, StructureCapture.defaultSeed(id) + i);
                    if (!result.succeeded()) {
                        break;
                    }
                    for (StructureSnapshot.Container c : result.snapshot().containers()) {
                        ResourceLocation table = c.lootTable() == null ? null : ResourceLocation.tryParse(c.lootTable());
                        if (table != null) {
                            found.add(table);
                        }
                    }
                } catch (RuntimeException e) {
                    JustEnoughStructures.LOGGER.debug("Indexing {} failed", id, e);
                    break;
                }
            }
            if (!found.isEmpty()) {
                tables.put(id, found);
            }
            progress.accept(++done);
        }

        Map<ResourceLocation, Set<ResourceLocation>> items = new TreeMap<>();
        for (Set<ResourceLocation> structureTables : tables.values()) {
            for (ResourceLocation table : structureTables) {
                items.computeIfAbsent(table, t -> itemsIn(server, t, new HashSet<>()));
            }
        }
        return new LootIndex(tables, items);
    }

    /** Every item a table can ever give, found by reading the table rather than rolling it. */
    public static Set<ResourceLocation> itemsIn(MinecraftServer server, ResourceLocation tableId, Set<ResourceLocation> seen) {
        Set<ResourceLocation> out = new TreeSet<>();
        if (!seen.add(tableId)) {
            return out;
        }
        LootTable table = server.getLootData().getLootTable(tableId);
        if (table == LootTable.EMPTY) {
            return out;
        }
        try {
            walk(server, LootDataType.TABLE.parser().toJsonTree(table), out, seen);
        } catch (RuntimeException e) {
            JustEnoughStructures.LOGGER.debug("Couldn't read loot table {}", tableId, e);
        }
        return out;
    }

    private static void walk(MinecraftServer server, JsonElement json, Set<ResourceLocation> out, Set<ResourceLocation> seen) {
        if (json instanceof JsonArray array) {
            array.forEach(e -> walk(server, e, out, seen));
            return;
        }
        if (!(json instanceof JsonObject object)) {
            return;
        }
        String type = object.has("type") && object.get("type").isJsonPrimitive() ? object.get("type").getAsString() : "";
        ResourceLocation name = object.has("name") && object.get("name").isJsonPrimitive()
                ? ResourceLocation.tryParse(object.get("name").getAsString()) : null;
        if (name != null) {
            switch (type) {
                case "minecraft:item", "item" -> {
                    out.add(name);
                    String functions = object.has("functions") ? object.get("functions").toString() : "";
                    if (name.getPath().equals("book") && functions.contains("enchant")) {
                        out.add(new ResourceLocation("enchanted_book"));
                    }
                    if (name.getPath().equals("map") && functions.contains("exploration_map")) {
                        out.add(new ResourceLocation("filled_map"));
                    }
                }
                case "minecraft:tag", "tag" -> {
                    for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, name))) {
                        item.unwrapKey().ifPresent(key -> out.add(key.location()));
                    }
                }
                case "minecraft:loot_table", "loot_table" -> out.addAll(itemsIn(server, name, seen));
                default -> {
                }
            }
        }
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            walk(server, e.getValue(), out, seen);
        }
    }
}
