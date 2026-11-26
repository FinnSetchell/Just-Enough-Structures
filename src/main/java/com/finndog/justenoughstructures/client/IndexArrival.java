package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;

/**
 * The last loot index received from the server, and whether one is on its way, readable from any
 * thread. EMI reads recipes only while it loads, on a separate thread, as the server is joined. Its
 * load waits here briefly for an index on its way instead of reloading once it arrives.
 *
 * <p>Uses no game classes, so game tests can run it on a server.
 */
public final class IndexArrival {
    private volatile LootIndex index;
    private volatile boolean coming;

    /** Called when the index is requested. */
    public void asked() {
        if (index == null) {
            coming = true;
        }
    }

    /** Called when the server reports it's still building the index. That takes minutes, so {@link #await} stops waiting. */
    public void building() {
        coming = false;
    }

    /** Called when the server's build failed, so nothing more comes until it reloads. */
    public void failed() {
        coming = false;
    }

    /** Called when an index arrives, early or finished. */
    public void arrived(LootIndex arrived) {
        index = arrived;
        coming = false;
    }

    /** Clears the index on leaving a server, or when the server reloads and the old index is out of date. */
    public void forget() {
        index = null;
        coming = false;
    }

    /** The index if it's here, or null. */
    public LootIndex now() {
        return index;
    }

    /**
     * Returns the index, waiting up to {@code millis} for one that's on its way: requested, and not
     * still being built. Returns at once if none is on its way. Not for the client thread, which the
     * index arrives on.
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
