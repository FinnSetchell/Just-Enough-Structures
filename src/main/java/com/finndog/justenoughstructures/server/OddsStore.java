package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.CacheFiles;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.RemovedItems;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Loot tables' odds kept on disk, so a start or a /reload doesn't roll them again: the odds players ask
 * for come back at once, and the loot index's rolls use them instead of rolling.
 *
 * <p>Each table's odds are kept under its hash ({@link LootIndex#tableHash}), so a table that changed is
 * rolled again. They're in a file named after everything else a roll depends on: the game and every
 * mod's version, the files of loot modifiers and of Loot Integrations, which change what a table
 * gives as it's rolled, and Item Obliterator's config, which takes items out ({@link RemovedItems}).
 * Other mods' configs can change rolls too and aren't in it. Structure and pool files aren't in it
 * either, so changing a datapack's structures keeps every table's odds.
 */
public final class OddsStore {
    /** Bump when what's kept, or how it's rolled, changes. */
    private static final String FORMAT = "2";
    private static final int KEPT_FILES = 4;
    private static final List<String> SOURCES = List.of("loot_modifiers", "loot");
    private static final String SUFFIX = ".odds";

    /**
     * A table's kept odds. {@code forPlayers} is whether they're good enough to answer a player with:
     * worked out, or rolled as many times as the odds roll. The loot index's own rolls are fewer, and are
     * kept only for its next build.
     */
    private record Kept(String hash, LootOdds odds, boolean forPlayers) {
    }

    // Server thread only.
    private static Map<ResourceLocation, Kept> kept = new HashMap<>();
    /** Each table's hash as loaded now, worked out once per load. */
    private static final Map<ResourceLocation, String> hashes = new HashMap<>();
    private static String key;
    private static boolean changed;

    private OddsStore() {
    }

    /**
     * After a start or /reload, on the index's thread: reads the odds kept for the game as it is now,
     * and hands them to the server thread.
     */
    static void refresh(MinecraftServer server) {
        String now = keyOf(server);
        Map<ResourceLocation, Kept> read = now == null ? new HashMap<>() : read(server.registryAccess(), file(now));
        server.execute(() -> {
            // Tables may have changed with a /reload, so their hashes are worked out again.
            hashes.clear();
            if (now != null && now.equals(key)) {
                // The same game: what's kept here is what was read, and what was kept since.
                return;
            }
            if (changed && key != null) {
                write(server.registryAccess(), key, kept);
            }
            key = now;
            kept = read;
            changed = false;
            JesLog.debug("Kept loot odds: {} tables", read.size());
        });
    }

    /** The odds kept for a loaded table as it is now, if they're good enough to answer a player with. Server thread only. */
    public static LootOdds forPlayers(MinecraftServer server, ResourceLocation table) {
        Kept k = current(server, table);
        return k != null && k.forPlayers() ? k.odds() : null;
    }

    /** The odds kept for a loaded table as it is now, of any kind. Server thread only. */
    static LootOdds any(MinecraftServer server, ResourceLocation table) {
        Kept k = current(server, table);
        return k == null ? null : k.odds();
    }

    /**
     * Keeps a loaded table's odds. Odds good enough for players aren't replaced by the loot index's
     * fewer rolls. Server thread only.
     */
    static void keep(MinecraftServer server, LootOdds odds, boolean forPlayers) {
        if (key == null) {
            return;
        }
        ResourceLocation table = odds.tableId();
        Kept before = current(server, table);
        if (before != null && before.forPlayers() && !forPlayers) {
            return;
        }
        kept.put(table, new Kept(hash(server, table), odds, forPlayers));
        changed = true;
    }

    /** Saves what's kept, if anything changed. Server thread only. */
    public static void save(MinecraftServer server) {
        if (changed && key != null) {
            write(server.registryAccess(), key, kept);
            changed = false;
        }
    }

    /** When the server stops: saves, then forgets. */
    static void stop(MinecraftServer server) {
        if (server != null) {
            save(server);
        }
        kept = new HashMap<>();
        hashes.clear();
        key = null;
        changed = false;
    }

    private static Kept current(MinecraftServer server, ResourceLocation table) {
        Kept k = kept.get(table);
        return k != null && k.hash().equals(hash(server, table)) && !k.hash().isEmpty() ? k : null;
    }

    private static String hash(MinecraftServer server, ResourceLocation table) {
        return hashes.computeIfAbsent(table, t -> LootIndex.tableHash(server, t));
    }

    /** A hash of what every table's rolls depend on besides the table itself, or null if the files can't be read. */
    public static String keyOf(MinecraftServer server) {
        try {
            Hasher hasher = Hashing.sha256().newHasher();
            //? if >=26.1 {
            /*String game = SharedConstants.getCurrentVersion().name();
            *///?} else {
            String game = SharedConstants.getCurrentVersion().getName();
            //?}
            hasher.putString(FORMAT + "|" + game + "|", StandardCharsets.UTF_8);
            new TreeMap<>(JustEnoughStructures.modVersions()).forEach((id, version) -> hasher.putString(id + "@" + version + "|", StandardCharsets.UTF_8));
            LootIndexStore.hashFiles(hasher, server.getResourceManager(), SOURCES, null);
            hasher.putString(RemovedItems.fingerprint(), StandardCharsets.UTF_8);
            return hasher.hash().toString();
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't fingerprint what loot rolls depend on, so no odds are kept", e);
            return null;
        }
    }

    private static Path file(String key) {
        return JustEnoughStructures.cacheDir().resolve("loot-index").resolve("odds-" + key + SUFFIX);
    }

    private static Map<ResourceLocation, Kept> read(RegistryAccess registries, Path file) {
        Map<ResourceLocation, Kept> out = new HashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            FriendlyByteBuf buf = Blobs.fromBytes(registries, Blobs.inflate(Files.readAllBytes(file)));
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                ResourceLocation table = buf.readResourceLocation();
                String hash = buf.readUtf();
                boolean forPlayers = buf.readBoolean();
                byte[] odds = buf.readByteArray();
                try {
                    out.put(table, new Kept(hash, Codecs.readOdds(Blobs.fromBytes(registries, odds)), forPlayers));
                } catch (RuntimeException e) {
                    // An item from a mod that's gone, say: that table is rolled again.
                    JesLog.debug("Couldn't read the kept odds of {}", table, e);
                }
            }
            CacheFiles.markUsed(file);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the kept loot odds {}, so they're rolled again", file, e);
        }
        return out;
    }

    private static void write(RegistryAccess registries, String key, Map<ResourceLocation, Kept> what) {
        Path file = file(key);
        try {
            byte[] bytes = Blobs.deflate(Blobs.toBytes(registries, buf -> {
                buf.writeVarInt(what.size());
                what.forEach((table, k) -> {
                    buf.writeResourceLocation(table);
                    buf.writeUtf(k.hash());
                    buf.writeBoolean(k.forPlayers());
                    // Each on its own, as odds are read to the end of what they're in.
                    buf.writeByteArray(Blobs.toBytes(registries, b -> Codecs.writeOdds(b, k.odds())));
                });
            }));
            Files.createDirectories(file.getParent());
            SafeFiles.write(file, bytes);
            CacheFiles.keepNewest(file.getParent(), p -> p.toString().endsWith(SUFFIX), KEPT_FILES, file);
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't save the kept loot odds to {}", file, e);
        }
    }

    /** For tests: the file the odds are kept in now, or null. */
    public static Path fileNow() {
        return key == null ? null : file(key);
    }
}
