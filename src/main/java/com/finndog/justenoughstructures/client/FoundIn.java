package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** The loot index turned around: for an item, the structures and loot tables it can come from. */
public final class FoundIn {
    private static Map<ResourceLocation, Map<ResourceLocation, Set<ResourceLocation>>> byItem = Map.of();
    private static LootIndex index;
    private static Map<ResourceLocation, Set<ResourceLocation>> byTable = Map.of();
    private static final Map<ResourceLocation, String> NAMES = new HashMap<>();

    private FoundIn() {
    }

    static void rebuild(LootIndex built) {
        Map<ResourceLocation, Map<ResourceLocation, Set<ResourceLocation>>> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> structure : built.tablesByStructure().entrySet()) {
            for (ResourceLocation table : structure.getValue()) {
                for (ResourceLocation item : built.itemsByTable().getOrDefault(table, Set.of())) {
                    out.computeIfAbsent(item, i -> new TreeMap<>())
                            .computeIfAbsent(structure.getKey(), s -> new TreeSet<>())
                            .add(table);
                }
            }
        }
        byItem = out;
        Map<ResourceLocation, Set<ResourceLocation>> tables = new HashMap<>();
        built.tablesByStructure().forEach((structure, used) -> used.forEach(table ->
                tables.computeIfAbsent(table, t -> new TreeSet<>()).add(structure)));
        byTable = tables;
        index = built;
    }

    static void clear() {
        byItem = Map.of();
        byTable = Map.of();
        index = null;
    }

    public static boolean ready() {
        return index != null;
    }

    /** Every loot table any structure uses, or none until the index is here. */
    public static Set<ResourceLocation> allTables() {
        return index == null ? Set.of() : index.itemsByTable().keySet();
    }

    /** The loot tables the structure uses, or null until the index is here. */
    public static Set<ResourceLocation> tablesIn(ResourceLocation structure) {
        return index == null ? null : index.tablesByStructure().getOrDefault(structure, Set.of());
    }

    /** The structures that use a loot table, or none until the index is here. */
    public static Set<ResourceLocation> structuresUsing(ResourceLocation table) {
        return byTable.getOrDefault(table, Set.of());
    }

    /** Structure id to the loot tables in it that can give this item. Empty if none do. */
    public static Map<ResourceLocation, Set<ResourceLocation>> structuresFor(Item item) {
        return byItem.getOrDefault(BuiltInRegistries.ITEM.getKey(item), Map.of());
    }

    /**
     * The best chance, per container, of any of these loot tables giving the item, or -1 until the
     * odds of at least one are known. Asks the server for any odds it doesn't have yet.
     */
    public static double chance(Item item, Set<ResourceLocation> tables) {
        double best = -1;
        for (ResourceLocation table : tables) {
            LootOdds odds = ClientRequests.odds(table).getNow(null);
            if (odds == null) {
                continue;
            }
            best = Math.max(best, 0);
            for (LootOdds.Row r : odds.rows()) {
                if (r.example().is(item)) {
                    best = Math.max(best, (double) r.hits() / odds.rolls());
                }
            }
        }
        return best;
    }

    /** A chance as a short percentage, with a decimal place for rare things. */
    public static String percent(double chance) {
        return chance < 0 ? "..." : chance >= 0.1 ? Math.round(chance * 100) + "%" : String.format(Locale.ROOT, "%.1f%%", chance * 100);
    }

    /** True if any loot in the structure can give an item whose name or id contains {@code text}. */
    public static boolean structureHasItem(ResourceLocation structure, String text) {
        if (index == null) {
            return false;
        }
        for (ResourceLocation table : index.tablesByStructure().getOrDefault(structure, Set.of())) {
            for (ResourceLocation item : index.itemsByTable().getOrDefault(table, Set.of())) {
                if (item.getPath().contains(text) || name(item).contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Loot items whose name or id contains {@code text}, most widespread first. */
    public static List<Item> itemsMatching(String text, int limit) {
        List<ResourceLocation> matches = new ArrayList<>();
        for (ResourceLocation item : byItem.keySet()) {
            if (item.getPath().contains(text) || name(item).contains(text)) {
                matches.add(item);
            }
        }
        matches.sort(Comparator.comparingInt((ResourceLocation id) -> -byItem.get(id).size()).thenComparing(FoundIn::name));
        return matches.stream().limit(limit).map(BuiltInRegistries.ITEM::get).toList();
    }

    private static String name(ResourceLocation item) {
        return NAMES.computeIfAbsent(item, id -> BuiltInRegistries.ITEM.get(id).getDescription().getString().toLowerCase(Locale.ROOT));
    }
}
