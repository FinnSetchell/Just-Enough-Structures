package com.finndog.justenoughstructures;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Daemon threads for JES work kept off the game's threads. */
public final class Threads {
    private Threads() {
    }

    /** A single daemon thread that runs tasks in order, so it never keeps the game from closing. */
    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(r -> daemon(r, name));
    }

    /** The same with a set priority, for long work that should yield to the game. */
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
