package com.finndog.justenoughstructures.capture;

import java.util.List;

/**
 * The outcome of a capture. {@code attempts} says what was tried, one line per terrain, which is
 * what we show when a structure refuses to generate anywhere.
 */
public record CaptureResult(StructureSnapshot snapshot, String error, List<String> attempts, long millis) {

    public static CaptureResult success(StructureSnapshot snapshot, List<String> attempts, long millis) {
        return new CaptureResult(snapshot, null, List.copyOf(attempts), millis);
    }

    public static CaptureResult failure(String error, List<String> attempts, long millis) {
        return new CaptureResult(null, error, List.copyOf(attempts), millis);
    }

    public boolean succeeded() {
        return snapshot != null;
    }
}
