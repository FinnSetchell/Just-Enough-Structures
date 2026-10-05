package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
*///?}

/**
 * A screen over the world, darkened behind it, drawn once and under everything else.
 *
 * <p>From 26.1 screens are handed each click and key press as an event, and collect what to draw
 * rather than drawing it, under new names. This passes them on to the methods screens had before,
 * so the screens here are written the same way for every version.
 */
abstract class BackdropScreen extends Screen {
    protected BackdropScreen(Component title) {
        super(title);
    }

    /** Darkens the world behind the screen. Draw it first. */
    protected void backdrop(GuiGraphics g) {
        //? if >=26.1 {
        /*extractTransparentBackground(g);
        *///?} else if >=1.21 {
        /*renderTransparentBackground(g);
        *///?} else {
        renderBackground(g);
        //?}
    }

    //? if >=26.1 {
    /*// Screen puts the blurred menu background down under the screen, which would blur what the screen
    // shows of the world. backdrop() does the background instead.
    @Override
    public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public final void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        render(g, mouseX, mouseY, partialTick);
    }

    // Draws the screen. On its own it draws the widgets.
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    // The click being handled, so a click passed on unchanged keeps what else it says, like the keys held.
    private MouseButtonEvent click;
    private boolean doubleClick;

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.click = event;
        this.doubleClick = doubleClick;
        return mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(click(mouseX, mouseY, button), doubleClick);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        this.click = event;
        return mouseReleased(event.x(), event.y(), event.button());
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(click(mouseX, mouseY, button));
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        this.click = event;
        return mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return super.mouseDragged(click(mouseX, mouseY, button), dx, dy);
    }

    @Override
    public final boolean keyPressed(KeyEvent event) {
        return keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return super.keyPressed(new KeyEvent(key, scanCode, modifiers));
    }

    // Whether shift or control (command on a Mac) is held, which 26.1 only tells screens with each event.
    static boolean hasShiftDown() {
        return down(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
    }

    static boolean hasControlDown() {
        return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? down(GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)
                : down(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
    }

    private static boolean down(int left, int right) {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }

    private MouseButtonEvent click(double mouseX, double mouseY, int button) {
        MouseButtonEvent last = click;
        if (last != null && last.x() == mouseX && last.y() == mouseY && last.button() == button) {
            return last;
        }
        return new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, last != null ? last.modifiers() : 0));
    }
    *///?} else if >=1.21 {
    /*// Screen.render puts the blurred menu background down before its widgets, which would cover what
    // the screen drew first. backdrop() has done the background already.
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
    *///?}
}
