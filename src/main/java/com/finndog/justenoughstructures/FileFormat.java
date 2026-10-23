package com.finndog.justenoughstructures;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The format number in the JSON files JES keeps its settings and changes in. A newer JES can tell
 * what it's reading, and an older one leaves a newer file alone. A file without one is format 1.
 * Loot tables and lang files written into packs, and the binary caches, don't carry it.
 */
public final class FileFormat {
    public static final String KEY = "format";
    /** Bump when a file this version writes couldn't be read the same way by the previous version. */
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

    /** Whether a newer JES saved this, so this version must neither read nor overwrite it. */
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
