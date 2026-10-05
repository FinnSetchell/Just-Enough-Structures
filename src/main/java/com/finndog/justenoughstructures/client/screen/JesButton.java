package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
*///?}

/**
 * A button drawn the way buttons were before 26.1: {@code renderWidget} draws the whole of it,
 * normally the vanilla button and then its label through {@code renderString}, and the buttons here
 * change one or the other. 26.1 named both differently, so this passes its calls on to them.
 */
abstract class JesButton extends Button {
    protected JesButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    //? if >=26.1 {
    /*@Override
    protected final void extractContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWidget(g, mouseX, mouseY, partialTick);
    }

    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(g);
        renderString(g, Minecraft.getInstance().font, (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255) << 24);
    }

    public void renderString(GuiGraphics g, Font font, int colour) {
        extractDefaultLabel(g.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.NONE));
    }
    *///?}
}
