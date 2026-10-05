package com.finndog.justenoughstructures.compat.rei;

// The 1.21.11 rename would catch REI's own class names, so it's off in this file.
//~ !identifier

import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

/** One structure an item can be found in, as REI keeps it. */
final class FoundInDisplay implements Display {
    private final FoundInRecipe recipe;
    private final List<EntryIngredient> outputs;

    FoundInDisplay(FoundInRecipe recipe) {
        this.recipe = recipe;
        this.outputs = List.of(EntryIngredients.of(recipe.item()));
    }

    FoundInRecipe recipe() {
        return recipe;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return List.of();
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return JesReiPlugin.FOUND_IN;
    }
}
