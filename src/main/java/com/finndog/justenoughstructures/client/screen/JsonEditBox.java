package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.mixin.MultiLineEditBoxAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The loot editor's JSON box. In 1.20.1 a click inside a multi-line box is taken as a click on its
 * scroll area before the cursor is moved, so the cursor stays where it was. This moves it.
 */
final class JsonEditBox extends MultiLineEditBox {
    JsonEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(font, x, y, width, height, placeholder, message);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && withinContentAreaPoint(mouseX, mouseY)) {
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
