package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.IndexArrival;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The wait EMI's load makes for a loot index that's on its way. The index arrives on the game's
 * thread and EMI loads on another, so here it's handed over from a thread of its own too.
 */
public final class IndexArrivalTests {
    /** Much less than the waits asked for below, and much more than a busy machine takes to notice. */
    private static final long SOON = 2500;

    private IndexArrivalTests() {
    }

    public static void indexOnItsWayIsWaitedFor(GameTestHelper helper) {
        LootIndex index = new LootIndex(Map.of(), Map.of());
        IndexArrival arrival = new IndexArrival();
        arrival.asked();
        later(() -> arrival.arrived(index));
        long started = System.nanoTime();
        LootIndex got = arrival.await(10_000);
        long waited = millisSince(started);
        helper.assertTrue(got == index, "an index that arrived during the wait wasn't handed over");
        helper.assertTrue(waited < SOON, "an index that arrived after 150 ms was handed over after " + waited);

        // One that's here already is handed over without a wait, however many times it's asked for.
        arrival.asked();
        started = System.nanoTime();
        helper.assertTrue(arrival.await(10_000) == index && arrival.now() == index && millisSince(started) < SOON, "an index that was here already was waited for");
        helper.succeed();
    }

    public static void indexThatIsntComingIsntWaitedFor(GameTestHelper helper) {
        LootIndex index = new LootIndex(Map.of(), Map.of());
        IndexArrival arrival = new IndexArrival();

        // Nobody's asked the server, as on one without the mod.
        long started = System.nanoTime();
        helper.assertTrue(arrival.await(10_000) == null && millisSince(started) < SOON, "an index nobody asked for was waited for");

        // The server says it's still building it, which it does in answer to being asked.
        arrival.asked();
        later(arrival::building);
        started = System.nanoTime();
        LootIndex got = arrival.await(10_000);
        long waited = millisSince(started);
        helper.assertTrue(got == null && waited < SOON, "an index the server is still building was waited for, for " + waited + " ms");

        // The server never answers: the wait ends when its time is up, and not before.
        arrival.asked();
        started = System.nanoTime();
        got = arrival.await(300);
        waited = millisSince(started);
        helper.assertTrue(got == null && waited >= 290 && waited < SOON, "a wait of 300 ms for an index that never came took " + waited);

        // Leaving the server forgets the one it sent, and that another was on its way.
        arrival.arrived(index);
        arrival.forget();
        started = System.nanoTime();
        helper.assertTrue(arrival.now() == null && arrival.await(10_000) == null && millisSince(started) < SOON, "an index was kept, or waited for, after leaving the server");
        helper.succeed();
    }

    /** Runs {@code then} on another thread in 150 ms. */
    private static void later(Runnable then) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                return;
            }
            then.run();
        }, "JES test index arrival");
        thread.setDaemon(true);
        thread.start();
    }

    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
