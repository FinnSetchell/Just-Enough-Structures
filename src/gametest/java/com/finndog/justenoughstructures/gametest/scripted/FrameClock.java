package com.finndog.justenoughstructures.gametest.scripted;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * When each frame was drawn, for the scripts that time frames, and the first frame drawn once a
 * condition held. {@link Director#onRender} calls {@link #frame} once a frame.
 */
final class FrameClock {
    private static long[] at = new long[1 << 14];
    private static int count;
    private static boolean on;
    private static BooleanSupplier watched;
    private static int metAt = -1;

    private FrameClock() {
    }

    /** Starts noting frames afresh. */
    static void start() {
        count = 0;
        on = true;
        watched = null;
        metAt = -1;
    }

    static void stop() {
        on = false;
        watched = null;
    }

    /** How many frames have been noted since {@link #start}. */
    static int count() {
        return count;
    }

    /** When frame {@code i} was drawn, from {@link System#nanoTime}. */
    static long at(int i) {
        return at[i];
    }

    /** From the next frame on, notes the first frame drawn once {@code condition} holds. */
    static void watch(BooleanSupplier condition) {
        watched = condition;
        metAt = -1;
    }

    /** The first frame drawn once the watched condition held, or -1. */
    static int metAt() {
        return metAt;
    }

    static void frame() {
        if (!on) {
            return;
        }
        if (count == at.length) {
            at = Arrays.copyOf(at, count * 2);
        }
        at[count] = System.nanoTime();
        if (watched != null && metAt < 0 && watched.getAsBoolean()) {
            metAt = count;
        }
        count++;
    }
}
