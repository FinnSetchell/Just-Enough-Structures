package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A button with an icon before its words, or the icon alone when they don't fit, as the Pack tools
 * button does with its wrench. The words stay its name either way, for the narrator.
 */
final class LabelledIconButton extends JesButton {
    private final ResourceLocation icon;
    private final int size;

    LabelledIconButton(int x, int y, int width, int height, ResourceLocation icon, int size, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress);
        this.icon = icon;
        this.size = size;
    }

    /** How wide a button needs to be for the words to show beside the icon. */
    static int widthFor(Font font, Component label, int size) {
        return size + 3 + font.width(label) + 10;
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int colour) {
        boolean words = widthFor(font, getMessage(), size) <= getWidth();
        int contentW = words ? size + 3 + font.width(getMessage()) : size;
        int x = getX() + (getWidth() - contentW) / 2;
        Gui.icon(g, icon, x, getY() + (getHeight() - size) / 2, size);
        if (words) {
            g.drawString(font, getMessage(), x + size + 3, getY() + (getHeight() - 8) / 2, colour, true);
        }
    }
}
