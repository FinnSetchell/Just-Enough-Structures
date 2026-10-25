package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Memory;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.LoadedPieces;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Which loot tables each structure uses and which items each table can give, and the items each
 * structure places itself without a loot table, like the elytra in an End city ship's item frame.
 * Built from each structure's template pools where it has them, plus generating it to catch loot set
 * by code, so it only knows about loot that structures actually place. An item counts as placed when
 * any piece the pools can reach, or any layout generated for the index, places it.
 */
public record LootIndex(Map<ResourceLocation, Set<ResourceLocation>> tablesByStructure,
                        Map<ResourceLocation, Set<ResourceLocation>> itemsByTable,
                        Map<ResourceLocation, Set<ResourceLocation>> placedByStructure) {

    public LootIndex(Map<ResourceLocation, Set<ResourceLocation>> tablesByStructure, Map<ResourceLocation, Set<ResourceLocation>> itemsByTable) {
        this(tablesByStructure, itemsByTable, Map.of());
    }

    /** Most layouts generated for a structure without pools to read. Different seeds pick different pieces. */
    private static final int MOST_LAYOUTS = 16;
    /**
     * A structure without pools is done once the layouts since it last found a new loot table have
     * placed this many pieces between them. An igloo is one piece or a few, and its basement chest
     * comes with about one igloo in two, so it gets many quick layouts. A mineshaft rolls for its rare
     * parts many times in each layout, so it gets as few as two, which take far longer.
     */
    private static final int QUIET_PIECES = 16;
    /** The same in blocks, for a big structure placed in a few pieces. */
    private static final long QUIET_BLOCKS = 50_000;
    /**
     * The first this many layouts use consecutive seeds, starting at the first layout's, so the index
     * finds at least what those seeds find.
     */
    private static final int NEXT_SEEDS = 4;
    /**
     * How far apart the seeds of the layouts after those are. A structure's random starts from its
     * seed, and seeds next to each other draw nearly the same first number, which can be the one that
     * picks a part.
     */
    private static final long LAYOUT_STEP = 0x6A09E667F3BCC909L;
    /** The game's functions that turn a plain book into an enchanted one. */
    private static final List<String> ENCHANTING = List.of("enchant_randomly", "enchant_with_levels", "set_enchantments");

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
     * couldn't be generated are counted in {@code failed}, except those that may work another time,
     * which the scan keeps to try again.
     */
    public static StructureScan scan(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                     AtomicInteger failed) {
        return scan(server, ids, progress, cancelled, failed, (id, firstView) -> {
        });
    }

    /** The same, handing each structure's first view, made with its first seed, to {@code firstViews} as it goes. */
    public static StructureScan scan(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                     AtomicInteger failed, BiConsumer<ResourceLocation, CaptureResult> firstViews) {
        return scan(server, ids, progress, cancelled, failed, firstViews, early -> {
        });
    }

    /**
     * The same, also handing {@code early} the partial scan once the quick part is done: every
     * structure's pools read, and the structures without pools generated. That is most of the loot,
     * long before the rest have each been generated for what their code adds as it places them.
     * {@code progress} counts finished structures, so those with pools only count once they've been
     * generated.
     */
    public static StructureScan scan(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                     AtomicInteger failed, BiConsumer<ResourceLocation, CaptureResult> firstViews, Consumer<StructureScan> early) {
        // Current container and spawner patches, which templates load with.
        Map<ResourceLocation, String> patches = templatePatches();
        // Reading other mods' pieces and loot tables can make vanilla complain on this thread.
        return JesLog.quietly(() -> scanQuietly(server, ids, progress, cancelled, failed, patches, firstViews, early));
    }

    /**
     * Each patched template's changes that affect loot, as a string that changes whenever they do:
     * container tables, and spawners switched to or from trial spawners (which drop loot when beaten).
     */
    private static Map<ResourceLocation, String> templatePatches() {
        Map<ResourceLocation, String> out = new TreeMap<>(ContainerPatches.byTemplate());
        SpawnerPatches.switchesByTemplate().forEach((template, switches) -> out.merge(template, switches, (containers, spawners) -> containers + "\n" + spawners));
        return out;
    }

    /**
     * Generates again only the structures that place a template whose changes differ from when
     * {@code base} was made, and those it's keeping to try again, and keeps the rest. Null if cancelled.
     */
    public static StructureScan update(MinecraftServer server, StructureScan base, BooleanSupplier cancelled) {
        Map<ResourceLocation, String> now = templatePatches();
        Set<ResourceLocation> changed = base.changedTemplates(now);
        if (changed.isEmpty() && base.retry().isEmpty()) {
            return base;
        }
        Set<ResourceLocation> affected = new TreeSet<>(base.placing(changed));
        affected.addAll(base.retry());
        StructureScan part = scan(server, new ArrayList<>(affected), done -> {
        }, cancelled, new AtomicInteger());
        if (part == null) {
            return null;
        }
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>(base.tables());
        Map<ResourceLocation, Set<ResourceLocation>> templates = new TreeMap<>(base.templates());
        Map<ResourceLocation, Set<ResourceLocation>> placed = new TreeMap<>(base.placed());
        for (ResourceLocation structure : affected) {
            tables.remove(structure);
            templates.remove(structure);
            placed.remove(structure);
        }
        tables.putAll(part.tables());
        templates.putAll(part.templates());
        placed.putAll(part.placed());
        JesLog.debug("Generated {} of {} structures again for what was changed in {} and {} tried again", affected.size(), base.templates().size(),
                changed, base.retry());
        return new StructureScan(tables, templates, now, part.retry(), placed);
    }

    /** The index from a scan, with the items each table can give read from the loot tables as they are now, and the items each structure places. */
    public static LootIndex of(MinecraftServer server, StructureScan scan) {
        return JesLog.quietly(() -> {
            Map<ResourceLocation, Set<ResourceLocation>> items = new TreeMap<>();
            for (Set<ResourceLocation> structureTables : scan.tables().values()) {
                for (ResourceLocation table : structureTables) {
                    items.computeIfAbsent(table, t -> itemsIn(server, t, new HashSet<>()));
                }
            }
            return new LootIndex(scan.tables(), items, scan.placed());
        });
    }

    private static StructureScan scanQuietly(MinecraftServer server, List<ResourceLocation> ids, IntConsumer progress, BooleanSupplier cancelled,
                                             AtomicInteger failed, Map<ResourceLocation, String> patches,
                                             BiConsumer<ResourceLocation, CaptureResult> firstViews, Consumer<StructureScan> early) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> templates = new TreeMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> placed = new TreeMap<>();
        Set<ResourceLocation> retry = new TreeSet<>();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        PoolScan scan = new PoolScan(server);
        int done = 0;
        // First what's quick to find: every structure's pools are read, and the structures without
        // pools generated, which is all there is to do for those.
        Map<ResourceLocation, PoolScan.Reach> pooled = new LinkedHashMap<>();
        for (ResourceLocation id : ids) {
            Found found = LoadedPieces.during(server, () -> {
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                PoolScan.Reach reach = pools(registry, scan, id);
                if (reach == null) {
                    return generate(server, id, null, cancelled, firstViews);
                }
                pooled.put(id, reach);
                return new Found(new TreeSet<>(reach.tables()), new TreeSet<>(reach.templates()), new TreeSet<>(reach.items()), true, false);
            });
            if (found == null) {
                return null;
            }
            keep(id, found, tables, templates, placed);
            if (!pooled.containsKey(id)) {
                if (found.tryAgain()) {
                    retry.add(id);
                } else if (!found.generated()) {
                    failed.incrementAndGet();
                }
                progress.accept(++done);
            }
        }
        early.accept(new StructureScan(new TreeMap<>(tables), new TreeMap<>(templates), patches, new TreeSet<>(retry), new TreeMap<>(placed)));
        // Then the slow part: one generation of each structure that has pools, for loot its code sets as it's placed.
        for (Map.Entry<ResourceLocation, PoolScan.Reach> entry : pooled.entrySet()) {
            ResourceLocation id = entry.getKey();
            Found found = LoadedPieces.during(server, () -> generate(server, id, entry.getValue(), cancelled, firstViews));
            if (found == null) {
                return null;
            }
            keep(id, found, tables, templates, placed);
            if (found.tryAgain()) {
                retry.add(id);
            } else if (!found.generated()) {
                failed.incrementAndGet();
            }
            progress.accept(++done);
        }
        // Those that failed for a temporary reason get one more try. Any that fail that way again
        // are kept to try another time.
        for (ResourceLocation id : List.copyOf(retry)) {
            Found again = scanOne(server, registry, scan, id, cancelled, firstViews);
            if (again == null) {
                return null;
            }
            if (!again.tryAgain()) {
                retry.remove(id);
                keep(id, again, tables, templates, placed);
                if (!again.generated()) {
                    failed.incrementAndGet();
                }
            }
        }
        return new StructureScan(tables, templates, patches, retry, placed);
    }

    /**
     * What generating a structure found: the loot tables it uses, the templates it places and the
     * items it places itself, whether it generated at all, and whether it's worth trying again, as it
     * failed for a reason that may pass.
     */
    private record Found(Set<ResourceLocation> tables, Set<ResourceLocation> templates, Set<ResourceLocation> items, boolean generated,
                         boolean tryAgain) {
    }

    private static void keep(ResourceLocation id, Found found, Map<ResourceLocation, Set<ResourceLocation>> tables,
                             Map<ResourceLocation, Set<ResourceLocation>> templates, Map<ResourceLocation, Set<ResourceLocation>> placed) {
        tables.remove(id);
        templates.remove(id);
        placed.remove(id);
        if (!found.tables().isEmpty()) {
            tables.put(id, found.tables());
        }
        if (!found.templates().isEmpty()) {
            templates.put(id, found.templates());
        }
        if (!found.items().isEmpty()) {
            placed.put(id, found.items());
        }
    }

    /** Reads a structure's pools and generates it. Null if cancelled. */
    private static Found scanOne(MinecraftServer server, Registry<Structure> registry, PoolScan scan, ResourceLocation id, BooleanSupplier cancelled,
                                 BiConsumer<ResourceLocation, CaptureResult> firstViews) {
        // One stretch of work, so the pieces read for its pools are still loaded when it's generated from them.
        return LoadedPieces.during(server, () -> readAndGenerate(server, registry, scan, id, cancelled, firstViews));
    }

    private static Found readAndGenerate(MinecraftServer server, Registry<Structure> registry, PoolScan scan, ResourceLocation id, BooleanSupplier cancelled,
                                         BiConsumer<ResourceLocation, CaptureResult> firstViews) {
        if (cancelled.getAsBoolean()) {
            return null;
        }
        return generate(server, id, pools(registry, scan, id), cancelled, firstViews);
    }

    /**
     * What a structure's template pools can place, or null if it isn't built from pools, or they
     * couldn't be read. Jigsaw structures' pieces can all be read without generating anything,
     * which finds rare pieces too.
     */
    private static PoolScan.Reach pools(Registry<Structure> registry, PoolScan scan, ResourceLocation id) {
        Structure structure = Regs.value(registry, id);
        try {
            return structure == null ? null : scan.scan(structure);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            JesLog.debug("Reading {}'s pools failed", id, e);
            return null;
        }
    }

    /**
     * Generates a structure for the loot only generating shows. For one without pools
     * ({@code fromPools} null) that is all of it. For one with pools it is what its code sets as it
     * places, on top of what the pools hold. Null if cancelled.
     */
    private static Found generate(MinecraftServer server, ResourceLocation id, PoolScan.Reach fromPools, BooleanSupplier cancelled,
                                  BiConsumer<ResourceLocation, CaptureResult> firstViews) {
        if (cancelled.getAsBoolean()) {
            return null;
        }
        Set<ResourceLocation> found = new TreeSet<>();
        Set<ResourceLocation> placed = new TreeSet<>();
        Set<ResourceLocation> items = new TreeSet<>();
        // One generation still runs for a structure with pools, for loot that code sets as it places.
        if (fromPools != null) {
            found.addAll(fromPools.tables());
            placed.addAll(fromPools.templates());
            items.addAll(fromPools.items());
        }
        // Otherwise keep generating new layouts until enough of them in a row turn up nothing new.
        int layouts = fromPools != null ? 1 : MOST_LAYOUTS;
        boolean generated = false;
        boolean tryAgain = false;
        int quietPieces = 0;
        long quietBlocks = 0;
        for (int i = 0; i < layouts; i++) {
            if (cancelled.getAsBoolean() || !Memory.waitUntilFree(cancelled, () -> JesLog.warnOnce("loot-index-memory",
                    "The loot index is waiting for the server to have more memory free before it generates more structures"))) {
                return null;
            }
            try {
                CaptureResult result = StructureCapture.captureInBackground(server, id, layoutSeed(id, i), cancelled);
                if (result == null) {
                    return null;
                }
                if (!result.succeeded()) {
                    // A structure that already has layouts keeps them instead of being tried again.
                    tryAgain = !generated && result.temporary();
                    break;
                }
                generated = true;
                if (i == 0) {
                    firstViews.accept(id, result);
                }
                int before = found.size() + items.size();
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
                    PlacedItems.fromBlockEntity(tag, items);
                }
                // What item frames and armour stands hold, including what structure code put there.
                for (CompoundTag tag : result.snapshot().entities()) {
                    PlacedItems.fromEntity(tag, items);
                }
                // A new placed item counts as something new, as a new loot table does.
                if (i == 0 || found.size() + items.size() > before) {
                    quietPieces = 0;
                    quietBlocks = 0;
                } else {
                    quietPieces += result.snapshot().pieceCount();
                    quietBlocks += result.snapshot().blockCount();
                    if (quietPieces >= QUIET_PIECES || quietBlocks >= QUIET_BLOCKS) {
                        break;
                    }
                }
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                // A broken structure from some mod is left out instead of ending the whole index.
                JesLog.debug("Indexing {} failed", id, e);
                break;
            }
        }
        return new Found(found, placed, items, generated, tryAgain);
    }

    /** The seed of a structure's layout for the loot index. The first is the one it's shown with. */
    public static long layoutSeed(ResourceLocation id, int layout) {
        long first = StructureCapture.defaultSeed(id);
        return layout < NEXT_SEEDS ? first + layout : first + layout * LAYOUT_STEP;
    }

    /**
     * Every item the server's chest loot tables can give: the tables of the chest type, and any in a
     * chests folder. More than structures place, as it doesn't need the index. Recipe viewers show a
     * waiting row on these items while the index is built. Empty if the tables can't be listed.
     */
    public static Set<ResourceLocation> chestItems(MinecraftServer server) {
        try {
            return JesLog.quietly(() -> {
                Set<ResourceLocation> out = new TreeSet<>();
                for (ResourceLocation id : tableIds(server)) {
                    if (id.getPath().startsWith("chests/") || LootRolls.table(server, id).getParamSet() == LootContextParamSets.CHEST) {
                        out.addAll(itemsIn(server, id, new HashSet<>()));
                    }
                }
                return out;
            });
        } catch (RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't list the items chest loot can give", e);
            return Set.of();
        }
    }

    /** Every loot table the server has loaded. */
    private static List<ResourceLocation> tableIds(MinecraftServer server) {
        //? if >=26.1 {
        /*return server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(Ids::of).toList();
        *///?} else if >=1.21 {
        /*return List.copyOf(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        *///?} else {
        return List.copyOf(server.getLootData().getKeys(LootDataType.TABLE));
        //?}
    }

    /** Every item a table can ever give, found by reading the table instead of rolling it. */
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
        // From 26.3 a tag entry's tag is under items, with a "#". Without one it's an item, and there can be a list of those.
        if (name == null && (type.equals("minecraft:tag") || type.equals("tag")) && object.has("items")) {
            JsonElement items = object.get("items");
            for (JsonElement item : items instanceof JsonArray list ? list : List.of(items)) {
                String id = item.isJsonPrimitive() ? item.getAsString() : "";
                ResourceLocation parsed = ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id);
                if (parsed != null && id.startsWith("#")) {
                    name = parsed;
                } else if (parsed != null) {
                    out.add(parsed);
                }
            }
        }
        if (name != null) {
            switch (type) {
                case "minecraft:item", "item" -> {
                    // From 26.3 an entry's functions are its modifier.
                    JsonElement functions = object.has("functions") ? object.get("functions") : object.get("modifier");
                    String text = functions == null ? "" : functions.toString();
                    // Whether every item the entry gives is turned into the other one listed here.
                    boolean turned = false;
                    if (name.equals(Ids.parse("book")) && text.contains("enchant")) {
                        out.add(Ids.parse("enchanted_book"));
                        turned = alwaysRuns(functions, ENCHANTING);
                    }
                    //? if <26.3 {
                    // An exploration map turns a plain map into a filled one. From 26.3 it draws on the entry's own item.
                    if (name.equals(Ids.parse("map")) && text.contains("exploration_map")) {
                        out.add(Ids.parse("filled_map"));
                        turned = alwaysRuns(functions, List.of("exploration_map"));
                    }
                    //?}
                    if (!turned) {
                        out.add(name);
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

    /**
     * Whether one of an entry's functions is one of the game's own of these kinds and has no
     * condition, so every item the entry gives goes through it.
     */
    private static boolean alwaysRuns(JsonElement functions, List<String> kinds) {
        if (functions == null) {
            return false;
        }
        for (JsonElement function : functions instanceof JsonArray list ? list : List.of(functions)) {
            if (!(function instanceof JsonObject f)) {
                continue;
            }
            // From 26.3 a function's kind is its type, and it has one condition rather than a list.
            JsonElement conditions = f.has("conditions") ? f.get("conditions") : f.get("condition");
            ResourceLocation kind = idAt(f, f.has("function") ? "function" : "type");
            if ((conditions == null || conditions instanceof JsonArray none && none.isEmpty())
                    && kind != null && kind.getNamespace().equals("minecraft") && kinds.contains(kind.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation idAt(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? ResourceLocation.tryParse(object.get(key).getAsString()) : null;
    }
}
