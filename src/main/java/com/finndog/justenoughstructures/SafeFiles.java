package com.finndog.justenoughstructures;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes files so that a crash, a power cut or a full disk part way through leaves the file as it
 * was, rather than empty or half written. The new contents go to a file beside it first, which then
 * takes its place in one go.
 */
public final class SafeFiles {
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
                // On the disk before it takes the old file's place, so a power cut can't leave it empty.
                channel.force(true);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
