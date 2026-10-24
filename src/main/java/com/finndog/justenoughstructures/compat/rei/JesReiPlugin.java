package com.finndog.justenoughstructures.compat.rei;

// The 1.21.11 rename would catch REI's own class names, so it's off in this file.
//~ !identifier

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import dev.architectury.event.EventResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to REI, so looking up how to get an item also lists the
 * structures whose loot can give it. The rows are made when REI asks for them, from the last loot
 * index received, so they appear as soon as the server sends it, without REI reloading. Before the
 * first index, an item chest loot can give has a waiting row, if the server has said which.
 *
 * <p>The player's chest loot setting can hide other mods' chest loot tabs, or JES's own. REI asks
 * each time it shows recipes, so a change shows without a reload.
 */
// Not final: on Forge and NeoForge, REI finds a subclass by an annotation only its Forge build has.
public class JesReiPlugin implements REIClientPlugin {
    static final CategoryIdentifier<FoundInDisplay> FOUND_IN = CategoryIdentifier.of(JustEnoughStructures.id("found_in"));

    private static volatile List<FoundInDisplay> all = List.of();
    private static volatile Map<Item, List<FoundInDisplay>> byItem = Map.of();
    private static boolean listening;

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new FoundInReiCategory());
        registry.registerVisibilityPredicate(category -> hidden(category) ? EventResult.interruptFalse() : EventResult.pass());
        Minecraft.getInstance().execute(JesReiPlugin::listen);
    }

    /** Whether the player's chest loot setting hides this tab. */
    private static boolean hidden(DisplayCategory<?> category) {
        CategoryIdentifier<?> id = category.getCategoryIdentifier();
        return id.equals(FOUND_IN) ? !ChestLootTabs.showsOwn() : ChestLootTabs.hides(id.getNamespace() + ":" + id.getPath());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerDisplayGenerator(FOUND_IN, new DynamicDisplayGenerator<FoundInDisplay>() {
            @Override
            public Optional<List<FoundInDisplay>> getRecipeFor(EntryStack<?> entry) {
                if (ChestLootTabs.showsOwn() && entry.getValue() instanceof ItemStack stack) {
                    List<FoundInDisplay> found = byItem.get(stack.getItem());
                    if (found != null) {
                        return Optional.of(found);
                    }
                    if (waitingFor(stack)) {
                        return Optional.of(List.of(new FoundInDisplay(FoundInRecipe.waiting(new ItemStack(stack.getItem())))));
                    }
                }
                return Optional.empty();
            }

            @Override
            public Optional<List<FoundInDisplay>> generate(ViewSearchBuilder builder) {
                return ChestLootTabs.showsOwn() && builder.getCategories().contains(FOUND_IN) ? Optional.of(all) : Optional.empty();
            }
        });
    }

    /** Client thread only: listens for every index, including one that has already arrived. */
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

    /** Whether an item gets a waiting row: there's been no index yet, and chest loot can give it. Safe from any thread. */
    private static boolean waitingFor(ItemStack stack) {
        return all.isEmpty() && ClientRequests.latestIndex() == null
                && ClientRequests.chestItems().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Whether REI has the structures to show. Test hook for the scripted scenarios. */
    public static boolean ready() {
        return !all.isEmpty();
    }

    /** Whether REI shows the Found in structures tab. Test hook for the scripted scenarios. */
    public static boolean foundInShown() {
        CategoryRegistry registry = CategoryRegistry.getInstance();
        return registry.tryGet(FOUND_IN).map(config -> registry.isCategoryVisible(config.getCategory())).orElse(false);
    }

    /** Where the first row's structure is on screen, or null before REI has shown one. Test hook for the scripted scenarios. */
    public static int[] firstRow() {
        return FoundInReiCategory.firstRow;
    }

    /** Opens REI on how to get an item, at the structures it's found in. Test hook for the scripted scenarios. */
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
