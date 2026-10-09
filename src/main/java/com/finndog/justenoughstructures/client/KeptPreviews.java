package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.network.Blobs;
import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * Structures a server has sent, kept on the player's disk. Each structure's first view is kept, so
 * opening it again only needs the server to say it hasn't changed rather than send it all again; when
 * it has changed, the new one takes the old one's place. So is the lighter copy its list picture is
 * drawn from, so the picture can be drawn again, at a new GUI scale or with other resource packs,
 * without asking the server at all, for as long as the server has that version of the structure. Each
 * server's are kept in a folder named after its fingerprint, and only the few most recently used
 * folders are kept.
 */
public final class KeptPreviews {
    private static final int KEPT_FOLDERS = 3;
    private static final int MAGIC = 0x4A45534B;

    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Just Enough Structures kept previews");
        t.setDaemon(true);
        return t;
    });
    private static final Object LOCK = new Object();
    /** Each kept first view, by the start of its file's name, and the {@link Blobs#hash} of what's in it. */
    private static final Map<String, Long> KEPT = new HashMap<>();
    /** Each kept picture's copy, by the start of its file's name. */
    private static final Map<String, Copy> PICTURES = new HashMap<>();
    /** Which version of each structure the server has now, from the structure list. */
    private static final Map<ResourceLocation, String> VERSIONS = new HashMap<>();
    private static int generation;
    /** The folder for the server joined now, or null when nothing's kept for it. */
    private static Path wanted;
    /** The same, once what's in it is known. */
    private static Path folder;

    private KeptPreviews() {
    }

    /** A kept picture's copy: which version of the structure it's of, and the {@link Blobs#hash} of what's in it. */
    private record Copy(String version, long hash) {
    }

    /**
     * When the structure list arrives: keeps structures in the folder for the server's fingerprint,
     * or keeps none, without a fingerprint or with {@code keep} false.
     */
    public static void use(String fingerprint, boolean keep) {
        Path dir = fingerprint == null || !keep ? null : root().resolve(name(fingerprint));
        int now;
        synchronized (LOCK) {
            if (Objects.equals(dir, wanted)) {
                return;
            }
            wanted = dir;
            now = ++generation;
            folder = null;
            KEPT.clear();
            PICTURES.clear();
            VERSIONS.clear();
        }
        if (dir == null) {
            return;
        }
        DISK.execute(() -> {
            Map<String, Long> views = new HashMap<>();
            Map<String, Copy> pictures = new HashMap<>();
            try {
                Files.createDirectories(dir);
                // Marks it as recently used, so it's kept over older ones.
                Files.setLastModifiedTime(dir, FileTime.fromMillis(System.currentTimeMillis()));
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        String[] parts = file.getFileName().toString().split("\\.");
                        try {
                            if (parts.length == 3 && parts[2].equals("bin")) {
                                views.put(parts[0], Long.parseUnsignedLong(parts[1], 16));
                            } else if (parts.length == 4 && parts[3].equals("pic")) {
                                pictures.put(parts[0], new Copy(parts[1], Long.parseUnsignedLong(parts[2], 16)));
                            }
                        } catch (NumberFormatException e) {
                            // Not one of ours.
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't use the kept previews in {}", dir, e);
                return;
            }
            synchronized (LOCK) {
                if (now != generation) {
                    return;
                }
                KEPT.putAll(views);
                PICTURES.putAll(pictures);
                folder = dir;
            }
            JesLog.debug("Keeping this server's structures in {}, which has {} first views and {} pictures' copies already", dir, views.size(),
                    pictures.size());
            tidy(dir);
        });
    }

    /** When the structure list arrives: which version of each structure the server has now, so a picture's copy of an older one isn't used. */
    public static void structures(List<StructureCatalog.Entry> entries) {
        Map<ResourceLocation, String> versions = new HashMap<>();
        for (StructureCatalog.Entry entry : entries) {
            versions.put(entry.id(), Hashing.murmur3_128().hashString(String.valueOf(entry.definition()), StandardCharsets.UTF_8).toString().substring(0, 16));
        }
        synchronized (LOCK) {
            VERSIONS.clear();
            VERSIONS.putAll(versions);
        }
    }

    /** Whether structures are being kept for the server joined now. */
    public static boolean inUse() {
        synchronized (LOCK) {
            return folder != null;
        }
    }

    /** Which version of a structure the server has now, or null before its structure list says. */
    public static String version(ResourceLocation id) {
        synchronized (LOCK) {
            return VERSIONS.get(id);
        }
    }

    // ------------------------------------------------------------------ first views

    /** The {@link Blobs#hash} of the first view kept for a structure, or 0 if there isn't one. */
    public static long kept(ResourceLocation id) {
        String stem = name(id.toString());
        synchronized (LOCK) {
            Long hash = folder == null ? null : KEPT.get(stem);
            return hash == null ? 0 : hash;
        }
    }

    /** Keeps a structure's first view, as the server sent it, in place of any kept before. */
    public static void keep(ResourceLocation id, byte[] payload) {
        String stem = name(id.toString());
        long hash = Blobs.hash(payload);
        Path dir;
        Long before;
        synchronized (LOCK) {
            dir = folder;
            if (dir == null) {
                return;
            }
            before = KEPT.put(stem, hash);
        }
        if (before != null && before == hash) {
            return;
        }
        DISK.execute(() -> {
            try {
                if (before != null) {
                    Files.deleteIfExists(file(dir, stem, before));
                }
                write(file(dir, stem, hash), id, payload);
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't keep the preview of {}", id, e);
                forget(dir, stem, hash);
            }
        });
    }

    /**
     * Reads a structure's kept first view and hands it to {@code then} on the kept previews' own
     * thread. One that's gone or been damaged since is forgotten, and null handed over instead.
     */
    public static void read(ResourceLocation id, Consumer<byte[]> then) {
        String stem = name(id.toString());
        Path dir;
        Long hash;
        synchronized (LOCK) {
            dir = folder;
            hash = dir == null ? null : KEPT.get(stem);
        }
        DISK.execute(() -> {
            byte[] payload = hash == null ? null : read(file(dir, stem, hash), id);
            if (hash != null && (payload == null || Blobs.hash(payload) != hash)) {
                forget(dir, stem, hash);
                payload = null;
            }
            then.accept(payload);
        });
    }

    /** Forgets a structure's kept first view, when what's in it can't be used. */
    public static void forget(ResourceLocation id) {
        String stem = name(id.toString());
        Path dir;
        Long hash;
        synchronized (LOCK) {
            dir = folder;
            hash = dir == null ? null : KEPT.get(stem);
        }
        if (hash != null) {
            DISK.execute(() -> forget(dir, stem, hash));
        }
    }

    private static void forget(Path dir, String stem, long hash) {
        synchronized (LOCK) {
            if (dir.equals(folder)) {
                KEPT.remove(stem, hash);
            }
        }
        delete(file(dir, stem, hash));
    }

    // ------------------------------------------------------------------ pictures' copies

    /** Whether the copy a structure's list picture is drawn from is kept for this version of it. */
    public static boolean hasPicture(ResourceLocation id, String version) {
        String stem = name(id.toString());
        synchronized (LOCK) {
            Copy copy = folder == null ? null : PICTURES.get(stem);
            return copy != null && copy.version().equals(version);
        }
    }

    /** Keeps the copy a structure's list picture is drawn from, of the given version of it, in place of any kept before. */
    public static void keepPicture(ResourceLocation id, String version, byte[] payload) {
        if (version == null) {
            return;
        }
        String stem = name(id.toString());
        Copy copy = new Copy(version, Blobs.hash(payload));
        Path dir;
        Copy before;
        synchronized (LOCK) {
            dir = folder;
            if (dir == null) {
                return;
            }
            before = PICTURES.put(stem, copy);
        }
        if (copy.equals(before)) {
            return;
        }
        DISK.execute(() -> {
            try {
                if (before != null) {
                    Files.deleteIfExists(pictureFile(dir, stem, before));
                }
                write(pictureFile(dir, stem, copy), id, payload);
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't keep the picture's copy of {}", id, e);
                forgetPicture(dir, stem, copy);
            }
        });
    }

    /**
     * Reads the copy a structure's list picture is drawn from and hands it to {@code then} on the kept
     * previews' own thread, or null when there's none of this version of it. One that's gone or been
     * damaged since is forgotten.
     */
    public static void readPicture(ResourceLocation id, String version, Consumer<byte[]> then) {
        String stem = name(id.toString());
        Path dir;
        Copy kept;
        synchronized (LOCK) {
            dir = folder;
            kept = dir == null ? null : PICTURES.get(stem);
        }
        Copy copy = kept != null && kept.version().equals(version) ? kept : null;
        DISK.execute(() -> {
            byte[] payload = copy == null ? null : read(pictureFile(dir, stem, copy), id);
            if (copy != null && (payload == null || Blobs.hash(payload) != copy.hash())) {
                forgetPicture(dir, stem, copy);
                payload = null;
            }
            then.accept(payload);
        });
    }

    /** Forgets the copy a structure's list picture is drawn from, when what's in it can't be used. */
    public static void forgetPicture(ResourceLocation id) {
        String stem = name(id.toString());
        Path dir;
        Copy copy;
        synchronized (LOCK) {
            dir = folder;
            copy = dir == null ? null : PICTURES.get(stem);
        }
        if (copy != null) {
            DISK.execute(() -> forgetPicture(dir, stem, copy));
        }
    }

    private static void forgetPicture(Path dir, String stem, Copy copy) {
        synchronized (LOCK) {
            if (dir.equals(folder)) {
                PICTURES.remove(stem, copy);
            }
        }
        delete(pictureFile(dir, stem, copy));
    }

    // ------------------------------------------------------------------ files

    private static void write(Path file, ResourceLocation id, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeUTF(id.toString());
            out.write(payload);
        }
        SafeFiles.write(file, bytes.toByteArray());
    }

    private static byte[] read(Path file, ResourceLocation id) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != MAGIC || !in.readUTF().equals(id.toString())) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the kept structure in {}", file, e);
            return null;
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            JesLog.debug("Couldn't delete the kept structure in {}", file, e);
        }
    }

    private static Path root() {
        return JustEnoughStructures.cacheDir().resolve("kept-previews");
    }

    /** A short name for a file or folder, the same each time for the same text. */
    private static String name(String text) {
        return Hashing.sha256().hashString(text, StandardCharsets.UTF_8).toString().substring(0, 24);
    }

    private static Path file(Path dir, String stem, long hash) {
        return dir.resolve(stem + "." + Long.toHexString(hash) + ".bin");
    }

    private static Path pictureFile(Path dir, String stem, Copy copy) {
        return dir.resolve(stem + "." + copy.version() + "." + Long.toHexString(copy.hash()) + ".pic");
    }

    /** Deletes all but the few most recently used folders. */
    private static void tidy(Path keep) {
        try (Stream<Path> list = Files.list(root())) {
            List<Path> folders = list.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
            for (Path old : folders.subList(Math.min(KEPT_FOLDERS, folders.size()), folders.size())) {
                if (!old.equals(keep)) {
                    try (Stream<Path> files = Files.walk(old)) {
                        for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't tidy the kept previews", e);
        }
    }
}
