package com.finndog.justenoughstructures.compat.jei;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to JEI, so looking up how to get an item also lists the
 * structures whose loot can give it. The recipes come from the loot index, which the server builds
 * after the world is joined, so they're added when it arrives rather than when JEI starts.
 */
@JeiPlugin
public final class JesJeiPlugin implements IModPlugin {
    static final RecipeType<FoundInRecipe> FOUND_IN = RecipeType.create(JustEnoughStructures.MOD_ID, "found_in", FoundInRecipe.class);

    private static IJeiRuntime runtime;
    private static List<FoundInRecipe> shown = List.of();
    private static boolean listening;

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
        if (!listening) {
            listening = true;
            ClientRequests.onEachIndex(JesJeiPlugin::show);
        }
        if (!ClientRequests.serverSupported()) {
            return;
        }
        // Asks the server for the index if nobody has yet; it arrives through the listener. The
        // structure list is what saved thumbnails are checked against, so the rows can have pictures.
        ClientRequests.index();
        ClientRequests.catalog();
        if (ClientRequests.indexReady()) {
            show(ClientRequests.index().join());
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        shown = List.of();
    }

    private static void show(LootIndex index) {
        if (runtime == null) {
            return;
        }
        IRecipeManager recipes = runtime.getRecipeManager();
        if (!shown.isEmpty()) {
            recipes.hideRecipes(FOUND_IN, shown);
        }
        shown = FoundInRecipe.fromIndex(index, StructureNames::structure);
        recipes.addRecipes(FOUND_IN, shown);
    }

    /** Opens JEI on just the structures an item is found in. For the screenshot harness; false without JEI running. */
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
