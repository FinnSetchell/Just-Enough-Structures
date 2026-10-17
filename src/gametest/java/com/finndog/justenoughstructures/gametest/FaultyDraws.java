package com.finndog.justenoughstructures.gametest;

import net.minecraft.world.entity.Entity;

/**
 * Makes the game fail to draw one entity, while armed, as another mod's renderer can fail on the
 * preview's stand-in for a world. Only the one a scenario picks from a preview, so the world's own
 * entities are left alone.
 */
public final class FaultyDraws {
    /** The entity that fails, or null for none. */
    public static volatile Entity armed;
    /** Whether the hook is in this game. It's left out of the test mod's jar for a real one. */
    public static volatile boolean hooked;
    /** How many times it has failed since it was armed. */
    public static volatile int failures;

    private FaultyDraws() {
    }

    public static void arm(Entity entity) {
        failures = 0;
        armed = entity;
    }

    /** Called as the game starts drawing {@code entity}. */
    public static void draw(Entity entity) {
        if (entity == null || entity != armed) {
            return;
        }
        failures++;
        throw new IllegalStateException("an entity renderer that fails, standing in for another mod's");
    }
}
