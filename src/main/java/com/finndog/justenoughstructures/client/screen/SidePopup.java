package com.finndog.justenoughstructures.client.screen;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A popup beside the preview about one of several like things in the structure, a spawner or a mob it
 * places, which its arrows step through as the camera turns to each. It's drawn like a chest, with its
 * name and which one it is along the top, and its buttons along the bottom.
 */
abstract class SidePopup {
    static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    static final int WIDTH = ChestPopup.WIDTH;

    final Component title;
    /** Which of the {@code count} like it this is, or -1 while it stands for all of them. */
    final int index;
    final int count;
    int x;
    int y;
    /** The tooltip of whatever's under the mouse, set as it's drawn, or null. */
    List<Component> hoveredTip;

    SidePopup(Component title, int index, int count) {
        this.title = title;
        this.index = index;
        this.count = count;
    }

    abstract int height(Font font);

    /** Draws it, and returns the item under the mouse, or empty. */
    abstract ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY);

    /** Scrolls whatever it lists, when the mouse is over it. */
    boolean scroll(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** Showing all the ones like it rather than a particular one of them. */
    boolean overview() {
        return index < 0;
    }

    /** Sits beside the preview at {@code x}, so what it's about can be seen. */
    void placeAt(int x, int screenHeight, Font font) {
        this.x = x;
        y = Math.max(4, (screenHeight - height(font)) / 2);
    }

    boolean contains(double mouseX, double mouseY, Font font) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height(font);
    }

    /** Where the buttons go, one row along the bottom. */
    int buttonRowY(Font font) {
        return y + height(font) - 26;
    }

    /** The title bar's text: the name, and on the right "3 / 17", or "all 17" before one is picked. */
    void renderTitle(GuiGraphics g, Font font) {
        int right = x + WIDTH - 8;
        if (count > 1) {
            String of = overview() ? Component.translatable("screen.justenoughstructures.container_all", count).getString()
                    : Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
            // Room for the widest it can say, so the title doesn't move as the arrows step.
            int all = font.width(Component.translatable("screen.justenoughstructures.container_all", count).getString());
            int last = font.width(Component.translatable("screen.justenoughstructures.container_index", count, count).getString());
            g.drawString(font, of, right - font.width(of), y + 6, Gui.LABEL_SOFT, false);
            right -= Math.max(all, last) + 4;
        }
        Gui.drawClipped(g, font, title.getString(), x + 8, y + 6, right - x - 8, Gui.LABEL, false);
    }
}
