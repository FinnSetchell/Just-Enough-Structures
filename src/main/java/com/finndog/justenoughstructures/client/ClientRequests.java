package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client end of the protocol: sends requests and hands back futures that complete when the answer
 * arrives. Everything here runs on the client thread.
 */
public final class ClientRequests {
    private static ClientSender sender;
    private static int nextRequestId = 1;
    private static CompletableFuture<List<StructureCatalog.Entry>> catalog;
    private static final Map<Integer, CompletableFuture<Codecs.CaptureReply>> CAPTURES = new HashMap<>();
    private static final Map<Integer, CompletableFuture<List<ItemStack>>> LOOT = new HashMap<>();
    private static final Map<Integer, CompletableFuture<LootOdds>> ODDS = new HashMap<>();
    private static final Map<ResourceLocation, CompletableFuture<LootOdds>> ODDS_BY_TABLE = new HashMap<>();
    private static final Map<Integer, CompletableFuture<Component>> LOCATES = new HashMap<>();
    private static final Map<Integer, Transfer> TRANSFERS = new HashMap<>();
    private static CompletableFuture<LootIndex> index;
    private static int indexDone;
    private static int indexTotal;
    private static long lastIndexPoll;

    private ClientRequests() {
    }

    public static void setSender(ClientSender clientSender) {
        sender = clientSender;
    }

    /** False when the server doesn't have the mod, so there's nobody to ask. */
    public static boolean serverSupported() {
        return sender != null && sender.canSend(JesNetwork.REQUEST_CATALOG);
    }

    /** Forget everything tied to the current connection. */
    public static void reset() {
        catalog = null;
        CAPTURES.values().forEach(f -> f.cancel(false));
        LOOT.values().forEach(f -> f.cancel(false));
        ODDS.values().forEach(f -> f.cancel(false));
        CAPTURES.clear();
        LOOT.clear();
        ODDS.clear();
        ODDS_BY_TABLE.clear();
        LOCATES.values().forEach(f -> f.cancel(false));
        LOCATES.clear();
        TRANSFERS.clear();
        Thumbnails.clear();
        if (index != null) {
            index.cancel(false);
        }
        index = null;
        indexDone = 0;
        indexTotal = 0;
        FoundIn.clear();
    }

    /**
     * The loot index, built by the server the first time anyone asks. Until it's ready, {@link #tick()}
     * keeps asking so the progress stays current.
     */
    public static CompletableFuture<LootIndex> index() {
        if (index == null || index.isCancelled()) {
            index = new CompletableFuture<>();
            pollIndex();
        }
        return index;
    }

    public static boolean indexReady() {
        return index != null && index.isDone() && !index.isCompletedExceptionally() && !index.isCancelled();
    }

    /** How much of the index is built, from 0 to 1, or -1 before the server has said. */
    public static float indexProgress() {
        return indexTotal <= 0 ? -1f : (float) indexDone / indexTotal;
    }

    public static void tick() {
        if (index != null && !index.isDone() && System.currentTimeMillis() - lastIndexPoll > 1500) {
            pollIndex();
        }
    }

    private static void pollIndex() {
        lastIndexPoll = System.currentTimeMillis();
        send(JesNetwork.REQUEST_INDEX, buf -> {
        });
    }

    public static void onIndexProgress(int done, int total) {
        indexDone = done;
        indexTotal = total;
    }

    public static CompletableFuture<List<StructureCatalog.Entry>> catalog() {
        if (catalog == null || catalog.isCompletedExceptionally() || catalog.isCancelled()) {
            catalog = new CompletableFuture<>();
            send(JesNetwork.REQUEST_CATALOG, buf -> {
            });
        }
        return catalog;
    }

    public static CompletableFuture<Codecs.CaptureReply> capture(ResourceLocation structure, long seed) {
        int id = nextRequestId++;
        CompletableFuture<Codecs.CaptureReply> future = new CompletableFuture<>();
        CAPTURES.put(id, future);
        send(JesNetwork.REQUEST_CAPTURE, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(structure);
            buf.writeLong(seed);
        });
        return future;
    }

    public static CompletableFuture<List<ItemStack>> loot(ResourceLocation table, long seed, int size) {
        int id = nextRequestId++;
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();
        LOOT.put(id, future);
        send(JesNetwork.REQUEST_LOOT, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(table);
            buf.writeLong(seed);
            buf.writeVarInt(size);
        });
        return future;
    }

    public static CompletableFuture<Component> locate(ResourceLocation structure) {
        int id = nextRequestId++;
        CompletableFuture<Component> future = new CompletableFuture<>();
        LOCATES.put(id, future);
        send(JesNetwork.REQUEST_LOCATE, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(structure);
        });
        return future;
    }

    public static void onLocate(int requestId, Component reply) {
        CompletableFuture<Component> future = LOCATES.remove(requestId);
        if (future != null) {
            future.complete(reply);
        }
    }

    /** Odds for a loot table, asked for once per connection and shared by everything that wants them. */
    public static CompletableFuture<LootOdds> odds(ResourceLocation table) {
        CompletableFuture<LootOdds> cached = ODDS_BY_TABLE.get(table);
        if (cached != null && !cached.isCancelled()) {
            return cached;
        }
        CompletableFuture<LootOdds> future = requestOdds(table);
        ODDS_BY_TABLE.put(table, future);
        return future;
    }

    private static CompletableFuture<LootOdds> requestOdds(ResourceLocation table) {
        int id = nextRequestId++;
        CompletableFuture<LootOdds> future = new CompletableFuture<>();
        ODDS.put(id, future);
        send(JesNetwork.REQUEST_ODDS, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(table);
        });
        return future;
    }

    // ------------------------------------------------------------------ incoming, already on the client thread

    public static void onTransferPart(Blobs.Part part) {
        Transfer transfer = TRANSFERS.computeIfAbsent(part.transferId(), id -> new Transfer(part.count()));
        transfer.accept(part);
        if (!transfer.complete()) {
            return;
        }
        TRANSFERS.remove(part.transferId());
        FriendlyByteBuf buf = Blobs.fromBytes(Blobs.inflate(transfer.bytes()));
        if (part.kind() == JesNetwork.KIND_CATALOG) {
            List<StructureCatalog.Entry> entries = Codecs.readCatalog(buf);
            if (catalog == null) {
                catalog = new CompletableFuture<>();
            }
            catalog.complete(entries);
        } else if (part.kind() == JesNetwork.KIND_INDEX) {
            LootIndex built = Codecs.readIndex(buf);
            FoundIn.rebuild(built);
            if (index == null) {
                index = new CompletableFuture<>();
            }
            index.complete(built);
        } else if (part.kind() == JesNetwork.KIND_CAPTURE) {
            CompletableFuture<Codecs.CaptureReply> future = CAPTURES.remove(part.requestId());
            if (future != null) {
                future.complete(Codecs.readCapture(buf));
            }
        }
    }

    public static void onLoot(int requestId, List<ItemStack> items) {
        CompletableFuture<List<ItemStack>> future = LOOT.remove(requestId);
        if (future != null) {
            future.complete(items);
        }
    }

    public static void onOdds(int requestId, LootOdds odds) {
        CompletableFuture<LootOdds> future = ODDS.remove(requestId);
        if (future != null) {
            future.complete(odds);
        }
    }

    private static void send(ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        if (sender == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        sender.send(channel, buf);
    }

    public interface ClientSender {
        boolean canSend(ResourceLocation channel);

        void send(ResourceLocation channel, FriendlyByteBuf buf);
    }

    private static final class Transfer {
        private final byte[][] parts;
        private int received;

        Transfer(int count) {
            this.parts = new byte[count][];
        }

        void accept(Blobs.Part part) {
            if (part.index() < parts.length && parts[part.index()] == null) {
                parts[part.index()] = part.data();
                received++;
            }
        }

        boolean complete() {
            return received == parts.length;
        }

        byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] part : parts) {
                out.writeBytes(part);
            }
            return out.toByteArray();
        }
    }
}
