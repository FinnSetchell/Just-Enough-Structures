package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An item a structure's loot can give, and which of the structure's loot tables give it. Shared by
 * the JEI, EMI and REI pages, and kept free of their classes so it can be tested without them.
 */
public record FoundInRecipe(ResourceLocation structure, ItemStack item, Set<ResourceLocation> tables) {

    /** One recipe for each item each structure can give, with structures in the order of their names. */
    public static List<FoundInRecipe> fromIndex(LootIndex index, Function<ResourceLocation, String> names) {
        List<FoundInRecipe> out = new ArrayList<>();
        List<ResourceLocation> structures = new ArrayList<>(index.tablesByStructure().keySet());
        structures.sort(Comparator.comparing(names));
        for (ResourceLocation structure : structures) {
            Map<ResourceLocation, Set<ResourceLocation>> tablesByItem = new TreeMap<>();
            for (ResourceLocation table : index.tablesByStructure().get(structure)) {
                for (ResourceLocation item : index.itemsByTable().getOrDefault(table, Set.of())) {
                    tablesByItem.computeIfAbsent(item, i -> new TreeSet<>()).add(table);
                }
            }
            tablesByItem.forEach((id, tables) -> {
                Item item = Regs.value(BuiltInRegistries.ITEM, id);
                if (item != Items.AIR) {
                    out.add(new FoundInRecipe(structure, new ItemStack(item), Set.copyOf(tables)));
                }
            });
        }
        return out;
    }
}
