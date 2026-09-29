package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.HashMap;
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
        index = built;
    }

    static void clear() {
        byItem = Map.of();
        index = null;
    }

    public static boolean ready() {
        return index != null;
    }

    /** Structure id to the loot tables in it that can give this item. Empty if none do. */
    public static Map<ResourceLocation, Set<ResourceLocation>> structuresFor(Item item) {
        return byItem.getOrDefault(BuiltInRegistries.ITEM.getKey(item), Map.of());
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

    private static String name(ResourceLocation item) {
        return NAMES.computeIfAbsent(item, id -> BuiltInRegistries.ITEM.get(id).getDescription().getString().toLowerCase(Locale.ROOT));
    }
}
