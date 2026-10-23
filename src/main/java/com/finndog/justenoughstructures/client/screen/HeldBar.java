package com.finndog.justenoughstructures.client.screen;

/**
 * A scroll bar being dragged: its list follows the mouse until the button is released. Lists start a
 * drag through {@link #at}. {@code moved}, if set, runs each time the drag moves the list, for a list
 * that also stores its offset elsewhere, like a text box.
 */
record HeldBar(Scroll scroll, int top, int height, Runnable moved) {
    /**
     * Starts dragging a list's bar if the press is on it or on the strip kept clear for it, the 8
     * pixels left of {@code right}. Returns null if the press is elsewhere or the list doesn't scroll.
     * {@link Scroll#hold} decides where the thumb is gripped.
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
