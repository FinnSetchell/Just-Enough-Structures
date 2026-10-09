package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Memory;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;

/**
 * Each structure's first view, kept on disk just as players are sent it, so it's there straight away
 * for everyone and after a restart. Only the first layout is kept: New layout makes a fresh one each
 * time. There's a folder for each setup, so a view is never shown for mods, datapacks, Pack tools
 * changes or a world it wasn't made with, and only the few most recently used folders are kept.
 */
public final class SavedPreviews {
    /** Goes up whenever what's saved changes shape, so old folders are never read. */
    private static final String FORMAT = "1";
    private static final int KEPT_FOLDERS = 3;
    private static final int MAGIC = 0x4A455331;
    /** What else decides how a structure looks, besides the loot index's sources: its biomes, and its notes file. */
    private static final List<String> SOURCES = List.of("worldgen/biome", "tags/worldgen/biome", "justenoughstructures/structures");
    private static final long MEMORY_WAIT_MILLIS = 5_000L;

    // Reading and saving get a thread of their own, so a saved view never waits behind a structure being made.
    private static final ExecutorService DISK = thread("Just Enough Structures saved previews");
    private static final ExecutorService FILLER = thread("Just Enough Structures filling saved previews");
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Path folder;
    // The generation the filler last started for, so it's only started once for each.
    private static int filled = -1;

    private SavedPreviews() {
    }

    private static ExecutorService thread(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /** Stops using saved views until the next {@link #use}, as after a /reload the setup may have changed. */
    public static synchronized void forget() {
        GENERATION.incrementAndGet();
        folder = null;
    }

    /**
     * Picks the folder for the server's setup now, once the loot index knows its fingerprint, which
     * is null if its sources couldn't be read. Server thread.
     */
    public static void use(MinecraftServer server, String fingerprint) {
        forget();
        int generation = GENERATION.get();
        if (fingerprint == null) {
            return;
        }
        ServerConfig.Settings settings = ServerConfig.get();
        ServerLevel overworld = server.overworld();
        // Structures that read the climate look different in another world, as can the ground.
        String world = overworld.getSeed() + "|" + overworld.getChunkSource().getGenerator().getClass().getName();
        DISK.execute(() -> {
            String key = keyFor(server, fingerprint, settings, world);
            if (key == null) {
                return;
            }
            Path dir = root().resolve(key);
            try {
                Files.createDirectories(dir);
                // Marks it as recently used, so it's kept over older ones.
                Files.setLastModifiedTime(dir, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException e) {
                JesLog.debug("Couldn't make the saved previews folder {}", dir, e);
                return;
            }
            synchronized (SavedPreviews.class) {
                if (generation != GENERATION.get()) {
                    return;
                }
                folder = dir;
            }
            tidy(dir);
            server.execute(() -> {
                if (LootIndexStore.ready()) {
                    fill(server);
                }
            });
        });
    }

    /** The folder saved views are read from and saved to now, or null while it's being worked out. */
    public static Path current() {
        return folder;
    }

    /** Reads a structure's saved first view on the saved previews' thread, and hands it, or null if there isn't one, to {@code then} there. */
    public static void load(Path dir, ResourceLocation id, Consumer<byte[]> then) {
        DISK.execute(() -> then.accept(read(dir, id)));
    }

    /** Saves a structure's first view, as players are sent it. */
    public static void save(Path dir, ResourceLocation id, byte[] payload) {
        DISK.execute(() -> write(dir, id, payload));
    }

    /** A structure as the loot index made it, which is its first view when made with its first seed: saved, so it's ready before anyone asks. */
    public static void offer(MinecraftServer server, ResourceLocation id, long seed, CaptureResult result) {
        offer(folder, server, id, seed, result);
    }

    /** The same, into a given folder. */
    public static void offer(Path dir, MinecraftServer server, ResourceLocation id, long seed, CaptureResult result) {
        if (dir == null || seed != StructureCapture.defaultSeed(id) || !result.succeeded() || ServerConfig.hides(id) || Files.exists(file(dir, id))) {
            return;
        }
        try {
            write(dir, id, payloadOf(server, id, seed, result));
        } catch (RuntimeException e) {
            JesLog.debug("Couldn't save the first view of {}", id, e);
        }
    }

    /**
     * On a dedicated server, once the loot index is ready: makes and saves every first view that
     * isn't saved yet, one structure at a time and stepping aside for any preview a player is
     * waiting on. A singleplayer game saves them as they're looked at instead. Server thread.
     */
    public static void fill(MinecraftServer server) {
        Path dir = folder;
        int generation = GENERATION.get();
        if (!server.isDedicatedServer() || dir == null || filled == generation) {
            return;
        }
        filled = generation;
        List<ResourceLocation> ids = server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .filter(id -> !ServerConfig.hides(id))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        BooleanSupplier cancelled = () -> generation != GENERATION.get() || !server.isRunning();
        FILLER.execute(() -> {
            long started = System.nanoTime();
            int made = 0;
            for (ResourceLocation id : ids) {
                if (cancelled.getAsBoolean() || !waitForMemory(cancelled)) {
                    return;
                }
                if (Files.exists(file(dir, id))) {
                    continue;
                }
                long seed = StructureCapture.defaultSeed(id);
                try {
                    CaptureResult result = StructureCapture.captureInBackground(server, id, seed, cancelled);
                    if (result == null) {
                        return;
                    }
                    if (result.succeeded()) {
                        write(dir, id, payloadOf(server, id, seed, result));
                        made++;
                    }
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    JesLog.debug("Couldn't make {} to save its first view", id, e);
                }
            }
            if (made > 0) {
                JesLog.debug("Saved the first views of {} structures in {} s", made, (System.nanoTime() - started) / 1_000_000_000L);
            }
        });
    }

    /** A structure's capture as players are sent it. */
    static byte[] payloadOf(MinecraftServer server, ResourceLocation id, long seed, CaptureResult result) {
        CaptureResult sent = JesServer.forPlayers(id, result);
        return Blobs.deflate(Blobs.toBytes(server.registryAccess(), buf -> Codecs.writeCapture(buf, id, seed, sent)));
    }

    /**
     * A name for everything a first view is made from: the loot index's fingerprint, the biomes and
     * notes files, the world, Pack tools' container and spawner changes, and the server settings that
     * change what's sent. Null if any of it couldn't be read.
     */
    private static String keyFor(MinecraftServer server, String fingerprint, ServerConfig.Settings settings, String world) {
        try {
            Hasher hasher = Hashing.sha256().newHasher();
            hasher.putString(FORMAT + "|" + JesNetwork.PROTOCOL + "|" + fingerprint + "|" + world + "|"
                    + settings.showLootLocations() + "|" + settings.containerChanges() + "|", StandardCharsets.UTF_8);
            for (String changes : List.of("containers.json", "spawners.json")) {
                Path file = LootOverrides.folder().resolve(changes);
                hasher.putString(changes, StandardCharsets.UTF_8);
                if (Files.exists(file)) {
                    hasher.putBytes(Files.readAllBytes(file));
                }
            }
            for (String source : SOURCES) {
                for (Map.Entry<ResourceLocation, Resource> file : new TreeMap<>(server.getResourceManager().listResources(source, path -> true)).entrySet()) {
                    hasher.putString(file.getKey().toString(), StandardCharsets.UTF_8);
                    try (InputStream in = file.getValue().open()) {
                        hasher.putBytes(in.readAllBytes());
                    }
                }
            }
            return hasher.hash().toString().substring(0, 24);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't work out where to save first views, so they won't be", e);
            return null;
        }
    }

    private static Path root() {
        return JustEnoughStructures.cacheDir().resolve("previews");
    }

    private static Path file(Path dir, ResourceLocation id) {
        return dir.resolve(Hashing.sha256().hashString(id.toString(), StandardCharsets.UTF_8).toString().substring(0, 24) + ".bin");
    }

    private static byte[] read(Path dir, ResourceLocation id) {
        Path file = file(dir, id);
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (in.readInt() != MAGIC || !in.readUTF().equals(id.toString())) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the saved first view of {}", id, e);
            return null;
        }
    }

    private static void write(Path dir, ResourceLocation id, byte[] payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 64);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeUTF(id.toString());
                out.write(payload);
            }
            Files.createDirectories(dir);
            SafeFiles.write(file(dir, id), bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't save the first view of {}", id, e);
        }
    }

    /** Deletes all but the few most recently used folders. */
    private static void tidy(Path keep) {
        try (Stream<Path> list = Files.list(root())) {
            List<Path> folders = list.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
            for (Path old : folders.subList(Math.min(KEPT_FOLDERS, folders.size()), folders.size())) {
                if (!old.equals(keep)) {
                    delete(old);
                }
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't tidy the saved previews", e);
        }
    }

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Waits while the server is short on memory, as the loot index does. False if cancelled meanwhile. */
    private static boolean waitForMemory(BooleanSupplier cancelled) {
        while (Memory.low()) {
            if (cancelled.getAsBoolean()) {
                return false;
            }
            try {
                Thread.sleep(MEMORY_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
