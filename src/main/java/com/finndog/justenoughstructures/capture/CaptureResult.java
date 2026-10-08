package com.finndog.justenoughstructures.capture;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The outcome of a capture. {@code attempts} says what was tried, one line per terrain, which is
 * what we show when a structure refuses to generate anywhere. A {@code temporary} failure says
 * nothing about the structure, like the server running short on memory or a wait behind another
 * capture, so trying again later may well work.
 */
public record CaptureResult(StructureSnapshot snapshot, Component reason, List<Component> attempts, long millis, boolean temporary) {

    public static CaptureResult success(StructureSnapshot snapshot, List<Component> attempts, long millis) {
        return new CaptureResult(snapshot, null, List.copyOf(attempts), millis, false);
    }

    public static CaptureResult failure(Component reason, List<Component> attempts, long millis) {
        return new CaptureResult(null, reason, List.copyOf(attempts), millis, false);
    }

    /** A failure that may well not happen again, as it was down to the server rather than the structure. */
    public static CaptureResult temporaryFailure(Component reason, List<Component> attempts, long millis) {
        return new CaptureResult(null, reason, List.copyOf(attempts), millis, true);
    }

    public boolean succeeded() {
        return snapshot != null;
    }

    /** Why it failed as plain text, for logs and reports, or null if it didn't. */
    public String error() {
        return reason == null ? null : reason.getString();
    }

    /** This result with {@link StructureSnapshot#withoutLoot()} in place of its snapshot. */
    public CaptureResult withoutLoot() {
        return succeeded() ? new CaptureResult(snapshot.withoutLoot(), reason, attempts, millis, temporary) : this;
    }
}
