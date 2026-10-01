package com.finndog.justenoughstructures;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * Keeps the game's log for problems people should hear about. Everything routine goes to the mod's
 * own debug log instead, which is only written when the game is started with
 * {@code -Djustenoughstructures.debug=true}. Nothing in here ever throws: a log that can't be
 * written is not worth stopping the game over.
 */
public final class JesLog {
    public static final String PROPERTY = "justenoughstructures.debug";

    private static final boolean ENABLED = readProperty();
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    /** How many quiet scopes this thread is inside. */
    private static final ThreadLocal<int[]> QUIET = new ThreadLocal<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Object LOCK = new Object();

    // Captures, the loot index, the server and render threads and the IO pool all write, so these
    // are only touched holding LOCK.
    private static Writer writer;
    private static Path file;
    private static boolean opened;
    /** Set when the file couldn't be written, which turns the debug log off for the rest of the run. */
    private static volatile boolean broken;

    private static boolean installed;

    private JesLog() {
    }

    private static boolean readProperty() {
        try {
            return Boolean.getBoolean(PROPERTY);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Called once as the mod starts, after the game's folder is known. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            // Kept in its own class so Log4j's core is only touched here, where a missing or
            // different Log4j can't stop the mod loading.
            QuietFilter.install();
        } catch (Throwable t) {
            debug("Couldn't quieten other code's warnings during captures", t);
        }
        if (!ENABLED) {
            return;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(JesLog::close, "Just Enough Structures debug log"));
            Map<String, String> versions = JustEnoughStructures.modVersions();
            debug("Just Enough Structures {} on Minecraft {}, Java {}", versions.get(JustEnoughStructures.MOD_ID), versions.get("minecraft"),
                    System.getProperty("java.version"));
            JustEnoughStructures.LOGGER.info("Debug log on: {}", JustEnoughStructures.debugLogFile());
        } catch (Throwable t) {
            // Only the header and the pointer to the file are lost.
        }
    }

    /** Writes to the debug log when it's on. Takes {} and a trailing exception like the logger does. */
    public static void debug(String format, Object... args) {
        if (!ENABLED || broken) {
            return;
        }
        try {
            FormattingTuple message = MessageFormatter.arrayFormat(format, args);
            write(message.getMessage(), message.getThrowable());
        } catch (Throwable t) {
            // Never let logging break what's being logged.
        }
    }

    /** A warning in the game's log the first time {@code key} comes up, and in the debug log after that. */
    public static void warnOnce(String key, String format, Object... args) {
        try {
            if (SEEN.add(key)) {
                JustEnoughStructures.LOGGER.warn(format, args);
            } else {
                debug(format, args);
            }
        } catch (Throwable t) {
            // As above.
        }
    }

    /** The same as {@link #warnOnce}, as an error. */
    public static void errorOnce(String key, String format, Object... args) {
        try {
            if (SEEN.add(key)) {
                JustEnoughStructures.LOGGER.error(format, args);
            } else {
                debug(format, args);
            }
        } catch (Throwable t) {
            // As above.
        }
    }

    /**
     * Runs {@code action} with other code's log lines on this thread kept out of the game's log.
     * Captures load every mod's structure pieces and loot, and vanilla complains about some of them
     * thousands of times on our threads, where it looks like it's us.
     */
    public static void quietly(Runnable action) {
        int[] depth = enterQuiet();
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    public static <T> T quietly(Supplier<T> action) {
        int[] depth = enterQuiet();
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    private static int[] enterQuiet() {
        int[] depth = QUIET.get();
        if (depth == null) {
            depth = new int[1];
            QUIET.set(depth);
        }
        depth[0]++;
        return depth;
    }

    static boolean quiet() {
        int[] depth = QUIET.get();
        return depth != null && depth[0] > 0;
    }

    public static void close() {
        synchronized (LOCK) {
            closeWriter();
        }
    }

    private static void write(String message, Throwable thrown) {
        Exception failure = null;
        Path failed = null;
        synchronized (LOCK) {
            if (broken) {
                return;
            }
            try {
                if (writer == null) {
                    open();
                }
                writer.write("[" + LocalTime.now().format(TIME) + "] [" + Thread.currentThread().getName() + "] " + message);
                writer.write(System.lineSeparator());
                if (thrown != null) {
                    PrintWriter out = new PrintWriter(writer);
                    thrown.printStackTrace(out);
                    out.flush();
                }
                writer.flush();
            } catch (IOException | RuntimeException e) {
                broken = true;
                closeWriter();
                failure = e;
                failed = file;
            }
        }
        // Outside the lock, so the game's logging never waits on ours.
        if (failure != null) {
            JustEnoughStructures.LOGGER.warn("Couldn't write the debug log {}: {}", failed, failure.toString());
        }
    }

    private static void open() throws IOException {
        file = JustEnoughStructures.debugLogFile();
        Files.createDirectories(file.getParent());
        if (opened) {
            // Something still logging after the shutdown hook closed it.
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return;
        }
        opened = true;
        // Keep the last run's log, which matters most when that run crashed.
        if (Files.exists(file)) {
            try {
                Files.move(file, file.resolveSibling("justenoughstructures-debug.old.log"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Then it's overwritten, which is no worse than not keeping it.
            }
        }
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException | RuntimeException e) {
            // Nothing more can be done with it.
        }
        writer = null;
    }
}
