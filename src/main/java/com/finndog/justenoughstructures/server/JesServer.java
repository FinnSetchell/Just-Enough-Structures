package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import io.netty.buffer.Unpooled;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Answers client requests. Loader code calls these from its packet handlers, on the server thread. */
public final class JesServer {
    private static final int MAX_CONTAINER_SLOTS = 54;
    private static final int ODDS_ROLLS = 2000;
    private static final int CACHED_CAPTURES = 32;
    private static final int MAX_QUEUED_PER_PLAYER = 3;

    // Structure generation normally runs on worker threads, so structure code expects to run off the
    // server thread. One thread keeps a busy screen from flooding the server with work.
    private static final ExecutorService CAPTURES = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Just Enough Structures capture");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger TRANSFER_IDS = new AtomicInteger();
    private static final Map<String, byte[]> CAPTURE_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHED_CAPTURES;
        }
    };
    private static final Map<UUID, AtomicInteger> QUEUED = new ConcurrentHashMap<>();
    private static byte[] catalog;

    // The loot index captures every structure, so it gets its own thread and never holds up previews.
    private static final ExecutorService INDEXER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Just Enough Structures loot index");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile byte[] index;
    private static volatile int indexGeneration;
    private static volatile int indexDone;
    private static volatile int indexTotal;
    private static boolean indexing;

    private JesServer() {
    }

    /** Called when the server starts and after /reload, since structures and loot can change. */
    public static void invalidate() {
        synchronized (CAPTURE_CACHE) {
            CAPTURE_CACHE.clear();
        }
        catalog = null;
        indexGeneration++;
        index = null;
        indexing = false;
    }

    /**
     * Sends the loot index if it's ready. Otherwise, starts building it (once) and tells the player
     * how far along it is; the client asks again until it arrives.
     */
    public static void onRequestIndex(ServerPlayer player) {
        byte[] ready = index;
        if (ready != null) {
            sendBlob(player, JesNetwork.KIND_INDEX, 0, ready);
            return;
        }
        MinecraftServer server = player.getServer();
        if (!indexing) {
            indexing = true;
            int generation = indexGeneration;
            indexDone = 0;
            indexTotal = server.registryAccess().registryOrThrow(Registries.STRUCTURE).size();
            INDEXER.execute(() -> {
                long started = System.nanoTime();
                LootIndex built = LootIndex.build(server, done -> indexDone = done, () -> generation != indexGeneration);
                if (built == null || generation != indexGeneration) {
                    return;
                }
                byte[] payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeIndex(buf, built)));
                JustEnoughStructures.LOGGER.info("Indexed the loot of {} structures in {} s", indexTotal, (System.nanoTime() - started) / 1_000_000_000L);
                server.execute(() -> {
                    if (generation == indexGeneration) {
                        index = payload;
                        indexing = false;
                    }
                });
            });
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(indexDone);
        buf.writeVarInt(indexTotal);
        JesNetwork.send(player, JesNetwork.INDEX_PROGRESS, buf);
    }

    public static void onRequestCatalog(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (catalog == null) {
            List<StructureCatalog.Entry> entries = StructureCatalog.build(server.registryAccess());
            catalog = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCatalog(buf, entries)));
        }
        sendBlob(player, JesNetwork.KIND_CATALOG, 0, catalog);
    }

    public static void onRequestCapture(ServerPlayer player, int requestId, ResourceLocation structure, long seed) {
        MinecraftServer server = player.getServer();
        String key = structure + "@" + seed;
        byte[] cached;
        synchronized (CAPTURE_CACHE) {
            cached = CAPTURE_CACHE.get(key);
        }
        if (cached != null) {
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId, cached);
            return;
        }

        AtomicInteger queued = QUEUED.computeIfAbsent(player.getUUID(), id -> new AtomicInteger());
        if (queued.incrementAndGet() > MAX_QUEUED_PER_PLAYER) {
            queued.decrementAndGet();
            CaptureResult busy = CaptureResult.failure("Too many previews are generating at once, try again in a moment", List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, busy))));
            return;
        }

        UUID playerId = player.getUUID();
        CAPTURES.execute(() -> {
            byte[] payload;
            try {
                CaptureResult result = StructureCapture.capture(server, structure, seed);
                payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, result)));
                if (result.succeeded()) {
                    synchronized (CAPTURE_CACHE) {
                        CAPTURE_CACHE.put(key, payload);
                    }
                }
            } catch (RuntimeException e) {
                JustEnoughStructures.LOGGER.error("Previewing {} failed", structure, e);
                CaptureResult failed = CaptureResult.failure("Something went wrong generating this: " + e, List.of(), 0);
                payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, failed)));
            } finally {
                queued.decrementAndGet();
            }
            byte[] done = payload;
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null) {
                    sendBlob(target, JesNetwork.KIND_CAPTURE, requestId, done);
                }
            });
        });
    }

    public static void onRequestLoot(ServerPlayer player, int requestId, ResourceLocation table, long seed, int size) {
        List<ItemStack> items = LootRolls.fill(player.serverLevel(), table, seed, Math.max(1, Math.min(size, MAX_CONTAINER_SLOTS)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        Codecs.writeItems(buf, items);
        JesNetwork.send(player, JesNetwork.LOOT, buf);
    }

    public static void onRequestOdds(ServerPlayer player, int requestId, ResourceLocation table) {
        LootOdds odds = LootRolls.odds(player.serverLevel(), table, ODDS_ROLLS, table.hashCode());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        Codecs.writeOdds(buf, odds);
        JesNetwork.send(player, JesNetwork.ODDS, buf);
    }

    private static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /**
     * Finds the nearest structure of this kind in the player's dimension, like /locate, and needs the
     * same permission. Runs on the server thread because structure lookups load chunk data.
     */
    public static void onRequestLocate(ServerPlayer player, int requestId, ResourceLocation id) {
        Component reply = player.hasPermissions(2)
                ? locate(player.serverLevel(), player.blockPosition(), id)
                : Component.translatable("screen.justenoughstructures.locate_no_permission");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        buf.writeComponent(reply);
        JesNetwork.send(player, JesNetwork.LOCATE, buf);
    }

    /** The nearest structure of this kind to {@code from}, as a sentence for the player. */
    public static Component locate(ServerLevel level, BlockPos from, ResourceLocation id) {
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, id));
        // Searching for something that can't generate here makes the game generate chunk after
        // chunk looking for it, which can stall the server for minutes, so rule that out first.
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Component.translatable("screen.justenoughstructures.locate_structures_off");
        }
        if (holder.isEmpty() || level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
            return Component.translatable("screen.justenoughstructures.locate_wrong_dimension");
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
        if (found == null) {
            return Component.translatable("screen.justenoughstructures.locate_none");
        }
        BlockPos at = found.getFirst();
        int dx = at.getX() - from.getX();
        int dz = at.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String direction = DIRECTIONS[Math.floorMod((int) Math.round(angle / 45.0), 8)];
        return Component.translatable("screen.justenoughstructures.locate_found",
                String.format("%,d", (int) Math.sqrt((double) dx * dx + (double) dz * dz)),
                Component.translatable("screen.justenoughstructures.direction." + direction), at.getX(), at.getZ());
    }

    private static void sendBlob(ServerPlayer player, int kind, int requestId, byte[] compressed) {
        List<byte[]> parts = Blobs.split(compressed);
        int transferId = TRANSFER_IDS.incrementAndGet();
        for (int i = 0; i < parts.size(); i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new Blobs.Part(transferId, kind, requestId, i, parts.size(), parts.get(i)).write(buf);
            JesNetwork.send(player, JesNetwork.TRANSFER, buf);
        }
    }
}
