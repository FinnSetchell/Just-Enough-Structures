package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.Gui;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How a "Found in structures" row looks and behaves in every recipe viewer: the structure's picture
 * and name, then the chance of the item in each container it has, then the item. Clicking the
 * structure opens it in the browser. Positions are from the row's top left corner.
 */
public final class FoundInRow {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 24;
    /** Where the item goes, the top left of the 16 px item inside its slot. */
    public static final int ITEM_X = WIDTH - 17;
    public static final int ITEM_Y = 4;
    public static final ItemStack MAP = new ItemStack(Items.FILLED_MAP);

    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DARK = 0xFFBBBBBB;

    private FoundInRow() {
    }

    public static Component title() {
        return Component.translatable("viewer.justenoughstructures.found_in");
    }

    /** Draws everything but the item. {@code dark} is for viewers with a dark background. */
    public static void draw(GuiGraphics g, FoundInRecipe recipe, double mouseX, double mouseY, boolean dark) {
        Font font = Minecraft.getInstance().font;
        int text = dark ? TEXT_DARK : TEXT;
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
        g.drawString(font, Gui.clip(font, StructureNames.structure(recipe.structure()), textWidth), 22, 3, text, false);
        Gui.small(g, font, Gui.clip(font, StructureNames.mod(recipe.structure().getNamespace()), (int) (textWidth / 0.75f)), 22, 14, Gui.LABEL_SOFT);
        g.drawString(font, chance, chanceX, 8, text, false);
    }

    /** Whether a point is over the structure's part of the row, which opens it when clicked. */
    public static boolean overStructure(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < WIDTH - 20 && mouseY >= 0 && mouseY < HEIGHT;
    }

    /** The structure's name and mod, which of its loot tables give the item, the chance, and how to open it. */
    public static List<Component> tooltip(FoundInRecipe recipe) {
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
            lines.add(Component.translatable("viewer.justenoughstructures.chance", FoundIn.percent(chance)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("viewer.justenoughstructures.open").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Opens the structure in the browser, coming back to the viewer when it's closed. */
    public static void open(FoundInRecipe recipe) {
        Minecraft minecraft = Minecraft.getInstance();
        JesScreen.startOn(recipe.structure());
        minecraft.setScreen(new JesScreen(minecraft.screen));
    }
}
