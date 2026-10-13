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
    /** Pixels the list may scroll past its end, for one drawn with a little room under its last row. */
    private final int slack;
    private double offset;
    /** How much of the list shows at once. */
    private int view;
    private int content;
    /** Whether the bar's thumb is held, and how far down the thumb. */
    private boolean held;
    private double grip;

    public Scroll(double step) {
        this(step, 0);
    }

    public Scroll(double step, int slack) {
        this.step = step;
        this.slack = slack;
    }

    public double offset() {
        return offset;
    }

    /** The offset in whole pixels, as content is drawn. */
    public int top() {
        return (int) offset;
    }

    /** How far its content runs past what shows: what the bar's whole length stands for. */
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

    /** Both at once, for a list that works out its own size and content. */
    public void fit(int view, int content) {
        this.view = view;
        this.content = content;
        clamp();
    }

    public void to(double offset) {
        this.offset = offset;
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

    /**
     * Takes hold of the bar {@code y} pixels down it, a bar {@code track} pixels tall: of the thumb
     * where it's pressed, or of its middle, brought to the press, when the press is beside it.
     */
    public void hold(double y, int track) {
        int most = most();
        int thumb = thumb(track, most);
        int drawn = thumbTop(track, most, offset);
        held = true;
        if (y >= drawn && y < drawn + thumb && most > 0) {
            // From where the thumb's top really is, not the whole pixel it's drawn at, so taking hold moves nothing.
            grip = y - (1 + (track - 2 - thumb) * offset / most);
        } else {
            grip = thumb / 2.0;
            dragTo(y, track);
        }
    }

    /** Moves the list so the thumb stays under the mouse where it's held, now {@code y} pixels down the bar. */
    public void dragTo(double y, int track) {
        int most = most();
        int room = track - 2 - thumb(track, most);
        if (!held || most <= 0 || room <= 0) {
            return;
        }
        offset = (y - grip - 1) / room * most;
        clamp();
    }

    public void letGo() {
        held = false;
    }

    private void clamp() {
        offset = Math.max(0, Math.min(offset, Math.max(0, content - view + slack)));
    }
}
