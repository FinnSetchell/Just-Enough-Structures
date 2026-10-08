package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
*///?} else {
import net.minecraft.world.level.storage.loot.LootDataType;
//?}
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Which loot tables each structure uses and which items each table can give. Built from each
 * structure's template pools where it has them, plus generating it to catch loot set by code, so
 * it only knows about loot that structures actually place.
 */
public record LootIndex(Map<ResourceLocation, Set<ResourceLocation>> tablesByStructure,
                        Map<ResourceLocation, Set<ResourceLocation>> itemsByTable) {

    /** Most layouts generated for a structure without pools to read. Different seeds pick different pieces. */
    private static final int SEEDS = 4;

    /**
     * Captures every structure and reads every loot table they use. {@code progress} gets the number
     * of structures done so far; {@code cancelled} is checked between structures.
     */
    public static LootIndex build(MinecraftServer server, IntConsumer progress, BooleanSupplier cancelled) {
        return build(server, new ArrayList<>(server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()), progress, cancelled);
    }

    public static LootIndex build(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled) {
        StructureScan scan = scan(server, ids, progress, cancelled, new AtomicInteger());
        return scan == null ? null : of(server, scan);
    }

    /**
     * The slow half: generates each structure, and reads its template pools where it has them, for
     * the loot tables it uses and the templates it can place. Null if cancelled. Structures that
     * couldn't be generated are counted in {@code failed}.
     */
    public static StructureScan scan(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                     AtomicInteger failed) {
        // How the containers and spawners changed in the browser stand now, which the templates are loaded with.
        Map<ResourceLocation, String> patches = templatePatches();
        // Reading other mods' pieces and loot tables can make vanilla complain on this thread.
        return JesLog.quietly(() -> scanQuietly(server, ids, progress, cancelled, failed, patches));
    }

    /**
     * Each template changed in the browser in a way that changes its loot, as text that changes
     * whenever the changes do: its containers, and its spawners made trial spawners or the other way
     * round, as a trial spawner drops loot when it's beaten.
     */
    private static Map<ResourceLocation, String> templatePatches() {
        Map<ResourceLocation, String> out = new TreeMap<>(ContainerPatches.byTemplate());
        SpawnerPatches.switchesByTemplate().forEach((template, switches) -> out.merge(template, switches, (containers, spawners) -> containers + "\n" + spawners));
        return out;
    }

    /**
     * Generates again only the structures that place a template whose changes differ from when
     * {@code base} was made, and keeps the rest. Null if cancelled.
     */
    public static StructureScan update(MinecraftServer server, StructureScan base, BooleanSupplier cancelled) {
        Map<ResourceLocation, String> now = templatePatches();
        Set<ResourceLocation> changed = base.changedTemplates(now);
        if (changed.isEmpty()) {
            return base;
        }
        Set<ResourceLocation> affected = base.placing(changed);
        StructureScan part = scan(server, new ArrayList<>(affected), done -> {
        }, cancelled, new AtomicInteger());
        if (part == null) {
            return null;
        }
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>(base.tables());
        Map<ResourceLocation, Set<ResourceLocation>> templates = new TreeMap<>(base.templates());
        for (ResourceLocation structure : affected) {
            tables.remove(structure);
            templates.remove(structure);
        }
        tables.putAll(part.tables());
        templates.putAll(part.templates());
        JesLog.debug("Generated {} of {} structures again for what was changed in {}", affected.size(), base.templates().size(), changed);
        return new StructureScan(tables, templates, now);
    }

    /** The index from a scan, with the items each table can give read from the loot tables as they are now. */
    public static LootIndex of(MinecraftServer server, StructureScan scan) {
        return JesLog.quietly(() -> {
            Map<ResourceLocation, Set<ResourceLocation>> items = new TreeMap<>();
            for (Set<ResourceLocation> structureTables : scan.tables().values()) {
                for (ResourceLocation table : structureTables) {
                    items.computeIfAbsent(table, t -> itemsIn(server, t, new HashSet<>()));
                }
            }
            return new LootIndex(scan.tables(), items);
        });
    }

    private static StructureScan scanQuietly(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                             AtomicInteger failed, Map<ResourceLocation, String> patches) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> templates = new TreeMap<>();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        PoolScan scan = new PoolScan(server);
        int done = 0;
        for (ResourceLocation id : ids) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            Set<ResourceLocation> found = new TreeSet<>();
            Set<ResourceLocation> placed = new TreeSet<>();
            // Jigsaw structures' pieces can all be read without generating anything, which finds
            // rare pieces too. One generation still runs for loot that code sets as it places.
            Structure structure = Regs.value(registry, id);
            PoolScan.Reach fromPools = null;
            try {
                fromPools = structure == null ? null : scan.scan(structure);
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                JesLog.debug("Reading {}'s pools failed", id, e);
            }
            if (fromPools != null) {
                found.addAll(fromPools.tables());
                placed.addAll(fromPools.templates());
            }
            // Otherwise keep generating new layouts until one turns up nothing new.
            int seeds = fromPools != null ? 1 : SEEDS;
            boolean generated = false;
            for (int i = 0; i < seeds; i++) {
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                try {
                    CaptureResult result = StructureCapture.captureInBackground(server, id, StructureCapture.defaultSeed(id) + i);
                    if (!result.succeeded()) {
                        break;
                    }
                    generated = true;
                    int before = found.size();
                    for (StructureSnapshot.Container c : result.snapshot().containers()) {
                        ResourceLocation table = c.lootTable() == null ? null : ResourceLocation.tryParse(c.lootTable());
                        if (table != null) {
                            found.add(table);
                        }
                        // The template a container came from, for structures placed without pools.
                        if (c.source() != null) {
                            placed.add(c.source().template());
                        }
                    }
                    // What trial spawners drop when they're beaten, like trial keys.
                    for (CompoundTag tag : result.snapshot().blockEntities()) {
                        for (String drop : TrialSpawners.loot(tag)) {
                            ResourceLocation table = ResourceLocation.tryParse(drop);
                            if (table != null) {
                                found.add(table);
                            }
                        }
                    }
                    if (i > 0 && found.size() == before) {
                        break;
                    }
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    // A broken structure from some mod is left out rather than ending the whole index.
                    JesLog.debug("Indexing {} failed", id, e);
                    break;
                }
            }
            if (!generated) {
                failed.incrementAndGet();
            }
            if (!found.isEmpty()) {
                tables.put(id, found);
            }
            if (!placed.isEmpty()) {
                templates.put(id, placed);
            }
            progress.accept(++done);
        }
        return new StructureScan(tables, templates, patches);
    }

    /** Every item a table can ever give, found by reading the table rather than rolling it. */
    public static Set<ResourceLocation> itemsIn(MinecraftServer server, ResourceLocation tableId, Set<ResourceLocation> seen) {
        Set<ResourceLocation> out = new TreeSet<>();
        if (!seen.add(tableId)) {
            return out;
        }
        LootTable table = LootRolls.table(server, tableId);
        if (table == LootTable.EMPTY) {
            return out;
        }
        try {
            //? if >=1.21 {
            /*walk(server, LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), table).getOrThrow(), out, seen);
            *///?} else {
            walk(server, LootDataType.TABLE.parser().toJsonTree(table), out, seen);
            //?}
        } catch (RuntimeException e) {
            JesLog.debug("Couldn't read loot table {}", tableId, e);
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
        ResourceLocation name = idAt(object, "name");
        // From 1.20.5 a table within a table is under value: its id, or the table itself, walked below.
        if (name == null && (type.equals("minecraft:loot_table") || type.equals("loot_table"))) {
            name = idAt(object, "value");
        }
        if (name != null) {
            switch (type) {
                case "minecraft:item", "item" -> {
                    out.add(name);
                    // From 26.3 an entry's functions are its modifier.
                    String functions = (object.has("functions") ? object.get("functions").toString() : "")
                            + (object.has("modifier") ? object.get("modifier").toString() : "");
                    if (name.getPath().equals("book") && functions.contains("enchant")) {
                        out.add(Ids.parse("enchanted_book"));
                    }
                    if (name.getPath().equals("map") && functions.contains("exploration_map")) {
                        out.add(Ids.parse("filled_map"));
                    }
                }
                case "minecraft:tag", "tag" -> {
                    for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, name))) {
                        item.unwrapKey().ifPresent(key -> out.add(Ids.of(key)));
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

    private static ResourceLocation idAt(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? ResourceLocation.tryParse(object.get(key).getAsString()) : null;
    }
}
