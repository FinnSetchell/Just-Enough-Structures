package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.CacheFiles;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Threads;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * Structures a server has sent, kept on the player's disk. Each structure's first view is kept, so
 * opening it again only needs the server to say it hasn't changed rather than send it all again; when
 * it has changed, the new one takes the old one's place. So is the lighter copy its list picture is
 * drawn from, so the picture can be drawn again, at a new GUI scale or with other resource packs,
 * without asking the server at all, for as long as the server has that version of the structure. Each
 * server's are kept in a folder named after its fingerprint and the way structures are sent, and only
 * the few most recently used folders are kept.
 */
public final class KeptPreviews {
    private static final int KEPT_FOLDERS = 3;
    private static final int MAGIC = 0x4A45534B;

    private static final ExecutorService DISK = Threads.single("Just Enough Structures kept previews");
    private static final Object LOCK = new Object();
    /** Each kept first view, by the start of its file's name. */
    private static final Map<String, Copy> VIEWS = new HashMap<>();
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

    /** What's kept for a structure, each in a file of its own. */
    private enum Kind {
        /** Its first view, matched with what the server would send by the {@link Blobs#hash} of it. */
        VIEW("first view"),
        /** The copy its list picture is drawn from, used while the server has the version of the structure it's of. */
        PICTURE("picture's copy");

        private final String what;

        Kind(String what) {
            this.what = what;
        }

        Map<String, Copy> kept() {
            return this == VIEW ? VIEWS : PICTURES;
        }

        Path file(Path dir, String stem, Copy copy) {
            String hash = Long.toHexString(copy.hash());
            return dir.resolve(this == VIEW ? stem + "." + hash + ".bin" : stem + "." + copy.version() + "." + hash + ".pic");
        }
    }

    /** One kept file: which version of the structure it's of, for a picture's copy, and the {@link Blobs#hash} of what's in it. */
    private record Copy(String version, long hash) {
    }

    /**
     * When the structure list arrives: keeps structures in the folder for the server's fingerprint,
     * or keeps none, without a fingerprint or with {@code keep} false.
     */
    public static void use(String fingerprint, boolean keep) {
        // A version of the mod that sends structures another way starts afresh, though its fingerprint may be the same.
        Path dir = fingerprint == null || !keep ? null : root().resolve(name(fingerprint + "|" + JesNetwork.PROTOCOL));
        int now;
        synchronized (LOCK) {
            if (Objects.equals(dir, wanted)) {
                return;
            }
            wanted = dir;
            now = ++generation;
            folder = null;
            VIEWS.clear();
            PICTURES.clear();
            VERSIONS.clear();
        }
        if (dir == null) {
            return;
        }
        DISK.execute(() -> {
            Map<String, Copy> views = new HashMap<>();
            Map<String, Copy> pictures = new HashMap<>();
            try {
                Files.createDirectories(dir);
                CacheFiles.markUsed(dir);
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        String[] parts = file.getFileName().toString().split("\\.");
                        try {
                            if (parts.length == 3 && parts[2].equals("bin")) {
                                views.put(parts[0], new Copy(null, Long.parseUnsignedLong(parts[1], 16)));
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
                VIEWS.putAll(views);
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

    /** The {@link Blobs#hash} of the first view kept for a structure, or 0 if there isn't one. */
    public static long kept(ResourceLocation id) {
        Copy copy = current(Kind.VIEW, name(id.toString()), null);
        return copy == null ? 0 : copy.hash();
    }

    /** Whether the copy a structure's list picture is drawn from is kept for this version of it. */
    public static boolean hasPicture(ResourceLocation id, String version) {
        return current(Kind.PICTURE, name(id.toString()), version) != null;
    }

    /** Keeps a structure's first view, as the server sent it, in place of any kept before. */
    public static void keep(ResourceLocation id, byte[] payload) {
        keep(Kind.VIEW, id, null, payload);
    }

    /** Keeps the copy a structure's list picture is drawn from, of the given version of it, in place of any kept before. */
    public static void keepPicture(ResourceLocation id, String version, byte[] payload) {
        if (version != null) {
            keep(Kind.PICTURE, id, version, payload);
        }
    }

    /**
     * Reads a structure's kept first view and hands it to {@code then} on the kept previews' own
     * thread. One that's gone or been damaged since is forgotten, and null handed over instead.
     */
    public static void read(ResourceLocation id, Consumer<byte[]> then) {
        read(Kind.VIEW, id, null, then);
    }

    /** The same for the copy a structure's list picture is drawn from, which is null too when it's of another version. */
    public static void readPicture(ResourceLocation id, String version, Consumer<byte[]> then) {
        read(Kind.PICTURE, id, version, then);
    }

    /** Forgets a structure's kept first view, when what's in it can't be used. */
    public static void forget(ResourceLocation id) {
        forget(Kind.VIEW, id);
    }

    /** Forgets the copy a structure's list picture is drawn from, when what's in it can't be used. */
    public static void forgetPicture(ResourceLocation id) {
        forget(Kind.PICTURE, id);
    }

    /** What's kept of a kind under a stem, of the version given when it's a picture's copy, or null. */
    private static Copy current(Kind kind, String stem, String version) {
        synchronized (LOCK) {
            Copy copy = folder == null ? null : kind.kept().get(stem);
            return copy == null || kind == Kind.PICTURE && !copy.version().equals(version) ? null : copy;
        }
    }

    private static void keep(Kind kind, ResourceLocation id, String version, byte[] payload) {
        String stem = name(id.toString());
        Copy copy = new Copy(version, Blobs.hash(payload));
        Path dir;
        Copy before;
        synchronized (LOCK) {
            dir = folder;
            if (dir == null) {
                return;
            }
            before = kind.kept().put(stem, copy);
        }
        if (copy.equals(before)) {
            return;
        }
        DISK.execute(() -> {
            try {
                if (before != null) {
                    Files.deleteIfExists(kind.file(dir, stem, before));
                }
                CacheFiles.write(kind.file(dir, stem, copy), MAGIC, id, payload);
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't keep the {} of {}", kind.what, id, e);
                forget(kind, dir, stem, copy);
            }
        });
    }

    private static void read(Kind kind, ResourceLocation id, String version, Consumer<byte[]> then) {
        String stem = name(id.toString());
        Path dir;
        Copy copy;
        synchronized (LOCK) {
            dir = folder;
            copy = current(kind, stem, version);
        }
        DISK.execute(() -> {
            byte[] payload = copy == null ? null : read(kind.file(dir, stem, copy), id);
            if (copy != null && (payload == null || Blobs.hash(payload) != copy.hash())) {
                forget(kind, dir, stem, copy);
                payload = null;
            }
            then.accept(payload);
        });
    }

    private static void forget(Kind kind, ResourceLocation id) {
        String stem = name(id.toString());
        Path dir;
        Copy copy;
        synchronized (LOCK) {
            dir = folder;
            copy = dir == null ? null : kind.kept().get(stem);
        }
        if (copy != null) {
            DISK.execute(() -> forget(kind, dir, stem, copy));
        }
    }

    private static void forget(Kind kind, Path dir, String stem, Copy copy) {
        synchronized (LOCK) {
            if (dir.equals(folder)) {
                kind.kept().remove(stem, copy);
            }
        }
        try {
            Files.deleteIfExists(kind.file(dir, stem, copy));
        } catch (IOException e) {
            JesLog.debug("Couldn't delete a kept {} in {}", kind.what, dir, e);
        }
    }

    private static byte[] read(Path file, ResourceLocation id) {
        try {
            return CacheFiles.read(file, MAGIC, id);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the kept structure in {}", file, e);
            return null;
        }
    }

    private static Path root() {
        return JustEnoughStructures.cacheDir().resolve("kept-previews");
    }

    /** A short name for a file or folder, the same each time for the same text. */
    private static String name(String text) {
        return Hashing.sha256().hashString(text, StandardCharsets.UTF_8).toString().substring(0, 24);
    }

    /** Deletes all but the few most recently used folders. */
    private static void tidy(Path keep) {
        try {
            CacheFiles.keepNewest(root(), Files::isDirectory, KEPT_FOLDERS, keep);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't tidy the kept previews", e);
        }
    }
}
