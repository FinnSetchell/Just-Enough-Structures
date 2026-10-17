package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;

/**
 * The loot index as it last arrived from the server being played on, and whether one is on its way,
 * kept where any thread can ask. EMI only takes recipes while it loads, on a thread of its own, and
 * it loads as a server is joined, just as the index is asked for. So its load asks here, and waits a
 * little for an index that's coming rather than load all over again once it's here. For the same
 * reason it asks here whether the index is an early one, with the finished one still to come.
 *
 * <p>Nothing of the game's is used here, so the game tests can try it on a server.
 */
public final class IndexArrival {
    private volatile LootIndex index;
    private volatile boolean coming;
    // The server said the index here is an early one, with the finished one still being built.
    private volatile boolean early;
    // When the server last said anything of the index, from System.nanoTime().
    private volatile long heard;

    /** The server has been asked for the index. */
    public void asked() {
        if (index == null) {
            coming = true;
        }
    }

    /** The server says it's still building the index, which takes minutes, so it isn't worth waiting for. */
    public void building() {
        coming = false;
        heard = System.nanoTime();
    }

    /** An index has come: {@code early} when the server says it's an early one, with the finished one to follow. */
    public void arrived(LootIndex arrived, boolean early) {
        this.early = early;
        index = arrived;
        coming = false;
        heard = System.nanoTime();
    }

    /** On leaving a server, or when it reloads and the index from before is no longer the one it would send. */
    public void forget() {
        index = null;
        coming = false;
        early = false;
    }

    /** The index if it's here, or null. */
    public LootIndex now() {
        return index;
    }

    /**
     * The index, waiting up to {@code millis} for one that's on its way: asked for, and not being
     * built. With none on its way this answers straight away. Not for the game's own thread, which
     * is the one the index arrives on.
     */
    public LootIndex await(long millis) {
        long until = System.nanoTime() + millis * 1_000_000L;
        while (index == null && coming && System.nanoTime() < until) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return index;
    }

    /**
     * How long yet to wait for the finished index, in ms, while the one here is an early one: until
     * the server has said nothing more of it for {@code quiet} ms, as it may have stopped building
     * the rest. 0 when there's nothing to wait for.
     */
    public long finishedWait(long quiet) {
        if (index == null || !early) {
            return 0;
        }
        return Math.max(0, quiet - (System.nanoTime() - heard) / 1_000_000L);
    }
}
