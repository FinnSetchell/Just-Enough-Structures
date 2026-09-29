package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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

    private JesServer() {
    }

    /** Called when the server starts and after /reload, since structures and loot can change. */
    public static void invalidate() {
        synchronized (CAPTURE_CACHE) {
            CAPTURE_CACHE.clear();
        }
        catalog = null;
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
