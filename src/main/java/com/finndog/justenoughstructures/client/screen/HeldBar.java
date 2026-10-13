package com.finndog.justenoughstructures.client.screen;

/** A scroll bar a press has taken hold of, so its list follows the mouse until the button comes up. */
record HeldBar(Scroll scroll, int top, int height) {
    /**
     * Takes hold of a list's bar when a press is on it or on the strip kept clear for it, the 8
     * pixels left of {@code right}. Null when the press is elsewhere, or the list has no bar.
     */
    static HeldBar at(Scroll scroll, double mouseX, double mouseY, int right, int top, int height) {
        if (!scroll.scrolls() || mouseX < right - 8 || mouseX >= right || mouseY < top || mouseY >= top + height) {
            return null;
        }
        scroll.hold(mouseY - top, height);
        return new HeldBar(scroll, top, height);
    }

    void dragTo(double mouseY) {
        scroll.dragTo(mouseY - top, height);
    }

    void letGo() {
        scroll.letGo();
    }
}
