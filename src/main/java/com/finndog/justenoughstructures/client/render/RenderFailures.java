package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;

/**
 * What happens when something fails to draw in a preview, which is often another mod's renderer or
 * model: that one thing is left out and the preview carries on. The first failure of each kind goes
 * to the game log with its cause, so a report about a missing chest or mob says why. Callers catch
 * {@code RuntimeException | LinkageError} on every version, as a mod built against another version
 * of something throws the second.
 */
final class RenderFailures {
    private RenderFailures() {
    }

    /** Notes {@code what} failing for something of {@code kind}, like a block or a type of mob. */
    static void failed(String what, Object kind, Throwable e) {
        JesLog.warnOnce("render:" + what + "|" + kind + "|" + e.getClass().getName(), "{} failed for {} in a preview, so it's left out", what, kind, e);
    }
}
