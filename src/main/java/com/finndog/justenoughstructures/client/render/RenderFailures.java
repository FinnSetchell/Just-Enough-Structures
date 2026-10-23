package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;

/**
 * Handles failures while drawing a preview, often in another mod's renderer or model: that one thing
 * is left out and the preview carries on. The first failure of each kind goes to the game log with its
 * cause, so a report about a missing chest or mob says why. Callers catch every Throwable and pass it
 * through {@link #rethrowIfFatal} first, since another mod's code can fail with more than exceptions:
 * a mod built against another version of a library throws a linkage error, and one that recurses
 * forever runs out of stack.
 */
public final class RenderFailures {
    private RenderFailures() {
    }

    /**
     * Rethrows {@code e} if it is, or wraps, a fatal VM error such as OutOfMemoryError. Anything else
     * only costs the preview the thing that threw, StackOverflowError included, since the stack has
     * unwound by the time it's caught.
     */
    public static void rethrowIfFatal(Throwable e) {
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
            if (cause instanceof VirtualMachineError && !(cause instanceof StackOverflowError)) {
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                // A checked exception, thrown without being declared.
                throw new IllegalStateException(e);
            }
        }
    }

    /** Logs {@code what} failing for {@code kind}, such as a block or a mob type, once per kind of failure. */
    static void failed(String what, Object kind, Throwable e) {
        JesLog.warnOnce("render:" + what + "|" + kind + "|" + e.getClass().getName(), "{} failed for {} in a preview, so it's left out", what, kind, e);
    }

    /**
     * Logs a block that threw when asked for its shape in the preview's stand-in level. The block stays
     * in, and the preview guesses its shape.
     */
    static void shapeFailed(Object block, Throwable e) {
        JesLog.warnOnce("shape:" + block + "|" + e.getClass().getName(), "{} failed when asked for its shape in a preview, so the preview guesses it", block, e);
    }
}
