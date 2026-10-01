package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The loot index, saved to disk so it's only built again when something it comes from changes.
 *
 * <p>Each saved index is named after a fingerprint of the installed mods and every structure,
 * pool, processor, template and loot table file. The fingerprint is worked out in the background
 * when the server starts and after /reload. If a saved index matches, it's loaded in moments.
 * Otherwise a dedicated server builds it straight away, still in the background, and singleplayer
 * waits until someone wants it. Nothing here holds up the server starting or players joining.
 */
public final class LootIndexStore {
    /** Goes up whenever what's saved changes shape, so old files are never read as new ones. */
    private static final String FORMAT = "1";
    private static final int KEPT_FILES = 4;
    private static final List<String> SOURCES = List.of("structures", "worldgen/structure", "worldgen/template_pool",
            "worldgen/processor_list", "loot_tables");

    // The index captures every structure, so it gets its own thread and never holds up previews.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Just Enough Structures loot index");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();
    // Players who've asked for the index. They're sent the new one whenever it changes.
    private static final Set<UUID> WAITING = ConcurrentHashMap.newKeySet();

    // Changed on the server thread only.
    private static LootIndex index;
    private static byte[] payload;
    private static String fingerprint;
    private static boolean checking;
    private static boolean building;
    private static boolean wanted;
    private static volatile int done;
    private static volatile int total;

    private LootIndexStore() {
    }

    /** When the server has started and after /reload: checks whether the index still holds. */
    public static void refresh(MinecraftServer server) {
        int generation = GENERATION.incrementAndGet();
        checking = true;
        building = false;
        String known = index == null ? null : fingerprint;
        Path dir = folder();
        WORKER.execute(() -> {
            long started = System.nanoTime();
            String current = fingerprintOf(server);
            if (generation != GENERATION.get()) {
                return;
            }
            boolean same = current != null && current.equals(known);
            LootIndex saved = current == null || same ? null : read(dir, current);
            long millis = (System.nanoTime() - started) / 1_000_000L;
            server.execute(() -> {
                if (generation != GENERATION.get()) {
                    return;
                }
                checking = false;
                if (same) {
                    JesLog.debug("The loot index is still up to date ({} ms to check)", millis);
                    publish(server);
                    return;
                }
                fingerprint = current;
                if (saved != null) {
                    JesLog.debug("Loaded the loot index from {} in {} ms", dir, millis);
                    index = saved;
                    publish(server);
                    return;
                }
                index = null;
                payload = null;
                // Anyone who had the old one gets the new one, so rebuild for them too.
                if (server.isDedicatedServer() || wanted || !WAITING.isEmpty()) {
                    build(server, generation);
                }
            });
        });
    }

    /** When the server stops: drops everything and stops any work in progress. */
    public static void stop() {
        GENERATION.incrementAndGet();
        index = null;
        payload = null;
        fingerprint = null;
        checking = false;
        building = false;
        wanted = false;
        WAITING.clear();
    }

    /** Sends the index if it's ready. Otherwise starts it if nothing else will, and says how far along it is. */
    public static void request(ServerPlayer player) {
        WAITING.add(player.getUUID());
        if (payload != null) {
            JesServer.sendIndex(player, payload);
            return;
        }
        wanted = true;
        if (!checking && !building) {
            build(player.getServer(), GENERATION.get());
        }
        JesServer.sendIndexProgress(player, done, total);
    }

    private static void build(MinecraftServer server, int generation) {
        building = true;
        List<ResourceLocation> ids = new ArrayList<>(server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet());
        done = 0;
        total = ids.size();
        String key = fingerprint;
        Path dir = folder();
        WORKER.execute(() -> {
            long started = System.nanoTime();
            AtomicInteger failed = new AtomicInteger();
            LootIndex built = LootIndex.build(server, ids, d -> done = d, () -> generation != GENERATION.get() || !server.isRunning(), failed);
            if (built == null) {
                return;
            }
            JesLog.debug("Indexed the loot of {} structures in {} s ({} wouldn't generate)", ids.size(),
                    (System.nanoTime() - started) / 1_000_000_000L, failed.get());
            if (key != null) {
                write(dir, key, built);
            }
            server.execute(() -> {
                if (generation == GENERATION.get()) {
                    building = false;
                    index = built;
                    publish(server);
                }
            });
        });
    }

    /** Works out what players get, leaving hidden structures out, and sends it to everyone who asked. */
    private static void publish(MinecraftServer server) {
        LootIndex shown = visible(index);
        payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeIndex(buf, shown)));
        for (UUID id : WAITING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                WAITING.remove(id);
            } else {
                JesServer.sendIndex(player, payload);
            }
        }
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
     * A hash of everything the index is built from: the game and mod versions, since structure
     * code can change with them, the containers changed in the browser, and the bytes of every
     * file structures and loot come from, in every mod and datapack. Null if the files couldn't be
     * read.
     */
    public static String fingerprintOf(MinecraftServer server) {
        try {
            Hasher hasher = Hashing.sha256().newHasher();
            hasher.putString(FORMAT + "|" + SharedConstants.getCurrentVersion().getName() + "|", StandardCharsets.UTF_8);
            new TreeMap<>(JustEnoughStructures.modVersions()).forEach((id, version) ->
                    hasher.putString(id + "@" + version + "|", StandardCharsets.UTF_8));
            // Containers pointed at other tables in the browser change what's found where, too.
            hasher.putString(ContainerPatches.summary() + "|", StandardCharsets.UTF_8);
            ResourceManager resources = server.getResourceManager();
            for (String source : SOURCES) {
                for (Map.Entry<ResourceLocation, Resource> file : new TreeMap<>(resources.listResources(source, path -> true)).entrySet()) {
                    hasher.putString(file.getKey().toString(), StandardCharsets.UTF_8);
                    try (InputStream in = file.getValue().open()) {
                        hasher.putBytes(in.readAllBytes());
                    }
                }
            }
            return hasher.hash().toString();
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't fingerprint the loot index's sources, so it won't be saved", e);
            return null;
        }
    }

    private static Path folder() {
        return JustEnoughStructures.cacheDir().resolve("loot-index");
    }

    /** The index saved under this fingerprint, or null if there isn't one that reads. */
    public static LootIndex read(Path dir, String key) {
        Path file = dir.resolve(key + ".bin");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            LootIndex saved = Codecs.readIndex(Blobs.fromBytes(Blobs.inflate(Files.readAllBytes(file))));
            // Marks it as recently used, so it's kept over older ones.
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return saved;
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the saved loot index {}, building it again", file, e);
            return null;
        }
    }

    /** Saves the index under this fingerprint, keeping the few most recently used others. */
    public static void write(Path dir, String key, LootIndex saved) {
        try {
            Files.createDirectories(dir);
            Path temp = dir.resolve(key + ".tmp");
            Files.write(temp, Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeIndex(buf, saved))));
            Files.move(temp, dir.resolve(key + ".bin"), StandardCopyOption.REPLACE_EXISTING);
            List<Path> files;
            try (Stream<Path> list = Files.list(dir)) {
                files = list.filter(p -> p.toString().endsWith(".bin"))
                        .sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed())
                        .toList();
            }
            for (Path old : files.subList(Math.min(KEPT_FILES, files.size()), files.size())) {
                Files.deleteIfExists(old);
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't save the loot index to {}", dir, e);
        }
    }
}
