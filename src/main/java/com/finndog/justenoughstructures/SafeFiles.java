package com.finndog.justenoughstructures;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes files so that a crash, a power cut or a full disk partway through leaves the file as it was,
 * not empty or half written. The new contents go to a file beside it first, which then replaces it in
 * one step.
 */
public final class SafeFiles {
    /** How many times to try moving the new file into place, as Windows can refuse it for a moment. */
    private static final int MOVE_ATTEMPTS = 10;

    private SafeFiles() {
    }

    public static void write(Path file, String text) throws IOException {
        write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path file, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                // Flushed to disk before it replaces the old file, so a power cut can't leave it empty.
                channel.force(true);
            }
            move(temp, file);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Windows refuses to move a file over another while something else has either open, which a virus
     * scanner does briefly with a newly written file, so the move is retried a few times before giving
     * up, as the game does with its own saves.
     */
    private static void move(Path temp, Path file) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (FileSystemException e) {
                // Only retry an access refusal, not a missing file or other errors.
                boolean refused = e instanceof AccessDeniedException || e.getClass() == FileSystemException.class;
                if (!refused || attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(attempt * 10L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
