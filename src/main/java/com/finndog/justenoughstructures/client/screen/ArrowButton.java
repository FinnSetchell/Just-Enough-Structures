package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** JEI's small previous and next buttons: a 13 pixel stone button with one of the 9 pixel icons on it, the step arrow's or another. */
final class ArrowButton extends JesButton {
    static final int SIZE = 13;
    private final ResourceLocation icon;

    ArrowButton(int x, int y, boolean left, Component label, OnPress onPress) {
        this(x, y, left ? Icons.STEP_LEFT : Icons.STEP_RIGHT, label, onPress);
    }

    ArrowButton(int x, int y, ResourceLocation icon, Component label, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress);
        this.icon = icon;
        setTooltip(Tooltip.create(label));
    }

    void setLabel(Component label) {
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        Gui.icon(g, icon, getX() + 2, getY() + 2, Icons.SMALL);
    }
}
