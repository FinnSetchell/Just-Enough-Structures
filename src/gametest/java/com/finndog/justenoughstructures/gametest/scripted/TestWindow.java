package com.finndog.justenoughstructures.gametest.scripted;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.User32;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
*///?} else {
import org.lwjgl.glfw.GLFWNativeWin32;
//?}

/**
 * Keeps a scripted run's window fit to use. Something outside the game can minimise its window in the
 * seconds it shows while the game loads, and once the run has hidden it, it stays that way. The game
 * then has no size to go by: the mouse positions the harness sends come out as NaN, so every click,
 * hover, drag and wheel misses while keys still work, and from 26.2 it can draw at 1x1, so a check
 * that waits for a picture waits out its time. A minimised window is put back, and hidden again when
 * the run hides it, without taking the focus from whoever is at the computer. A run whose window can't
 * be put back stops and says so, rather than failing every check after.
 */
final class TestWindow {
    /** How many times a run puts its window back. Any more, and something keeps minimising it. */
    private static final int MOST = 3;
    /**
     * How long the window stays minimised before it's put back, in ticks. Something outside the game that
     * minimises it while the game is busy is only done once the game next reads its window's messages,
     * which it does once a frame, and a window put back before then is left shown at a minimised
     * window's size. Seen on 1.20.1, whose first ticks come before it first reads them. The game runs at
     * most ten ticks a frame.
     */
    private static final int SETTLE_TICKS = 40;
    /** How long a window that isn't minimised may have no size, in ticks, as it can be a frame behind. */
    private static final int SIZELESS_TICKS = 100;
    /**
     * Whether a minimised window that keeps its size is put back. From 26.3 the window is SDL's, which
     * keeps its size when minimised, and the checks pass as it is; put back once the run has hidden it
     * through SDL, it stayed minimised (26.3 Fabric, 17 October 2026).
     */
    //? if >=26.3 {
    /*private static final boolean PUT_BACK = false;
    *///?} else {
    private static final boolean PUT_BACK = true;
    //?}

    private static int putBack;
    private static int minimised;
    private static int sizeless;
    private static boolean sayWhenBack;
    private static boolean saidLeft;

    private TestWindow() {
    }

    /**
     * Puts the window back once it has been minimised a while, hidden again if {@code hidden}. Returns why
     * the run can't go on, or null when it can.
     */
    static String check(Minecraft mc, boolean hidden) {
        Window window = mc.getWindow();
        long hwnd = hwnd(window);
        boolean iconic = hwnd != 0 && User32.IsIconic(hwnd);
        boolean sized = window.getScreenWidth() > 0 && window.getScreenHeight() > 0 && window.getWidth() > 1 && window.getHeight() > 1;
        if (iconic && sized && !PUT_BACK) {
            if (!saidLeft) {
                saidLeft = true;
                JustEnoughStructures.LOGGER.warn("The test window is minimised; it keeps its size, so it's left as it is ({})", sizes(window));
            }
            return null;
        }
        if (iconic) {
            sizeless = 0;
            if (++minimised < SETTLE_TICKS) {
                return null;
            }
            minimised = 0;
            if (putBack == MOST) {
                return "it has been minimised " + (MOST + 1) + " times";
            }
            putBack++;
            JustEnoughStructures.LOGGER.warn("The test window is minimised; putting it back ({})", sizes(window));
            // Back to its own size, without the focus that GLFW's own restore takes (it uses SW_RESTORE).
            User32.ShowWindow(hwnd, User32.SW_SHOWNOACTIVATE);
            if (hidden) {
                User32.ShowWindow(hwnd, User32.SW_HIDE);
            }
            if (User32.IsIconic(hwnd)) {
                return "it is still minimised after being put back";
            }
            sayWhenBack = true;
            return null;
        }
        minimised = 0;
        if (!sized) {
            // Off Windows a minimised window has no size either, and can't be put back without the focus.
            if (sizeless++ == 0) {
                JustEnoughStructures.LOGGER.warn("The test window has no size ({})", sizes(window));
            }
            return sizeless > SIZELESS_TICKS ? "it has had no size for " + SIZELESS_TICKS / 20 + " seconds" : null;
        }
        sizeless = 0;
        if (sayWhenBack) {
            sayWhenBack = false;
            JustEnoughStructures.LOGGER.info("The test window is back ({})", sizes(window));
        }
        return null;
    }

    private static String sizes(Window window) {
        return "window " + window.getScreenWidth() + "x" + window.getScreenHeight() + ", drawn at " + window.getWidth() + "x" + window.getHeight();
    }

    /** The window's handle as Windows knows it, or 0 on any other system. */
    private static long hwnd(Window window) {
        if (Platform.get() != Platform.WINDOWS) {
            return 0;
        }
        //? if >=26.3 {
        /*return SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(window.handle()), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
        *///?} else if >=26.1 {
        /*return GLFWNativeWin32.glfwGetWin32Window(window.handle());
        *///?} else {
        return GLFWNativeWin32.glfwGetWin32Window(window.getWindow());
        //?}
    }
}
