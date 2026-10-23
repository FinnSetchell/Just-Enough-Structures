package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A popup beside the preview for one of several similar things in a structure, such as spawners or
 * mobs. Its arrows step through them and the camera turns to each. It's drawn like a chest, with the
 * name and position ("3 / 17") along the top and the buttons along the bottom.
 */
abstract class SidePopup {
    static final ResourceLocation TEXTURE = Ids.parse("textures/gui/container/generic_54.png");
    static final int WIDTH = ChestPopup.WIDTH;

    final Component title;
    /** Index among the {@code count} similar things, or -1 while it shows all of them. */
    final int index;
    final int count;
    int x;
    int y;
    /** The tooltip for the part under the mouse, set while drawing, or null. */
    List<Component> hoveredTip;

    SidePopup(Component title, int index, int count) {
        this.title = title;
        this.index = index;
        this.count = count;
    }

    abstract int height(Font font);

    /** Draws it, and returns the item under the mouse, or empty. */
    abstract ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY);

    /** Scrolls its list, when the mouse is over it. */
    boolean scroll(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** Returns the grabbed scroll bar if the press is on it, or null. */
    HeldBar grabBar(double mouseX, double mouseY) {
        return null;
    }

    /** A point part of the way down its list's scroll bar, or null without one. Test hook for the scripted scenarios. */
    int[] barPoint(float along) {
        return null;
    }

    /** How far its list is scrolled, or -1 without one. */
    double scrolled() {
        return -1;
    }

    /** Whether it shows all of them instead of one. */
    boolean overview() {
        return index < 0;
    }

    /** Places it at {@code x}, beside the preview so what it's about stays in view, and centred vertically. */
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

    /** Draws the title row: the name, and on the right "3 / 17", or "all 17" before one is selected. */
    void renderTitle(GuiGraphics g, Font font) {
        int right = x + WIDTH - 8;
        if (count > 1) {
            String of = overview() ? Component.translatable("screen.justenoughstructures.container_all", count).getString()
                    : Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
            // Room for the widest count text, so the title doesn't shift as the arrows step.
            int all = font.width(Component.translatable("screen.justenoughstructures.container_all", count).getString());
            int last = font.width(Component.translatable("screen.justenoughstructures.container_index", count, count).getString());
            g.drawString(font, of, right - font.width(of), y + 6, Gui.labelSoft(), false);
            right -= Math.max(all, last) + 4;
        }
        Gui.drawClipped(g, font, title.getString(), x + 8, y + 6, right - x - 8, Gui.label(), false);
    }
}
