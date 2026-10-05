package com.finndog.justenoughstructures.compat.rei;

// The 1.21.11 rename would catch REI's own class names, so it's off in this file.
//~ !identifier

import com.finndog.justenoughstructures.client.screen.Gui;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

/** REI's side of a {@link FoundInRow}. */
final class FoundInReiCategory implements DisplayCategory<FoundInDisplay> {
    private static final int PAD = 4;

    @Override
    public CategoryIdentifier<? extends FoundInDisplay> getCategoryIdentifier() {
        return JesReiPlugin.FOUND_IN;
    }

    @Override
    public Component getTitle() {
        return FoundInRow.title();
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(FoundInRow.MAP);
    }

    @Override
    public int getDisplayWidth(FoundInDisplay display) {
        return FoundInRow.WIDTH + PAD * 2;
    }

    @Override
    public int getDisplayHeight() {
        return FoundInRow.HEIGHT + PAD * 2;
    }

    @Override
    public List<Widget> setupDisplay(FoundInDisplay display, Rectangle bounds) {
        int x = bounds.x + PAD;
        int y = bounds.y + PAD;
        return List.of(
                Widgets.createRecipeBase(bounds),
                new Row(display.recipe(), x, y),
                Widgets.createSlot(new Point(x + FoundInRow.ITEM_X, y + FoundInRow.ITEM_Y)).entries(display.getOutputEntries().get(0)).markOutput());
    }

    /** Where the first row's structure is drawn on screen, for the screenshot harness, or null before REI shows one. */
    static volatile int[] firstRow;

    /** The structure's part of the row. REI hands it screen positions, so they're made relative to the row. */
    private static final class Row extends WidgetWithBounds {
        private final FoundInRecipe recipe;
        private final Rectangle bounds;

        Row(FoundInRecipe recipe, int x, int y) {
            this.recipe = recipe;
            this.bounds = new Rectangle(x, y, FoundInRow.WIDTH - 20, FoundInRow.HEIGHT);
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        //? if >=26.1 {
        /*@Override
        public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        *///?} else {
        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //?}
            if (firstRow == null || bounds.y < firstRow[1]) {
                firstRow = new int[]{bounds.x + 30, bounds.y + bounds.height / 2};
            }
            Gui.push(g);
            Gui.translate(g, bounds.x, bounds.y);
            FoundInRow.draw(g, recipe, mouseX - bounds.x, mouseY - bounds.y, REIRuntime.getInstance().isDarkThemeEnabled());
            Gui.pop(g);
            if (containsMouse(mouseX, mouseY)) {
                Tooltip.create(new Point(mouseX, mouseY), FoundInRow.tooltip(recipe)).queue();
            }
        }

        //? if >=26.1 {
        /*@Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
        *///?} else {
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //?}
            if (button != 0 || !containsMouse(mouseX, mouseY)) {
                return false;
            }
            FoundInRow.open(recipe);
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }
    }
}
