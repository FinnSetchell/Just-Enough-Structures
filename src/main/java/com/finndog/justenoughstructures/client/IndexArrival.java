package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;

/**
 * The loot index as it last arrived from the server being played on, and whether one is on its way,
 * kept where any thread can ask. EMI only takes recipes while it loads, on a thread of its own, and
 * it loads as a server is joined, just as the index is asked for. So its load asks here, and waits a
 * little for an index that's coming rather than load all over again once it's here.
 *
 * <p>Nothing of the game's is used here, so the game tests can try it on a server.
 */
public final class IndexArrival {
    private volatile LootIndex index;
    private volatile boolean coming;

    /** The server has been asked for the index. */
    public void asked() {
        if (index == null) {
            coming = true;
        }
    }

    /** The server says it's still building the index, which takes minutes, so it isn't worth waiting for. */
    public void building() {
        coming = false;
    }

    public void arrived(LootIndex arrived) {
        index = arrived;
        coming = false;
    }

    /** On leaving a server, or when it reloads and the index from before is no longer the one it would send. */
    public void forget() {
        index = null;
        coming = false;
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
}
