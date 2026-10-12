package com.finndog.justenoughstructures.client.screen;

/**
 * How far down a list is scrolled, in pixels of its content: what the wheel does to it, how far it
 * may go, and where that puts the thumb of its bar. Nothing here draws, so the game tests can run it.
 */
public final class Scroll {
    /** The least a thumb is tall, so a long list's can still be seen. */
    static final int THUMB_MIN = 12;

    /** Pixels one notch of the wheel moves the list. */
    private final double step;
    private double offset;
    /** How much of the list shows at once. */
    private int view;
    private int content;

    public Scroll(double step) {
        this.step = step;
    }

    public double offset() {
        return offset;
    }

    /** The offset in whole pixels, as content is drawn. */
    public int top() {
        return (int) offset;
    }

    /** The furthest it can scroll. */
    public int most() {
        return Math.max(0, content - view);
    }

    /** Whether there's more than shows at once. */
    public boolean scrolls() {
        return content > view;
    }

    public void view(int view) {
        this.view = view;
        clamp();
    }

    public void content(int content) {
        this.content = content;
        clamp();
    }

    public void wheel(double delta) {
        offset -= delta * step;
        clamp();
    }

    public void reset() {
        offset = 0;
    }

    /**
     * Scrolls so a row from {@code top} to {@code bottom}, in content terms, is in view: just far
     * enough when part of it shows, and near the top, with the row before it, when none of it does.
     */
    public void reveal(int top, int bottom) {
        if (bottom <= offset || top >= offset + view) {
            offset = Math.max(0, top - (bottom - top));
        } else if (top < offset) {
            offset = top;
        } else if (bottom > offset + view) {
            offset = bottom - view;
        }
    }

    /** How tall the thumb is in a bar {@code track} pixels tall, for a list that can scroll {@code most} pixels. */
    public static int thumb(int track, double most) {
        int inner = track - 2;
        return Math.max(THUMB_MIN, (int) (inner * inner / (inner + most)));
    }

    /** How far down such a bar the thumb's top is, for a list scrolled {@code offset} pixels. */
    public static int thumbTop(int track, double most, double offset) {
        return 1 + (int) ((track - 2 - thumb(track, most)) * Math.min(1, offset / most));
    }

    private void clamp() {
        offset = Math.max(0, Math.min(offset, most()));
    }
}
