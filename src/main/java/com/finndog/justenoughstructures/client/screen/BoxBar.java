package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;

/**
 * JES's scroll bar and wheel handling for a vanilla multi-line text box, so it scrolls like the other
 * lists. The bar goes in the 8 pixels right of the box, where vanilla puts its built-in one. The box
 * tracks its scroll offset itself, and the cursor moves it while typing, so this reads the offset
 * before each use and writes back what the bar or the wheel changes. The screen draws the box through
 * {@link #render}, not as one of its widgets, and passes presses and the wheel here first. Used by the
 * loot editor's JSON view.
 */
final class BoxBar {
    /** Three of the box's 9-pixel lines a notch, as the other text views scroll three rows. */
    private final Scroll scroll = new Scroll(27);
    private final MultiLineEditBox box;

    BoxBar(MultiLineEditBox box) {
        this.box = box;
        JsonEditBox.hideBar(box);
    }

    /** The right edge of the strip beside the box where the bar is drawn. */
    private int right() {
        return box.getX() + box.getWidth() + 8;
    }

    private void follow() {
        scroll.fit(box.getHeight(), box.getHeight() + JsonEditBox.most(box));
        scroll.to(JsonEditBox.offset(box));
    }

    private void lead() {
        JsonEditBox.scrollTo(box, scroll.offset());
    }

    /** Draws the box and the bar beside it. */
    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        follow();
        int edge = box.getX() + box.getWidth();
        // From 26.1 the box draws its built-in bar to its right and reacts to the mouse there. That bar is
        // clipped off, and the box isn't told about the mouse while it's beside the box.
        Gui.scissor(g, box.getX(), box.getY(), edge, box.getY() + box.getHeight());
        boolean beside = mouseX >= edge;
        Gui.render(g, box, beside ? -1 : mouseX, beside ? -1 : mouseY, partialTick);
        Gui.endScissor(g);
        Gui.scrollbar(g, right() - 4, box.getY(), box.getHeight(), scroll.offset(), scroll.most());
    }

    /** Returns the grabbed scroll bar if the press is on its strip, or null. */
    HeldBar grab(double mouseX, double mouseY) {
        follow();
        return HeldBar.at(scroll, mouseX, mouseY, right(), box.getY(), box.getHeight(), this::lead);
    }

    /** Scrolls the box when the wheel is over it or its bar. */
    boolean wheel(double mouseX, double mouseY, double delta) {
        if (!box.visible || mouseX < box.getX() || mouseX >= right() || mouseY < box.getY() || mouseY >= box.getY() + box.getHeight()) {
            return false;
        }
        follow();
        scroll.wheel(delta);
        lead();
        return true;
    }
}
