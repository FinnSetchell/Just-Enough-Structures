package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/**
 * Work for the server thread that only gets the time each tick leaves over, so it never makes the
 * server fall behind: the loot index's rolls, and the odds players ask for. Other mods' loot code runs
 * in those, and some of it touches the world, so they can't run on a thread of their own.
 *
 * <p>Each loader calls {@link #tickStarted} as a server tick starts and {@link #tickEnded} as it
 * ends. At the end of a tick the jobs get what's left of the tick's length, less a reserve and up to
 * a limit, and a little even when nothing is left, so they still finish on a server that's always
 * busy. The game's own queue for work between ticks is no place for them: it runs whatever is queued
 * until a whole tick's length after the tick ended, however long the tick took, so work that queued
 * itself again and again held every tick back by up to a tick's length.
 *
 * <p>Jobs run on the server thread, and adding one is safe from any thread. A server that isn't
 * ticking runs none: a paused singleplayer game, or a dedicated server that has paused because nobody
 * is on. They carry on when it ticks again.
 */
public final class SpareTime {
    /**
     * Left free at the end of every tick, for what the game does between ticks, like players' packets
     * and chunks that have loaded, and because a job can run a little past what it was given.
     */
    private static final long RESERVE_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    /**
     * The most one tick gives the jobs, however quiet the server: a roll can run on past the time it's
     * given, and in singleplayer the server shares the computer with the game drawing the world.
     */
    private static final long MOST_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    /** What a tick gives the jobs when it leaves no time over, so they finish on a server that's always busy. */
    private static final long LEAST_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    /** Jobs a player is waiting on, which go first, and the rest. */
    private static final Queue<Queued> WAITED_FOR = new ConcurrentLinkedQueue<>();
    private static final Queue<Queued> OTHERS = new ConcurrentLinkedQueue<>();
    /** When the tick under way started. Server thread only. */
    private static long tickStarted;

    private SpareTime() {
    }

    /** Work done a little at a time. */
    @FunctionalInterface
    public interface Job {
        /**
         * Works for about {@code nanos}, then says whether it's done. A job that isn't is given more
         * time at the end of a later tick. One that throws is dropped.
         */
        boolean work(long nanos);
    }

    private record Queued(MinecraftServer server, Job job) {
    }

    /** Queues work nobody is waiting on, like the loot index's rolls. */
    public static void add(MinecraftServer server, Job job) {
        OTHERS.add(new Queued(server, job));
    }

    /** Queues work a player is waiting on, which goes before the rest. */
    public static void addWaitedFor(MinecraftServer server, Job job) {
        WAITED_FOR.add(new Queued(server, job));
    }

    /** Whether this job is still queued. */
    public static boolean queued(Job job) {
        return WAITED_FOR.stream().anyMatch(q -> q.job() == job) || OTHERS.stream().anyMatch(q -> q.job() == job);
    }

    /** When the server stops: drops every job, as they belong to that world. Any added later are dropped by the next server. */
    public static void clear() {
        WAITED_FOR.clear();
        OTHERS.clear();
    }

    /** Called by the loader as each server tick starts. */
    public static void tickStarted(MinecraftServer server) {
        tickStarted = System.nanoTime();
    }

    /** Called by the loader as each server tick ends: runs the jobs for the time the tick left over. */
    public static void tickEnded(MinecraftServer server) {
        if (WAITED_FOR.isEmpty() && OTHERS.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        long until = now + budget(tickNanos(server), now - tickStarted);
        run(server, WAITED_FOR, until);
        run(server, OTHERS, until);
    }

    /** How long the jobs get at the end of a tick that took {@code took} of its {@code tick}. */
    static long budget(long tick, long took) {
        return Math.max(LEAST_NANOS, Math.min(MOST_NANOS, tick - took - RESERVE_NANOS));
    }

    /** Gives each job in turn what's left until {@code until}, dropping those that finish or throw. */
    private static void run(MinecraftServer server, Queue<Queued> jobs, long until) {
        Iterator<Queued> each = jobs.iterator();
        while (each.hasNext()) {
            long left = until - System.nanoTime();
            if (left <= 0) {
                return;
            }
            Queued queued = each.next();
            // One left over from a server that has stopped.
            boolean done = queued.server() != server;
            if (!done) {
                try {
                    done = queued.job().work(left);
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    JustEnoughStructures.LOGGER.error("Work in the server's spare time failed, so it was dropped", e);
                    done = true;
                }
            }
            if (done) {
                each.remove();
            }
        }
    }

    /** A tick's length on this server. */
    public static long tickNanos(MinecraftServer server) {
        //? if >=1.21 {
        /*return server.tickRateManager().nanosecondsPerTick();
        *///?} else {
        // On 1.20.1 a tick is always 50 ms.
        return TimeUnit.MILLISECONDS.toNanos(50);
        //?}
    }

    /** What a tick gives the jobs when it leaves no time over. */
    public static long leastNanos() {
        return LEAST_NANOS;
    }
}
