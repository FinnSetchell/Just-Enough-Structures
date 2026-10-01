package com.finndog.justenoughstructures.compat.emi;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** EMI's side of a {@link FoundInRow}. */
final class FoundInEmiRecipe implements EmiRecipe {
    private final FoundInRecipe recipe;
    private final EmiStack item;
    private final ResourceLocation id;

    FoundInEmiRecipe(FoundInRecipe recipe) {
        this.recipe = recipe;
        this.item = EmiStack.of(recipe.item());
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(recipe.item().getItem());
        // EMI's convention for recipes that aren't in the game's recipe list: a path starting with a slash.
        this.id = JustEnoughStructures.id("/found_in/" + recipe.structure().getNamespace() + "/" + recipe.structure().getPath()
                + "/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return JesEmiPlugin.FOUND_IN;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of();
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(item);
    }

    @Override
    public int getDisplayWidth() {
        return FoundInRow.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return FoundInRow.HEIGHT;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new Row(recipe));
        widgets.addSlot(item, FoundInRow.ITEM_X - 1, FoundInRow.ITEM_Y - 1).recipeContext(this);
    }

    /** Where the first row's structure is drawn on screen, for the screenshot harness, or null before EMI shows one. */
    static volatile int[] firstRow;

    /** The structure's part of the row, which EMI hands mouse positions from the recipe's corner. */
    private static final class Row extends Widget {
        private final FoundInRecipe recipe;

        Row(FoundInRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public Bounds getBounds() {
            return new Bounds(0, 0, FoundInRow.WIDTH - 20, FoundInRow.HEIGHT);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // EMI draws the recipe moved into place, so where it is on screen comes from the pose.
            org.joml.Vector4f corner = g.pose().last().pose().transform(new org.joml.Vector4f(0, 0, 0, 1));
            if (firstRow == null || corner.y() < firstRow[1] - FoundInRow.HEIGHT / 2) {
                firstRow = new int[]{(int) corner.x() + 30, (int) corner.y() + FoundInRow.HEIGHT / 2};
            }
            FoundInRow.draw(g, recipe, mouseX, mouseY, false);
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            return FoundInRow.tooltip(recipe).stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) {
                return false;
            }
            FoundInRow.open(recipe);
            return true;
        }
    }
}
