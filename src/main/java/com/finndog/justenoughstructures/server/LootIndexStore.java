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
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The loot index, saved to disk so it's only rebuilt when its sources change.
 *
 * <p>The slow part, which loot tables each structure uses and what it places itself, is saved as a {@link StructureScan}
 * under a fingerprint of the game and mod versions and every structure, pool, processor, template
 * and loot modifier file. Loot tables aren't in the fingerprint: what each table can give is read
 * again after every start and /reload, which is quick. Pack tools changes to containers, and spawners
 * switched to or from trial spawners, regenerate only the structures that place those templates, as
 * do structures that failed for a temporary reason. Anything else that changes, or a failed update,
 * rebuilds the whole scan.
 *
 * <p>Loot modifiers on Forge and NeoForge, and Fabric's event that changes what a table drops, only
 * show what they add when a table is rolled. So before the finished index goes out, each table is
 * rolled a few hundred times on the server thread, and what that adds is saved with the scan under a
 * hash of the table. Only a table that changed, or one no structure used before, is rolled again.
 *
 * <p>It all runs on a separate thread and never holds up the server starting or players joining.
 * A dedicated server builds at once; singleplayer waits until someone asks. While a rebuild runs,
 * the old index stays in use.
 *
 * <p>With no old index, a build sends an early one first: every structure's template pools plus
 * the structures without pools, which is most of the loot. Players see progress for the rest and
 * get the finished index when it's done.
 */
public final class LootIndexStore {
    /** Bump when the saved format or what goes in it changes, so old files aren't read as new. */
    private static final String FORMAT = "7";
    private static final int KEPT_FILES = 4;
    // Loot modifiers are Forge's and NeoForge's, listed in forge/ or neoforge/ except on NeoForge from 26.1.
    private static final List<String> SOURCES = List.of(Folders.STRUCTURES, "worldgen/structure", "worldgen/template_pool",
            "worldgen/processor_list", "loot_modifiers");
    /**
     * How many times each loot table is rolled for the index. The odds players see are rolled more
     * times from the same seed, so whatever these rolls find has a chance there too.
     */
    private static final int ROLLS = 500;
    /** The most of the server's time one table's rolls take. A table some mod makes slow to roll gets fewer. */
    private static final long TABLE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    /** How long the rolls run in one go on the server thread, before the next tick gets its turn. */
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    // The index captures every structure, so it runs on a separate thread and never holds up previews.
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
    // The items chest loot can give, sent with progress so recipe viewers can show a waiting row on
    // them. Null until worked out for a build. Written on the index's thread.
    private static volatile Set<ResourceLocation> chestItems;
    // For the debug log: what each source file hashed to last time, to say which ones changed.
    private static volatile Map<String, HashCode> lastFiles = Map.of();
    // For tests: called with the number of structures done, each time one finishes.
    private static volatile IntConsumer progressWatcher = d -> {
    };

    private LootIndexStore() {
    }

    /**
     * Returns the fingerprint of the server's structure sources, or null while it's still being
     * computed after a start or /reload. Server thread only.
     */
    public static String knownFingerprint() {
        return checking ? null : fingerprint;
    }

    /** Whether the index is built and up to date, so nothing's generating structures for it. Server thread only. */
    public static boolean ready() {
        return !checking && !building && index != null;
    }

    /** Called when the server has started and after /reload: checks whether the saved index still matches. */
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
            Map<ResourceLocation, String> unrolled = Map.of();
            if (base != null) {
                // Only structures placing patched templates, and those to retry, are generated
                // again; what each table can give is read from the current loot tables.
                try {
                    updated = LootIndex.update(server, base, () -> generation != GENERATION.get() || !server.isRunning());
                    if (updated == null) {
                        return;
                    }
                    updatedIndex = LootIndex.of(server, updated);
                    unrolled = LootIndex.unrolled(server, updated);
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
            Map<ResourceLocation, String> toRoll = unrolled;
            if (ready == null) {
                // A build can follow, so this is ready before anyone is told how it's going.
                chestItems = LootIndex.chestItems(server);
            }
            server.execute(() -> {
                if (generation != GENERATION.get()) {
                    return;
                }
                checking = false;
                fingerprint = current;
                JesServer.fingerprintKnown(server, current);
                if (ready != null) {
                    JesLog.debug("The loot index is up to date in {} ms, with {} loot tables to roll", millis, toRoll.size());
                    rollThenPublish(server, generation, ready, readyIndex, toRoll, current, dir);
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
     * Sets a watcher, called on the index's thread with the number of structures done each time one
     * finishes, after any progress message to players has been queued. Null clears it. For tests.
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
        chestItems = null;
        WAITING.clear();
    }

    /** When a player leaves: they're no longer sent the index. */
    public static void left(UUID player) {
        WAITING.remove(player);
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
     * Tells a player how far the build has got. It never reports all done, as every structure can be
     * generated a while before the finished index is ready: the finished index says that by arriving,
     * and "done" beside an early index would read as finished. Once the build fails it says so after
     * the last count, which older clients just keep showing. The items chest loot can give come last,
     * for recipe viewers to show a waiting row on. Server thread only.
     */
    private static void tellProgress(ServerPlayer player) {
        Set<ResourceLocation> items = chestItems;
        JesServer.sendIndexProgress(player, Math.max(0, Math.min(done, total - 1)), total, broken, items == null ? Set.of() : items);
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
            if (chestItems == null) {
                chestItems = LootIndex.chestItems(server);
            }
            AtomicInteger failed = new AtomicInteger();
            // This also captures every structure's first view along the way, which is saved for players.
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
            Map<ResourceLocation, String> unrolled = LootIndex.unrolled(server, scanned);
            JesLog.debug("Indexed the loot of {} structures in {} s ({} wouldn't generate, {} to try again)", ids.size(),
                    (System.nanoTime() - started) / 1_000_000_000L, failed.get(), scanned.retry().size());
            if (key != null) {
                // Saved before the rolls as well, so a server stopped during them only has those left to do.
                write(dir, key, scanned);
            }
            server.execute(() -> {
                if (generation == GENERATION.get()) {
                    rollThenPublish(server, generation, scanned, built, unrolled, key, dir);
                }
            });
        });
    }

    /**
     * Rolls the tables in {@code unrolled}, adds to the index what the rolls gave that reading the
     * tables doesn't show, then makes it the index and sends it. It's sent only then, as EMI loads
     * all over again for each index. The scan is saved again with the rolls, under {@code key} unless
     * that's null. Server thread only.
     */
    private static void rollThenPublish(MinecraftServer server, int generation, StructureScan scanned, LootIndex read,
                                        Map<ResourceLocation, String> unrolled, String key, Path dir) {
        if (unrolled.isEmpty()) {
            finish(server, scanned, read);
            return;
        }
        building = true;
        new TableRolls(server, unrolled.keySet(), () -> generation != GENERATION.get() || !server.isRunning(), seen -> {
            Map<ResourceLocation, StructureScan.Rolled> rolled = new TreeMap<>();
            Map<ResourceLocation, Set<ResourceLocation>> added = new TreeMap<>();
            seen.forEach((table, items) -> {
                Set<ResourceLocation> unread = new TreeSet<>(items);
                unread.removeAll(read.itemsByTable().getOrDefault(table, Set.of()));
                rolled.put(table, new StructureScan.Rolled(unrolled.get(table), unread));
                if (!unread.isEmpty()) {
                    added.put(table, unread);
                }
            });
            JesLog.debug("Rolling found items that reading doesn't show in {} loot tables {}", added.size(), added);
            StructureScan withRolls = scanned.withRolled(rolled);
            finish(server, withRolls, read.withItems(added));
            if (key != null) {
                inBackground(server, generation, () -> write(dir, key, withRolls));
            }
        }).run();
    }

    /** Makes these the scan and the index, and sends the index. Server thread only. */
    private static void finish(MinecraftServer server, StructureScan done, LootIndex finished) {
        building = false;
        scan = done;
        index = finished;
        publish(server);
    }

    /**
     * Rolls loot tables on the server thread, a few milliseconds at a time in its spare time, then
     * hands {@code done} the items each one gave. Loot modifiers and other mods' loot code run in the
     * rolls as they do when a chest is filled, and some of them touch the world, so the rolls can't
     * run on the index's thread. Stops once {@code stale} is true, without calling {@code done}.
     */
    private static final class TableRolls implements Runnable {
        private final MinecraftServer server;
        private final Iterator<ResourceLocation> tables;
        private final int count;
        private final BooleanSupplier stale;
        private final Consumer<Map<ResourceLocation, Set<ResourceLocation>>> done;
        private final Map<ResourceLocation, Set<ResourceLocation>> seen = new TreeMap<>();
        private final long started = System.nanoTime();
        private ResourceLocation table;
        private LootRolls.Roller roller;
        private long tableNanos;
        private long rollingNanos;
        private int rolls;

        TableRolls(MinecraftServer server, Set<ResourceLocation> tables, BooleanSupplier stale,
                   Consumer<Map<ResourceLocation, Set<ResourceLocation>>> done) {
            this.server = server;
            this.tables = List.copyOf(tables).iterator();
            this.count = tables.size();
            this.stale = stale;
            this.done = done;
        }

        @Override
        public void run() {
            if (stale.getAsBoolean()) {
                return;
            }
            long sliceStarted = System.nanoTime();
            long left;
            while ((left = SLICE_NANOS - (System.nanoTime() - sliceStarted)) > 0) {
                if (table == null) {
                    if (!tables.hasNext()) {
                        rollingNanos += System.nanoTime() - sliceStarted;
                        JesLog.debug("Rolled {} loot tables {} times in all for the loot index, in {} ms of the server's time over {} ms",
                                count, rolls, rollingNanos / 1_000_000L, (System.nanoTime() - started) / 1_000_000L);
                        done.accept(seen);
                        return;
                    }
                    table = tables.next();
                    roller = null;
                    tableNanos = 0;
                }
                long before = System.nanoTime();
                boolean finished;
                try {
                    if (roller == null) {
                        // From the seed the odds are rolled with.
                        roller = new LootRolls.Roller(server.overworld(), table, ROLLS, table.hashCode());
                    }
                    finished = roller.rollFor(Math.min(left, TABLE_NANOS - tableNanos));
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    JesLog.debug("Rolling {} for the loot index failed", table, e);
                    finished = true;
                }
                tableNanos += System.nanoTime() - before;
                if (finished || tableNanos >= TABLE_NANOS) {
                    Set<ResourceLocation> items = new TreeSet<>();
                    if (roller != null) {
                        LootOdds odds = roller.odds();
                        rolls += odds.rolls();
                        for (LootOdds.Row row : odds.rows()) {
                            items.add(BuiltInRegistries.ITEM.getKey(row.example().getItem()));
                        }
                    }
                    seen.put(table, items);
                    table = null;
                }
            }
            rollingNanos += System.nanoTime() - sliceStarted;
            JesServer.later(server, this);
        }
    }

    /**
     * Runs {@code task} on the index's thread. Anything it throws is logged, and the index isn't
     * tried again until the next /reload, as it would only fail the same way. Otherwise the thread
     * would end, players would wait for the index forever, and nothing would reach the log when a
     * pack's crash reporter handles uncaught errors.
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
                        // that none is coming. Those with an earlier finished index keep using it.
                        if (payload == null || partial) {
                            Players.forEachOnline(server, WAITING, LootIndexStore::tellProgress);
                        }
                    }
                });
            }
        });
    }

    /**
     * Called once the quick part of a build is done. Players get the partial index instead of nothing
     * until every structure has been generated, unless they already have a finished one. Called on
     * the index's thread.
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

    /** While the rest of an early index is built, tells everyone who has it how far that has got, at most every 2 seconds. Called on the index's thread. */
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

    /** Sends the finished index to everyone who asked; a dedicated server then saves the first views it hasn't saved yet. */
    private static void publish(MinecraftServer server) {
        partial = false;
        send(server);
        SavedPreviews.fill(server);
    }

    /** Builds what players get from the current index, leaving hidden structures out, and sends it to everyone who asked. */
    private static void send(MinecraftServer server) {
        payload = shown(server);
        Players.forEachOnline(server, WAITING, player -> JesServer.sendIndex(player, payload));
    }

    /**
     * After Pack tools changed which structures are hidden: everyone who asked gets the index again,
     * if that changed what they're sent. Only then, as EMI reloads fully for each index. Server thread only.
     */
    public static void hiddenChanged(MinecraftServer server) {
        if (index == null) {
            return;
        }
        byte[] now = shown(server);
        if (!Arrays.equals(now, payload)) {
            payload = now;
            Players.forEachOnline(server, WAITING, player -> JesServer.sendIndex(player, now));
        }
    }

    /** What players are sent of the current index. */
    private static byte[] shown(MinecraftServer server) {
        LootIndex shown = visible(index);
        boolean early = partial;
        return Blobs.deflate(Blobs.toBytes(server.registryAccess(), buf -> {
            Codecs.writeIndex(buf, shown);
            // Appended after the index, so older clients stop reading before it. Marks an early index
            // for EMI, which reloads fully for each index and so waits for the finished one.
            Codecs.writeIndexEarly(buf, early);
            // Then what structures place themselves, where clients from before it stop.
            Codecs.writeIndexPlaced(buf, shown.placedByStructure());
        }));
    }

    /** The index without the structures the server hides, and without loot tables only they use. */
    public static LootIndex visible(LootIndex full) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        full.tablesByStructure().forEach((id, used) -> {
            if (!ServerConfig.hides(id)) {
                tables.put(id, used);
            }
        });
        Map<ResourceLocation, Set<ResourceLocation>> placed = new TreeMap<>();
        full.placedByStructure().forEach((id, items) -> {
            if (!ServerConfig.hides(id)) {
                placed.put(id, items);
            }
        });
        Set<ResourceLocation> stillUsed = tables.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<ResourceLocation, Set<ResourceLocation>> items = new TreeMap<>();
        full.itemsByTable().forEach((table, found) -> {
            if (stillUsed.contains(table)) {
                items.put(table, found);
            }
        });
        return new LootIndex(tables, items, placed);
    }

    /**
     * A hash of everything that decides which loot tables each structure uses: the game and mod
     * versions, since structure code can change with them, and the bytes of every structure, pool,
     * processor and template file, in every mod and datapack. Loot modifier files too, as what they
     * add is saved with the scan. Null if the files couldn't be read. Loot tables and Pack tools
     * patches aren't in it; they're brought up to date without generating everything again.
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
     * and datapack, in the same order each time. Each file's hash also goes in {@code files}, unless
     * that is null.
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

    /** Returns the scan saved under this fingerprint, or null if there's none or it can't be read. */
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
