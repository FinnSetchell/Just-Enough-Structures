package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.server.SpareTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * Work the server does in its spare time, like the loot index's rolls. The test server never waits
 * between ticks, so it ticks as fast as it can and any tick leaves most of a tick's length over. These
 * tests make their own ticks take as long as a busy pack's.
 */
public final class SpareTimeTests {
    /** How long the pace is measured for, each time. */
    private static final long PHASE = TimeUnit.MILLISECONDS.toNanos(1500);
    /** How much of its pace the test server has to keep with work that never ends. */
    private static final double KEPT = 0.75;

    private SpareTimeTests() {
    }

    /**
     * Work that never runs out, as the loot index's rolls take minutes in a big pack, only takes what
     * the server's ticks leave over. Each tick here takes most of a tick's length, as a busy pack's do,
     * and the server's pace is measured without the work, with it, and without it again.
     *
     * <p>The test server never waits between ticks, so whatever the work takes slows it, where a real
     * server would have spent that time waiting. Kept to a tenth of a tick's length, the work would
     * leave the test server nine tenths of its pace, as 18 ticks a second would be of the game's 20, so
     * that is what the work may take. The pace itself is only held to three quarters of what it was
     * without the work: what the tests before this one left running slows the test server by more than
     * a tenth from one second to the next. The work is stopped after its turn, and stops itself in any
     * case a little after that, so a failure leaves nothing running.
     */
    public static void workThatNeverEndsLeavesTheServerItsTicks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long tick = SpareTime.tickNanos(server);
        long busy = tick * 17 / 20;
        long started = System.nanoTime();
        AtomicBoolean stop = new AtomicBoolean();
        long[] worked = {0};
        SpareTime.Job endless = nanos -> {
            if (stop.get() || System.nanoTime() - started > 4 * PHASE) {
                return true;
            }
            long before = System.nanoTime();
            spin(nanos);
            worked[0] += System.nanoTime() - before;
            return false;
        };
        Pace[] paces = {new Pace(), new Pace(), new Pace()};
        boolean[] reported = new boolean[1];
        helper.succeedWhen(() -> {
            long now = System.nanoTime();
            int phase = (int) Math.min(3, (now - started) / PHASE);
            if (phase == 1 && paces[1].ticks == 0) {
                SpareTime.add(server, endless);
            } else if (phase >= 2 && !stop.get()) {
                stop.set(true);
            }
            if (phase < 3) {
                paces[phase].tick(now);
                spin(busy);
                helper.assertTrue(false, "still measuring the server's pace");
            }
            double without = (paces[0].rate() + paces[2].rate()) / 2;
            double with = paces[1].rate();
            double perTick = (double) worked[0] / Math.max(1, paces[1].ticks);
            String seen = String.format(Locale.ROOT, "with ticks of %.1f ms the server ticked %.1f times a second without the work, %.1f with it"
                    + " and %.1f after it, and the work had %.2f ms a tick", busy / 1e6, paces[0].rate(), with, paces[2].rate(), perTick / 1e6);
            if (!reported[0]) {
                reported[0] = true;
                JustEnoughStructures.LOGGER.info("In the test of work in the server's spare time: {}", seen);
            }
            helper.assertTrue(!SpareTime.queued(endless), "the work is still queued after it was stopped");
            helper.assertTrue(perTick <= tick / 10.0, seen + ", more than a tenth of a tick's length");
            helper.assertTrue(with >= KEPT * without, seen + ", not three quarters of its pace");
        });
    }

    /** The ticks seen in one part of a test, for how fast the server went. */
    private static final class Pace {
        private int ticks;
        private long first;
        private long last;

        void tick(long now) {
            if (ticks++ == 0) {
                first = now;
            }
            last = now;
        }

        /** Ticks a second, from the first tick seen to the last. */
        double rate() {
            return ticks < 2 ? 0 : (ticks - 1) * (double) TimeUnit.SECONDS.toNanos(1) / (last - first);
        }
    }

    /**
     * Ticks that leave no time over still give the work a little each, so it finishes on a server that's
     * always busy. Here each tick takes longer than a tick's length, and the work needs a few of those
     * little turns. It stops itself after a while, so a failure leaves nothing running.
     */
    public static void workStillFinishesOnABusyServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long overrun = SpareTime.tickNanos(server) * 6 / 5;
        long needed = SpareTime.leastNanos() * 5;
        long started = System.nanoTime();
        List<Long> given = new ArrayList<>();
        long[] worked = {0};
        AtomicBoolean finished = new AtomicBoolean();
        SpareTime.Job job = nanos -> {
            if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(15)) {
                return true;
            }
            given.add(nanos);
            long before = System.nanoTime();
            spin(Math.min(nanos, needed - worked[0]));
            worked[0] += System.nanoTime() - before;
            finished.set(worked[0] >= needed);
            return finished.get();
        };
        // This tick is a busy one too, as the work gets its first turn at its end.
        spin(overrun);
        SpareTime.addWaitedFor(server, job);
        boolean[] logged = new boolean[1];
        helper.succeedWhen(() -> {
            if (!finished.get()) {
                spin(overrun);
                helper.assertTrue(false, "the work hasn't finished after " + given.size() + " turns");
            }
            long most = given.stream().mapToLong(Long::longValue).max().orElse(0);
            if (!logged[0]) {
                logged[0] = true;
                JustEnoughStructures.LOGGER.info("In the test of work in the server's spare time: with ticks of {} ms, work needing {} ms finished in {} turns of at most {} ms",
                        overrun / 1_000_000L, String.format(Locale.ROOT, "%.1f", needed / 1e6), given.size(), String.format(Locale.ROOT, "%.2f", most / 1e6));
            }
            helper.assertTrue(most <= SpareTime.leastNanos(), String.format(Locale.ROOT,
                    "ticks with no time over gave the work turns of up to %.2f ms, not the %.2f ms they should", most / 1e6, SpareTime.leastNanos() / 1e6));
        });
    }

    private static void spin(long nanos) {
        long until = System.nanoTime() + nanos;
        while (System.nanoTime() - until < 0) {
            Thread.onSpinWait();
        }
    }
}
