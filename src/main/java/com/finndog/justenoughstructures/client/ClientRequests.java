package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.screen.Nav;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client end of the protocol: sends requests and hands back futures that complete when the answer
 * arrives. Everything here runs on the client thread, and every future completes on it, though a
 * structure is unpacked off it first.
 */
public final class ClientRequests {
    private static ClientSender sender;
    private static int nextRequestId = 1;
    private static CompletableFuture<List<StructureCatalog.Entry>> catalog;
    private static final Map<Integer, CompletableFuture<Codecs.CaptureReply>> CAPTURES = new HashMap<>();
    /** Requests whose answer is kept for next time, so what comes back takes the place of any kept before. */
    private static final Map<Integer, Keeping> KEEPING = new HashMap<>();
    private static int reloads;
    private static boolean indexWanted;
    private static final Map<Integer, CompletableFuture<List<ItemStack>>> LOOT = new HashMap<>();
    private static final Map<Integer, CompletableFuture<LootOdds>> ODDS = new HashMap<>();
    private static final Map<ResourceLocation, CompletableFuture<LootOdds>> ODDS_BY_TABLE = new HashMap<>();
    private static final ArrayDeque<Map.Entry<ResourceLocation, CompletableFuture<LootOdds>>> ODDS_WAITING = new ArrayDeque<>();
    private static final int ODDS_IN_FLIGHT = 2;
    private static final Map<Integer, CompletableFuture<Component>> LOCATES = new HashMap<>();
    private static final Map<Integer, Transfer> TRANSFERS = new HashMap<>();
    private static final List<Consumer<LootIndex>> INDEX_LISTENERS = new ArrayList<>();
    private static CompletableFuture<LootIndex> index;
    private static int indexDone;
    private static int indexTotal;
    private static long lastIndexPoll;
    // What the server needs to locate and teleport. Until it says, the same as /locate and /tp.
    private static int locatePermission = 2;
    private static int teleportPermission = 2;
    private static boolean compassSearch;
    private static boolean packTools;
    private static Map<ResourceLocation, LootOverrides.Status> overrides = Map.of();
    private static int nextUploadId = 1;
    private static final Map<Integer, CompletableFuture<Codecs.TableReply>> TABLES = new HashMap<>();
    private static final Map<Integer, CompletableFuture<EditReply>> EDITS = new HashMap<>();
    private static int structureChanges;
    /** Waiting for what Pack tools shows, and the last that arrived. */
    private static CompletableFuture<PackToolsState> tools;
    private static PackToolsState lastTools;

    private ClientRequests() {
    }

    public static void setSender(ClientSender clientSender) {
        sender = clientSender;
    }

    /** False when the server doesn't have the mod, or has another version of it, so there's nobody to ask. */
    public static boolean serverSupported() {
        return sender != null && sender.canSend(JesNetwork.REQUEST_CATALOG);
    }

    /** True when the server has the mod, but a version that talks differently. */
    public static boolean serverOnOtherVersion() {
        return sender != null && !serverSupported() && sender.sendable().stream().anyMatch(JesNetwork::isJesChannel);
    }

    /** Forget everything tied to the current connection. */
    public static void reset() {
        catalog = null;
        CAPTURES.values().forEach(f -> f.cancel(false));
        LOOT.values().forEach(f -> f.cancel(false));
        ODDS.values().forEach(f -> f.cancel(false));
        CAPTURES.clear();
        KEEPING.clear();
        LOOT.clear();
        ODDS.clear();
        ODDS_WAITING.forEach(e -> e.getValue().cancel(false));
        ODDS_WAITING.clear();
        ODDS_BY_TABLE.clear();
        LOCATES.values().forEach(f -> f.cancel(false));
        LOCATES.clear();
        TRANSFERS.clear();
        Thumbnails.clear();
        KeptPreviews.use(null, false);
        Nav.forget();
        if (index != null) {
            index.cancel(false);
        }
        index = null;
        indexDone = 0;
        indexTotal = 0;
        FoundIn.clear();
        locatePermission = 2;
        teleportPermission = 2;
        compassSearch = false;
        packTools = false;
        overrides = Map.of();
        TABLES.values().forEach(f -> f.cancel(false));
        TABLES.clear();
        EDITS.values().forEach(f -> f.cancel(false));
        EDITS.clear();
        if (tools != null) {
            tools.cancel(false);
        }
        tools = null;
        lastTools = null;
    }

    // ------------------------------------------------------------------ Pack tools

    /** Asks for everything Pack tools shows: the server's rules, edited tables, changed containers and more. */
    public static CompletableFuture<PackToolsState> tools() {
        if (tools == null || tools.isDone()) {
            tools = new CompletableFuture<>();
            send(JesNetwork.REQUEST_TOOLS, buf -> {
            });
        }
        return tools;
    }

    /** What Pack tools last heard from the server, or null before it has. */
    public static PackToolsState lastTools() {
        return lastTools;
    }

    /** Saves the server's rules in server.json5. They apply straight away, except using changed containers. */
    public static CompletableFuture<EditReply> saveRules(ServerConfig.Settings settings) {
        return toolsAction(JesNetwork.TOOLS_RULES, buf -> Codecs.writeSettings(buf, settings));
    }

    /** Saves what players are told about a structure. It applies straight away. */
    public static CompletableFuture<EditReply> saveStructure(ResourceLocation id, String notes, boolean secret) {
        return toolsAction(JesNetwork.TOOLS_STRUCTURE, buf -> {
            buf.writeResourceLocation(id);
            buf.writeUtf(notes.length() > Codecs.MAX_NOTES ? notes.substring(0, Codecs.MAX_NOTES) : notes, Codecs.MAX_NOTES);
            buf.writeBoolean(secret);
        });
    }

    /** Runs /reload on the server, which Pack tools users can do even without the command. */
    public static CompletableFuture<EditReply> reloadServer() {
        return toolsAction(JesNetwork.TOOLS_RELOAD, buf -> {
        });
    }

    private static CompletableFuture<EditReply> toolsAction(int action, Consumer<FriendlyByteBuf> payload) {
        int requestId = nextRequestId++;
        CompletableFuture<EditReply> future = new CompletableFuture<>();
        EDITS.put(requestId, future);
        send(JesNetwork.TOOLS_ACTION, buf -> {
            buf.writeVarInt(requestId);
            buf.writeVarInt(action);
            payload.accept(buf);
        });
        return future;
    }

    /** How many times the list of structures has changed since joining, after a /reload or Pack tools, so the browser knows to ask again. */
    public static int structureChanges() {
        return structureChanges;
    }

    /** What the server said about an edit: a message, and for a draft, what it would give. */
    public record EditReply(Component message, LootOdds odds) {
    }

    /** Asks which loot tables have an override, for players who can edit them. The answer marks them in the browser. */
    public static void requestOverrides() {
        if (serverSupported() && canUsePackTools()) {
            send(JesNetwork.REQUEST_OVERRIDES, buf -> {
            });
        }
    }

    public static void onOverrides(Map<ResourceLocation, LootOverrides.Status> statuses) {
        overrides = Map.copyOf(statuses);
    }

    /** How a loot table's override stands, or null if it isn't edited (or this player can't edit loot). */
    public static LootOverrides.Status overrideStatus(String table) {
        ResourceLocation id = table == null ? null : ResourceLocation.tryParse(table);
        return id == null ? null : overrides.get(id);
    }

    /** Whether the server lets this player use Pack tools: edit loot tables and chests, hide structures and change its settings. */
    public static boolean canUsePackTools() {
        return packTools;
    }

    /**
     * Whether the browser shows Pack tools: its button, the popup's icons and the marks on edited
     * tables. Not for players who can't use it, nor for those who've hidden it in the settings.
     */
    public static boolean showsPackTools() {
        return packTools && !ClientState.hidePackTools;
    }

    /** A loot table for the editor: as it is now, as the mods have it, and whether it's overridden. */
    public static CompletableFuture<Codecs.TableReply> table(ResourceLocation id) {
        int requestId = nextRequestId++;
        CompletableFuture<Codecs.TableReply> future = new CompletableFuture<>();
        TABLES.put(requestId, future);
        send(JesNetwork.REQUEST_TABLE, buf -> {
            buf.writeVarInt(requestId);
            buf.writeResourceLocation(id);
        });
        return future;
    }

    /** Rolls an edit that isn't saved yet. */
    public static CompletableFuture<EditReply> draftOdds(ResourceLocation id, String json) {
        return upload(JesNetwork.KIND_DRAFT, id, json);
    }

    /** Saves an edit as an override. It applies after /reload. */
    public static CompletableFuture<EditReply> saveTable(ResourceLocation id, String json) {
        return upload(JesNetwork.KIND_SAVE, id, json);
    }

    /** Keeps an override whose original changed, or turns one off. */
    public static CompletableFuture<EditReply> tableAction(ResourceLocation id, int action) {
        int requestId = nextRequestId++;
        CompletableFuture<EditReply> future = new CompletableFuture<>();
        EDITS.put(requestId, future);
        send(JesNetwork.TABLE_ACTION, buf -> {
            buf.writeVarInt(requestId);
            buf.writeResourceLocation(id);
            buf.writeVarInt(action);
        });
        return future;
    }

    /** Points a container in a template at another loot table, or with a null table, back at its own. */
    public static CompletableFuture<EditReply> containerAction(ResourceLocation template, BlockPos pos, ResourceLocation table) {
        int requestId = nextRequestId++;
        CompletableFuture<EditReply> future = new CompletableFuture<>();
        EDITS.put(requestId, future);
        send(JesNetwork.CONTAINER_ACTION, buf -> {
            buf.writeVarInt(requestId);
            buf.writeResourceLocation(template);
            buf.writeBlockPos(pos);
            buf.writeBoolean(table != null);
            if (table != null) {
                buf.writeResourceLocation(table);
            }
        });
        return future;
    }

    /** Takes a spawner in a template back to how it was. */
    public static CompletableFuture<EditReply> undoSpawner(ResourceLocation template, BlockPos pos) {
        return spawnerAction(template, pos, null, null);
    }

    /**
     * Gives a spawner in a template another mob, "" for none, and makes it {@code block}: a spawner
     * or a trial spawner. With a null mob, it goes back to how it was.
     */
    public static CompletableFuture<EditReply> spawnerAction(ResourceLocation template, BlockPos pos, String mob, ResourceLocation block) {
        int requestId = nextRequestId++;
        CompletableFuture<EditReply> future = new CompletableFuture<>();
        EDITS.put(requestId, future);
        send(JesNetwork.SPAWNER_ACTION, buf -> {
            buf.writeVarInt(requestId);
            buf.writeResourceLocation(template);
            buf.writeBlockPos(pos);
            buf.writeBoolean(mob != null);
            if (mob != null) {
                buf.writeUtf(mob, 256);
                buf.writeResourceLocation(block);
            }
        });
        return future;
    }

    /** Fills a container of {@code size} slots from an edit that isn't saved yet, as {@link #loot} does from a saved table. */
    public static CompletableFuture<List<ItemStack>> draftRoll(ResourceLocation id, String json, long seed, int size) {
        int requestId = nextRequestId++;
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();
        LOOT.put(requestId, future);
        sendUpload(JesNetwork.KIND_DRAFT_ROLL, requestId, buf -> Codecs.writeDraftRoll(buf, id, json, seed, size));
        return future;
    }

    private static CompletableFuture<EditReply> upload(int kind, ResourceLocation id, String json) {
        int requestId = nextRequestId++;
        CompletableFuture<EditReply> future = new CompletableFuture<>();
        EDITS.put(requestId, future);
        sendUpload(kind, requestId, buf -> Codecs.writeDraft(buf, id, json));
        return future;
    }

    /** Something bigger than one packet, sent in parts. */
    private static void sendUpload(int kind, int requestId, Consumer<FriendlyByteBuf> writer) {
        byte[] compressed = Blobs.deflate(Blobs.toBytes(registries(), writer::accept));
        List<byte[]> parts = Blobs.split(compressed, Blobs.UPLOAD_PART_SIZE);
        int transferId = nextUploadId++;
        for (int i = 0; i < parts.size(); i++) {
            Blobs.Part part = new Blobs.Part(transferId, kind, requestId, i, parts.size(), parts.get(i));
            send(JesNetwork.UPLOAD, part::write);
        }
    }

    public static void onEditReply(int requestId, Component message, LootOdds odds) {
        CompletableFuture<EditReply> future = EDITS.remove(requestId);
        if (future != null) {
            future.complete(new EditReply(message, odds));
        }
    }

    /** Whether the server can set a held structure compass searching. */
    public static boolean canPointCompass() {
        return compassSearch;
    }

    /** Asks the server to set the held compass searching for the structure. Answers like a locate. */
    public static CompletableFuture<Component> pointCompass(ResourceLocation structure) {
        int id = nextRequestId++;
        CompletableFuture<Component> future = new CompletableFuture<>();
        LOCATES.put(id, future);
        send(JesNetwork.REQUEST_COMPASS, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(structure);
        });
        return future;
    }

    public static boolean canLocate() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && Players.hasPermission(mc.player, locatePermission);
    }

    public static boolean canTeleport() {
        Minecraft mc = Minecraft.getInstance();
        return canLocate() && Players.hasPermission(mc.player, teleportPermission);
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

    /**
     * Calls {@code listener} with every loot index that arrives, including the new one after a
     * /reload, which is then fetched straight away rather than when the browser next wants it.
     */
    public static void onEachIndex(Consumer<LootIndex> listener) {
        INDEX_LISTENERS.add(listener);
    }

    public static boolean indexReady() {
        return index != null && index.isDone() && !index.isCompletedExceptionally() && !index.isCancelled();
    }

    /** How much of the index is built, from 0 to 1, or -1 before the server has said. */
    public static float indexProgress() {
        return indexTotal <= 0 ? -1f : (float) indexDone / indexTotal;
    }

    public static void tick() {
        if (indexWanted && index == null && serverSupported()) {
            index();
            // The structure list is what saved thumbnails are checked against, so rows can have pictures.
            catalog();
        }
        if (index != null && !index.isDone() && System.currentTimeMillis() - lastIndexPoll > 1500) {
            pollIndex();
        }
    }

    /**
     * For recipe viewers, which load before it's known whether the server has JES: asks for the loot
     * index as soon as it is, and again on every server joined after.
     */
    public static void wantIndex() {
        indexWanted = true;
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

    /**
     * Asks the server for a structure. A preview, the one the player is looking at, goes ahead of the
     * list's pictures on the server. A first view the game kept from before is only sent again if
     * it's changed.
     */
    public static CompletableFuture<Codecs.CaptureReply> capture(ResourceLocation structure, long seed, boolean preview) {
        int id = nextRequestId++;
        CompletableFuture<Codecs.CaptureReply> future = new CompletableFuture<>();
        CAPTURES.put(id, future);
        long kept = 0;
        if (seed == StructureCapture.defaultSeed(structure) && KeptPreviews.inUse()) {
            KEEPING.put(id, new Keeping(structure, KeptPreviews.version(structure), !preview));
            if (preview) {
                kept = KeptPreviews.kept(structure);
            }
        }
        requestCapture(id, structure, seed, preview, kept);
        return future;
    }

    /**
     * The lighter copy of a structure its list picture is drawn from. One the game kept, of the
     * structure as the server has it now, is used without asking the server at all.
     */
    public static CompletableFuture<Codecs.CaptureReply> picture(ResourceLocation structure) {
        String version = KeptPreviews.version(structure);
        if (!KeptPreviews.hasPicture(structure, version)) {
            JesLog.debug("The picture of {} is asked for from the server", structure);
            return capture(structure, StructureCapture.defaultSeed(structure), false);
        }
        int id = nextRequestId++;
        CompletableFuture<Codecs.CaptureReply> future = new CompletableFuture<>();
        CAPTURES.put(id, future);
        KEEPING.put(id, new Keeping(structure, version, true));
        KeptPreviews.readPicture(structure, version, payload -> Minecraft.getInstance().execute(() -> {
            // The player may have left while it was read.
            if (CAPTURES.get(id) != future || future.isDone()) {
                CAPTURES.remove(id, future);
                KEEPING.remove(id);
                return;
            }
            if (payload != null) {
                JesLog.debug("The picture of {} is drawn from the copy kept from before", structure);
                onCapture(id, payload, true);
            } else {
                requestCapture(id, structure, StructureCapture.defaultSeed(structure), false, 0);
            }
        }));
        return future;
    }

    private static void requestCapture(int id, ResourceLocation structure, long seed, boolean preview, long kept) {
        send(JesNetwork.REQUEST_CAPTURE, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(structure);
            buf.writeLong(seed);
            buf.writeBoolean(preview);
            buf.writeLong(kept);
        });
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

    /** Finds the nearest one, and with {@code teleport} also takes the player there. */
    public static CompletableFuture<Component> locate(ResourceLocation structure, boolean teleport) {
        int id = nextRequestId++;
        CompletableFuture<Component> future = new CompletableFuture<>();
        LOCATES.put(id, future);
        send(JesNetwork.REQUEST_LOCATE, buf -> {
            buf.writeVarInt(id);
            buf.writeResourceLocation(structure);
            buf.writeBoolean(teleport);
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

    /**
     * Odds cost the server a couple of thousand loot rolls each, and the found-in list can want a
     * hundred tables at once, so only a couple are asked for at a time and the rest wait their turn.
     */
    private static CompletableFuture<LootOdds> requestOdds(ResourceLocation table) {
        CompletableFuture<LootOdds> future = new CompletableFuture<>();
        ODDS_WAITING.add(Map.entry(table, future));
        sendWaitingOdds();
        return future;
    }

    private static void sendWaitingOdds() {
        while (ODDS.size() < ODDS_IN_FLIGHT && !ODDS_WAITING.isEmpty()) {
            Map.Entry<ResourceLocation, CompletableFuture<LootOdds>> next = ODDS_WAITING.poll();
            if (next.getValue().isCancelled()) {
                continue;
            }
            int id = nextRequestId++;
            ODDS.put(id, next.getValue());
            send(JesNetwork.REQUEST_ODDS, buf -> {
                buf.writeVarInt(id);
                buf.writeResourceLocation(next.getKey());
            });
        }
    }

    // ------------------------------------------------------------------ incoming, already on the client thread

    public static void onTransferPart(Blobs.Part part) {
        Transfer transfer = TRANSFERS.computeIfAbsent(part.transferId(), id -> new Transfer(part.count()));
        transfer.accept(part);
        if (!transfer.complete()) {
            return;
        }
        TRANSFERS.remove(part.transferId());
        if (part.kind() == JesNetwork.KIND_CAPTURE) {
            onCapture(part.requestId(), transfer.bytes(), false);
            return;
        }
        if (part.kind() == JesNetwork.KIND_SAME) {
            onSame(part.requestId());
            return;
        }
        FriendlyByteBuf buf = Blobs.fromBytes(registries(), Blobs.inflate(transfer.bytes()));
        if (part.kind() == JesNetwork.KIND_CATALOG) {
            String fingerprint = Codecs.readFingerprint(buf);
            List<StructureCatalog.Entry> entries = Codecs.readCatalog(buf);
            Thumbnails.onCatalog(fingerprint, entries);
            // A world played here saves its own first views, so they're only kept from other computers.
            KeptPreviews.use(fingerprint, !Minecraft.getInstance().hasSingleplayerServer());
            KeptPreviews.structures(entries);
            if (catalog == null) {
                catalog = new CompletableFuture<>();
            }
            catalog.complete(entries);
        } else if (part.kind() == JesNetwork.KIND_INDEX) {
            LootIndex built = Codecs.readIndex(buf);
            FoundIn.rebuild(built);
            // The server sends a new one when it changes, after a /reload for instance.
            if (index == null || index.isDone()) {
                index = new CompletableFuture<>();
            }
            index.complete(built);
            INDEX_LISTENERS.forEach(listener -> listener.accept(built));
        } else if (part.kind() == JesNetwork.KIND_TOOLS) {
            lastTools = Codecs.readTools(buf);
            if (tools != null) {
                tools.complete(lastTools);
            }
        } else if (part.kind() == JesNetwork.KIND_TABLE) {
            CompletableFuture<Codecs.TableReply> future = TABLES.remove(part.requestId());
            if (future != null) {
                future.complete(Codecs.readTable(buf));
            }
        }
    }

    /**
     * Unpacking a big structure takes a good part of a second, so it's done off the render thread,
     * and not at all when nobody is waiting for it any more, like a preview the player has already
     * clicked away from. A first view that unpacks is kept for next time. {@code wasKept} is true
     * for the copy that was kept, which is forgotten if it won't unpack.
     */
    private static void onCapture(int requestId, byte[] compressed, boolean wasKept) {
        RegistryAccess registries = registries();
        CompletableFuture<Codecs.CaptureReply> future = CAPTURES.remove(requestId);
        Keeping keeping = KEEPING.remove(requestId);
        if (future == null || future.isDone()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
                    Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(registries, Blobs.inflate(compressed)));
                    if (keeping != null && reply.result().succeeded() && keeping.structure().equals(reply.id())) {
                        keep(keeping, compressed, wasKept, reply, registries);
                    }
                    return reply;
                }, Util.backgroundExecutor())
                .whenCompleteAsync((reply, error) -> {
                    if (error != null) {
                        JesLog.errorOnce("capture-read", "Couldn't read a structure from the server", error);
                        if (wasKept && keeping != null && keeping.picture()) {
                            KeptPreviews.forgetPicture(keeping.structure());
                        } else if (wasKept && keeping != null) {
                            KeptPreviews.forget(keeping.structure());
                        }
                        future.completeExceptionally(error);
                    } else {
                        future.complete(reply);
                    }
                }, Minecraft.getInstance());
    }

    /**
     * Keeps what came back for next time. A first view also gives the copy its list picture is drawn
     * from, so that's never asked for. That's made once the preview's on its way, as for a big
     * structure it takes a moment.
     */
    private static void keep(Keeping keeping, byte[] compressed, boolean wasKept, Codecs.CaptureReply reply, RegistryAccess registries) {
        ResourceLocation structure = keeping.structure();
        if (keeping.picture()) {
            if (!wasKept) {
                KeptPreviews.keepPicture(structure, keeping.version(), compressed);
            }
            return;
        }
        if (!wasKept) {
            KeptPreviews.keep(structure, compressed);
        }
        if (keeping.version() == null || KeptPreviews.hasPicture(structure, keeping.version())) {
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            try {
                CaptureResult result = reply.result();
                CaptureResult picture = CaptureResult.success(result.snapshot().forPicture(), result.attempts(), result.millis());
                KeptPreviews.keepPicture(structure, keeping.version(),
                        Blobs.deflate(Blobs.toBytes(registries, buf -> Codecs.writeCapture(buf, structure, reply.seed(), picture))));
            } catch (RuntimeException e) {
                JesLog.debug("Couldn't make the picture's copy of {}", structure, e);
            }
        });
    }

    /**
     * The server says the first view the game kept is the one it would send, so that's used instead.
     * If it's gone since, it's asked for after all.
     */
    private static void onSame(int requestId) {
        Keeping keeping = KEEPING.get(requestId);
        CompletableFuture<Codecs.CaptureReply> future = CAPTURES.get(requestId);
        if (keeping == null || keeping.picture() || future == null || future.isDone()) {
            CAPTURES.remove(requestId);
            KEEPING.remove(requestId);
            return;
        }
        ResourceLocation structure = keeping.structure();
        KeptPreviews.read(structure, payload -> Minecraft.getInstance().execute(() -> {
            // The player may have left, or moved on to another, while it was read.
            if (CAPTURES.get(requestId) != future || future.isDone()) {
                CAPTURES.remove(requestId, future);
                KEEPING.remove(requestId);
                return;
            }
            if (payload != null) {
                JesLog.debug("{} hasn't changed on the server, so the kept preview is shown", structure);
                onCapture(requestId, payload, true);
            } else {
                JesLog.debug("The kept preview of {} has gone, so it's asked for again", structure);
                requestCapture(requestId, structure, StructureCapture.defaultSeed(structure), true, 0);
            }
        }));
    }

    /**
     * The server's locate settings. After a /reload the structure list and loot may have changed
     * too, so what's cached is dropped and fetched again the next time it's wanted.
     */
    public static void onSettings(int locate, int teleport, boolean reloaded, boolean compass, boolean canUsePackTools, boolean structuresChanged) {
        locatePermission = locate;
        teleportPermission = teleport;
        compassSearch = compass;
        packTools = canUsePackTools;
        // The browser asks which tables are edited as it opens, which the first time is before the
        // server has said this player may see them, and a /reload can change them, so ask now.
        requestOverrides();
        if (structuresChanged) {
            structureChanges++;
            if (catalog != null && catalog.isDone()) {
                catalog = null;
            }
        }
        if (!reloaded) {
            return;
        }
        reloads++;
        if (catalog != null && catalog.isDone()) {
            catalog = null;
        }
        if (index != null && index.isDone()) {
            index = null;
            indexDone = 0;
            indexTotal = 0;
            FoundIn.clear();
            if (!INDEX_LISTENERS.isEmpty()) {
                index();
            }
        }
        ODDS_BY_TABLE.clear();
    }

    /** How many times the server has reloaded since joining, so screens can tell when to fetch again. */
    public static int reloads() {
        return reloads;
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
        sendWaitingOdds();
    }

    /** The game's registries as the server sent them, which reading items and text needs from 1.20.5. */
    private static RegistryAccess registries() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.registryAccess() : RegistryAccess.EMPTY;
    }

    private static void send(ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        // NeoForge throws on a payload the server never agreed to, which would take the game down.
        if (sender == null || !sender.canSend(channel)) {
            return;
        }
        FriendlyByteBuf buf = Blobs.buffer(registries());
        writer.accept(buf);
        sender.send(channel, buf);
    }

    /**
     * A request whose answer is kept: the structure's first view, or with {@code picture} the copy its
     * list picture is drawn from, and which version of the structure the server had when it was asked.
     */
    private record Keeping(ResourceLocation structure, String version, boolean picture) {
    }

    public interface ClientSender {
        boolean canSend(ResourceLocation channel);

        /** Every channel the server listens on. */
        Collection<ResourceLocation> sendable();

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
