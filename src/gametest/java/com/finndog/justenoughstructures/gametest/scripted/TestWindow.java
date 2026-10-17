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
 * that waits for a picture waits out its time. A minimised window is put back as it was, shown or
 * hidden, without taking the focus from whoever is at the computer. A run whose window can't be put
 * back stops and says so, rather than failing every check after.
 */
final class TestWindow {
    /** How many times a run puts its window back. Any more, and something keeps minimising it. */
    private static final int MOST = 3;
    /** How long a window that isn't minimised may have no size, in ticks, as it can be a frame behind. */
    private static final int SIZELESS_TICKS = 100;

    private static int putBack;
    private static int sizeless;
    private static boolean sayWhenBack;

    private TestWindow() {
    }

    /** Puts the window back if it's minimised. Returns why the run can't go on, or null when it can. */
    static String check(Minecraft mc) {
        Window window = mc.getWindow();
        long hwnd = hwnd(window);
        if (hwnd != 0 && User32.IsIconic(hwnd)) {
            sizeless = 0;
            if (putBack == MOST) {
                return "it has been minimised " + (MOST + 1) + " times";
            }
            putBack++;
            JustEnoughStructures.LOGGER.warn("The test window is minimised; putting it back ({})", sizes(window));
            boolean shown = User32.IsWindowVisible(hwnd);
            // Back to its own size, without the focus that GLFW's own restore takes (it uses SW_RESTORE).
            User32.ShowWindow(hwnd, User32.SW_SHOWNOACTIVATE);
            if (!shown) {
                User32.ShowWindow(hwnd, User32.SW_HIDE);
            }
            if (User32.IsIconic(hwnd)) {
                return "it is still minimised after being put back";
            }
            sayWhenBack = true;
            return null;
        }
        if (window.getScreenWidth() <= 0 || window.getScreenHeight() <= 0 || window.getWidth() <= 1 || window.getHeight() <= 1) {
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
