package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
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
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How a "Found in structures" row looks and behaves in every recipe viewer: the structure's picture
 * and name, then the chance of the item in each container it has, or "Placed" when the structure
 * only places it itself, then the item. Clicking the structure opens it in the browser. A waiting
 * row has the server's progress instead, and does nothing when clicked. A table row has no item or
 * chance, and opens the structure with its loot table picked. Positions are from the row's top left
 * corner.
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
    // Where a row's text starts, after the picture, and how much room it has before the item.
    private static final int TEXT_X = 22;
    private static final int TEXT_WIDTH = ITEM_X - 3 - TEXT_X;

    // The text a waiting row last drew and when, for the scripted scenarios.
    private static volatile String waitingDrawn;
    private static volatile long waitingDrawnAt;

    private FoundInRow() {
    }

    public static Component title() {
        return Component.translatable("viewer.justenoughstructures.found_in");
    }

    /** Draws everything but the item. {@code dark} is for viewers with a dark background. */
    public static void draw(GuiGraphics g, FoundInRecipe recipe, double mouseX, double mouseY, boolean dark) {
        Font font = Minecraft.getInstance().font;
        int text = dark ? TEXT_DARK : TEXT;
        if (recipe.isWaiting()) {
            drawWaiting(g, font, text);
            return;
        }
        String chance = recipe.isTable() ? "" : recipe.onlyPlaced() ? FoundIn.placedText()
                : FoundIn.percent(FoundIn.chance(recipe.item().getItem(), recipe.tables()));
        int chanceX = WIDTH - 22 - font.width(chance);
        if (overStructure(mouseX, mouseY)) {
            g.fill(0, 0, WIDTH - 20, HEIGHT, 0x40FFFFFF);
        }
        if (!Thumbnails.draw(g, recipe.structure(), 1, 3, 18)) {
            g.renderItem(MAP, 2, 4);
        }
        int textWidth = chanceX - 26;
        g.drawString(font, Gui.clip(font, StructureNames.structure(recipe.structure()), textWidth), 22, 3, text, false);
        Gui.small(g, font, Gui.clipSmall(font, StructureNames.mod(recipe.structure().getNamespace()), textWidth), 22, 14, Gui.labelSoft());
        g.drawString(font, chance, chanceX, 8, text, false);
    }

    /** The server's progress beside the map, small, over as many lines as fit and cut short on the last. */
    private static void drawWaiting(GuiGraphics g, Font font, int color) {
        g.renderItem(MAP, 2, 4);
        String text = ClientRequests.viewerWaitText().getString();
        waitingDrawn = text;
        waitingDrawnAt = System.nanoTime();
        float scale = Gui.smallScale();
        int width = (int) (TEXT_WIDTH / scale);
        int step = (int) Math.ceil(font.lineHeight * scale) + 1;
        int fit = Math.max(1, (HEIGHT + 1) / step);
        List<String> lines = font.getSplitter().splitLines(text, width, Style.EMPTY).stream().map(line -> line.getString().strip()).toList();
        int shown = Math.min(lines.size(), fit);
        int y = (HEIGHT - shown * step + 1) / 2;
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            if (i == shown - 1 && lines.size() > shown) {
                line = Gui.clipSmall(font, line + " " + String.join(" ", lines.subList(shown, lines.size())), TEXT_WIDTH);
            }
            Gui.small(g, font, line, TEXT_X, y + i * step, color);
        }
    }

    /** The text a waiting row last drew, or null if none has been since {@code nanos}. Test hook for the scripted scenarios. */
    public static String waitingDrawn(long nanos) {
        return waitingDrawn != null && waitingDrawnAt - nanos >= 0 ? waitingDrawn : null;
    }

    /** Whether a point is over the structure's part of the row, which opens it when clicked. */
    public static boolean overStructure(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < WIDTH - 20 && mouseY >= 0 && mouseY < HEIGHT;
    }

    /** The structure's name and mod, which of its loot tables give the item, the chance, whether it places it, and how to open it. */
    public static List<Component> tooltip(FoundInRecipe recipe) {
        if (recipe.isWaiting()) {
            return List.of(ClientRequests.viewerWaitText());
        }
        List<Component> lines = new ArrayList<>();
        String name = StructureNames.structure(recipe.structure());
        lines.add(Component.literal(name));
        lines.add(Component.literal(StructureNames.mod(recipe.structure().getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        // Which of its loot tables, unless that only repeats the structure's name, or the row is on the table's own page.
        String tables = recipe.isTable() ? "" : recipe.tables().stream().map(t -> StructureNames.lootTable(t.toString())).distinct().sorted()
                .filter(t -> !t.equals(name)).collect(Collectors.joining(", "));
        if (!tables.isEmpty()) {
            lines.add(Component.literal(tables).withStyle(ChatFormatting.GRAY));
        }
        double chance = recipe.isTable() ? -1 : FoundIn.chance(recipe.item().getItem(), recipe.tables());
        if (chance >= 0) {
            lines.add(Component.translatable("viewer.justenoughstructures.chance", FoundIn.percent(chance)).withStyle(ChatFormatting.GRAY));
        }
        if (recipe.placed() && !recipe.isTable()) {
            String placed = recipe.onlyPlaced() ? "screen.justenoughstructures.placed_tip" : "screen.justenoughstructures.placed_too";
            lines.add(Component.translatable(placed).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("viewer.justenoughstructures.open").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /**
     * Opens the structure in the browser, coming back to the viewer when it's closed, with a table
     * row's loot table picked. False for a waiting row, which has none.
     */
    public static boolean open(FoundInRecipe recipe) {
        if (recipe.isWaiting()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (recipe.isTable()) {
            JesScreen.startOn(recipe.structure(), recipe.tables().iterator().next());
        } else {
            JesScreen.startOn(recipe.structure());
        }
        minecraft.setScreen(new JesScreen(minecraft.screen));
        return true;
    }
}
