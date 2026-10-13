package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.screen.Scroll;
import net.minecraft.gametest.framework.GameTestHelper;

/** Loader-neutral test bodies for how the browser's lists scroll. */
public final class ScrollTests {
    private ScrollTests() {
    }

    /** The wheel moves a list by its step and never past either end, and a list that got shorter comes back within it. */
    public static void scrollStaysWithinItsContent(GameTestHelper helper) {
        Scroll scroll = new Scroll(20);
        scroll.view(100);
        scroll.content(250);
        helper.assertTrue(scroll.scrolls() && scroll.most() == 150, "250 pixels in a window of 100 can scroll " + scroll.most());
        scroll.wheel(1);
        helper.assertTrue(scroll.offset() == 0, "the wheel went above the top, to " + scroll.offset());
        scroll.wheel(-1);
        helper.assertTrue(scroll.offset() == 20, "one notch down moved it " + scroll.offset());
        scroll.wheel(-0.5);
        helper.assertTrue(scroll.offset() == 30 && scroll.top() == 30, "half a notch more left it at " + scroll.offset());
        scroll.wheel(-100);
        helper.assertTrue(scroll.offset() == 150, "the wheel went past the end, to " + scroll.offset());

        scroll.content(180);
        helper.assertTrue(scroll.offset() == 80, "a list that got shorter stayed scrolled to " + scroll.offset());
        scroll.view(200);
        helper.assertTrue(scroll.offset() == 0 && !scroll.scrolls() && scroll.most() == 0, "a list that fits is still scrolled, to " + scroll.offset());
        scroll.wheel(-3);
        helper.assertTrue(scroll.offset() == 0, "a list that fits scrolled to " + scroll.offset());

        scroll.view(100);
        scroll.wheel(-2);
        scroll.reset();
        helper.assertTrue(scroll.offset() == 0, "it didn't go back to the top: " + scroll.offset());
        helper.succeed();
    }

    /** A row out of view is brought in with the row before it, one partly showing only as far as it needs, and one in view not at all. */
    public static void scrollRevealsARow(GameTestHelper helper) {
        Scroll scroll = new Scroll(20);
        scroll.view(100);
        scroll.content(1000);
        scroll.reveal(400, 420);
        helper.assertTrue(scroll.offset() == 380, "a row below the window was brought to " + scroll.offset());
        scroll.reveal(430, 450);
        helper.assertTrue(scroll.offset() == 380, "a row already in view moved the list to " + scroll.offset());
        scroll.reveal(470, 490);
        helper.assertTrue(scroll.offset() == 390, "a row cut off at the bottom was brought to " + scroll.offset());
        scroll.reveal(370, 395);
        helper.assertTrue(scroll.offset() == 370, "a row cut off at the top was brought to " + scroll.offset());
        scroll.reveal(100, 120);
        helper.assertTrue(scroll.offset() == 80, "a row above the window was brought to " + scroll.offset());
        scroll.reveal(0, 20);
        helper.assertTrue(scroll.offset() == 0, "the first row was brought to " + scroll.offset());
        helper.succeed();
    }

    /** The bar's thumb is as tall as the share of the list that shows, never too small to see, and runs from one end of the bar to the other. */
    public static void thumbFollowsTheScroll(GameTestHelper helper) {
        int track = 100;
        helper.assertTrue(Scroll.thumb(track, 150) == 38, "the thumb for 250 pixels in 100 is " + Scroll.thumb(track, 150));
        helper.assertTrue(Scroll.thumb(track, 100_000) == 12, "a very long list's thumb is " + Scroll.thumb(track, 100_000));
        helper.assertTrue(Scroll.thumbTop(track, 150, 0) == 1, "at the top the thumb starts at " + Scroll.thumbTop(track, 150, 0));
        int end = Scroll.thumbTop(track, 150, 150) + Scroll.thumb(track, 150);
        helper.assertTrue(end == track - 1, "at the end the thumb stops at " + end + " of " + track);
        // A list drawn a pixel or two past its end keeps the thumb inside the bar.
        helper.assertTrue(Scroll.thumbTop(track, 150, 152) == Scroll.thumbTop(track, 150, 150), "scrolled past the end, the thumb left the bar");
        int last = 0;
        for (int offset = 0; offset <= 150; offset++) {
            int top = Scroll.thumbTop(track, 150, offset);
            helper.assertTrue(top >= last, "the thumb went back up at " + offset);
            last = top;
        }
        helper.succeed();
    }

    /**
     * A list drawn with a little room under its last row scrolls that far past its end, by the wheel
     * or when told where to go, while its bar still stands for the content alone.
     */
    public static void scrollCanRunPastItsEnd(GameTestHelper helper) {
        Scroll scroll = new Scroll(36, 2);
        scroll.fit(100, 250);
        helper.assertTrue(scroll.most() == 150, "the bar of 250 pixels in a window of 100 stands for " + scroll.most());
        scroll.wheel(-100);
        helper.assertTrue(scroll.offset() == 152, "with two pixels of room the wheel stopped at " + scroll.offset());
        helper.assertTrue(Scroll.thumbTop(100, scroll.most(), scroll.offset()) == Scroll.thumbTop(100, 150, 150), "past its end, the thumb left the bar's end");
        scroll.to(40);
        helper.assertTrue(scroll.offset() == 40, "told to go to 40 it went to " + scroll.offset());
        scroll.to(1000);
        helper.assertTrue(scroll.offset() == 152, "told to go far past the end it went to " + scroll.offset());
        scroll.to(-5);
        helper.assertTrue(scroll.offset() == 0, "told to go above the top it went to " + scroll.offset());

        // The thumb brought exactly to the bar's end is the content's end; the mouse on past it takes the room too.
        scroll.hold(1, 100);
        scroll.dragTo(61, 100);
        helper.assertTrue(scroll.offset() == 150, "with the thumb at the bar's end the list is at " + scroll.offset());
        scroll.dragTo(500, 100);
        helper.assertTrue(scroll.offset() == 152, "dragged on past the bar's end the list is at " + scroll.offset());
        scroll.letGo();

        // Content a pixel short of the window: no bar, and only that pixel of the room to scroll.
        scroll.fit(100, 99);
        helper.assertTrue(!scroll.scrolls() && scroll.offset() == 1, "a list a pixel short of its window is scrolled to " + scroll.offset());
        scroll.fit(100, 90);
        helper.assertTrue(scroll.offset() == 0, "a list well short of its window is scrolled to " + scroll.offset());
        helper.succeed();
    }

    /** Dragging the bar's thumb moves the list with it, a press beside the thumb brings it there, and taking hold moves nothing. */
    public static void barDragsTheList(GameTestHelper helper) {
        int track = 100;
        Scroll scroll = new Scroll(20);
        scroll.view(100);
        scroll.content(250);
        // The thumb is 38 tall with 60 pixels to travel, for 150 of the list.
        scroll.hold(20, track);
        helper.assertTrue(scroll.offset() == 0, "taking hold of the thumb moved the list to " + scroll.offset());
        scroll.dragTo(50, track);
        helper.assertTrue(scroll.offset() == 75, "dragging the thumb half way moved the list to " + scroll.offset());
        scroll.dragTo(500, track);
        helper.assertTrue(scroll.offset() == 150, "dragging past the bar's end left the list at " + scroll.offset());
        scroll.dragTo(-500, track);
        helper.assertTrue(scroll.offset() == 0, "dragging past the bar's top left the list at " + scroll.offset());
        scroll.letGo();
        scroll.dragTo(50, track);
        helper.assertTrue(scroll.offset() == 0, "the list moved with nothing held, to " + scroll.offset());

        scroll.hold(80, track);
        helper.assertTrue(scroll.offset() == 150, "a press near the bar's end brought the list to " + scroll.offset());
        int middle = Scroll.thumbTop(track, 150, scroll.offset()) + Scroll.thumb(track, 150) / 2;
        helper.assertTrue(middle == 80, "the thumb's middle is at " + middle + ", not under the press at 80");
        scroll.letGo();

        // Part way down, the thumb is drawn at a whole pixel a little above where it really is.
        scroll.reset();
        scroll.wheel(-1.85);
        double before = scroll.offset();
        int drawn = Scroll.thumbTop(track, 150, before);
        scroll.hold(drawn, track);
        scroll.dragTo(drawn, track);
        helper.assertTrue(Math.abs(scroll.offset() - before) < 0.001, "holding the thumb still moved the list from " + before + " to " + scroll.offset());
        scroll.dragTo(drawn + 1, track);
        helper.assertTrue(Math.abs(scroll.offset() - before - 2.5) < 0.001, "one pixel of the bar moved the list " + (scroll.offset() - before));
        helper.succeed();
    }
}
