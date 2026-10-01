package com.finndog.justenoughstructures.compat.jei;

import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** JEI's side of a {@link FoundInRow}: one row per structure that can give the item. */
final class FoundInCategory implements IRecipeCategory<FoundInRecipe> {
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic slot;

    FoundInCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(FoundInRow.WIDTH, FoundInRow.HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, FoundInRow.MAP);
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<FoundInRecipe> getRecipeType() {
        return JesJeiPlugin.FOUND_IN;
    }

    @Override
    public Component getTitle() {
        return FoundInRow.title();
    }

    // Older JEI 15 releases size categories by their background instead of the width and height.
    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return FoundInRow.WIDTH;
    }

    @Override
    public int getHeight() {
        return FoundInRow.HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FoundInRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, FoundInRow.ITEM_X, FoundInRow.ITEM_Y)
                .addItemStack(recipe.item())
                .setBackground(slot, -1, -1);
    }

    @Override
    public void draw(FoundInRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        FoundInRow.draw(g, recipe, mouseX, mouseY, false);
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public List<Component> getTooltipStrings(FoundInRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        return FoundInRow.overStructure(mouseX, mouseY) ? FoundInRow.tooltip(recipe) : List.of();
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public boolean handleInput(FoundInRecipe recipe, double mouseX, double mouseY, InputConstants.Key input) {
        if (input.getType() != InputConstants.Type.MOUSE || input.getValue() != InputConstants.MOUSE_BUTTON_LEFT
                || !FoundInRow.overStructure(mouseX, mouseY)) {
            return false;
        }
        FoundInRow.open(recipe);
        return true;
    }
}
