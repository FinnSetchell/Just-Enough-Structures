package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.CacheFiles;
import com.finndog.justenoughstructures.Folders;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.Threads;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The loot index, saved to disk so it's only built again when something it comes from changes.
 *
 * <p>The slow half, which loot tables each structure uses, is saved as a {@link StructureScan}
 * named after a fingerprint of the installed mods and every structure, pool, processor and
 * template file. Loot tables don't change which tables a structure uses, so they're not in it:
 * what each table can give is read from the tables after every start and /reload, which is quick.
 * Containers changed in the browser only send the structures that place those templates through
 * again, as do structures that failed for a reason that may have passed, like the server running
 * short on memory. Anything else that changes means generating every structure again, as does
 * anything that goes wrong along the way.
 *
 * <p>All of this runs in the background. If a saved scan matches, the index is ready in moments.
 * Otherwise a dedicated server builds it straight away and singleplayer waits until someone wants
 * it, and while it's rebuilt the index from before stays in use. Nothing here holds up the server
 * starting or players joining.
 *
 * <p>A build with no index from before to go on using hands one out early: what every structure's
 * template pools hold and the whole of the structures without pools, which is most of the loot and
 * quick to find. The rest, one generation of each structure with pools, takes many times longer in
 * a big pack. The early index says it's early, players are told how far the rest has got, and they
 * get the finished index when it's done.
 */
public final class LootIndexStore {
    /**
     * Goes up whenever what's saved changes shape, or what goes in it does, so old files are never
     * read as new ones. 3: vaults and what trial spawners drop. 4: structures to try again.
     */
    private static final String FORMAT = "4";
    private static final int KEPT_FILES = 4;
    private static final List<String> SOURCES = List.of(Folders.STRUCTURES, "worldgen/structure", "worldgen/template_pool",
            "worldgen/processor_list");

    // The index captures every structure, so it gets its own thread and never holds up previews.
    private static final ExecutorService WORKER = Threads.single("Just Enough Structures loot index", Thread.MIN_PRIORITY);
    private static final AtomicInteger GENERATION = new AtomicInteger();
    // Players who've asked for the index. They're sent the new one whenever it changes.
    private static final Set<UUID> WAITING = ConcurrentHashMap.newKeySet();

    // Changed on the server thread only.
    private static StructureScan scan;
    private static LootIndex index;
    private static byte[] payload;
    private static String fingerprint;
    private static boolean checking;
    private static boolean building;
    // Building failed, and won't be tried again until the next /reload.
    private static boolean broken;
    private static boolean wanted;
    // The index players have is an early one, with more to come. Read on the index's thread too.
    private static volatile boolean partial;
    private static volatile long progressSaid;
    private static volatile int done;
    private static volatile int total;
    // For the debug log: what each source file hashed to last time, to say which ones changed.
    private static volatile Map<String, HashCode> lastFiles = Map.of();
    // For tests: told how many structures are done, each time another one is.
    private static volatile IntConsumer progressWatcher = d -> {
    };

    private LootIndexStore() {
    }

    /**
     * The fingerprint of what the server's structures are made from, or null while it's still being
     * worked out after the server started or reloaded. Server thread only.
     */
    public static String knownFingerprint() {
        return checking ? null : fingerprint;
    }

    /** Whether the index is built and up to date, so nothing's generating structures for it. Server thread only. */
    public static boolean ready() {
        return !checking && !building && index != null;
    }

    /** When the server has started and after /reload: checks whether the index still holds. */
    public static void refresh(MinecraftServer server) {
        int generation = GENERATION.incrementAndGet();
        checking = true;
        building = false;
        broken = false;
        String known = scan == null ? null : fingerprint;
        StructureScan knownScan = scan;
        Path dir = folder();
        inBackground(server, generation, () -> {
            long started = System.nanoTime();
            String current = fingerprintOf(server);
            if (generation != GENERATION.get()) {
                return;
            }
            StructureScan base = current == null ? null : current.equals(known) ? knownScan : read(dir, current);
            StructureScan updated = null;
            LootIndex updatedIndex = null;
            if (base != null) {
                // Only structures placing templates whose containers were changed are generated
                // again; what each table can give is read from the loot tables as they are now.
                try {
                    updated = LootIndex.update(server, base, () -> generation != GENERATION.get() || !server.isRunning());
                    if (updated == null) {
                        return;
                    }
                    updatedIndex = LootIndex.of(server, updated);
                    if (updated != base && current != null) {
                        write(dir, current, updated);
                    }
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    JesLog.debug("Couldn't bring the saved loot index up to date, so it's built again", e);
                    updated = null;
                    updatedIndex = null;
                }
            }
            long millis = (System.nanoTime() - started) / 1_000_000L;
            StructureScan ready = updated;
            LootIndex readyIndex = updatedIndex;
            server.execute(() -> {
                if (generation != GENERATION.get()) {
                    return;
                }
                checking = false;
                fingerprint = current;
                JesServer.fingerprintKnown(server, current);
                if (ready != null) {
                    JesLog.debug("The loot index is up to date in {} ms", millis);
                    scan = ready;
                    index = readyIndex;
                    publish(server);
                    return;
                }
                scan = null;
                // Anyone who had the old one gets the new one, so rebuild for them too. They keep
                // the old one until it's done.
                if (server.isDedicatedServer() || wanted || !WAITING.isEmpty()) {
                    build(server, generation);
                } else {
                    index = null;
                    payload = null;
                    partial = false;
                }
            });
        });
    }

    /**
     * Has {@code watcher} told on the index's thread how many structures are done, each time another
     * one is and after players have been told. Null for nobody. For tests.
     */
    public static void watchProgress(IntConsumer watcher) {
        progressWatcher = watcher != null ? watcher : d -> {
        };
    }

    /** When the server stops: drops everything and stops any work in progress. */
    public static void stop() {
        GENERATION.incrementAndGet();
        scan = null;
        index = null;
        payload = null;
        fingerprint = null;
        checking = false;
        building = false;
        broken = false;
        wanted = false;
        partial = false;
        // In singleplayer the next world's server starts in the same game, and a player asking it
        // before it has started a build of its own would be told how far this one got.
        done = 0;
        total = 0;
        WAITING.clear();
    }

    /** Sends the index if it's ready. Otherwise starts it if nothing else will, and says how far along it is, or that it failed. */
    public static void request(ServerPlayer player) {
        WAITING.add(player.getUUID());
        if (payload != null) {
            JesServer.sendIndex(player, payload);
            if (partial) {
                // It's an early index, so they're told there's more to come and how far that has got.
                tellProgress(player);
            }
            return;
        }
        wanted = true;
        if (!checking && !building && !broken) {
            build(Players.server(player), GENERATION.get());
        }
        tellProgress(player);
    }

    /**
     * Tells a player how far the build has got, but never that all of it is done: the finished index
     * says that by arriving, and a player told so beside an early one would take it for the finished
     * one. Every structure can be done a while before the finished index is ready, and the index's
     * thread can get there before the server gets round to saying how far it had got. Once the build
     * has failed it says that too, after the figure it stopped at, which a version from before goes
     * on showing as it always has. Server thread.
     */
    private static void tellProgress(ServerPlayer player) {
        JesServer.sendIndexProgress(player, Math.max(0, Math.min(done, total - 1)), total, broken);
    }

    private static void build(MinecraftServer server, int generation) {
        building = true;
        List<ResourceLocation> ids = new ArrayList<>(server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet());
        done = 0;
        total = ids.size();
        String key = fingerprint;
        Path dir = folder();
        inBackground(server, generation, () -> {
            long started = System.nanoTime();
            AtomicInteger failed = new AtomicInteger();
            // It makes every structure's first view on the way, which is saved for players.
            StructureScan scanned = LootIndex.scan(server, ids, d -> {
                        done = d;
                        sayProgress(server, generation);
                        progressWatcher.accept(d);
                    }, () -> generation != GENERATION.get() || !server.isRunning(), failed,
                    (id, firstView) -> SavedPreviews.offer(server, id, StructureCapture.defaultSeed(id), firstView),
                    early -> publishEarly(server, generation, early, started));
            if (scanned == null) {
                JesLog.debug("Stopped building the loot index after {} of {} structures, as {}", done, ids.size(),
                        server.isRunning() ? "the server's data was reloaded" : "the server is stopping");
                return;
            }
            LootIndex built = LootIndex.of(server, scanned);
            JesLog.debug("Indexed the loot of {} structures in {} s ({} wouldn't generate, {} to try again)", ids.size(),
                    (System.nanoTime() - started) / 1_000_000_000L, failed.get(), scanned.retry().size());
            if (key != null) {
                write(dir, key, scanned);
            }
            server.execute(() -> {
                if (generation == GENERATION.get()) {
                    building = false;
                    scan = scanned;
                    index = built;
                    publish(server);
                }
            });
        });
    }

    /**
     * Runs {@code task} on the index's thread. Anything it throws is logged, and the index isn't
     * tried again until the next /reload, as it would only fail the same way. Left alone, the
     * thread would end and players would wait for the index for ever, with nothing in the log
     * when a pack's crash reporter takes over uncaught errors.
     */
    private static void inBackground(MinecraftServer server, int generation, Runnable task) {
        WORKER.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                JustEnoughStructures.LOGGER.error("Couldn't build the loot index, so finding structures by item won't work until the next /reload", t);
                server.execute(() -> {
                    if (generation == GENERATION.get()) {
                        checking = false;
                        building = false;
                        broken = true;
                        // Whoever is waiting for an index, or for the rest of an early one, is told
                        // that none is coming. Those with a finished one from before go on using it.
                        if (payload == null || partial) {
                            Players.forEachOnline(server, WAITING, LootIndexStore::tellProgress);
                        }
                    }
                });
            }
        });
    }

    /**
     * Once the quick part of a build is done: players get the index as far as it goes rather than
     * nothing until every structure has been generated, unless they have a finished one from before
     * to go on using. On the index's thread.
     */
    private static void publishEarly(MinecraftServer server, int generation, StructureScan early, long started) {
        LootIndex soon = LootIndex.of(server, early);
        server.execute(() -> {
            if (generation != GENERATION.get() || !building || (index != null && !partial)) {
                return;
            }
            JesLog.debug("The loot index can be used after {} s, with {} of {} structures still to generate", (System.nanoTime() - started) / 1_000_000_000L,
                    total - done, total);
            index = soon;
            partial = true;
            send(server);
            Players.forEachOnline(server, WAITING, LootIndexStore::tellProgress);
        });
    }

    /** While the rest of an early index is found: tells everyone who has it how far that's got, every couple of seconds. On the index's thread. */
    private static void sayProgress(MinecraftServer server, int generation) {
        long now = System.currentTimeMillis();
        // Not once the last one is done: the finished index is on its way, and says so by arriving.
        if (!partial || done >= total || now - progressSaid < 2000) {
            return;
        }
        progressSaid = now;
        server.execute(() -> {
            if (generation == GENERATION.get() && partial) {
                Players.forEachOnline(server, WAITING, LootIndexStore::tellProgress);
            }
        });
    }

    /** The finished index goes to everyone who asked, and a dedicated server then saves the first views it hasn't yet. */
    private static void publish(MinecraftServer server) {
        partial = false;
        send(server);
        SavedPreviews.fill(server);
    }

    /** Works out what players get from the index as it stands, leaving hidden structures out, and sends it to everyone who asked. */
    private static void send(MinecraftServer server) {
        LootIndex shown = visible(index);
        boolean early = partial;
        payload = Blobs.deflate(Blobs.toBytes(server.registryAccess(), buf -> {
            Codecs.writeIndex(buf, shown);
            // After the index, where a version from before stops reading. An early one says so, for
            // EMI, which loads all over again for each index and so waits for the finished one.
            Codecs.writeIndexEarly(buf, early);
        }));
        Players.forEachOnline(server, WAITING, player -> JesServer.sendIndex(player, payload));
    }

    /** The index without the structures the server hides, and without loot tables only they use. */
    public static LootIndex visible(LootIndex full) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        full.tablesByStructure().forEach((id, used) -> {
            if (!ServerConfig.hides(id)) {
                tables.put(id, used);
            }
        });
        Set<ResourceLocation> stillUsed = tables.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<ResourceLocation, Set<ResourceLocation>> items = new TreeMap<>();
        full.itemsByTable().forEach((table, found) -> {
            if (stillUsed.contains(table)) {
                items.put(table, found);
            }
        });
        return new LootIndex(tables, items);
    }

    /**
     * A hash of everything that decides which loot tables each structure uses: the game and mod
     * versions, since structure code can change with them, and the bytes of every structure, pool,
     * processor and template file, in every mod and datapack. Null if the files couldn't be read.
     * Loot tables and the containers changed in the browser aren't in it; they're brought up to date
     * without generating everything again.
     */
    public static String fingerprintOf(MinecraftServer server) {
        try {
            Hasher hasher = Hashing.sha256().newHasher();
            //? if >=26.1 {
            /*String game = SharedConstants.getCurrentVersion().name();
            *///?} else {
            String game = SharedConstants.getCurrentVersion().getName();
            //?}
            hasher.putString(FORMAT + "|" + game + "|", StandardCharsets.UTF_8);
            new TreeMap<>(JustEnoughStructures.modVersions()).forEach((id, version) ->
                    hasher.putString(id + "@" + version + "|", StandardCharsets.UTF_8));
            Map<String, HashCode> files = JesLog.enabled() ? new HashMap<>() : null;
            hashFiles(hasher, server.getResourceManager(), SOURCES, files);
            if (files != null) {
                logChanges(files);
            }
            return hasher.hash().toString();
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't fingerprint the loot index's sources, so it won't be saved", e);
            return null;
        }
    }

    /**
     * Hashes the name and bytes of every file in these folders of the server's data, from every mod
     * and datapack, in the same order each time. Each file's own hash goes in {@code files} too, unless
     * it's null.
     */
    static void hashFiles(Hasher hasher, ResourceManager resources, List<String> sources, Map<String, HashCode> files) throws IOException {
        for (String source : sources) {
            for (Map.Entry<ResourceLocation, Resource> file : new TreeMap<>(resources.listResources(source, path -> true)).entrySet()) {
                hasher.putString(file.getKey().toString(), StandardCharsets.UTF_8);
                try (InputStream in = file.getValue().open()) {
                    byte[] bytes = in.readAllBytes();
                    hasher.putBytes(bytes);
                    if (files != null) {
                        files.put(file.getKey().toString(), Hashing.murmur3_128().hashBytes(bytes));
                    }
                }
            }
        }
    }

    /**
     * For the debug log: which source files changed since the last fingerprint. A pack whose files
     * come out different on every reload has its whole index built again each time.
     */
    private static void logChanges(Map<String, HashCode> files) {
        Map<String, HashCode> before = lastFiles;
        lastFiles = files;
        if (before.isEmpty()) {
            return;
        }
        List<String> changed = files.entrySet().stream().filter(e -> !e.getValue().equals(before.get(e.getKey())))
                .map(Map.Entry::getKey).sorted().toList();
        List<String> gone = before.keySet().stream().filter(file -> !files.containsKey(file)).sorted().toList();
        if (!changed.isEmpty() || !gone.isEmpty()) {
            JesLog.debug("Since the last check of the loot index, {} of its source files changed or are new {} and {} are gone {}",
                    changed.size(), changed.subList(0, Math.min(10, changed.size())), gone.size(), gone.subList(0, Math.min(10, gone.size())));
        }
    }

    private static Path folder() {
        return JustEnoughStructures.cacheDir().resolve("loot-index");
    }

    /** The scan saved under this fingerprint, or null if there isn't one that reads. */
    public static StructureScan read(Path dir, String key) {
        Path file = dir.resolve(key + ".bin");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            StructureScan saved = Codecs.readScan(Blobs.fromBytes(RegistryAccess.EMPTY, Blobs.inflate(Files.readAllBytes(file))));
            CacheFiles.markUsed(file);
            return saved;
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the saved loot index {}, building it again", file, e);
            return null;
        }
    }

    /** Saves the scan under this fingerprint, keeping the few most recently used others. */
    public static void write(Path dir, String key, StructureScan saved) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(key + ".bin");
            SafeFiles.write(file, Blobs.deflate(Blobs.toBytes(RegistryAccess.EMPTY, buf -> Codecs.writeScan(buf, saved))));
            CacheFiles.keepNewest(dir, p -> p.toString().endsWith(".bin"), KEPT_FILES, file);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't save the loot index to {}", dir, e);
        }
    }
}
