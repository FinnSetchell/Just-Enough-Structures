package com.finndog.justenoughstructures.compat.jei;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
/*import mezz.jei.api.recipe.types.IRecipeType;
*///?} else {
import mezz.jei.api.recipe.RecipeType;
//?}

/**
 * Adds a "Found in structures" category to JEI, so looking up how to get an item also lists the
 * structures whose loot can give it. The recipes come from the loot index, which the server builds
 * after the world is joined, so they're added when it arrives rather than when JEI starts. Until
 * then each item chest loot can give has a waiting row, if the server has said which.
 *
 * <p>The player's chest loot setting can hide other mods' chest loot tabs, or JES's own, which JEI
 * does at once, so a change shows without a reload.
 */
@JeiPlugin
public final class JesJeiPlugin implements IModPlugin {
    //? if >=26.1 {
    /*// From JEI 20 RecipeType is marked for removal in favour of IRecipeType, which the JEI for 1.21.1 and older doesn't have.
    static final IRecipeType<FoundInRecipe> FOUND_IN = IRecipeType.create(JustEnoughStructures.MOD_ID, "found_in", FoundInRecipe.class);
    *///?} else {
    static final RecipeType<FoundInRecipe> FOUND_IN = RecipeType.create(JustEnoughStructures.MOD_ID, "found_in", FoundInRecipe.class);
    //?}

    private static IJeiRuntime runtime;
    private static List<FoundInRecipe> shown = List.of();
    // Whether what's shown came from an index, rather than being waiting rows.
    private static boolean fromIndex;
    private static boolean listening;
    // Other mods' tabs this plugin hid, so only those are shown again, and whether it hid its own.
    private static final Set<String> hiddenOthers = new HashSet<>();
    private static boolean hiddenOwn;

    @Override
    public ResourceLocation getPluginUid() {
        return JustEnoughStructures.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FoundInCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        shown = List.of();
        fromIndex = false;
        hiddenOthers.clear();
        hiddenOwn = false;
        if (!listening) {
            listening = true;
            ClientRequests.onEachIndex(JesJeiPlugin::show);
            ClientRequests.onChestItems(JesJeiPlugin::showWaiting);
            ClientRequests.wantIndex();
            ChestLootTabs.onChange(JesJeiPlugin::showChosenTabs);
        }
        showChosenTabs();
        if (!ClientRequests.serverSupported()) {
            return;
        }
        // Asks the server for the index if nobody has yet; it arrives through the listener. The
        // structure list is what saved thumbnails are checked against, so the rows can have pictures.
        ClientRequests.index();
        ClientRequests.catalog();
        if (ClientRequests.indexReady()) {
            show(ClientRequests.index().join());
        } else {
            showWaiting();
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        shown = List.of();
        fromIndex = false;
        hiddenOthers.clear();
        hiddenOwn = false;
    }

    /** Hides or shows the chest loot tabs as the player's setting says. JEI hides a whole tab at once. */
    private static void showChosenTabs() {
        if (runtime == null) {
            return;
        }
        IRecipeManager recipes = runtime.getRecipeManager();
        try {
            for (String id : ChestLootTabs.OTHER_TABS) {
                boolean hide = ChestLootTabs.hides(id);
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (key == null || hide == hiddenOthers.contains(id)) {
                    continue;
                }
                // Only a tab that's there: one of a mod that isn't installed is left alone.
                recipes.getRecipeType(key).ifPresent(type -> {
                    if (hide) {
                        recipes.hideRecipeCategory(type);
                        hiddenOthers.add(id);
                    } else {
                        recipes.unhideRecipeCategory(type);
                        hiddenOthers.remove(id);
                    }
                });
            }
            boolean hideOwn = !ChestLootTabs.showsOwn();
            if (hideOwn != hiddenOwn) {
                if (hideOwn) {
                    recipes.hideRecipeCategory(FOUND_IN);
                } else {
                    recipes.unhideRecipeCategory(FOUND_IN);
                }
                hiddenOwn = hideOwn;
            }
        } catch (RuntimeException | LinkageError e) {
            JesLog.warnOnce("jei-chest-loot-tabs", "Couldn't hide or show chest loot tabs in JEI as the setting says", e);
        }
    }

    /** Whether JEI shows the Found in structures tab; false without JEI running. Test hook for the scripted scenarios. */
    public static boolean foundInShown() {
        return runtime != null && runtime.getRecipeManager().createRecipeCategoryLookup().get()
                .anyMatch(category -> category.getRecipeType().getUid().equals(FOUND_IN.getUid()));
    }

    private static void show(LootIndex index) {
        if (runtime != null) {
            replace(FoundInRecipe.fromIndex(index, StructureNames::structure));
            fromIndex = true;
        }
    }

    /** Waiting rows for the items chest loot can give, while there's no index. They go when it comes. */
    private static void showWaiting() {
        if (runtime != null && !fromIndex && ClientRequests.latestIndex() == null) {
            replace(FoundInRecipe.waitingFor(ClientRequests.chestItems()));
        }
    }

    private static void replace(List<FoundInRecipe> rows) {
        IRecipeManager recipes = runtime.getRecipeManager();
        if (!shown.isEmpty()) {
            recipes.hideRecipes(FOUND_IN, shown);
        }
        shown = rows;
        recipes.addRecipes(FOUND_IN, shown);
    }

    /** Opens JEI on every way to get an item; false without JEI running. Test hook for the scripted scenarios. */
    public static boolean showRecipesFor(ItemStack stack) {
        if (runtime == null) {
            return false;
        }
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        return true;
    }

    /** Opens JEI on just the structures an item is found in; false without JEI running. Test hook for the scripted scenarios. */
    public static boolean showFoundIn(ItemStack stack) {
        if (runtime == null) {
            return false;
        }
        List<FoundInRecipe> matching = shown.stream().filter(r -> r.item().is(stack.getItem())).toList();
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(FOUND_IN), matching,
                List.of(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack)));
        return true;
    }
}
