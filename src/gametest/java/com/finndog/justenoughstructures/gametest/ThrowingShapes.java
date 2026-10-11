package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.render.SnapshotView;
import net.minecraft.world.level.BlockGetter;

/**
 * Makes every block in a preview fail when it's asked for its shape, while armed. Other mods' blocks
 * do that for real: one that takes the world it's handed for a real one fails with the preview's
 * stand-in. The real world's blocks are left alone, as the game asks them for their shapes all the time.
 */
public final class ThrowingShapes {
    /** Set by a scenario for as long as it wants the failures. */
    public static volatile boolean armed;
    /** Whether the hook is in this game. It's left out of the test mod's jar for a real one where the game is obfuscated. */
    public static volatile boolean hooked;
    /** How many times each question has failed, so a scenario can tell that both were asked. */
    public static volatile int shapes;
    public static volatile int solids;

    private ThrowingShapes() {
    }

    /** Called before a block state works out its shape in {@code level}. */
    public static void shape(BlockGetter level) {
        if (armed && level instanceof SnapshotView) {
            shapes++;
            throw new ClassCastException("A test block's shape wanted a real world and got a preview's");
        }
    }

    /** Called before a block state says whether it's solid in {@code level}. */
    public static void solid(BlockGetter level) {
        if (armed && level instanceof SnapshotView) {
            solids++;
            throw new ClassCastException("A test block wanted a real world to say whether it's solid, and got a preview's");
        }
    }
}
