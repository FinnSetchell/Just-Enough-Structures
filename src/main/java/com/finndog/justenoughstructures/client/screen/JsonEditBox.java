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
 * The loot editor's JSON box. Before 26.1 a click inside a multi-line box is taken as a click on its
 * scroll area before the cursor is moved, so the cursor stays where it was. This moves it. 26.1 moves
 * it itself, and only makes these boxes through a builder, so there it's a plain one.
 */
//? if >=26.1 {
/*final class JsonEditBox {
    private JsonEditBox() {
    }

    static MultiLineEditBox create(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        return MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(placeholder).build(font, width, height, message);
    }
}
*///?} else {
final class JsonEditBox extends MultiLineEditBox {
    private JsonEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(font, x, y, width, height, placeholder, message);
    }

    static MultiLineEditBox create(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        return new JsonEditBox(font, x, y, width, height, placeholder, message);
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
                // Then the cursor just doesn't move, as before.
            }
            return true;
        }
        return handled;
    }
}
//?}
