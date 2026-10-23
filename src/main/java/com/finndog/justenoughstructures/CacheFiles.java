package com.finndog.justenoughstructures;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/** Files in the {@link JustEnoughStructures#cacheDir cache folder}, where only the few most recently used of each kind are kept. */
public final class CacheFiles {
    private CacheFiles() {
    }

    /** Writes {@code payload} behind a header of {@code magic} and {@code id}, so {@link #read} rejects a file of another kind or for another id. */
    public static void write(Path file, int magic, ResourceLocation id, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(magic);
            out.writeUTF(id.toString());
            out.write(payload);
        }
        SafeFiles.write(file, bytes.toByteArray());
    }

    /** Reads what {@link #write} saved for {@code id}, or null when the file is of another kind or for another id. */
    public static byte[] read(Path file, int magic, ResourceLocation id) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            return in.readInt() == magic && in.readUTF().equals(id.toString()) ? in.readAllBytes() : null;
        }
    }

    /** Sets the modified time to now, so {@link #keepNewest} keeps it over older ones. */
    public static void markUsed(Path path) throws IOException {
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
    }

    /**
     * Deletes all but the {@code count} most recently used files or folders in {@code dir} that match
     * {@code which}, never deleting {@code keep}. A folder is deleted with its contents.
     */
    public static void keepNewest(Path dir, Predicate<Path> which, int count, Path keep) throws IOException {
        List<Path> found;
        try (Stream<Path> list = Files.list(dir)) {
            found = list.filter(which)
                    .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
        }
        for (Path old : found.subList(Math.min(count, found.size()), found.size())) {
            if (!old.equals(keep)) {
                try (Stream<Path> files = Files.walk(old)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }
}
