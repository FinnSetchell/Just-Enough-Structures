package com.ishland.c2me.fixes.worldgen.threading_issues.common;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * A stand-in for C2ME's world random, under the same name so the browser's mixin for it applies to
 * this too. C2ME keeps its parts inside its jar, which the development game can't load, so this is
 * how the tests check previews with it. Like C2ME's, it builds on the game's single-threaded random
 * rather than its legacy one, and refuses to be drawn from on any thread but its owner's.
 */
public class CheckedThreadLocalRandom extends SingleThreadedRandomSource {
    private final Supplier<Thread> owner;

    public CheckedThreadLocalRandom(long seed, Supplier<Thread> owner) {
        super(seed);
        this.owner = owner;
    }

    @Override
    public int next(int bits) {
        if (Thread.currentThread() != owner.get()) {
            throw new IllegalStateException("drawn from on " + Thread.currentThread().getName() + ", not its owner's thread");
        }
        return super.next(bits);
    }
}
