package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;

/**
 * The last loot index received from the server, and whether one is on its way, readable from any
 * thread. EMI reads recipes only while it loads, on a separate thread, as the server is joined. Its
 * load waits here briefly for an index on its way instead of reloading once it arrives, and checks
 * here whether the index is an early one, with the finished one still to come.
 *
 * <p>Uses no game classes, so game tests can run it on a server.
 */
public final class IndexArrival {
    private volatile LootIndex index;
    private volatile boolean coming;
    // The current index is an early one, and the server is still building the finished one.
    private volatile boolean early;
    // When the server last sent anything about the index, as a System.nanoTime() reading.
    private volatile long heard;

    /** Called when the index is requested. */
    public void asked() {
        if (index == null) {
            coming = true;
        }
    }

    /** Called when the server reports it's still building the index. That takes minutes, so {@link #await} stops waiting. */
    public void building() {
        coming = false;
        heard = System.nanoTime();
    }

    /**
     * Called when the server's build failed, so nothing more comes until it reloads. An early index
     * already here becomes final.
     */
    public void failed() {
        coming = false;
        early = false;
        heard = System.nanoTime();
    }

    /** Called when an index arrives. {@code early} is true for an early index, with the finished one to follow. */
    public void arrived(LootIndex arrived, boolean early) {
        this.early = early;
        index = arrived;
        coming = false;
        heard = System.nanoTime();
    }

    /** Clears the index on leaving a server, or when the server reloads and the old index is out of date. */
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

    /**
     * Milliseconds left to wait for the finished index while the current one is early. Waiting ends
     * once the server has been silent for {@code quiet} ms, as it may have stopped building the rest.
     * 0 when there's nothing to wait for.
     */
    public long finishedWait(long quiet) {
        if (index == null || !early) {
            return 0;
        }
        return Math.max(0, quiet - (System.nanoTime() - heard) / 1_000_000L);
    }
}
