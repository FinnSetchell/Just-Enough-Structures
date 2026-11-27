package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.IndexArrival;
import com.finndog.justenoughstructures.client.ShownIndex;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * How EMI's load waits for a loot index on its way. The index arrives on the game thread and EMI loads on another, so these tests hand it over
 * from a separate thread. And which indexes a recipe viewer takes.
 */
public final class IndexArrivalTests {
    /** Well under the waits asked for below, and well over the time a busy machine needs to notice an arrival. */
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
        arrival.arrived(index);
        arrival.forget();
        started = System.nanoTime();
        helper.assertTrue(arrival.now() == null && arrival.await(10_000) == null && millisSince(started) < SOON, "an index was kept, or waited for, after leaving the server");
        helper.succeed();
    }

    /**
     * A viewer that shows an early index, or is about to, leaves the ones the server sends as the build
     * goes on for the finished one; with none shown it takes the first; once the build has failed it
     * takes the latest.
     */
    public static void viewersWaitForTheFinishedIndex(GameTestHelper helper) {
        ShownIndex viewer = new ShownIndex();
        LootIndex early = new LootIndex(Map.of(), Map.of());
        helper.assertFalse(viewer.waitsForFinished(true, false), "a viewer with no index waited for the finished one instead of taking the first");
        viewer.coming(early);
        helper.assertTrue(viewer.waitsForFinished(true, false), "a viewer about to load an early index took the next one sent, restarting its load");
        viewer.coming(null);
        viewer.showing(early);
        helper.assertTrue(viewer.waitsForFinished(true, false), "a viewer showing an early index took the next one sent as the build went on");
        helper.assertFalse(viewer.waitsForFinished(false, false), "a viewer showing an early index didn't take the finished one");
        helper.assertFalse(viewer.waitsForFinished(true, true), "after a failed build, a viewer kept the first early index instead of the latest");
        helper.succeed();
    }

    /**
     * An early index is handed to EMI's load as soon as it's here, as a finished one is: it holds
     * most of the loot, long before the finished one.
     */
    public static void earlyIndexIsHandedOverAtOnce(GameTestHelper helper) {
        LootIndex early = new LootIndex(Map.of(), Map.of());
        IndexArrival arrival = new IndexArrival();
        arrival.asked();
        arrival.arrived(early);
        arrival.building();
        long started = System.nanoTime();
        helper.assertTrue(arrival.await(10_000) == early && millisSince(started) < SOON, "an early index wasn't handed over to EMI's load straight away");
        helper.succeed();
    }

    /**
     * A recipe viewer takes an index only when it differs from the one it shows and the one on its
     * way. They're compared by what they hold, as the server's copy of an unchanged index arrives as
     * a new one each time.
     */
    public static void viewerTakesOnlyANewIndex(GameTestHelper helper) {
        LootIndex index = index("diamond", Set.of());
        ShownIndex shown = new ShownIndex();
        helper.assertTrue(!shown.isNew(null), "no index at all was one to take");
        helper.assertTrue(shown.isNew(index) && shown.shown() == null, "a viewer showing no index wouldn't take one");

        shown.showing(index);
        LootIndex again = index("diamond", Set.of());
        helper.assertTrue(again != index && again.equals(index), "the copy of the index isn't a separate, equal one, so this shows nothing");
        helper.assertTrue(!shown.isNew(again) && shown.shown() == index, "the same index, sent again, was taken again");
        helper.assertTrue(shown.isNew(index("emerald", Set.of())), "an index whose loot table gives something else wasn't taken");
        helper.assertTrue(shown.isNew(index("diamond", Set.of(Ids.parse("minecraft:tnt")))), "an index where the structure places something else wasn't taken");
        helper.assertTrue(shown.isNew(new LootIndex(Map.of(), Map.of())), "an index with no structures left wasn't taken");

        // One on its way, as when EMI is asked to reload for it: neither it nor the one shown is taken until that load starts.
        LootIndex changed = index("emerald", Set.of());
        shown.coming(changed);
        helper.assertTrue(!shown.isNew(index("emerald", Set.of())) && !shown.isNew(again), "an index on its way, or the one shown, was taken again");
        helper.assertTrue(shown.isNew(index("gold_ingot", Set.of())), "a third index wasn't taken while another was on its way");
        shown.coming(null);
        shown.showing(changed);
        helper.assertTrue(shown.isNew(again) && !shown.isNew(index("emerald", Set.of())), "after the load, the old index wasn't taken or the loaded one was");

        // A viewer that has restarted shows no index, so even the same one is to be taken.
        shown.showing(null);
        helper.assertTrue(shown.isNew(index("emerald", Set.of())), "a viewer that had restarted wouldn't take the index it had before");
        helper.succeed();
    }

    /** An index of one structure with one loot table, which gives {@code item}, and places {@code placed}. Built anew each time, as an arrival is. */
    private static LootIndex index(String item, Set<ResourceLocation> placed) {
        return new LootIndex(Map.of(Ids.parse("minecraft:desert_pyramid"), Set.of(Ids.parse("minecraft:chests/desert_pyramid"))),
                Map.of(Ids.parse("minecraft:chests/desert_pyramid"), Set.of(Ids.parse("minecraft:" + item))),
                placed.isEmpty() ? Map.of() : Map.of(Ids.parse("minecraft:desert_pyramid"), placed));
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
