package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.screen.Nav;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.StructureNameFiles;
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
import java.util.function.IntConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
//? if emi {
import com.finndog.justenoughstructures.JustEnoughStructures;
//?}

/**
 * Client side of the protocol: sends requests and returns futures that complete when the reply
 * arrives. Runs on the client thread unless a method says otherwise, and every future completes on
 * it, though structures are unpacked on a background thread first.
 */
public final class ClientRequests {
    private static ClientSender sender;
    private static int nextRequestId = 1;
    private static CompletableFuture<List<StructureCatalog.Entry>> catalog;
    private static final Map<Integer, CompletableFuture<Codecs.CaptureReply>> CAPTURES = new HashMap<>();
    /** Requests whose replies go into the kept-previews cache, replacing any older copy. */
    private static final Map<Integer, Keeping> KEEPING = new HashMap<>();
    private static int reloads;
    private static boolean indexWanted;
    private static boolean viewersLookedFor;
    /** The loot index as it arrives, readable from other threads such as EMI's loader. */
    private static final IndexArrival ARRIVAL = new IndexArrival();
    private static final Map<Integer, CompletableFuture<List<ItemStack>>> LOOT = new HashMap<>();
    private static final Map<Integer, CompletableFuture<LootOdds>> ODDS = new HashMap<>();
    private static final Map<ResourceLocation, CompletableFuture<LootOdds>> ODDS_BY_TABLE = new HashMap<>();
    private static final ArrayDeque<Map.Entry<ResourceLocation, CompletableFuture<LootOdds>>> ODDS_WAITING = new ArrayDeque<>();
    private static final int ODDS_IN_FLIGHT = 2;
    private static final Map<Integer, CompletableFuture<Component>> LOCATES = new HashMap<>();
    private static final Map<Integer, Transfer> TRANSFERS = new HashMap<>();
    private static final List<Consumer<LootIndex>> INDEX_LISTENERS = new ArrayList<>();
    private static final List<Runnable> STOPPED_LISTENERS = new ArrayList<>();
    private static CompletableFuture<LootIndex> index;
    private static int indexDone;
    private static int indexTotal;
    // The server's index build failed, so nothing more comes until it reloads.
    private static boolean indexFailed;
    private static long lastIndexPoll;
    // Permission levels the server requires to locate and teleport. Until it says, those of /locate and /tp.
    private static int locatePermission = 2;
    private static int teleportPermission = 2;
    private static boolean compassSearch;
    private static boolean packTools;
    private static Map<ResourceLocation, LootOverrides.Status> overrides = Map.of();
    private static int nextUploadId = 1;
    private static final Map<Integer, CompletableFuture<Codecs.TableReply>> TABLES = new HashMap<>();
    private static final Map<Integer, CompletableFuture<EditReply>> EDITS = new HashMap<>();
    private static int structureChanges;
    /** Pending request for the Pack tools state, and the last state received. */
    private static CompletableFuture<PackToolsState> tools;
    private static PackToolsState lastTools;

    private ClientRequests() {
    }

    public static void setSender(ClientSender clientSender) {
        sender = clientSender;
    }

    /** False when the server doesn't run JES, or runs a version with another protocol. */
    public static boolean serverSupported() {
        return sender != null && sender.canSend(JesNetwork.REQUEST_CATALOG);
    }

    /** True when the server runs JES, but with a different protocol version. */
    public static boolean serverOnOtherVersion() {
        return sender != null && !serverSupported() && sender.sendable().stream().anyMatch(JesNetwork::isJesChannel);
    }

    /** Forgets everything tied to the current connection. */
    public static void reset() {
        catalog = null;
        StructureNames.useGiven(null);
        cancelAll(CAPTURES);
        KEEPING.clear();
        cancelAll(LOOT);
        cancelAll(ODDS);
        ODDS_WAITING.forEach(e -> e.getValue().cancel(false));
        ODDS_WAITING.clear();
        ODDS_BY_TABLE.clear();
        cancelAll(LOCATES);
        TRANSFERS.clear();
        Thumbnails.clear();
        KeptPreviews.use(null, false);
        Nav.forget();
        if (index != null) {
            index.cancel(false);
        }
        index = null;
        ARRIVAL.forget();
        indexDone = 0;
        indexTotal = 0;
        indexFailed = false;
        FoundIn.clear();
        locatePermission = 2;
        teleportPermission = 2;
        compassSearch = false;
        packTools = false;
        overrides = Map.of();
        cancelAll(TABLES);
        cancelAll(EDITS);
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

    /** The last Pack tools state received, or null if none has arrived. */
    public static PackToolsState lastTools() {
        return lastTools;
    }

    /** Saves the server's rules to server.json5. They apply at once, except using changed containers and spawners, which waits for /reload. */
    public static CompletableFuture<EditReply> saveRules(ServerConfig.Settings settings) {
        return toolsAction(JesNetwork.TOOLS_RULES, buf -> Codecs.writeSettings(buf, settings));
    }

    /** Saves what players are told about a structure: its notes, and whether it's secret. Applies at once. */
    public static CompletableFuture<EditReply> saveStructure(ResourceLocation id, String notes, boolean secret) {
        return toolsAction(JesNetwork.TOOLS_STRUCTURE, buf -> {
            buf.writeResourceLocation(id);
            buf.writeUtf(notes.length() > Codecs.MAX_NOTES ? notes.substring(0, Codecs.MAX_NOTES) : notes, Codecs.MAX_NOTES);
            buf.writeBoolean(secret);
        });
    }

    /** Sets a structure's name in one language, or removes it when blank. Applies at once. */
    public static CompletableFuture<EditReply> saveName(ResourceLocation id, String language, String name) {
        if (!StructureNameFiles.isLanguage(language)) {
            return CompletableFuture.completedFuture(new EditReply(Component.translatable("screen.justenoughstructures.tools.no_language", language), null));
        }
        String kept = StructureNameFiles.tidy(name);
        return toolsAction(JesNetwork.TOOLS_NAME, buf -> {
            buf.writeResourceLocation(id);
            buf.writeUtf(language, StructureNameFiles.MAX_LANGUAGE);
            buf.writeUtf(kept, StructureNameFiles.MAX_NAME);
        });
    }

    /** Runs /reload on the server, which Pack tools users can do even without the command. */
    public static CompletableFuture<EditReply> reloadServer() {
        return toolsAction(JesNetwork.TOOLS_RELOAD, buf -> {
        });
    }

    private static CompletableFuture<EditReply> toolsAction(int action, Consumer<FriendlyByteBuf> payload) {
        return ask(EDITS, JesNetwork.TOOLS_ACTION, buf -> {
            buf.writeVarInt(action);
            payload.accept(buf);
        });
    }

    /** How many times the structure list has changed since joining, after a /reload or Pack tools, so the browser knows to fetch it again. */
    public static int structureChanges() {
        return structureChanges;
    }

    /** The server's reply to an edit: a message, and for a draft, the odds it would give. */
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

    /** The status of a loot table's override, or null if it isn't edited or this player can't edit loot. */
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
        return ask(TABLES, JesNetwork.REQUEST_TABLE, buf -> buf.writeResourceLocation(id));
    }

    /** Gets the odds of an edit that isn't saved yet. */
    public static CompletableFuture<EditReply> draftOdds(ResourceLocation id, String json) {
        return upload(JesNetwork.KIND_DRAFT, id, json);
    }

    /** Saves an edit as an override. It applies after /reload. */
    public static CompletableFuture<EditReply> saveTable(ResourceLocation id, String json) {
        return upload(JesNetwork.KIND_SAVE, id, json);
    }

    /** Keeps an override whose original changed, or turns one off. */
    public static CompletableFuture<EditReply> tableAction(ResourceLocation id, int action) {
        return ask(EDITS, JesNetwork.TABLE_ACTION, buf -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(action);
        });
    }

    /** Points a container in a template at another loot table, or back at its original one when {@code table} is null. */
    public static CompletableFuture<EditReply> containerAction(ResourceLocation template, BlockPos pos, ResourceLocation table) {
        return ask(EDITS, JesNetwork.CONTAINER_ACTION, buf -> {
            buf.writeResourceLocation(template);
            buf.writeBlockPos(pos);
            buf.writeBoolean(table != null);
            if (table != null) {
                buf.writeResourceLocation(table);
            }
        });
    }

    /** Undoes the change to a spawner in a template. */
    public static CompletableFuture<EditReply> undoSpawner(ResourceLocation template, BlockPos pos) {
        return spawnerAction(template, pos, null, null);
    }

    /**
     * Sets the mob of a spawner in a template ("" for none) and its {@code block}, a spawner or a
     * trial spawner. A null mob undoes the change.
     */
    public static CompletableFuture<EditReply> spawnerAction(ResourceLocation template, BlockPos pos, String mob, ResourceLocation block) {
        return ask(EDITS, JesNetwork.SPAWNER_ACTION, buf -> {
            buf.writeResourceLocation(template);
            buf.writeBlockPos(pos);
            buf.writeBoolean(mob != null);
            if (mob != null) {
                buf.writeUtf(mob, 256);
                buf.writeResourceLocation(block);
            }
        });
    }

    /** Fills a container of {@code size} slots from an edit that isn't saved yet, as {@link #loot} does from a saved table. */
    public static CompletableFuture<List<ItemStack>> draftRoll(ResourceLocation id, String json, long seed, int size) {
        return ask(LOOT, requestId -> sendUpload(JesNetwork.KIND_DRAFT_ROLL, requestId, buf -> Codecs.writeDraftRoll(buf, id, json, seed, size)));
    }

    private static CompletableFuture<EditReply> upload(int kind, ResourceLocation id, String json) {
        return ask(EDITS, requestId -> sendUpload(kind, requestId, buf -> Codecs.writeDraft(buf, id, json)));
    }

    /** Sends a payload that may not fit in one packet, compressed and split into parts. */
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
        answer(EDITS, requestId, new EditReply(message, odds));
    }

    /** Whether the server can set a held structure compass searching. */
    public static boolean canPointCompass() {
        return compassSearch;
    }

    /** Asks the server to set the held compass searching for the structure. Answers like a locate. */
    public static CompletableFuture<Component> pointCompass(ResourceLocation structure) {
        return ask(LOCATES, JesNetwork.REQUEST_COMPASS, buf -> buf.writeResourceLocation(structure));
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
     * Returns the loot index, requesting it if needed. Until it arrives, {@link #tick()} asks again
     * every 1.5 s so the progress stays current.
     */
    public static CompletableFuture<LootIndex> index() {
        if (index == null || index.isCancelled()) {
            index = new CompletableFuture<>();
            ARRIVAL.asked();
            pollIndex();
        }
        return index;
    }

    /**
     * Calls {@code listener} with every loot index that arrives, including the new one after a
     * /reload, which is then requested at once instead of when the browser next wants it.
     */
    public static void onEachIndex(Consumer<LootIndex> listener) {
        INDEX_LISTENERS.add(listener);
    }

    /** Runs {@code listener} when the server reports that its index build failed, so nothing more is coming. */
    public static void onIndexStopped(Runnable listener) {
        STOPPED_LISTENERS.add(listener);
    }

    public static boolean indexReady() {
        return index != null && index.isDone() && !index.isCompletedExceptionally() && !index.isCancelled();
    }

    /** This server's loot index if it has arrived, or null. Safe from any thread. */
    public static LootIndex latestIndex() {
        return ARRIVAL.now();
    }

    /**
     * For a recipe viewer loading on another thread: returns the loot index, waiting up to
     * {@code millis} for one that's been requested and is on its way. On the client thread, where the
     * index arrives, it never waits.
     */
    public static LootIndex awaitIndex(long millis) {
        return Minecraft.getInstance().isSameThread() ? ARRIVAL.now() : ARRIVAL.await(millis);
    }

    /**
     * For a recipe viewer that must reload to show a new index; see {@link IndexArrival#finishedWait}.
     * Always 0 with a server too old to mark early indexes.
     */
    public static long finishedIndexWait(long quiet) {
        return ARRIVAL.finishedWait(quiet);
    }

    /** How much of the index is built, from 0 to 1, or -1 before the server has said. */
    public static float indexProgress() {
        return indexTotal <= 0 ? -1f : (float) indexDone / indexTotal;
    }

    /**
     * Whether the current index is an early one, with the server still building the rest. It has
     * most of the loot, and the finished index replaces it when done. The server signals this by
     * sending progress after the index, which it never does after a finished one.
     */
    public static boolean indexFilling() {
        return indexReady() && !indexFailed && indexTotal > 0 && indexDone < indexTotal;
    }

    /** Whether the server reported that its index build failed. It then has no index to send, or nothing after the early one, until it reloads. */
    public static boolean indexFailed() {
        return indexFailed;
    }

    /** Whether the current index is an early one the server couldn't finish, so some structures' loot is missing from it. */
    public static boolean indexCutShort() {
        return indexReady() && indexFailed;
    }

    /** Text to show while there's no index: the server's progress, or that its build failed. */
    public static Component indexWaitText() {
        if (indexFailed) {
            return Component.translatable("screen.justenoughstructures.index_failed");
        }
        float progress = indexProgress();
        return progress < 0 ? Component.translatable("screen.justenoughstructures.indexing")
                : Component.translatable("screen.justenoughstructures.indexing_progress", Math.round(progress * 100));
    }

    public static void tick() {
        if (!viewersLookedFor) {
            viewersLookedFor = true;
            indexWanted |= loadsWithTheIndex();
        }
        if (indexWanted && index == null && serverSupported()) {
            index();
            // Also fetch the structure list, which saved thumbnails are checked against, so rows can show pictures.
            catalog();
        }
        if (index != null && !index.isDone() && System.currentTimeMillis() - lastIndexPoll > 1500) {
            pollIndex();
        }
    }

    /**
     * For recipe viewers, which load before it's known whether the server runs JES: requests the loot
     * index as soon as that's known, and on every server joined after.
     */
    public static void wantIndex() {
        indexWanted = true;
    }

    /**
     * True when EMI is installed. EMI only reads recipes while it loads, which happens as the server
     * is joined, so the index is requested at join, not after its plugin has run. An index the server
     * has saved then arrives in time, and EMI needn't reload.
     */
    private static boolean loadsWithTheIndex() {
        //? if emi {
        return JustEnoughStructures.modVersions().containsKey("emi");
        //?} else {
        /*return false;
        *///?}
    }

    private static void pollIndex() {
        lastIndexPoll = System.currentTimeMillis();
        send(JesNetwork.REQUEST_INDEX, buf -> {
        });
    }

    public static void onIndexProgress(int done, int total, boolean failed) {
        indexDone = done;
        indexTotal = total;
        boolean stopped = failed && !indexFailed;
        indexFailed = failed;
        if (failed) {
            ARRIVAL.failed();
        } else if (total > 0 && done < total) {
            ARRIVAL.building();
        }
        if (stopped) {
            STOPPED_LISTENERS.forEach(Runnable::run);
        }
    }

    /** Fetches the structure list again when it's next wanted. Test hook for the scripted scenarios. */
    public static void forgetCatalog() {
        if (catalog != null && catalog.isDone()) {
            catalog = null;
        }
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
     * Requests a structure from the server. A preview, the structure the player is looking at, is
     * served ahead of list pictures. If its first view is in the kept-previews cache, the server only
     * sends it again if it has changed.
     */
    public static CompletableFuture<Codecs.CaptureReply> capture(ResourceLocation structure, long seed, boolean preview) {
        return ask(CAPTURES, id -> {
            long kept = 0;
            if (seed == StructureCapture.defaultSeed(structure) && KeptPreviews.inUse()) {
                KEEPING.put(id, new Keeping(structure, KeptPreviews.version(structure), !preview));
                if (preview) {
                    kept = KeptPreviews.kept(structure);
                }
            }
            requestCapture(id, structure, seed, preview, kept);
        });
    }

    /**
     * Gets a structure's picture copy, the lighter copy its list picture is drawn from. A kept copy
     * that matches the server's current version is used without asking the server.
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
        return ask(LOOT, JesNetwork.REQUEST_LOOT, buf -> {
            buf.writeResourceLocation(table);
            buf.writeLong(seed);
            buf.writeVarInt(size);
        });
    }

    /** Locates the nearest such structure, and with {@code teleport} also moves the player there. */
    public static CompletableFuture<Component> locate(ResourceLocation structure, boolean teleport) {
        return ask(LOCATES, JesNetwork.REQUEST_LOCATE, buf -> {
            buf.writeResourceLocation(structure);
            buf.writeBoolean(teleport);
        });
    }

    public static void onLocate(int requestId, Component reply) {
        answer(LOCATES, requestId, reply);
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
     * hundred tables at once, so only {@link #ODDS_IN_FLIGHT} are requested at a time and the rest queue.
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
            // Names saved in Pack tools follow the list. Older servers don't send them.
            Map<String, Map<String, String>> names = buf.isReadable() ? Codecs.readNames(buf) : null;
            StructureNames.useGiven(names);
            JesLog.debug("The structure list came with {} structures, and names given in {} languages", entries.size(),
                    names == null ? "no" : names.size());
            Thumbnails.onCatalog(fingerprint, entries);
            // A world hosted in this game saves first views on its integrated server, so the client only keeps them for other servers.
            KeptPreviews.use(fingerprint, !Minecraft.getInstance().hasSingleplayerServer());
            KeptPreviews.structures(entries);
            if (catalog == null) {
                catalog = new CompletableFuture<>();
            }
            catalog.complete(entries);
        } else if (part.kind() == JesNetwork.KIND_INDEX) {
            LootIndex built = Codecs.readIndex(buf);
            // Newer servers mark an early index, with the finished one to follow.
            boolean early = Codecs.readIndexEarly(buf);
            JesLog.debug("The loot index came with {} structures{}", built.tablesByStructure().size(), early ? ", an early one" : "");
            // Progress so far described the build of this index, so reset it.
            indexDone = 0;
            indexTotal = 0;
            indexFailed = false;
            ARRIVAL.arrived(built, early);
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
     * Unpacks a structure reply. A big structure takes a good part of a second, so it's unpacked on a
     * background thread, and skipped when nothing waits for it, like a preview the player has already
     * left. A first view that unpacks is kept for next time. {@code wasKept} is true for a kept copy,
     * which is deleted if it won't unpack.
     */
    private static void onCapture(int requestId, byte[] compressed, boolean wasKept) {
        RegistryAccess registries = registries();
        CompletableFuture<Codecs.CaptureReply> future = CAPTURES.remove(requestId);
        Keeping keeping = KEEPING.remove(requestId);
        if (future == null || future.isDone()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
                    Codecs.CaptureReply reply = Codecs.unpackCapture(registries, compressed);
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
     * Saves the reply to the kept-previews cache. A first view also yields the picture copy, so that
     * never needs its own request. The copy is made in a background task, since it takes a moment for
     * big structures.
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
                        Codecs.packCapture(registries, structure, reply.seed(), picture));
            } catch (RuntimeException e) {
                JesLog.debug("Couldn't make the picture's copy of {}", structure, e);
            }
        });
    }

    /**
     * The server says the kept first view matches what it would send, so the kept copy is shown. If
     * the file is gone, the structure is requested again.
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
            // The player may have left, or moved to another structure, while it was read.
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
     * The server's browser settings: locate and teleport levels, compass search and Pack tools access.
     * When Pack tools changed the structures, the structure list is dropped. After a /reload the list
     * and odds are dropped too, and the loot index is asked for again at once if a recipe viewer
     * listens, otherwise when next wanted.
     */
    public static void onSettings(Codecs.BrowserSettings settings) {
        locatePermission = settings.locatePermission();
        teleportPermission = settings.teleportPermission();
        compassSearch = settings.compassSearch();
        packTools = settings.packTools();
        // The browser asks for edited tables when it opens, the first time before the server has said
        // this player may see them, and a /reload can change them, so ask now.
        requestOverrides();
        if (settings.structuresChanged()) {
            structureChanges++;
            if (catalog != null && catalog.isDone()) {
                catalog = null;
            }
        }
        if (!settings.reloaded()) {
            return;
        }
        reloads++;
        if (catalog != null && catalog.isDone()) {
            catalog = null;
        }
        if (index != null && index.isDone()) {
            index = null;
            ARRIVAL.forget();
            indexDone = 0;
            indexTotal = 0;
            indexFailed = false;
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
        answer(LOOT, requestId, items);
    }

    public static void onOdds(int requestId, LootOdds odds) {
        answer(ODDS, requestId, odds);
        sendWaitingOdds();
    }

    /** The registries the server sent, needed to read items and text from 1.20.5. */
    private static RegistryAccess registries() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.registryAccess() : RegistryAccess.EMPTY;
    }

    /**
     * Stores a new future in {@code waiting} under a fresh request id, then calls {@code send} with
     * that id. The reply carrying the id completes it.
     */
    private static <T> CompletableFuture<T> ask(Map<Integer, CompletableFuture<T>> waiting, IntConsumer send) {
        int requestId = nextRequestId++;
        CompletableFuture<T> future = new CompletableFuture<>();
        waiting.put(requestId, future);
        send.accept(requestId);
        return future;
    }

    /** Starts a request on {@code channel}, writing the request id and then {@code payload}. */
    private static <T> CompletableFuture<T> ask(Map<Integer, CompletableFuture<T>> waiting, ResourceLocation channel, Consumer<FriendlyByteBuf> payload) {
        return ask(waiting, requestId -> send(channel, buf -> {
            buf.writeVarInt(requestId);
            payload.accept(buf);
        }));
    }

    /** Completes the request with this id, if it's still waiting. */
    private static <T> void answer(Map<Integer, CompletableFuture<T>> waiting, int requestId, T reply) {
        CompletableFuture<T> future = waiting.remove(requestId);
        if (future != null) {
            future.complete(reply);
        }
    }

    /** Cancels every request still waiting in {@code waiting}. */
    private static void cancelAll(Map<Integer, ? extends CompletableFuture<?>> waiting) {
        waiting.values().forEach(future -> future.cancel(false));
        waiting.clear();
    }

    private static void send(ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        // NeoForge throws on a payload the server never agreed to, which would crash the game.
        if (sender == null || !sender.canSend(channel)) {
            return;
        }
        FriendlyByteBuf buf = Blobs.buffer(registries());
        writer.accept(buf);
        sender.send(channel, buf);
    }

    /**
     * A request whose reply goes into the kept-previews cache: the structure's first view, or with
     * {@code picture} its picture copy, plus the structure's version when it was requested.
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
