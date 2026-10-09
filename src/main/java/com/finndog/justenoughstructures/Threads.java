package com.finndog.justenoughstructures;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Threads of the mod's own, for work kept off the game's threads. */
public final class Threads {
    private Threads() {
    }

    /** A thread that runs what it's given one thing at a time, and never keeps the game from closing. */
    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(r -> daemon(r, name));
    }

    /** The same at a priority of its own, as for long work that should give way to the game's. */
    public static ExecutorService single(String name, int priority) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = daemon(r, name);
            t.setPriority(priority);
            return t;
        });
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
