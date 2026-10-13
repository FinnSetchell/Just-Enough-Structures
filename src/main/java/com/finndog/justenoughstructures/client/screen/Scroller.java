package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** A part of a screen that scrolls on its own: drawn between {@link #begin} and {@link #end}. */
final class Scroller {
    private final Scroll scroll = new Scroll(20);
    private int x, y, w, h;

    /** Starts drawing; returns where the content's top is now. */
    int begin(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        scroll.view(h);
        Gui.scissor(g, x, y, x + w, y + h);
        ui.clip(y, y + h);
        return y - scroll.top();
    }

    /** How wide the content can be, leaving room for the scroll bar when it needs one. */
    int width() {
        return scroll.scrolls() ? w - 8 : w;
    }

    void end(GuiGraphics g, ToolsUi ui, int contentHeight) {
        scroll.content(contentHeight);
        Gui.endScissor(g);
        ui.unclip();
        if (scroll.scrolls()) {
            Gui.scrollbar(g, x + w - 4, y, h, scroll.offset(), scroll.most());
            ui.bar(this);
        }
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        scroll.wheel(delta);
        return true;
    }

    /** Takes hold of the scroll bar when a press is on it, or null. */
    HeldBar grab(double mouseX, double mouseY) {
        return HeldBar.at(scroll, mouseX, mouseY, x + w, y, h);
    }

    void reset() {
        scroll.reset();
    }

    /** A point {@code along} of the way down the scroll bar, for the screenshot harness. */
    int[] barPoint(float along) {
        return new int[]{x + w - 3, y + 1 + Math.round((h - 3) * along)};
    }

    double offset() {
        return scroll.offset();
    }

    /** See {@link Scroll#reveal}. */
    void reveal(int top, int bottom) {
        scroll.reveal(top, bottom);
    }
}
