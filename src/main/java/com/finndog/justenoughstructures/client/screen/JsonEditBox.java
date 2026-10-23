package com.finndog.justenoughstructures.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
//? if <26.1 {
import com.finndog.justenoughstructures.mixin.MultiLineEditBoxAccessor;
import net.minecraft.client.gui.screens.Screen;
//?}

/**
 * The loot editor's JSON box, and the box for writing notes. Before 26.1 a click inside a
 * multi-line box is handled as a click on its scroll area and the cursor doesn't move; this class
 * moves it. 26.1 moves the cursor itself and only builds these boxes through a builder, so there
 * it's a plain box. For a box shown with a {@link BoxBar}, it also reads and sets the scroll offset,
 * and hides the box's built-in bar where it can.
 */
//? if >=26.1 {
/*final class JsonEditBox {
    private JsonEditBox() {
    }

    static MultiLineEditBox create(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        return MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(placeholder).build(font, width, height, message);
    }

    // Here the built-in bar can't be hidden: BoxBar clips it off as it draws the box.
    static void hideBar(MultiLineEditBox box) {
    }

    static double offset(MultiLineEditBox box) {
        return box.scrollAmount();
    }

    static int most(MultiLineEditBox box) {
        return box.maxScrollAmount();
    }

    static void scrollTo(MultiLineEditBox box, double offset) {
        box.setScrollAmount(offset);
    }
}
*///?} else {
final class JsonEditBox extends MultiLineEditBox {
    private boolean ownBar = true;

    private JsonEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(font, x, y, width, height, placeholder, message);
    }

    static MultiLineEditBox create(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        return new JsonEditBox(font, x, y, width, height, placeholder, message);
    }

    /** Hides the box's built-in scroll bar, so it also takes no presses beside the box. */
    static void hideBar(MultiLineEditBox box) {
        ((JsonEditBox) box).ownBar = false;
    }

    /** How far the box is scrolled, in pixels. */
    static double offset(MultiLineEditBox box) {
        return ((JsonEditBox) box).scrollAmount();
    }

    /** How far it can be scrolled. */
    static int most(MultiLineEditBox box) {
        return ((JsonEditBox) box).getMaxScrollAmount();
    }

    static void scrollTo(MultiLineEditBox box, double offset) {
        ((JsonEditBox) box).setScrollAmount(offset);
    }

    @Override
    protected boolean scrollbarVisible() {
        return ownBar && super.scrollbarVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == InputConstants.MOUSE_BUTTON_LEFT && withinContentAreaPoint(mouseX, mouseY)) {
            try {
                MultiLineEditBoxAccessor box = (MultiLineEditBoxAccessor) (Object) this;
                box.justenoughstructures$textField().setSelecting(Screen.hasShiftDown());
                box.justenoughstructures$seekCursorScreen(mouseX, mouseY);
            } catch (RuntimeException e) {
                // If the accessor fails, the cursor stays where it was.
            }
            return true;
        }
        return handled;
    }
}
//?}
