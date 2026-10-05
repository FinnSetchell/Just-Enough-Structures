package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A screen over the world, darkened behind it, drawn once and under everything else. */
abstract class BackdropScreen extends Screen {
    protected BackdropScreen(Component title) {
        super(title);
    }

    /** Darkens the world behind the screen. Draw it first. */
    protected void backdrop(GuiGraphics g) {
        //? if >=1.21 {
        /*renderTransparentBackground(g);
        *///?} else {
        renderBackground(g);
        //?}
    }

    //? if >=1.21 {
    /*// Screen.render puts the blurred menu background down before its widgets, which would cover what
    // the screen drew first. backdrop() has done the background already.
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
    *///?}
}
