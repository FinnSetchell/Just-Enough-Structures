package com.finndog.justenoughstructures.client.screen;

/**
 * A scroll bar a press has taken hold of, so its list follows the mouse until the button comes up.
 * {@code moved}, when there is one, is told each time the bar moves the list, for a list that keeps its
 * own offset.
 */
record HeldBar(Scroll scroll, int top, int height, Runnable moved) {
    /**
     * Takes hold of a list's bar when a press is on it or on the strip kept clear for it, the 8
     * pixels left of {@code right}. Null when the press is elsewhere, or the list has no bar.
     */
    static HeldBar at(Scroll scroll, double mouseX, double mouseY, int right, int top, int height) {
        return at(scroll, mouseX, mouseY, right, top, height, null);
    }

    static HeldBar at(Scroll scroll, double mouseX, double mouseY, int right, int top, int height, Runnable moved) {
        if (!scroll.scrolls() || mouseX < right - 8 || mouseX >= right || mouseY < top || mouseY >= top + height) {
            return null;
        }
        scroll.hold(mouseY - top, height);
        HeldBar held = new HeldBar(scroll, top, height, moved);
        // A press beside the thumb has already brought the list to it.
        held.tell();
        return held;
    }

    void dragTo(double mouseY) {
        scroll.dragTo(mouseY - top, height);
        tell();
    }

    void letGo() {
        scroll.letGo();
    }

    private void tell() {
        if (moved != null) {
            moved.run();
        }
    }
}
