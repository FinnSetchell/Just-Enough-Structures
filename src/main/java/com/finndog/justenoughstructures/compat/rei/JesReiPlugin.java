package com.finndog.justenoughstructures.compat.rei;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to REI, so looking up how to get an item also lists the
 * structures whose loot can give it. The rows are made when REI asks for them, from whichever loot
 * index arrived last, so they're there as soon as the server sends it without REI reloading.
 */
public final class JesReiPlugin implements REIClientPlugin {
    static final CategoryIdentifier<FoundInDisplay> FOUND_IN = CategoryIdentifier.of(JustEnoughStructures.id("found_in"));

    private static volatile List<FoundInDisplay> all = List.of();
    private static volatile Map<Item, List<FoundInDisplay>> byItem = Map.of();
    private static boolean listening;

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new FoundInReiCategory());
        Minecraft.getInstance().execute(JesReiPlugin::listen);
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerDisplayGenerator(FOUND_IN, new DynamicDisplayGenerator<FoundInDisplay>() {
            @Override
            public Optional<List<FoundInDisplay>> getRecipeFor(EntryStack<?> entry) {
                if (entry.getValue() instanceof ItemStack stack) {
                    List<FoundInDisplay> found = byItem.get(stack.getItem());
                    return found == null ? Optional.empty() : Optional.of(found);
                }
                return Optional.empty();
            }

            @Override
            public Optional<List<FoundInDisplay>> generate(ViewSearchBuilder builder) {
                return builder.getCategories().contains(FOUND_IN) ? Optional.of(all) : Optional.empty();
            }
        });
    }

    /** On the game's thread: hears about every index, including one that's already here. */
    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        ClientRequests.onEachIndex(JesReiPlugin::arrived);
        ClientRequests.wantIndex();
        if (ClientRequests.indexReady()) {
            arrived(ClientRequests.index().join());
        }
    }

    /** Whether REI has the structures to show. For the screenshot harness. */
    public static boolean ready() {
        return !all.isEmpty();
    }

    /** Where the first row's structure is on screen, or null before REI has shown one. For the screenshot harness. */
    public static int[] firstRow() {
        return FoundInReiCategory.firstRow;
    }

    /** Opens REI on how to get an item, at the structures it's found in. For the screenshot harness. */
    public static void showFoundIn(ItemStack stack) {
        ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(stack)).setPreferredOpenedCategory(FOUND_IN).open();
    }

    private static void arrived(LootIndex index) {
        List<FoundInDisplay> displays = FoundInRecipe.fromIndex(index, StructureNames::structure).stream().map(FoundInDisplay::new).toList();
        Map<Item, List<FoundInDisplay>> grouped = new HashMap<>();
        for (FoundInDisplay display : displays) {
            grouped.computeIfAbsent(display.recipe().item().getItem(), item -> new ArrayList<>()).add(display);
        }
        grouped.replaceAll((item, list) -> List.copyOf(list));
        byItem = grouped;
        all = displays;
    }
}
