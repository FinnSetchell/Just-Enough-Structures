package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A container opened from the preview, drawn with the vanilla chest texture. */
final class ChestPopup {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    static final int WIDTH = 176;
    static final int INFO_HEIGHT = 60;

    final StructureSnapshot.Container container;
    final Component title;
    final int size;
    final int rows;
    final int index;
    final int count;
    long seed;
    List<ItemStack> items;
    int x;
    int y;

    ChestPopup(StructureSnapshot.Container container, Component title, int size, int index, int count) {
        this.container = container;
        this.title = title;
        this.size = size;
        this.rows = Math.max(1, Math.min(6, (size + 8) / 9));
        this.index = index;
        this.count = count;
    }

    int height() {
        return rows * 18 + 24 + INFO_HEIGHT;
    }

    void place(int screenWidth, int screenHeight) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - height()) / 2);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height();
    }

    /** Draws the popup and returns the stack under the mouse, or empty. */
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int chestHeight = rows * 18 + 17;
        g.blit(TEXTURE, x, y, 0, 0, WIDTH, chestHeight);
        g.blit(TEXTURE, x, y + chestHeight, 0, 215, WIDTH, 7);
        g.drawString(font, title, x + 8, y + 6, Gui.LABEL, false);
        if (count > 1) {
            String of = (index + 1) + " / " + count;
            g.drawString(font, of, x + WIDTH - 8 - font.width(of), y + 6, Gui.LABEL_SOFT, false);
        }

        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < rows * 9; slot++) {
            int sx = x + 8 + (slot % 9) * 18;
            int sy = y + 18 + (slot / 9) * 18;
            if (slot >= size) {
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
                continue;
            }
            ItemStack stack = items != null && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                g.renderItem(stack, sx, sy);
                g.renderItemDecorations(font, stack, sx, sy);
            }
            if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }

        int infoTop = y + chestHeight + 7;
        Gui.panel(g, x, infoTop, WIDTH, INFO_HEIGHT);
        String table = container.lootTable() == null
                ? Component.translatable("screen.justenoughstructures.prefilled").getString()
                : StructureNames.lootTable(container.lootTable());
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.field.loot_table").getString(), x + 7, infoTop + 5, Gui.LABEL_SOFT);
        g.drawString(font, Gui.clip(font, table, WIDTH - 14), x + 7, infoTop + 12, 0xFF202020, false);
        if (items == null) {
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), x + 7, infoTop + 23, Gui.LABEL_SOFT);
        } else if (container.lootTable() != null) {
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.roll_hint").getString(), x + 7, infoTop + 23, Gui.LABEL_SOFT);
        }
        return hovered;
    }

    /** Where the popup's buttons go, one row along the bottom of the info panel. */
    int buttonRowY() {
        return y + rows * 18 + 24 + INFO_HEIGHT - 24;
    }
}
