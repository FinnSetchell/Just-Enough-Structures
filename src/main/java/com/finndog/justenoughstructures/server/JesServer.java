package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import io.netty.buffer.Unpooled;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/** Answers client requests. Loader code calls these from its packet handlers, on the server thread. */
public final class JesServer {
    private static final int MAX_CONTAINER_SLOTS = 54;
    private static final int ODDS_ROLLS = 2000;
    private static final int CACHED_CAPTURES = 32;
    private static final int MAX_QUEUED_PER_PLAYER = 3;

    // Structure generation normally runs on worker threads, so structure code expects to run off the
    // server thread. One thread keeps a busy screen from flooding the server with work. Its queue puts
    // the structure a player is looking at ahead of the list's pictures, which can take a while each.
    private static final ThreadPoolExecutor CAPTURES = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "Just Enough Structures capture");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicLong CAPTURE_ORDER = new AtomicLong();
    /** Each player's newest preview, so ones they've already moved on from aren't generated. */
    private static final Map<UUID, Integer> LATEST_PREVIEW = new ConcurrentHashMap<>();

    /** A capture waiting its turn: previews first, then in the order they were asked for. */
    private record CaptureTask(boolean preview, long order, Runnable work) implements Runnable, Comparable<CaptureTask> {
        @Override
        public void run() {
            work.run();
        }

        @Override
        public int compareTo(CaptureTask other) {
            if (preview != other.preview) {
                return preview ? -1 : 1;
            }
            return Long.compare(order, other.order);
        }
    }

    private static final AtomicInteger TRANSFER_IDS = new AtomicInteger();
    private static final Map<String, byte[]> CAPTURE_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHED_CAPTURES;
        }
    };
    private static final Map<UUID, AtomicInteger> QUEUED = new ConcurrentHashMap<>();
    private static byte[] catalog;

    private static final Map<ResourceLocation, LootOdds> ODDS_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> LOCATING = ConcurrentHashMap.newKeySet();
    // Players who've opened the browser this session, so they have the mod and hear about a /reload.
    private static final Set<UUID> BROWSING = ConcurrentHashMap.newKeySet();
    private static CompassSearch compassSearch;

    private JesServer() {
    }

    /**
     * Reads the server settings again and starts over, when the server starts and after /reload.
     * Mentions hidden ids that aren't structures, which are usually typos.
     */
    public static void reload(MinecraftServer server) {
        ServerConfig.Settings settings = ServerConfig.load();
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : settings.hiddenStructures()) {
            if (!structures.containsKey(id)) {
                JustEnoughStructures.LOGGER.warn("{} is set to be hidden, but there's no structure with that id", id);
            }
        }
        invalidate();
        PackToolsServer.reloaded();
        LootIndexStore.refresh(server);
        for (UUID id : BROWSING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                BROWSING.remove(id);
            } else {
                sendSettings(player, true, true);
            }
        }
    }

    /**
     * After Pack tools changed which structures are shown or what's said about them: the list is
     * built again, previews that showed where secret loot is are dropped, and everyone browsing is
     * told, along with whether they can still use Pack tools.
     */
    public static void structuresChanged(MinecraftServer server) {
        catalog = null;
        synchronized (CAPTURE_CACHE) {
            CAPTURE_CACHE.clear();
        }
        for (UUID id : BROWSING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                BROWSING.remove(id);
            } else {
                sendSettings(player, false, true);
            }
        }
    }

    /**
     * Before the world loads, so the settings and container changes are in place for the first
     * chunks it generates, not just from the first /reload.
     */
    public static void starting() {
        ServerConfig.load();
        ContainerPatches.load();
        SpawnerPatches.load();
    }

    /** Called when the server starts and after /reload, since structures and loot can change. */
    public static void invalidate() {
        ContainerPatches.load();
        SpawnerPatches.load();
        synchronized (CAPTURE_CACHE) {
            CAPTURE_CACHE.clear();
        }
        catalog = null;
        ODDS_CACHE.clear();
    }

    /** When the server stops: drops what belonged to that world and stops the loot index. */
    public static void stop() {
        invalidate();
        Uploads.clear();
        LootIndexStore.stop();
        BROWSING.clear();
    }

    public static void onRequestIndex(ServerPlayer player) {
        LootIndexStore.request(player);
    }

    static void sendIndex(ServerPlayer player, byte[] payload) {
        sendBlob(player, JesNetwork.KIND_INDEX, 0, payload);
    }

    static void sendIndexProgress(ServerPlayer player, int done, int total) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(done);
        buf.writeVarInt(total);
        JesNetwork.send(player, JesNetwork.INDEX_PROGRESS, buf);
    }

    public static void onRequestCatalog(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (catalog == null) {
            List<StructureCatalog.Entry> entries = visibleCatalog(server);
            catalog = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCatalog(buf, entries)));
        }
        BROWSING.add(player.getUUID());
        sendSettings(player, false, false);
        sendBlob(player, JesNetwork.KIND_CATALOG, 0, catalog);
    }

    // ------------------------------------------------------------------ loot table editing

    private static boolean canEdit(ServerPlayer player) {
        return PackToolsAccess.allowed(player);
    }

    /** A loot table for the editor, as it is now and as the mods have it. */
    public static Codecs.TableReply tableFor(ServerPlayer player, ResourceLocation id) {
        if (!canEdit(player)) {
            return new Codecs.TableReply(null, Component.translatable("screen.justenoughstructures.override.no_permission"));
        }
        return new Codecs.TableReply(LootOverrides.view(player.getServer().getResourceManager(), id), null);
    }

    public static void onRequestTable(ServerPlayer player, int requestId, ResourceLocation id) {
        Codecs.TableReply reply = tableFor(player, id);
        sendBlob(player, JesNetwork.KIND_TABLE, requestId, Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeTable(buf, reply))));
    }

    /** Which loot tables have an override, for players who can edit them, so the browser can mark them. */
    public static void onRequestOverrides(ServerPlayer player) {
        Map<ResourceLocation, LootOverrides.Status> statuses = canEdit(player)
                ? LootOverrides.statuses(player.getServer().getResourceManager()) : Map.of();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(statuses.size());
        statuses.forEach((id, status) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(status.ordinal());
        });
        JesNetwork.send(player, JesNetwork.OVERRIDES, buf);
    }

    /** Keeping an override whose original changed, or turning one off. */
    public static Component tableAction(ServerPlayer player, ResourceLocation id, int action) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        if (action == JesNetwork.ACTION_KEEP) {
            return LootOverrides.keep(player.getServer().getResourceManager(), id);
        }
        Component reply = LootOverrides.remove(id);
        if (replyIs(reply, "override.removed")) {
            PackToolsServer.waiting(PackToolsState.tableKey(id));
        }
        return reply;
    }

    private static boolean replyIs(Component reply, String keyEnd) {
        return reply != null && reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith(keyEnd);
    }

    public static void onTableAction(ServerPlayer player, int requestId, ResourceLocation id, int action) {
        sendEditReply(player, requestId, tableAction(player, id, action), null);
    }

    /** What an unsaved edit would give, or what's wrong with it. */
    public record DraftOdds(Component problem, LootOdds odds) {
    }

    /** Rolls an edit that isn't saved yet, so the editor can show what it would give. */
    public static DraftOdds draftOdds(ServerPlayer player, ResourceLocation id, String json) {
        if (!canEdit(player)) {
            return new DraftOdds(Component.translatable("screen.justenoughstructures.override.no_permission"), null);
        }
        Component problem = LootOverrides.check(id, json);
        if (problem != null) {
            return new DraftOdds(problem, null);
        }
        return new DraftOdds(null, LootRolls.odds(player.serverLevel(), id, LootOverrides.parse(json), ODDS_ROLLS, id.hashCode()));
    }

    /** One roll of an edit that isn't saved yet, or nothing if it can't be rolled. */
    public static List<ItemStack> draftRoll(ServerPlayer player, Codecs.DraftRoll roll) {
        int size = Math.max(1, Math.min(roll.size(), MAX_CONTAINER_SLOTS));
        if (!canEdit(player) || LootOverrides.check(roll.draft().id(), roll.draft().json()) != null) {
            return java.util.Collections.nCopies(size, ItemStack.EMPTY);
        }
        return LootRolls.fill(player.serverLevel(), LootOverrides.parse(roll.draft().json()), roll.seed(), size);
    }

    public static Component saveTable(ServerPlayer player, ResourceLocation id, String json) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        Component reply = LootOverrides.save(player.getServer().getResourceManager(), id, json);
        if (replyIs(reply, "override.saved")) {
            PackToolsServer.waiting(PackToolsState.tableKey(id));
        }
        return reply;
    }

    /**
     * Points one container in a template at another loot table, after checking the container is
     * really there and the table exists. It applies from the next /reload.
     */
    public static Component patchContainer(ServerPlayer player, ResourceLocation template, BlockPos pos, ResourceLocation table) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        MinecraftServer server = player.getServer();
        Optional<StructureTemplate> loaded = server.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.container.no_template");
        }
        StructureTemplate.StructureBlockInfo container = null;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) loaded.get()).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.pos().equals(pos) && info.nbt() != null && info.nbt().contains("LootTable", Tag.TAG_STRING)) {
                    container = info;
                }
            }
        }
        if (container == null) {
            return Component.translatable("screen.justenoughstructures.container.not_there");
        }
        if (!LootOverrides.exists(server, table)) {
            return Component.translatable("screen.justenoughstructures.container.no_table", table.toString());
        }
        if (!ServerConfig.get().containerChanges()) {
            return Component.translatable("screen.justenoughstructures.container.turned_off");
        }
        // The template may already be patched, in which case what it had first is what the patch remembers.
        ContainerPatches.Patch existing = ContainerPatches.find(template, pos);
        String original = existing != null ? existing.original() : ContainerPatches.ownTable(template, pos, container.nbt().getString("LootTable"));
        Component reply = ContainerPatches.save(new ContainerPatches.Patch(template, pos, BuiltInRegistries.BLOCK.getKey(container.state().getBlock()), original, table));
        if (replyIs(reply, "container.saved")) {
            PackToolsServer.waiting(PackToolsState.chestKey(template, pos));
        }
        return reply;
    }

    public static Component unpatchContainer(ServerPlayer player, ResourceLocation template, BlockPos pos) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        Component reply = ContainerPatches.remove(template, pos);
        if (replyIs(reply, "container.removed")) {
            PackToolsServer.waiting(PackToolsState.chestKey(template, pos));
        }
        return reply;
    }

    /**
     * Gives one spawner in a template another mob, or none when {@code mob} is empty, after checking
     * the spawner is really there and the mob is one a spawner can make. It applies from the next /reload.
     */
    public static Component patchSpawner(ServerPlayer player, ResourceLocation template, BlockPos pos, String mob) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        Optional<StructureTemplate> loaded = player.getServer().getStructureManager().get(template);
        if (loaded.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.container.no_template");
        }
        StructureTemplate.StructureBlockInfo spawner = null;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) loaded.get()).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.pos().equals(pos) && SpawnerPatches.isSpawner(info)) {
                    spawner = info;
                }
            }
        }
        if (spawner == null) {
            return Component.translatable("screen.justenoughstructures.spawner.not_there");
        }
        ResourceLocation id = mob.isEmpty() ? null : ResourceLocation.tryParse(mob);
        if (!mob.isEmpty() && (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) || !SpawnerPatches.spawnable(BuiltInRegistries.ENTITY_TYPE.get(id)))) {
            return Component.translatable("screen.justenoughstructures.spawner.not_a_mob", mob);
        }
        if (!ServerConfig.get().containerChanges()) {
            return Component.translatable("screen.justenoughstructures.spawner.turned_off");
        }
        // The template may already be patched, in which case what it had first is what the patch remembers.
        SpawnerPatches.Patch existing = SpawnerPatches.find(template, pos);
        String original = existing != null ? existing.original() : SpawnerPatches.ownMob(template, pos, spawner.nbt());
        int others = existing != null ? existing.others() : SpawnerPatches.ownOthers(template, pos, spawner.nbt());
        Component reply = SpawnerPatches.save(new SpawnerPatches.Patch(template, pos, BuiltInRegistries.BLOCK.getKey(spawner.state().getBlock()),
                original, others, id == null ? "" : id.toString()));
        if (replyIs(reply, "spawner.saved")) {
            PackToolsServer.waiting(PackToolsState.spawnerKey(template, pos));
        }
        return reply;
    }

    public static Component unpatchSpawner(ServerPlayer player, ResourceLocation template, BlockPos pos) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        Component reply = SpawnerPatches.remove(template, pos);
        if (replyIs(reply, "spawner.removed")) {
            PackToolsServer.waiting(PackToolsState.spawnerKey(template, pos));
        }
        return reply;
    }

    // ------------------------------------------------------------------ Pack tools

    /** Everything Pack tools shows, for a player allowed to use it. */
    public static void onRequestTools(ServerPlayer player) {
        if (!canEdit(player)) {
            return;
        }
        PackToolsState state = PackToolsServer.state(player.getServer());
        sendBlob(player, JesNetwork.KIND_TOOLS, 0, Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeTools(buf, state))));
    }

    public static void onSaveRules(ServerPlayer player, int requestId, ServerConfig.Settings settings) {
        Component reply = canEdit(player) ? PackToolsServer.saveRules(player.getServer(), settings)
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onSaveStructure(ServerPlayer player, int requestId, ResourceLocation id, String notes, boolean secret) {
        Component reply = canEdit(player) ? PackToolsServer.saveStructure(player.getServer(), id, notes, secret)
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onReload(ServerPlayer player, int requestId) {
        Component reply = canEdit(player) ? PackToolsServer.reload(player.getServer())
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onContainerAction(ServerPlayer player, int requestId, ResourceLocation template, BlockPos pos, ResourceLocation table) {
        Component reply = table == null ? unpatchContainer(player, template, pos) : patchContainer(player, template, pos, table);
        sendEditReply(player, requestId, reply, null);
    }

    public static void onSpawnerAction(ServerPlayer player, int requestId, ResourceLocation template, BlockPos pos, String mob) {
        Component reply = mob == null ? unpatchSpawner(player, template, pos) : patchSpawner(player, template, pos, mob);
        sendEditReply(player, requestId, reply, null);
    }

    /** A part of an upload. Once it's all in, it's dealt with on the server thread. */
    public static void onUploadPart(MinecraftServer server, ServerPlayer player, Blobs.Part part) {
        Uploads.Done done = Uploads.accept(player.getUUID(), part);
        if (done == null) {
            return;
        }
        server.execute(() -> {
            if (done.kind() == JesNetwork.KIND_DRAFT_ROLL) {
                Codecs.DraftRoll roll;
                try {
                    roll = Codecs.readDraftRoll(Blobs.fromBytes(Blobs.inflate(done.bytes())));
                } catch (RuntimeException e) {
                    JesLog.warnOnce("upload:" + player.getUUID(), "Ignoring an upload from {} that didn't read: {}", player.getName().getString(), e.getMessage());
                    return;
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(done.requestId());
                Codecs.writeItems(buf, draftRoll(player, roll));
                JesNetwork.send(player, JesNetwork.LOOT, buf);
                return;
            }
            Codecs.Draft draft;
            try {
                draft = Codecs.readDraft(Blobs.fromBytes(Blobs.inflate(done.bytes())));
            } catch (RuntimeException e) {
                JesLog.warnOnce("upload:" + player.getUUID(), "Ignoring an upload from {} that didn't read: {}", player.getName().getString(), e.getMessage());
                return;
            }
            if (done.kind() == JesNetwork.KIND_DRAFT) {
                DraftOdds result = draftOdds(player, draft.id(), draft.json());
                sendEditReply(player, done.requestId(), result.problem(), result.odds());
            } else if (done.kind() == JesNetwork.KIND_SAVE) {
                sendEditReply(player, done.requestId(), saveTable(player, draft.id(), draft.json()), null);
            }
        });
    }

    private static void sendEditReply(ServerPlayer player, int requestId, Component message, LootOdds odds) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        buf.writeBoolean(message != null);
        if (message != null) {
            buf.writeComponent(message);
        }
        buf.writeBoolean(odds != null);
        if (odds != null) {
            Codecs.writeOdds(buf, odds);
        }
        JesNetwork.send(player, JesNetwork.EDIT_REPLY, buf);
    }

    /** Set by the loader when a structure compass mod is installed. */
    public static void setCompassSearch(CompassSearch search) {
        compassSearch = search;
    }

    /**
     * Who can locate and teleport, and whether this player can use Pack tools, so the browser only
     * offers what the server will allow. {@code reloaded} drops everything the client has from before
     * a /reload; {@code structuresChanged} only the list of structures.
     */
    private static void sendSettings(ServerPlayer player, boolean reloaded, boolean structuresChanged) {
        ServerConfig.Settings settings = ServerConfig.get();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(settings.locatePermission());
        buf.writeVarInt(settings.teleportPermission());
        buf.writeBoolean(reloaded);
        buf.writeBoolean(compassSearch != null);
        buf.writeBoolean(PackToolsAccess.allowed(player));
        buf.writeBoolean(structuresChanged);
        JesNetwork.send(player, JesNetwork.SETTINGS, buf);
    }

    /** Every structure, less the ones the server hides, each marked if where its loot is is hidden. */
    public static List<StructureCatalog.Entry> visibleCatalog(MinecraftServer server) {
        boolean showLoot = ServerConfig.get().showLootLocations();
        return StructureCatalog.build(server.registryAccess()).stream()
                .filter(entry -> !ServerConfig.hides(entry.id()))
                .map(entry -> showLoot ? entry : entry.withInfo(entry.info().hidingLoot()))
                .toList();
    }

    /** Whether players are kept from seeing where this structure's loot is, by its datapack file or the server settings. */
    public static boolean hidesLootLocations(ResourceLocation structure) {
        return !ServerConfig.get().showLootLocations() || StructureInfo.forStructure(structure).hideLootLocations();
    }

    /** A capture as players get it: without where its loot is, when that's hidden. */
    public static CaptureResult forPlayers(ResourceLocation structure, CaptureResult result) {
        return hidesLootLocations(structure) ? result.withoutLoot() : result;
    }

    public static void onRequestCapture(ServerPlayer player, int requestId, ResourceLocation structure, long seed, boolean preview) {
        MinecraftServer server = player.getServer();
        if (ServerConfig.hides(structure)) {
            CaptureResult hidden = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.hidden"), List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, hidden))));
            return;
        }
        String key = structure + "@" + seed;
        byte[] cached;
        synchronized (CAPTURE_CACHE) {
            cached = CAPTURE_CACHE.get(key);
        }
        if (cached != null) {
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId, cached);
            return;
        }

        // Only the list's pictures count towards a player's limit: the preview they're looking at is
        // never turned away, and a newer one replaces any they haven't been shown yet.
        AtomicInteger queued = QUEUED.computeIfAbsent(player.getUUID(), id -> new AtomicInteger());
        if (!preview && queued.incrementAndGet() > MAX_QUEUED_PER_PLAYER) {
            queued.decrementAndGet();
            CaptureResult busy = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.too_many"), List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, busy))));
            return;
        }

        UUID playerId = player.getUUID();
        if (preview) {
            LATEST_PREVIEW.put(playerId, requestId);
        }
        CAPTURES.execute(new CaptureTask(preview, CAPTURE_ORDER.getAndIncrement(), () -> {
            if (preview && !Integer.valueOf(requestId).equals(LATEST_PREVIEW.get(playerId))) {
                CaptureResult skipped = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.superseded"), List.of(), 0);
                byte[] reply = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, skipped)));
                server.execute(() -> {
                    ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                    if (target != null) {
                        sendBlob(target, JesNetwork.KIND_CAPTURE, requestId, reply);
                    }
                });
                return;
            }
            byte[] payload;
            try {
                CaptureResult result = forPlayers(structure, StructureCapture.capture(server, structure, seed));
                payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, result)));
                if (result.succeeded()) {
                    synchronized (CAPTURE_CACHE) {
                        CAPTURE_CACHE.put(key, payload);
                    }
                }
            } catch (RuntimeException e) {
                JesLog.errorOnce("preview:" + structure, "Previewing {} failed", structure, e);
                CaptureResult failed = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.went_wrong", String.valueOf(e)), List.of(), 0);
                payload = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, structure, seed, failed)));
            } finally {
                if (!preview) {
                    queued.decrementAndGet();
                }
            }
            byte[] done = payload;
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null) {
                    sendBlob(target, JesNetwork.KIND_CAPTURE, requestId, done);
                }
            });
        }));
    }

    public static void onRequestLoot(ServerPlayer player, int requestId, ResourceLocation table, long seed, int size) {
        List<ItemStack> items = LootRolls.fill(player.serverLevel(), table, seed, Math.max(1, Math.min(size, MAX_CONTAINER_SLOTS)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        Codecs.writeItems(buf, items);
        JesNetwork.send(player, JesNetwork.LOOT, buf);
    }

    public static void onRequestOdds(ServerPlayer player, int requestId, ResourceLocation table) {
        // Rolled with a fixed seed, so the answer never changes until a reload: work it out once.
        LootOdds odds = ODDS_CACHE.computeIfAbsent(table, t -> LootRolls.odds(player.serverLevel(), t, ODDS_ROLLS, t.hashCode()));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        Codecs.writeOdds(buf, odds);
        JesNetwork.send(player, JesNetwork.ODDS, buf);
    }

    private static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /**
     * Queues a locate on the server thread, unless this player already has one waiting: each is a
     * full structure search, so repeated clicks mustn't pile them up.
     */
    public static void queueLocate(MinecraftServer server, ServerPlayer player, int requestId, ResourceLocation id, boolean teleport) {
        UUID uuid = player.getUUID();
        if (!LOCATING.add(uuid)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(requestId);
            buf.writeComponent(Component.translatable("screen.justenoughstructures.locate_busy"));
            JesNetwork.send(player, JesNetwork.LOCATE, buf);
            return;
        }
        server.execute(() -> {
            try {
                onRequestLocate(player, requestId, id, teleport);
            } finally {
                LOCATING.remove(uuid);
            }
        });
    }

    /** Sets the player's compass searching for a structure, on the server thread, and answers like a locate. */
    public static void queueCompass(MinecraftServer server, ServerPlayer player, int requestId, ResourceLocation id) {
        server.execute(() -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(requestId);
            buf.writeComponent(compassFor(player, id));
            JesNetwork.send(player, JesNetwork.LOCATE, buf);
        });
    }

    /** What to tell the player after trying to set their compass searching. */
    public static Component compassFor(ServerPlayer player, ResourceLocation id) {
        if (compassSearch == null) {
            return Component.translatable("screen.justenoughstructures.compass_unsupported");
        }
        if (ServerConfig.hides(id)) {
            return Component.translatable("screen.justenoughstructures.locate_hidden");
        }
        return compassSearch.search(player, id);
    }

    public static void onRequestLocate(ServerPlayer player, int requestId, ResourceLocation id, boolean teleport) {
        Component reply = locateFor(player, id, teleport);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(requestId);
        buf.writeComponent(reply);
        JesNetwork.send(player, JesNetwork.LOCATE, buf);
    }

    /**
     * Finds the nearest structure of this kind in the player's dimension, like /locate, and with
     * {@code teleport} takes the player there, like /tp. Each needs the permission level the server
     * settings give it; a player who can locate but not teleport just gets told where it is.
     * Returns what to tell the player. Runs on the server thread because structure lookups load
     * chunk data.
     */
    public static Component locateFor(ServerPlayer player, ResourceLocation id, boolean teleport) {
        ServerConfig.Settings settings = ServerConfig.get();
        if (!player.hasPermissions(settings.locatePermission())) {
            return Component.translatable("screen.justenoughstructures.locate_no_permission");
        }
        if (settings.hides(id)) {
            return Component.translatable("screen.justenoughstructures.locate_hidden");
        }
        Located found = find(player.serverLevel(), player.blockPosition(), id);
        if (!teleport || !player.hasPermissions(settings.teleportPermission()) || found.pos() == null) {
            return found.message();
        }
        Optional<BlockPos> spot = standingSpot(player.serverLevel(), found.pos().getX(), found.pos().getZ());
        if (spot.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.locate_no_ground", found.pos().getX(), found.pos().getZ());
        }
        BlockPos to = spot.get();
        player.teleportTo(player.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
        return Component.translatable("screen.justenoughstructures.locate_teleported", to.getX(), to.getY(), to.getZ());
    }

    /** Where a locate ended up, or a null position and the reason why not. */
    private record Located(BlockPos pos, Component message) {
    }

    /** The nearest structure of this kind to {@code from}, as a sentence for the player. */
    public static Component locate(ServerLevel level, BlockPos from, ResourceLocation id) {
        return find(level, from, id).message();
    }

    private static Located find(ServerLevel level, BlockPos from, ResourceLocation id) {
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, id));
        // Searching for something that can't generate here makes the game generate chunk after
        // chunk looking for it, which can stall the server for minutes, so rule that out first.
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return new Located(null, Component.translatable("screen.justenoughstructures.locate_structures_off"));
        }
        if (holder.isEmpty() || level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
            return new Located(null, Component.translatable("screen.justenoughstructures.locate_wrong_dimension"));
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
        if (found == null) {
            return new Located(null, Component.translatable("screen.justenoughstructures.locate_none"));
        }
        BlockPos at = found.getFirst();
        int dx = at.getX() - from.getX();
        int dz = at.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String direction = DIRECTIONS[Math.floorMod((int) Math.round(angle / 45.0), 8)];
        return new Located(at, Component.translatable("screen.justenoughstructures.locate_found",
                String.format("%,d", (int) Math.sqrt((double) dx * dx + (double) dz * dz)),
                Component.translatable("screen.justenoughstructures.direction." + direction), at.getX(), at.getZ()));
    }

    /**
     * Somewhere safe to stand at this x and z: on the top block (water counts, you'll float), or in
     * a dimension with a roof like the Nether, on the highest floor beneath it. Empty if there's
     * only lava or nothing at all, like over the End's void. Loads or generates the chunk, as /tp would.
     */
    public static Optional<BlockPos> standingSpot(ServerLevel level, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int bottom = level.getMinBuildHeight();
        if (!level.dimensionType().hasCeiling()) {
            BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            boolean safe = feet.getY() > bottom && !level.getFluidState(feet.below()).is(FluidTags.LAVA);
            return safe ? Optional.of(feet) : Optional.empty();
        }
        int top = bottom + level.dimensionType().logicalHeight() - 1;
        for (int y = top - 2; y > bottom; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (solid(level, feet.below()) && open(level, feet) && open(level, feet.above())) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return !solid(level, pos) && !level.getFluidState(pos).is(FluidTags.LAVA);
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
