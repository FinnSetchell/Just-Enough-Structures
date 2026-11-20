package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.loot.ItemKinds;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An item a structure's loot can give, and which of the structure's loot tables give it. Shared by
 * the JEI, EMI and REI pages, and kept free of their classes so it can be tested without them.
 * Without a structure it's a waiting row, shown on an item while the server builds the loot index.
 * Without an item it's a table row: a structure that uses a loot table, for a page about the table.
 * {@code placed} when the structure places the item itself; with no tables, that's the only way it has it.
 */
public record FoundInRecipe(ResourceLocation structure, ItemStack item, Set<ResourceLocation> tables, boolean placed, Set<String> kinds) {

    public FoundInRecipe(ResourceLocation structure, ItemStack item, Set<ResourceLocation> tables) {
        this(structure, item, tables, false);
    }

    public FoundInRecipe(ResourceLocation structure, ItemStack item, Set<ResourceLocation> tables, boolean placed) {
        this(structure, item, tables, placed, Set.of());
    }

    /**
     * The stacks the row gives, which a recipe viewer shows in turn and matches a looked-up stack
     * against: one of each potion or named book its tables give ({@link ItemKinds#stacks}), or the
     * item alone. {@code registries} are the world's.
     */
    public List<ItemStack> stacks(RegistryAccess registries) {
        if (kinds.isEmpty()) {
            return List.of(item);
        }
        // Many rows give the same kinds, so they share one list.
        synchronized (STACKS) {
            if (stacksFrom != registries) {
                STACKS.clear();
                stacksFrom = registries;
            }
            return STACKS.computeIfAbsent(BuiltInRegistries.ITEM.getKey(item.getItem()) + "|" + String.join(",", new TreeSet<>(kinds)), key -> {
                List<ItemStack> stacks = ItemKinds.stacks(item.getItem(), kinds, registries);
                return stacks.isEmpty() ? List.of(item) : List.copyOf(stacks);
            });
        }
    }

    /** The stacks rows give, by item and kinds, for the registries they were made with. */
    private static final Map<String, List<ItemStack>> STACKS = new HashMap<>();
    private static RegistryAccess stacksFrom;

    /**
     * Whether the row gives this stack: it's the row's item, and one of its kinds where the row has
     * kinds. A row without them gives every stack of the item.
     */
    public boolean gives(ItemStack stack) {
        return stack.is(item.getItem()) && (kinds.isEmpty() || ItemKinds.of(stack).keySet().stream().anyMatch(kinds::contains));
    }

    /** A waiting row for an item, with no structure yet. */
    public static FoundInRecipe waiting(ItemStack item) {
        return new FoundInRecipe(null, item, Set.of());
    }

    /** A waiting row for each of these items the game knows. */
    public static List<FoundInRecipe> waitingFor(Set<ResourceLocation> items) {
        List<FoundInRecipe> out = new ArrayList<>();
        for (ResourceLocation id : items) {
            Item item = Regs.value(BuiltInRegistries.ITEM, id);
            if (item != Items.AIR) {
                out.add(waiting(new ItemStack(item)));
            }
        }
        return out;
    }

    /** Whether this is a waiting row rather than a structure's. */
    public boolean isWaiting() {
        return structure == null;
    }

    /** A table row: a structure that uses {@code table}. */
    public static FoundInRecipe forTable(ResourceLocation structure, ResourceLocation table) {
        return new FoundInRecipe(structure, ItemStack.EMPTY, Set.of(table));
    }

    /** Whether this is a table row, which has a structure but no item. */
    public boolean isTable() {
        return structure != null && item.isEmpty();
    }

    /** Whether the structure places the item and none of its loot gives it, so there's no chance to show. */
    public boolean onlyPlaced() {
        return placed && tables.isEmpty();
    }

    /** For each loot table any structure uses, in id order, a table row for each of those structures, in the order of their names. */
    public static Map<ResourceLocation, List<FoundInRecipe>> byTable(LootIndex index, Function<ResourceLocation, String> names) {
        Map<ResourceLocation, List<FoundInRecipe>> out = new TreeMap<>();
        FoundIn.structuresByTable(index).forEach((table, users) -> {
            List<ResourceLocation> structures = new ArrayList<>(users);
            structures.sort(Comparator.comparing(names));
            out.put(table, structures.stream().map(structure -> forTable(structure, table)).toList());
        });
        return out;
    }

    /**
     * One recipe for each item each structure can give or places, with structures in the order of
     * their names. An item that's rolled and placed both is one recipe.
     */
    public static List<FoundInRecipe> fromIndex(LootIndex index, Function<ResourceLocation, String> names) {
        List<FoundInRecipe> out = new ArrayList<>();
        Set<ResourceLocation> all = new TreeSet<>(index.tablesByStructure().keySet());
        all.addAll(index.placedByStructure().keySet());
        List<ResourceLocation> structures = new ArrayList<>(all);
        structures.sort(Comparator.comparing(names));
        for (ResourceLocation structure : structures) {
            Map<ResourceLocation, Set<ResourceLocation>> tablesByItem = new TreeMap<>();
            for (ResourceLocation table : index.tablesByStructure().getOrDefault(structure, Set.of())) {
                for (ResourceLocation item : index.itemsByTable().getOrDefault(table, Set.of())) {
                    tablesByItem.computeIfAbsent(item, i -> new TreeSet<>()).add(table);
                }
            }
            Set<ResourceLocation> placed = index.placedByStructure().getOrDefault(structure, Set.of());
            placed.forEach(item -> tablesByItem.computeIfAbsent(item, i -> new TreeSet<>()));
            tablesByItem.forEach((id, tables) -> {
                Item item = Regs.value(BuiltInRegistries.ITEM, id);
                if (item != Items.AIR) {
                    // The potions or named books the structure's tables give of it, for the viewers to match.
                    Set<String> kinds = new TreeSet<>();
                    tables.forEach(table -> kinds.addAll(index.kindsByTable().getOrDefault(table, Map.of()).getOrDefault(id, Set.of())));
                    out.add(new FoundInRecipe(structure, new ItemStack(item), Set.copyOf(tables), placed.contains(id), Set.copyOf(kinds)));
                }
            });
        }
        return out;
    }
}
