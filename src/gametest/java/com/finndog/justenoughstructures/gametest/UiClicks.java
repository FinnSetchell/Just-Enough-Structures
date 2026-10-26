package com.finndog.justenoughstructures.gametest;

/**
 * Counts the button clicks the game is asked to play, whatever the volume, so a scenario can check
 * that a control clicks once when it's used, like a vanilla button.
 */
public final class UiClicks {
    /** The sound's path, as vanilla buttons play it. */
    private static final String CLICK = "ui.button.click";
    /** Whether the hook was applied. The test jar for a real game leaves it out, except on Fabric where the game isn't obfuscated. */
    public static volatile boolean hooked;
    private static volatile int count;

    private UiClicks() {
    }

    /** Called as the game is asked to play a sound. */
    public static void played(String path) {
        if (CLICK.equals(path)) {
            count++;
        }
    }

    public static int count() {
        return count;
    }
}
