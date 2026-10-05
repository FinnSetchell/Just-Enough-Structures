package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** JEI's small previous and next buttons: a 13 pixel stone button with a white arrow on it. */
final class ArrowButton extends JesButton {
    static final int SIZE = 13;
    private final boolean left;

    ArrowButton(int x, int y, boolean left, Component label, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress);
        this.left = left;
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        Gui.arrow(g, getX() + 4, getY() + 3, left);
    }
}
