package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Memory;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.Threads;
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
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/** Answers client requests. Loader code calls these from its packet handlers, on the server thread. */
public final class JesServer {
    private static final int MAX_CONTAINER_SLOTS = 54;
    private static final int ODDS_ROLLS = 2000;
    /** How long odds roll in one go on the server thread, before the next tick gets its turn. */
    private static final long ODDS_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
    /** How long one table's odds roll in all. A table some mod makes slow to roll gets fewer rolls. */
    private static final long ODDS_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(3);
    private static final int CACHED_CAPTURES = 32;
    private static final int MAX_QUEUED_PER_PLAYER = 3;

    // Structure generation normally runs on worker threads, so structure code expects to run off the
    // server thread. A thread for the structures players are looking at and one for the list's
    // pictures keep a busy screen from flooding the server with work. Only one capture runs at a time
    // either way, and a picture stops for a preview that's waiting, to be made again after it.
    private static final ExecutorService PREVIEWS = Threads.single("Just Enough Structures capture");
    private static final ExecutorService PICTURES = Threads.single("Just Enough Structures pictures");
    /** Each player's newest preview, so ones they've already moved on from stop. */
    private static final Map<UUID, Integer> LATEST_PREVIEW = new ConcurrentHashMap<>();

    private static final AtomicInteger TRANSFER_IDS = new AtomicInteger();
    /** Captures already sent, newest use last, kept to {@link #CACHED_CAPTURES} and {@link #CACHE_BYTES}. */
    private static final Map<String, Ready> CAPTURE_CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /** Goes up whenever the kept captures are dropped, so one started before isn't kept after. */
    private static int captureGeneration;
    /** A few big structures can be megabytes each, so the cache is limited by size as well as count. */
    private static final long CACHE_BYTES = 48L << 20;
    private static long cachedBytes;
    private static final Map<UUID, AtomicInteger> QUEUED = new ConcurrentHashMap<>();
    private static byte[] catalog;
    /** The fingerprint {@link #catalog} was sent with, which tells clients which saved pictures still hold. */
    private static String catalogFingerprint;

    private static final Map<ResourceLocation, LootOdds> ODDS_CACHE = new ConcurrentHashMap<>();
    /** Odds still being rolled, with everyone waiting for each. Only touched on the server thread. */
    private static final Map<ResourceLocation, List<Consumer<LootOdds>>> ROLLING = new HashMap<>();
    private static final Set<UUID> LOCATING = ConcurrentHashMap.newKeySet();
    // Players who've opened the browser this session, so they have the mod and hear about a /reload.
    private static final Set<UUID> BROWSING = ConcurrentHashMap.newKeySet();
    private static CompassSearch compassSearch;

    private JesServer() {
    }

    /** The server that's running, for code with nothing else to ask, like a datapack being read. Null between worlds. */
    private static volatile MinecraftServer running;
    /** Whether the chunk guard's been checked since the server started. */
    private static boolean guardChecked;

    public static MinecraftServer running() {
        return running;
    }

    /**
     * Reads the server settings again and starts over, when the server starts and after /reload.
     * Mentions hidden ids that aren't structures, which are usually typos.
     */
    public static void reload(MinecraftServer server) {
        running = server;
        if (!guardChecked) {
            guardChecked = true;
            StructureCapture.checkChunkGuard(server);
        }
        ServerConfig.Settings settings = ServerConfig.load();
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : settings.hiddenStructures()) {
            if (!structures.containsKey(id)) {
                JustEnoughStructures.LOGGER.warn("{} is set to be hidden, but there's no structure with that id", id);
            }
        }
        invalidate();
        PackToolsServer.reloaded();
        // Saved first views wait for the loot index's fingerprint, as what they were made from may have changed.
        SavedPreviews.forget();
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
        clearCaptures();
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
    public static void starting(MinecraftServer server) {
        running = server;
        ServerConfig.load();
        ContainerPatches.load();
        SpawnerPatches.load();
    }

    /** Called when the server starts and after /reload, since structures and loot can change. */
    public static void invalidate() {
        ContainerPatches.load();
        SpawnerPatches.load();
        clearCaptures();
        catalog = null;
        ODDS_CACHE.clear();
        ROLLING.clear();
    }

    /**
     * Once the fingerprint of what structures are made from is worked out: a structure list already
     * sent without it, or with an old one, is sent again, so everyone browsing knows which of their
     * saved pictures still hold. Server thread only.
     */
    static void fingerprintKnown(MinecraftServer server, String fingerprint) {
        SavedPreviews.use(server, fingerprint);
        if (catalog == null || Objects.equals(catalogFingerprint, fingerprint)) {
            return;
        }
        catalog = null;
        for (UUID id : BROWSING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                BROWSING.remove(id);
            } else {
                sendSettings(player, false, true);
            }
        }
    }

    /** Drops the captures kept to send again, along with any still being made from before. */
    private static void clearCaptures() {
        synchronized (CAPTURE_CACHE) {
            captureGeneration++;
            CAPTURE_CACHE.clear();
            cachedBytes = 0;
        }
    }

    /** When a player leaves: drops anything they were halfway through sending, and stops their previews and pictures. */
    public static void left(ServerPlayer player) {
        UUID id = player.getUUID();
        Uploads.forget(id);
        RequestLimits.forget(id);
        LATEST_PREVIEW.remove(id);
        BROWSING.remove(id);
        QUEUED.remove(id);
    }

    /** When the server stops: drops what belonged to that world, and stops the loot index and the captures under way. */
    public static void stop() {
        running = null;
        guardChecked = false;
        invalidate();
        Uploads.clear();
        RequestLimits.clear();
        LootIndexStore.stop();
        SavedPreviews.forget();
        BROWSING.clear();
        LATEST_PREVIEW.clear();
        QUEUED.clear();
        LOCATING.clear();
    }

    public static void onRequestIndex(ServerPlayer player) {
        LootIndexStore.request(player);
    }

    static void sendIndex(ServerPlayer player, byte[] payload) {
        if (maySend(player, payload)) {
            sendBlob(player, JesNetwork.KIND_INDEX, 0, payload);
        }
    }

    static void sendIndexProgress(ServerPlayer player, int done, int total) {
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
        buf.writeVarInt(done);
        buf.writeVarInt(total);
        JesNetwork.send(player, JesNetwork.INDEX_PROGRESS, buf);
    }

    public static void onRequestCatalog(ServerPlayer player) {
        MinecraftServer server = Players.server(player);
        if (catalog == null) {
            List<StructureCatalog.Entry> entries = visibleCatalog(server);
            String fingerprint = LootIndexStore.knownFingerprint();
            catalog = Blobs.deflate(Blobs.toBytes(player.level().registryAccess(), buf -> {
                Codecs.writeFingerprint(buf, fingerprint);
                Codecs.writeCatalog(buf, entries);
            }));
            catalogFingerprint = fingerprint;
        }
        BROWSING.add(player.getUUID());
        sendSettings(player, false, false);
        if (maySend(player, catalog)) {
            sendBlob(player, JesNetwork.KIND_CATALOG, 0, catalog);
        }
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
        return new Codecs.TableReply(LootOverrides.view(Players.server(player).getResourceManager(), id), null);
    }

    public static void onRequestTable(ServerPlayer player, int requestId, ResourceLocation id) {
        Codecs.TableReply reply = tableFor(player, id);
        sendBlob(player, JesNetwork.KIND_TABLE, requestId, Blobs.deflate(Blobs.toBytes(player.level().registryAccess(), buf -> Codecs.writeTable(buf, reply))));
    }

    /** Which loot tables have an override, for players who can edit them, so the browser can mark them. */
    public static void onRequestOverrides(ServerPlayer player) {
        Map<ResourceLocation, LootOverrides.Status> statuses = canEdit(player)
                ? LootOverrides.statuses(Players.server(player).getResourceManager()) : Map.of();
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
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
            return LootOverrides.keep(Players.server(player).getResourceManager(), id);
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
    public static void draftOdds(ServerPlayer player, ResourceLocation id, String json, Consumer<DraftOdds> reply) {
        if (!canEdit(player)) {
            reply.accept(new DraftOdds(Component.translatable("screen.justenoughstructures.override.no_permission"), null));
            return;
        }
        Component problem = LootOverrides.check(id, json);
        if (problem != null) {
            reply.accept(new DraftOdds(problem, null));
            return;
        }
        LootRolls.Roller roller = new LootRolls.Roller(Players.level(player), id, LootOverrides.parse(json), ODDS_ROLLS, id.hashCode());
        rollInSlices(Players.server(player), roller, ODDS_BUDGET_NANOS, odds -> reply.accept(new DraftOdds(null, odds)));
    }

    /** One roll of an edit that isn't saved yet, or nothing if it can't be rolled. */
    public static List<ItemStack> draftRoll(ServerPlayer player, Codecs.DraftRoll roll) {
        int size = Math.max(1, Math.min(roll.size(), MAX_CONTAINER_SLOTS));
        if (!canEdit(player) || LootOverrides.check(roll.draft().id(), roll.draft().json()) != null) {
            return java.util.Collections.nCopies(size, ItemStack.EMPTY);
        }
        return LootRolls.fill(Players.level(player), LootOverrides.parse(roll.draft().json()), roll.seed(), size);
    }

    public static Component saveTable(ServerPlayer player, ResourceLocation id, String json) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        Component reply = LootOverrides.save(Players.server(player).getResourceManager(), id, json);
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
        MinecraftServer server = Players.server(player);
        Optional<StructureTemplate> loaded = server.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.container.no_template");
        }
        StructureTemplate.StructureBlockInfo container = null;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) loaded.get()).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.pos().equals(pos) && info.nbt() != null && Nbt.hasString(info.nbt(), "LootTable")) {
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
        String original = existing != null ? existing.original() : ContainerPatches.ownTable(template, pos, Nbt.string(container.nbt(), "LootTable"));
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
        return patchSpawner(player, template, pos, mob, null);
    }

    /**
     * The same, and makes the spawner {@code block}: a spawner or a trial spawner, or what it was in
     * its template. Null leaves it the block it's set to be now.
     */
    public static Component patchSpawner(ServerPlayer player, ResourceLocation template, BlockPos pos, String mob, ResourceLocation block) {
        if (!canEdit(player)) {
            return Component.translatable("screen.justenoughstructures.override.no_permission");
        }
        MinecraftServer server = Players.server(player);
        Optional<StructureTemplate> loaded = server.getStructureManager().get(template);
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
        if (!mob.isEmpty() && (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) || !SpawnerPatches.spawnable(Regs.value(BuiltInRegistries.ENTITY_TYPE, id)))) {
            return Component.translatable("screen.justenoughstructures.spawner.not_a_mob", mob);
        }
        // The template may already be patched, in which case what it had first is what the patch remembers.
        SpawnerPatches.Patch existing = SpawnerPatches.find(template, pos);
        ResourceManager resources = server.getResourceManager();
        ResourceLocation own = existing != null ? existing.block() : SpawnerPatches.ownBlock(template, pos, spawner, resources);
        ResourceLocation target = block != null ? block : existing != null ? existing.target() : own;
        if (!target.equals(own) && !SpawnerPatches.canBecome(target)) {
            return Component.translatable("screen.justenoughstructures.spawner.cant_become", target.toString());
        }
        if (!ServerConfig.get().containerChanges()) {
            return Component.translatable("screen.justenoughstructures.spawner.turned_off");
        }
        String original = existing != null ? existing.original() : SpawnerPatches.ownMob(template, pos, spawner, resources);
        int others = existing != null ? existing.others() : SpawnerPatches.ownOthers(template, pos, spawner, resources);
        Component reply = SpawnerPatches.save(new SpawnerPatches.Patch(template, pos, own, original, others, id == null ? "" : id.toString(),
                target.equals(own) ? null : target));
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
        PackToolsState state = PackToolsServer.state(Players.server(player));
        sendBlob(player, JesNetwork.KIND_TOOLS, 0, Blobs.deflate(Blobs.toBytes(player.level().registryAccess(), buf -> Codecs.writeTools(buf, state))));
    }

    public static void onSaveRules(ServerPlayer player, int requestId, ServerConfig.Settings settings) {
        Component reply = canEdit(player) ? PackToolsServer.saveRules(Players.server(player), settings)
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onSaveStructure(ServerPlayer player, int requestId, ResourceLocation id, String notes, boolean secret) {
        Component reply = canEdit(player) ? PackToolsServer.saveStructure(Players.server(player), id, notes, secret)
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onReload(ServerPlayer player, int requestId) {
        Component reply = canEdit(player) ? PackToolsServer.reload(Players.server(player))
                : Component.translatable("screen.justenoughstructures.override.no_permission");
        sendEditReply(player, requestId, reply, null);
    }

    public static void onContainerAction(ServerPlayer player, int requestId, ResourceLocation template, BlockPos pos, ResourceLocation table) {
        Component reply = table == null ? unpatchContainer(player, template, pos) : patchContainer(player, template, pos, table);
        sendEditReply(player, requestId, reply, null);
    }

    public static void onSpawnerAction(ServerPlayer player, int requestId, ResourceLocation template, BlockPos pos, String mob, ResourceLocation block) {
        Component reply = mob == null ? unpatchSpawner(player, template, pos) : patchSpawner(player, template, pos, mob, block);
        sendEditReply(player, requestId, reply, null);
    }

    /**
     * A part of an upload. Once it's all in, it's dealt with on the server thread. Every upload is a
     * Pack tools edit, so one from a player who can't use Pack tools is turned down before anything
     * is unpacked, and unpacking stops well short of filling the memory.
     */
    public static void onUploadPart(MinecraftServer server, ServerPlayer player, Blobs.Part part) {
        Uploads.Done done = Uploads.accept(player.getUUID(), part);
        if (done == null) {
            return;
        }
        server.execute(() -> {
            if (!canEdit(player)) {
                JesLog.warnOnce("upload-refused:" + player.getUUID(), "Ignoring an upload from {}, who can't use Pack tools", player.getName().getString());
                if (done.kind() == JesNetwork.KIND_DRAFT_ROLL) {
                    FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
                    buf.writeVarInt(done.requestId());
                    Codecs.writeItems(buf, List.of());
                    JesNetwork.send(player, JesNetwork.LOOT, buf);
                } else {
                    sendEditReply(player, done.requestId(), Component.translatable("screen.justenoughstructures.override.no_permission"), null);
                }
                return;
            }
            if (done.kind() == JesNetwork.KIND_DRAFT_ROLL) {
                Codecs.DraftRoll roll;
                try {
                    roll = Codecs.readDraftRoll(Blobs.fromBytes(server.registryAccess(), Blobs.inflate(done.bytes(), Uploads.MOST_UNPACKED)));
                } catch (RuntimeException e) {
                    JesLog.warnOnce("upload:" + player.getUUID(), "Ignoring an upload from {} that didn't read: {}", player.getName().getString(), e.getMessage());
                    return;
                }
                FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
                buf.writeVarInt(done.requestId());
                Codecs.writeItems(buf, draftRoll(player, roll));
                JesNetwork.send(player, JesNetwork.LOOT, buf);
                return;
            }
            Codecs.Draft draft;
            try {
                draft = Codecs.readDraft(Blobs.fromBytes(server.registryAccess(), Blobs.inflate(done.bytes(), Uploads.MOST_UNPACKED)));
            } catch (RuntimeException e) {
                JesLog.warnOnce("upload:" + player.getUUID(), "Ignoring an upload from {} that didn't read: {}", player.getName().getString(), e.getMessage());
                return;
            }
            if (done.kind() == JesNetwork.KIND_DRAFT) {
                draftOdds(player, draft.id(), draft.json(), result -> sendEditReply(player, done.requestId(), result.problem(), result.odds()));
            } else if (done.kind() == JesNetwork.KIND_SAVE) {
                sendEditReply(player, done.requestId(), saveTable(player, draft.id(), draft.json()), null);
            }
        });
    }

    private static void sendEditReply(ServerPlayer player, int requestId, Component message, LootOdds odds) {
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
        buf.writeVarInt(requestId);
        buf.writeBoolean(message != null);
        if (message != null) {
            Codecs.writeComponent(buf, message);
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
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
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
        return StructureCatalog.build(server).stream()
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

    /**
     * {@code kept} is the {@link Blobs#hash} of the first view the player's game kept from before, or 0, so
     * it's only sent again if it's changed.
     */
    public static void onRequestCapture(ServerPlayer player, int requestId, ResourceLocation structure, long seed, boolean preview, long kept) {
        MinecraftServer server = Players.server(player);
        if (ServerConfig.hides(structure)) {
            CaptureResult hidden = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.hidden"), List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Codecs.packCapture(player.level().registryAccess(), structure, seed, hidden));
            return;
        }
        String key = cacheKey(structure, seed, !preview);
        Ready cached;
        synchronized (CAPTURE_CACHE) {
            cached = CAPTURE_CACHE.get(key);
        }
        if (cached != null) {
            sendCapture(player, requestId, structure, seed, cached, kept);
            return;
        }

        // Only the list's pictures count towards a player's limit: the preview they're looking at is
        // never turned away, and a newer one replaces any they haven't been shown yet.
        AtomicInteger queued = QUEUED.computeIfAbsent(player.getUUID(), id -> new AtomicInteger());
        if (!preview && queued.incrementAndGet() > MAX_QUEUED_PER_PLAYER) {
            queued.decrementAndGet();
            CaptureResult busy = CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.too_many"), List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Codecs.packCapture(player.level().registryAccess(), structure, seed, busy));
            return;
        }

        UUID playerId = player.getUUID();
        if (preview) {
            LATEST_PREVIEW.put(playerId, requestId);
        }
        int generation;
        synchronized (CAPTURE_CACHE) {
            generation = captureGeneration;
        }
        // A preview stops once its player has moved on to another or left, a picture once its player
        // has left, and both once the world closes, rather than keep hold of the old world.
        BooleanSupplier unwanted = preview
                ? () -> !server.isRunning() || !Integer.valueOf(requestId).equals(LATEST_PREVIEW.get(playerId))
                : () -> !server.isRunning() || !BROWSING.contains(playerId);
        // A first view is usually saved already. It's read on a thread of its own, so it never waits
        // behind a structure someone else is having made.
        Path saved = seed == StructureCapture.defaultSeed(structure) ? SavedPreviews.current() : null;
        Runnable build = () -> make(server, player, playerId, requestId, structure, seed, preview, key, generation, unwanted, queued, saved, kept);
        if (saved == null) {
            build.run();
            return;
        }
        SavedPreviews.load(saved, structure, !preview, payload -> {
            if (payload == null) {
                build.run();
                return;
            }
            if (!preview) {
                queued.decrementAndGet();
            }
            Ready ready = Ready.of(payload);
            cache(key, ready, generation);
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null) {
                    sendCapture(target, requestId, structure, seed, ready, kept);
                }
            });
        });
    }

    /** Has a capture made on the thread for previews or the one for the list's pictures, and sends it. */
    private static void make(MinecraftServer server, ServerPlayer player, UUID playerId, int requestId, ResourceLocation structure, long seed,
                             boolean preview, String key, int generation, BooleanSupplier unwanted, AtomicInteger queued, Path saved, long kept) {
        (preview ? PREVIEWS : PICTURES).execute(() -> {
            Ready payload;
            try {
                CaptureResult result;
                if (unwanted.getAsBoolean()) {
                    result = null;
                } else if (Memory.low()) {
                    result = CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.low_memory"), List.of(), 0);
                } else {
                    result = preview ? StructureCapture.capture(server, structure, seed, unwanted)
                            : StructureCapture.captureInBackground(server, structure, seed, unwanted);
                }
                // Nobody wants it any more. Only a player who's moved on to another preview is still
                // there to be told.
                CaptureResult reply = result != null ? result
                        : CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.superseded"), List.of(), 0);
                if (reply.succeeded()) {
                    // A first view is made into both the preview and the list picture, whichever was asked for.
                    boolean first = seed == StructureCapture.defaultSeed(structure);
                    Ready full = preview || first ? Ready.of(SavedPreviews.payloadOf(server, structure, seed, reply)) : null;
                    Ready picture = !preview || first ? Ready.of(SavedPreviews.pictureOf(server, structure, seed, reply)) : null;
                    if (full != null) {
                        cache(cacheKey(structure, seed, false), full, generation);
                    }
                    if (picture != null) {
                        cache(cacheKey(structure, seed, true), picture, generation);
                    }
                    if (saved != null && first) {
                        SavedPreviews.save(saved, structure, false, full.payload());
                        SavedPreviews.save(saved, structure, true, picture.payload());
                    }
                    payload = preview ? full : picture;
                } else {
                    payload = Ready.of(Codecs.packCapture(player.level().registryAccess(), structure, seed, reply));
                }
            } catch (RuntimeException e) {
                JesLog.errorOnce("preview:" + structure, "Previewing {} failed", structure, e);
                CaptureResult failed = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.went_wrong", String.valueOf(e)), List.of(), 0);
                payload = Ready.of(Codecs.packCapture(player.level().registryAccess(), structure, seed, failed));
            } catch (OutOfMemoryError e) {
                // Whatever the capture had is garbage once this returns, so the server can carry on.
                JustEnoughStructures.LOGGER.error("Ran out of memory previewing {}; it's too big for this server's memory", structure);
                CaptureResult failed = CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.out_of_memory"), List.of(), 0);
                payload = Ready.of(Codecs.packCapture(player.level().registryAccess(), structure, seed, failed));
            } finally {
                if (!preview) {
                    queued.decrementAndGet();
                }
            }
            Ready done = payload;
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target == null) {
                    return;
                }
                if (done.hash() == kept) {
                    sendBlob(target, JesNetwork.KIND_SAME, requestId, new byte[0]);
                } else {
                    sendBlob(target, JesNetwork.KIND_CAPTURE, requestId, done.payload());
                }
            });
        });
    }

    /** What a capture is kept under in memory: its structure and seed, and whether it's the version for a list picture. */
    private static String cacheKey(ResourceLocation structure, long seed, boolean picture) {
        return structure + "@" + seed + (picture ? "#picture" : "");
    }

    /**
     * A capture ready to send, with its {@link Blobs#hash} to tell whether a player's game kept the
     * same one. Worked out once, off the server thread, as a big structure takes a few milliseconds.
     */
    private record Ready(byte[] payload, long hash) {
        static Ready of(byte[] payload) {
            return new Ready(payload, Blobs.hash(payload));
        }
    }

    /**
     * Sends a capture that's ready, unless the player's game kept the same one from before, which it's
     * told to use instead, or the player is asking for them far faster than the browser does.
     */
    private static void sendCapture(ServerPlayer player, int requestId, ResourceLocation structure, long seed, Ready ready, long kept) {
        if (ready.hash() == kept) {
            sendBlob(player, JesNetwork.KIND_SAME, requestId, new byte[0]);
        } else if (maySend(player, ready.payload())) {
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId, ready.payload());
        } else {
            CaptureResult busy = CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.too_many"), List.of(), 0);
            sendBlob(player, JesNetwork.KIND_CAPTURE, requestId,
                    Codecs.packCapture(player.level().registryAccess(), structure, seed, busy));
        }
    }

    /**
     * Keeps a capture to send again, dropping the least recently sent ones once there are too many
     * or they take too much room. One bigger than the whole limit isn't kept, nor one started before
     * the kept ones were last dropped, as it may be from before a /reload.
     */
    private static void cache(String key, Ready ready, int generation) {
        if (ready.payload().length > CACHE_BYTES) {
            return;
        }
        synchronized (CAPTURE_CACHE) {
            if (generation != captureGeneration) {
                return;
            }
            Ready old = CAPTURE_CACHE.put(key, ready);
            cachedBytes += ready.payload().length - (old == null ? 0 : old.payload().length);
            Iterator<Map.Entry<String, Ready>> eldest = CAPTURE_CACHE.entrySet().iterator();
            while ((cachedBytes > CACHE_BYTES || CAPTURE_CACHE.size() > CACHED_CAPTURES) && eldest.hasNext()) {
                Map.Entry<String, Ready> entry = eldest.next();
                if (entry.getKey().equals(key)) {
                    break;
                }
                cachedBytes -= entry.getValue().payload().length;
                eldest.remove();
            }
        }
    }

    public static void onRequestLoot(ServerPlayer player, int requestId, ResourceLocation table, long seed, int size) {
        List<ItemStack> items = LootRolls.fill(Players.level(player), table, seed, Math.max(1, Math.min(size, MAX_CONTAINER_SLOTS)));
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
        buf.writeVarInt(requestId);
        Codecs.writeItems(buf, items);
        JesNetwork.send(player, JesNetwork.LOOT, buf);
    }

    public static void onRequestOdds(ServerPlayer player, int requestId, ResourceLocation table) {
        Consumer<LootOdds> reply = odds -> {
            FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
            buf.writeVarInt(requestId);
            Codecs.writeOdds(buf, odds);
            JesNetwork.send(player, JesNetwork.ODDS, buf);
        };
        MinecraftServer server = Players.server(player);
        // Only tables that exist are kept, so made-up names from a client can't fill the cache. One
        // that doesn't exist would only ever roll nothing, so that's the answer, without rolling it.
        if (!LootOverrides.exists(server, table)) {
            reply.accept(new LootOdds(table, ODDS_ROLLS, ODDS_ROLLS, List.of()));
            return;
        }
        // Rolled with a fixed seed, so the answer never changes until a reload: work it out once.
        LootOdds known = ODDS_CACHE.get(table);
        if (known != null) {
            reply.accept(known);
            return;
        }
        List<Consumer<LootOdds>> waiting = ROLLING.get(table);
        if (waiting != null) {
            waiting.add(reply);
            return;
        }
        List<Consumer<LootOdds>> these = new ArrayList<>(List.of(reply));
        ROLLING.put(table, these);
        LootRolls.Roller roller = new LootRolls.Roller(Players.level(player), table, ODDS_ROLLS, table.hashCode());
        rollInSlices(server, roller, ODDS_BUDGET_NANOS, odds -> {
            // After a /reload these are the old table's odds: still the answer to what was asked, but not kept.
            if (ROLLING.get(table) == these) {
                ROLLING.remove(table);
                ODDS_CACHE.put(table, odds);
            }
            these.forEach(waiter -> waiter.accept(odds));
        });
    }

    /**
     * Rolls for a moment now and the rest in the server's spare time between ticks, so a table
     * that's slow to roll can't hold the server up. With {@code budget} used up, or a roll that
     * throws, it stops at the odds so far.
     */
    private static void rollInSlices(MinecraftServer server, LootRolls.Roller roller, long budget, Consumer<LootOdds> done) {
        long started = System.nanoTime();
        boolean finished;
        try {
            finished = roller.rollFor(Math.min(ODDS_SLICE_NANOS, budget));
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            JesLog.debug("Rolling {} failed after {} rolls", roller.odds().tableId(), roller.odds().rolls(), e);
            finished = true;
        }
        long left = budget - (System.nanoTime() - started);
        if (finished || left <= 0) {
            LootOdds odds = roller.odds();
            if (!finished) {
                JesLog.debug("{} is slow to roll, so its odds are from {} rolls", odds.tableId(), odds.rolls());
            }
            done.accept(odds);
        } else {
            later(server, () -> rollInSlices(server, roller, left, done));
        }
    }

    /** Runs {@code task} on the server thread in its spare time, or a few ticks on at the latest. */
    private static void later(MinecraftServer server, Runnable task) {
        //? if >=26.1 {
        /*server.schedule(new TickTask(server.getTickCount(), task));
        *///?} else {
        server.tell(new TickTask(server.getTickCount(), task));
        //?}
    }

    private static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /**
     * Queues a locate on the server thread, unless this player already has one waiting: each is a
     * full structure search, so repeated clicks mustn't pile them up.
     */
    public static void queueLocate(MinecraftServer server, ServerPlayer player, int requestId, ResourceLocation id, boolean teleport) {
        UUID uuid = player.getUUID();
        if (!LOCATING.add(uuid)) {
            FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
            buf.writeVarInt(requestId);
            Codecs.writeComponent(buf, Component.translatable("screen.justenoughstructures.locate_busy"));
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
            FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
            buf.writeVarInt(requestId);
            Codecs.writeComponent(buf, compassFor(player, id));
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
        try {
            return compassSearch.search(player, id);
        } catch (LinkageError e) {
            // A compass build that's missing something the search calls: it's turned off rather than
            // let the error stop the server.
            JesLog.warnOnce("compass-linkage", "Explorer's Compass is missing something the browser calls, so it won't set the compass searching any more: {}",
                    e.toString());
            compassSearch = null;
            return Component.translatable("screen.justenoughstructures.compass_unsupported");
        }
    }

    public static void onRequestLocate(ServerPlayer player, int requestId, ResourceLocation id, boolean teleport) {
        Component reply = locateFor(player, id, teleport);
        FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
        buf.writeVarInt(requestId);
        Codecs.writeComponent(buf, reply);
        JesNetwork.send(player, JesNetwork.LOCATE, buf);
    }

    /**
     * Finds the nearest structure of this kind, like /locate, and with {@code teleport} takes the
     * player there, like /tp. It looks in the player's dimension, or for one that can't generate
     * there, in the dimension where it does, from where a portal would take them, and teleports them
     * across. Each needs the permission level the server settings give it; a player who can locate
     * but not teleport just gets told where it is. Returns what to tell the player. Runs on the
     * server thread because structure lookups load chunk data.
     */
    public static Component locateFor(ServerPlayer player, ResourceLocation id, boolean teleport) {
        ServerConfig.Settings settings = ServerConfig.get();
        if (!Players.hasPermission(player, settings.locatePermission())) {
            return Component.translatable("screen.justenoughstructures.locate_no_permission");
        }
        if (settings.hides(id)) {
            return Component.translatable("screen.justenoughstructures.locate_hidden");
        }
        ServerLevel level = Players.level(player);
        BlockPos from = player.blockPosition();
        ServerLevel searched = searchedIn(level, id);
        ServerLevel elsewhere = searched != null && searched != level ? searched : null;
        if (elsewhere != null) {
            double scale = DimensionType.getTeleportationScale(level.dimensionType(), elsewhere.dimensionType());
            from = BlockPos.containing(from.getX() * scale, from.getY(), from.getZ() * scale);
            level = elsewhere;
        }
        Located found = find(level, from, id, elsewhere == null ? null : dimensionName(elsewhere));
        if (!teleport || !Players.hasPermission(player, settings.teleportPermission()) || found.pos() == null) {
            return found.message();
        }
        Optional<BlockPos> spot = standingSpot(level, found.pos().getX(), found.pos().getZ());
        if (spot.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.locate_no_ground", found.pos().getX(), found.pos().getZ());
        }
        BlockPos to = spot.get();
        Players.teleport(player, level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        return elsewhere == null ? Component.translatable("screen.justenoughstructures.locate_teleported", to.getX(), to.getY(), to.getZ())
                : Component.translatable("screen.justenoughstructures.locate_teleported_to", dimensionName(elsewhere), to.getX(), to.getY(), to.getZ());
    }

    /** Whether this structure can generate in this dimension at all. */
    private static boolean canGenerate(ServerLevel level, ResourceLocation id) {
        Optional<Holder.Reference<Structure>> holder = Regs.holder(level.registryAccess().registryOrThrow(Registries.STRUCTURE),
                ResourceKey.create(Registries.STRUCTURE, id));
        return holder.isPresent() && !level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty();
    }

    /**
     * The dimension a structure is looked for in from {@code level}: that one if it can generate
     * there, otherwise the first where it can, the Overworld, the Nether and the End first, or null
     * if it can't generate anywhere.
     */
    public static ServerLevel searchedIn(ServerLevel level, ResourceLocation id) {
        if (canGenerate(level, id)) {
            return level;
        }
        for (ServerLevel other : level.getServer().getAllLevels()) {
            if (canGenerate(other, id)) {
                return other;
            }
        }
        return null;
    }

    /** "The End", or for a mod's dimension, its name if the mod gives one, otherwise its id tidied up. */
    private static Component dimensionName(ServerLevel level) {
        ResourceLocation id = Ids.of(level.dimension());
        String vanilla = switch (id.toString()) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end" -> "end";
            default -> null;
        };
        return vanilla != null ? Component.translatable("screen.justenoughstructures.dimension." + vanilla)
                : Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'), Ids.pretty(id.getPath()));
    }

    /** Where a locate ended up, or a null position and the reason why not. */
    private record Located(BlockPos pos, Component message) {
    }

    /** The nearest structure of this kind to {@code from}, as a sentence for the player. */
    public static Component locate(ServerLevel level, BlockPos from, ResourceLocation id) {
        return find(level, from, id, null).message();
    }

    /** {@code elsewhere} names the dimension searched when it isn't the player's own, to say where it is. */
    private static Located find(ServerLevel level, BlockPos from, ResourceLocation id, Component elsewhere) {
        Optional<Holder.Reference<Structure>> holder = Regs.holder(level.registryAccess().registryOrThrow(Registries.STRUCTURE),
                ResourceKey.create(Registries.STRUCTURE, id));
        // Searching for something that can't generate here makes the game generate chunk after
        // chunk looking for it, which can stall the server for minutes, so rule that out first.
        //? if >=26.1 {
        /*boolean structures = level.getServer().getWorldGenSettings().options().generateStructures();
        *///?} else {
        boolean structures = level.getServer().getWorldData().worldGenOptions().generateStructures();
        //?}
        if (!structures) {
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
        String distance = String.format("%,d", (int) Math.sqrt((double) dx * dx + (double) dz * dz));
        Component way = Component.translatable("screen.justenoughstructures.direction." + direction);
        return new Located(at, elsewhere == null ? Component.translatable("screen.justenoughstructures.locate_found", distance, way, at.getX(), at.getZ())
                : Component.translatable("screen.justenoughstructures.locate_found_in", elsewhere, distance, way, at.getX(), at.getZ()));
    }

    /**
     * Somewhere safe to stand at this x and z: on the top block (water counts, you'll float), or in
     * a dimension with a roof like the Nether, on the highest floor beneath it. Empty if there's
     * only lava or nothing at all, like over the End's void. Loads or generates the chunk, as /tp would.
     */
    public static Optional<BlockPos> standingSpot(ServerLevel level, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int bottom = Levels.minY(level);
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

    /**
     * Whether the player may be sent this big answer now, as each player is only sent so much so
     * quickly. A client that asks for more than it could be reading is the only one that's refused.
     */
    private static boolean maySend(ServerPlayer player, byte[] compressed) {
        if (RequestLimits.send(player.getUUID(), compressed.length)) {
            return true;
        }
        JesLog.warnOnce("sent:" + player.getUUID(), "{} is asking for structures and lists far faster than the browser does, so some aren't being sent",
                player.getName().getString());
        return false;
    }

    private static void sendBlob(ServerPlayer player, int kind, int requestId, byte[] compressed) {
        List<byte[]> parts = Blobs.split(compressed);
        int transferId = TRANSFER_IDS.incrementAndGet();
        for (int i = 0; i < parts.size(); i++) {
            FriendlyByteBuf buf = Blobs.buffer(player.level().registryAccess());
            new Blobs.Part(transferId, kind, requestId, i, parts.size(), parts.get(i)).write(buf);
            JesNetwork.send(player, JesNetwork.TRANSFER, buf);
        }
    }
}
