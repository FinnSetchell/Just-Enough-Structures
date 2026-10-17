package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.IndexArrival;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The wait EMI's load makes for a loot index that's on its way. The index arrives on the game's
 * thread and EMI loads on another, so here it's handed over from a thread of its own too. Then the
 * wait EMI makes for the finished index while an early one is here.
 */
public final class IndexArrivalTests {
    /** Much less than the waits asked for below, and much more than a busy machine takes to notice. */
    private static final long SOON = 2500;
    /** How long the server may say nothing here before the finished index stops being waited for. */
    private static final long QUIET = 1000;

    private IndexArrivalTests() {
    }

    public static void indexOnItsWayIsWaitedFor(GameTestHelper helper) {
        LootIndex index = new LootIndex(Map.of(), Map.of());
        IndexArrival arrival = new IndexArrival();
        arrival.asked();
        later(() -> arrival.arrived(index, false));
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

        // The server says its build failed, so none is coming.
        arrival.asked();
        later(arrival::failed);
        started = System.nanoTime();
        got = arrival.await(10_000);
        waited = millisSince(started);
        helper.assertTrue(got == null && waited < SOON, "an index whose build had failed was waited for, for " + waited + " ms");

        // The server never answers: the wait ends when its time is up, and not before.
        arrival.asked();
        started = System.nanoTime();
        got = arrival.await(300);
        waited = millisSince(started);
        helper.assertTrue(got == null && waited >= 290 && waited < SOON, "a wait of 300 ms for an index that never came took " + waited);

        // Leaving the server forgets the one it sent, and that another was on its way.
        arrival.arrived(index, false);
        arrival.forget();
        started = System.nanoTime();
        helper.assertTrue(arrival.now() == null && arrival.await(10_000) == null && millisSince(started) < SOON, "an index was kept, or waited for, after leaving the server");
        helper.succeed();
    }

    /**
     * With an early index here, the finished one is waited for while the server goes on saying how
     * the rest is going, and no longer once it has said nothing for the time it's given, as its build
     * may have stopped. A finished index, which is also how one from a server that doesn't mark them
     * arrives, is nothing to wait for. EMI's own load takes whichever is here.
     */
    public static void earlyIndexWaitsForTheFinishedOne(GameTestHelper helper) {
        LootIndex early = new LootIndex(Map.of(), Map.of());
        LootIndex finished = new LootIndex(Map.of(), Map.of());
        IndexArrival arrival = new IndexArrival();
        helper.assertTrue(arrival.finishedWait(QUIET) == 0, "with no index here, a finished one was waited for");

        arrival.asked();
        arrival.arrived(early, true);
        long wait = arrival.finishedWait(QUIET);
        helper.assertTrue(wait > 0 && wait <= QUIET, "an early index that had only just come had " + wait + " ms of waiting for the finished one");
        long started = System.nanoTime();
        helper.assertTrue(arrival.await(10_000) == early && millisSince(started) < SOON, "an early index wasn't handed over to EMI's load straight away");

        // Past the time it's given, but the server has gone on saying how the rest is going.
        pause(QUIET + 200);
        arrival.building();
        helper.assertTrue(arrival.finishedWait(QUIET) > 0, "the wait for the finished index ended while the server was still saying how the rest was going");

        // Then it says nothing more, as when its build has stopped.
        pause(QUIET + 200);
        helper.assertTrue(arrival.finishedWait(QUIET) == 0, "the finished index was still waited for after the server had said nothing for " + (QUIET + 200) + " ms");

        // The server says the build of the rest failed, so the early one is all there is.
        arrival.arrived(early, true);
        arrival.building();
        arrival.failed();
        helper.assertTrue(arrival.finishedWait(QUIET) == 0 && arrival.now() == early, "the finished index was waited for after its build had failed");

        // The finished one, or one from a server that doesn't say, is nothing to wait for.
        arrival.arrived(early, true);
        arrival.arrived(finished, false);
        helper.assertTrue(arrival.finishedWait(QUIET) == 0 && arrival.now() == finished, "a finished index that came after an early one was waited on");

        // Leaving the server forgets that the one here was early.
        arrival.arrived(early, true);
        arrival.forget();
        helper.assertTrue(arrival.finishedWait(QUIET) == 0, "the finished index was waited for after leaving the server");
        helper.succeed();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
