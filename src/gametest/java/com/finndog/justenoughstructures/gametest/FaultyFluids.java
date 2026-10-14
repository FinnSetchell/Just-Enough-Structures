package com.finndog.justenoughstructures.gametest;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * While it's on, the game's own fluid drawing stands in for another mod's that fails part
 * way through a vertex. Only on the thread that turned it on, the render thread, where previews build
 * their meshes, so the world's own chunks never meet it.
 */
public final class FaultyFluids {
    /** Whether the hook went in, so a check can tell it apart from no fluid being drawn. */
    public static volatile boolean hooked;
    /** How many times it has failed since it was turned on. */
    public static volatile int failures;
    private static volatile Thread on;

    private FaultyFluids() {
    }

    public static void start() {
        failures = 0;
        on = Thread.currentThread();
    }

    public static void stop() {
        on = null;
    }

    public static boolean active() {
        return on == Thread.currentThread();
    }

    /** Called as the game starts drawing a fluid: half a vertex into {@code consumer}, then a failure. */
    public static void tesselate(VertexConsumer consumer) {
        if (!active()) {
            return;
        }
        failures++;
        //? if >=1.21 {
        /*consumer.addVertex(0f, 0f, 0f);
        *///?} else {
        consumer.vertex(0.0, 0.0, 0.0);
        //?}
        throw new IllegalStateException("a fluid renderer that fails part way through a vertex, standing in for another mod's");
    }
}
