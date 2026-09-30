package com.finndog.justenoughstructures.compat.jei;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.Gui;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One row per structure: its picture and name, then the chance of the item in each container it
 * has and the item itself. Clicking the structure opens it in the browser.
 */
final class FoundInCategory implements IRecipeCategory<FoundInRecipe> {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 24;
    private static final int TEXT = 0xFF404040;
    private static final ItemStack MAP = new ItemStack(Items.FILLED_MAP);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic slot;

    FoundInCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, MAP);
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<FoundInRecipe> getRecipeType() {
        return JesJeiPlugin.FOUND_IN;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.justenoughstructures.found_in");
    }

    // Older JEI 15 releases size categories by their background instead of the width and height.
    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FoundInRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 17, 4)
                .addItemStack(recipe.item())
                .setBackground(slot, -1, -1);
    }

    @Override
    public void draw(FoundInRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        String chance = FoundIn.percent(FoundIn.chance(recipe.item().getItem(), recipe.tables()));
        int chanceX = WIDTH - 22 - font.width(chance);
        if (overStructure(mouseX, mouseY)) {
            g.fill(0, 0, WIDTH - 20, HEIGHT, 0x40FFFFFF);
        }
        int thumbnail = Thumbnails.textureId(recipe.structure());
        if (thumbnail >= 0) {
            StructureViewport.drawTexture(g, thumbnail, 1, 3, 18, 18);
        } else {
            g.renderItem(MAP, 2, 4);
        }
        int textWidth = chanceX - 26;
        g.drawString(font, Gui.clip(font, StructureNames.structure(recipe.structure()), textWidth), 22, 3, TEXT, false);
        Gui.small(g, font, Gui.clip(font, StructureNames.mod(recipe.structure().getNamespace()), (int) (textWidth / 0.75f)), 22, 14, Gui.LABEL_SOFT);
        g.drawString(font, chance, chanceX, 8, TEXT, false);
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public List<Component> getTooltipStrings(FoundInRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (!overStructure(mouseX, mouseY)) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        String name = StructureNames.structure(recipe.structure());
        lines.add(Component.literal(name));
        lines.add(Component.literal(StructureNames.mod(recipe.structure().getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        // Which of its loot tables, unless that only repeats the structure's name.
        String tables = recipe.tables().stream().map(t -> StructureNames.lootTable(t.toString())).distinct().sorted()
                .filter(t -> !t.equals(name)).collect(Collectors.joining(", "));
        if (!tables.isEmpty()) {
            lines.add(Component.literal(tables).withStyle(ChatFormatting.GRAY));
        }
        double chance = FoundIn.chance(recipe.item().getItem(), recipe.tables());
        if (chance >= 0) {
            lines.add(Component.translatable("jei.justenoughstructures.chance", FoundIn.percent(chance)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("jei.justenoughstructures.open").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public boolean handleInput(FoundInRecipe recipe, double mouseX, double mouseY, InputConstants.Key input) {
        if (input.getType() != InputConstants.Type.MOUSE || input.getValue() != InputConstants.MOUSE_BUTTON_LEFT || !overStructure(mouseX, mouseY)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        JesScreen.startOn(recipe.structure());
        minecraft.setScreen(new JesScreen(minecraft.screen));
        return true;
    }

    private static boolean overStructure(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < WIDTH - 20 && mouseY >= 0 && mouseY < HEIGHT;
    }
}
