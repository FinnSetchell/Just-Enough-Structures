package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;

/**
 * What happens when something fails to draw in a preview, which is often another mod's renderer or
 * model: that one thing is left out and the preview carries on. The first failure of each kind goes
 * to the game log with its cause, so a report about a missing chest or mob says why. Callers catch
 * everything and pass it through {@link #rethrowIfFatal} first, as another mod's code can fail with
 * more than exceptions: a mod built against another version of something throws a linkage error, and
 * one that calls itself for ever runs out of stack.
 */
public final class RenderFailures {
    private RenderFailures() {
    }

    /**
     * Throws {@code e} on if the game can't carry on after it, because it ran out of memory or Java
     * itself broke, even when the game has wrapped it in an exception of its own. Anything else only
     * costs the preview the thing that threw it, running out of stack included: that's over once the
     * stack has unwound to the caller.
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

    /** Notes {@code what} failing for something of {@code kind}, like a block or a type of mob. */
    static void failed(String what, Object kind, Throwable e) {
        JesLog.warnOnce("render:" + what + "|" + kind + "|" + e.getClass().getName(), "{} failed for {} in a preview, so it's left out", what, kind, e);
    }

    /**
     * Notes a block failing when asked for a shape, which a preview asks with its stand-in for a world.
     * Nothing is left out for it: the preview makes do with a guess.
     */
    static void shapeFailed(Object block, Throwable e) {
        JesLog.warnOnce("shape:" + block + "|" + e.getClass().getName(), "{} failed when asked for its shape in a preview, so the preview guesses it", block, e);
    }
}
