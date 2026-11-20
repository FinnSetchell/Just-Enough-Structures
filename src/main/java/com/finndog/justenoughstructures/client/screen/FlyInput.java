package com.finndog.justenoughstructures.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.3 {
/*import java.nio.FloatBuffer;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;
*///?}

/**
 * Takes the mouse for flying through the preview with the browser still open, and gives it back.
 * The cursor is hidden and held the way the game does it, but not through the game's own grab, which
 * would close the screen and turn the player. The game never gives back a grab it didn't make, so the
 * browser always does that itself. Everything here differs between versions of the game.
 */
public final class FlyInput {
    /** The screen that has the mouse, or null. */
    private static Screen owner;
    /** Where the mouse was when last read, in window pixels, to tell how far it has moved since. */
    private static double lastX;
    private static double lastY;
    /**
     * The game's mouse position when the mouse was taken, kept until it changes. Until then the game
     * hasn't heard where the cursor went, and the position is from before.
     */
    private static boolean stale;
    private static double staleX;
    private static double staleY;
    /** The game's mouse position when the cursor was put back at the crosshair, kept until it changes. */
    private static boolean back;
    private static double backX;
    private static double backY;

    private FlyInput() {
    }

    /** Whether {@code screen} has the mouse. */
    static boolean grabbed(Screen screen) {
        return owner != null && owner == screen;
    }

    /**
     * Whether the mouse can be taken for {@code screen}: it's the screen showing, with no loading
     * screen over it, in a window that has the focus, and the game hasn't taken the mouse itself.
     */
    static boolean canGrab(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == screen && mc.getOverlay() == null && mc.isWindowActive() && !mc.mouseHandler.isMouseGrabbed();
    }

    static boolean windowActive() {
        return Minecraft.getInstance().isWindowActive();
    }

    /** Hides the cursor and holds it for {@code screen}, at a point in GUI pixels. */
    static void grab(Screen screen, double guiX, double guiY) {
        if (owner != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double x = windowX(guiX);
        double y = windowY(guiY);
        owner = screen;
        back = false;
        stale = true;
        staleX = mc.mouseHandler.xpos();
        staleY = mc.mouseHandler.ypos();
        lastX = x;
        lastY = y;
        cursor(true, true, x, y);
    }

    /**
     * Shows the cursor again: with {@code warp} at a point in GUI pixels, otherwise wherever it is,
     * for a window that has lost the focus. Does nothing unless {@code screen} has the mouse.
     */
    static void release(Screen screen, double guiX, double guiY, boolean warp) {
        if (owner == null || owner != screen) {
            return;
        }
        owner = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed()) {
            // The game has taken the mouse since, as it does going back to the world, so it's the
            // game's to give back. Showing the cursor now would leave it showing in the world.
            return;
        }
        cursor(false, warp, windowX(guiX), windowY(guiY));
        // The cursor coming back isn't a drag.
        mc.mouseHandler.setIgnoreFirstMove();
        back = warp;
        backX = mc.mouseHandler.xpos();
        backY = mc.mouseHandler.ypos();
    }

    /**
     * Whether the cursor is at the crosshair: hidden there, or just put back there and not moved
     * since. The game only hears where it is once it moves.
     */
    static boolean centred() {
        if (owner != null) {
            return true;
        }
        if (back) {
            Minecraft mc = Minecraft.getInstance();
            back = mc.mouseHandler.xpos() == backX && mc.mouseHandler.ypos() == backY;
        }
        return back;
    }

    /**
     * Gives the mouse back, at the middle of the window, when the screen that took it isn't the one
     * showing any more, as when another mod swaps the screen without closing it. Every client tick.
     */
    public static void backstop(Minecraft mc) {
        if (owner != null && mc.screen != owner) {
            release(owner, mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, true);
        }
    }

    /**
     * How far to turn for the mouse's movement since the last call, as {yaw, pitch} in degrees: as
     * far as the game turns the player, with the player's sensitivity and mouse settings.
     */
    static float[] turn() {
        double[] moved = moved();
        Options options = Minecraft.getInstance().options;
        double s = options.sensitivity().get() * 0.6 + 0.2;
        double perPixel = s * s * s * 8.0 * 0.15;
        double[] sign = inverted(options);
        return new float[]{(float) (moved[0] * perPixel * sign[0]), (float) (moved[1] * perPixel * sign[1])};
    }

    /** The movement keys that fly the camera: forward, back, left, right, up, down, faster. */
    static KeyMapping[] movement() {
        Options o = Minecraft.getInstance().options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint};
    }

    /** Whether {@code key}, as a screen is told of a press, is one of the movement keys. */
    static boolean movementKey(int key) {
        for (KeyMapping mapping : movement()) {
            InputConstants.Key bound = InputConstants.getKey(mapping.saveString());
            if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a movement key is held, read from the keyboard: the key itself doesn't count as pressed
     * while a screen is open. One bound to a mouse button never is.
     */
    static boolean down(KeyMapping mapping) {
        InputConstants.Key bound = InputConstants.getKey(mapping.saveString());
        if (bound.getType() != InputConstants.Type.KEYSYM || bound.getValue() < 0) {
            return false;
        }
        Window window = Minecraft.getInstance().getWindow();
        //? if >=26.1 {
        /*return InputConstants.isKeyDown(window, bound.getValue());
        *///?} else {
        return InputConstants.isKeyDown(window.getWindow(), bound.getValue());
        //?}
    }

    private static double windowX(double guiX) {
        Window window = Minecraft.getInstance().getWindow();
        return guiX * window.getScreenWidth() / Math.max(1, window.getGuiScaledWidth());
    }

    private static double windowY(double guiY) {
        Window window = Minecraft.getInstance().getWindow();
        return guiY * window.getScreenHeight() / Math.max(1, window.getGuiScaledHeight());
    }

    /** -1 on each axis the player has turned round in the mouse settings, otherwise 1. */
    private static double[] inverted(Options options) {
        //? if >=26.1 {
        /*return new double[]{options.invertMouseX().get() ? -1 : 1, options.invertMouseY().get() ? -1 : 1};
        *///?} else {
        return new double[]{1, options.invertYMouse().get() ? -1 : 1};
        //?}
    }

    /** How far the mouse has moved since the last call, in window pixels. */
    private static double[] moved() {
        //? if >=26.3 {
        /*// SDL holds the cursor inside the window, so the positions the game is told stop at its edge.
        // SDL's own count of how far the mouse moved doesn't, and nothing else reads it.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            SDLMouse.SDL_GetRelativeMouseState(x, y);
            return new double[]{x.get(0), y.get(0)};
        }
        *///?} else {
        // With the cursor hidden, GLFW reports positions that go on past the window's edge.
        Minecraft mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        if (stale) {
            if (x == staleX && y == staleY) {
                return new double[]{0, 0};
            }
            stale = false;
        }
        double[] moved = {x - lastX, y - lastY};
        lastX = x;
        lastY = y;
        return moved;
        //?}
    }

    /**
     * The calls that hide and show the cursor, at a point in window pixels. Showing it without
     * {@code warp} leaves it where it is.
     */
    private static void cursor(boolean grab, boolean warp, double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        //? if >=26.3 {
        /*if (grab) {
            InputConstants.grabMouse(window, x, y);
            // Nothing else reads SDL's count of how far the mouse moved, so it holds everything
            // since the game started.
            moved();
            return;
        }
        if (warp) {
            InputConstants.releaseMouse(window, x, y);
        } else {
            // Putting the cursor back would move it in the window that has the focus now.
            SDLMouse.SDL_SetWindowRelativeMouseMode(window.handle(), false);
        }
        mc.mouseHandler.resyncMousePosition();
        *///?} else if >=26.1 {
        /*// Without the focus GLFW leaves the cursor where it is, so there's nothing to tell apart.
        InputConstants.grabOrReleaseMouse(window, grab ? InputConstants.CURSOR_DISABLED : InputConstants.CURSOR_NORMAL, x, y);
        *///?} else {
        // Without the focus GLFW leaves the cursor where it is, so there's nothing to tell apart.
        InputConstants.grabOrReleaseMouse(window.getWindow(), grab ? InputConstants.CURSOR_DISABLED : InputConstants.CURSOR_NORMAL, x, y);
        //?}
    }
}
