package com.finndog.justenoughstructures;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The format number every file JES saves carries, so a later version knows what it's reading, and
 * an earlier one leaves a newer file alone rather than misreading it or writing over what it
 * doesn't understand. A file without one is from before formats were written, which is format 1.
 */
public final class FileFormat {
    public static final String KEY = "format";
    /** Goes up when a file this version writes couldn't be read the same way by the one before. */
    public static final int CURRENT = 1;

    private FileFormat() {
    }

    public static int of(JsonObject json) {
        try {
            return json != null && json.has(KEY) ? json.get(KEY).getAsInt() : 1;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Whether a newer version saved this, so it isn't for this one to read or write. */
    public static boolean newer(JsonObject json) {
        return of(json) > CURRENT;
    }

    /** Throws when a newer version saved the file. The message is just the file's name; the log says why. */
    public static void check(JsonObject json, Path file) throws IOException {
        if (newer(json)) {
            JesLog.warnOnce("newer-format:" + file, "{} was saved by a newer version of Just Enough Structures (format {}), so this one leaves it as it is",
                    file, of(json));
            throw new IOException(String.valueOf(file.getFileName()));
        }
    }

    /** The same object with this version's format first, for writing. */
    public static JsonObject stamped(JsonObject json) {
        JsonObject out = new JsonObject();
        out.addProperty(KEY, CURRENT);
        json.entrySet().forEach(e -> {
            if (!e.getKey().equals(KEY)) {
                out.add(e.getKey(), e.getValue());
            }
        });
        return out;
    }
}
