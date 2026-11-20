package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;

/**
 * Stands in for the mouse and keys while a scripted run flies through a preview. The fly camera hides
 * and holds the real cursor itself, which in a test game is the PC's own, and it reads how far the
 * mouse moved and which keys are held straight from the window, which the harness's made-up input
 * doesn't reach. A hook on the camera's input records what it would have done with the cursor and
 * hands it these instead, whenever a run is scripted.
 */
public final class FlyInputs {
    /** Grabbing the cursor, and showing it again at the crosshair or where it was. */
    public static final String GRAB = "grab";
    public static final String RELEASE = "release";
    public static final String RELEASE_IN_PLACE = "release_in_place";

    private static final List<String> calls = new ArrayList<>();
    private static final Set<KeyMapping> held = Collections.newSetFromMap(new IdentityHashMap<>());
    private static double lookX;
    private static double lookY;

    private FlyInputs() {
    }

    /** Called instead of hiding or showing the real cursor. */
    public static void cursor(boolean grab, boolean warp) {
        calls.add(grab ? GRAB : warp ? RELEASE : RELEASE_IN_PLACE);
    }

    /** How far the mouse moved since the camera last asked, in window pixels: what the scenario fed it since. */
    public static double[] moved() {
        double[] moved = {lookX, lookY};
        lookX = 0;
        lookY = 0;
        return moved;
    }

    public static boolean held(KeyMapping key) {
        return held.contains(key);
    }

    /** Moves the made-up mouse by window pixels, for the camera to turn by in its next frame. */
    public static void look(double dx, double dy) {
        lookX += dx;
        lookY += dy;
    }

    /** Holds a key down, or lets go of it. */
    public static void hold(KeyMapping key, boolean down) {
        if (down) {
            held.add(key);
        } else {
            held.remove(key);
        }
    }

    /** The cursor calls the camera made, oldest first. */
    public static List<String> calls() {
        return List.copyOf(calls);
    }

    public static String lastCall() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }

    public static void clear() {
        calls.clear();
        held.clear();
        lookX = 0;
        lookY = 0;
    }

    /**
     * Whether the hook is on the camera's input. Without it, flying in a test game would take the
     * PC's real cursor. Mixin keeps the hook's methods in the class under their own names.
     */
    public static boolean hooked() {
        try {
            Class<?> input = Class.forName("com.finndog.justenoughstructures.client.screen.FlyInput", true, JesScreen.class.getClassLoader());
            for (Method method : input.getDeclaredMethods()) {
                if (method.getName().contains("jesTest$")) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            // No such class: nothing to hook.
        }
        return false;
    }
}
