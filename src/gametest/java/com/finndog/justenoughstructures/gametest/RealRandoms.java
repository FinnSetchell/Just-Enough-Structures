package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.LevelRandom;
import com.finndog.justenoughstructures.capture.StructureCapture;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.util.RandomSource;

/**
 * Notices structure code drawing from a real world's random while a capture places it. A capture
 * runs off the server thread, and two threads drawing from one random at once crashes the game.
 */
public final class RealRandoms {
    private static final Map<String, Integer> SEEN = new ConcurrentHashMap<>();

    private RealRandoms() {
    }

    public static void check(RandomSource random) {
        if (StructureCapture.sandboxStructures() == null || !(random instanceof LevelRandom level) || !level.justenoughstructures$isLevelRandom()) {
            return;
        }
        String where = Arrays.stream(new Throwable().getStackTrace()).skip(2).limit(12).map(StackTraceElement::toString)
                .collect(Collectors.joining("\n\tat "));
        if (SEEN.merge(where, 1, Integer::sum) == 1) {
            JustEnoughStructures.LOGGER.warn("A capture drew from a real world's random:\n\tat {}", where);
        }
    }

    /** How many different places have done it so far. */
    public static int places() {
        return SEEN.size();
    }
}
