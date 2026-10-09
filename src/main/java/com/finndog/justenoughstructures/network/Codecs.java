package com.finndog.justenoughstructures.network;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21 {
/*import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
*///?}

/** Wire formats for everything the server sends back. */
public final class Codecs {
    private static final int MAX_JSON = 1 << 20;
    /** The longest notes for players Pack tools sends. */
    public static final int MAX_NOTES = 8192;

    private Codecs() {
    }

    // ------------------------------------------------------------------ items and text
    // From 1.20.5 these need the game's registries, which every buffer from Blobs carries.

    public static void writeItem(FriendlyByteBuf buf, ItemStack stack) {
        //? if >=1.21 {
        /*ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, stack);
        *///?} else {
        buf.writeItem(stack);
        //?}
    }

    public static ItemStack readItem(FriendlyByteBuf buf) {
        //? if >=1.21 {
        /*return ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        *///?} else {
        return buf.readItem();
        //?}
    }

    public static void writeComponent(FriendlyByteBuf buf, Component component) {
        //? if >=1.21 {
        /*ComponentSerialization.TRUSTED_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, component);
        *///?} else {
        buf.writeComponent(component);
        //?}
    }

    public static Component readComponent(FriendlyByteBuf buf) {
        //? if >=1.21 {
        /*return ComponentSerialization.TRUSTED_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        *///?} else {
        return buf.readComponent();
        //?}
    }

    private static CompoundTag readAnySizeNbt(FriendlyByteBuf buf) {
        //? if >=1.21 {
        /*return (CompoundTag) buf.readNbt(NbtAccounter.unlimitedHeap());
        *///?} else {
        return buf.readAnySizeNbt();
        //?}
    }

    // ------------------------------------------------------------------ catalog

    /**
     * What the server's structures are made from, so a client knows whether the pictures it saved
     * last time still show them. Null while the server is still working it out.
     */
    public static void writeFingerprint(FriendlyByteBuf buf, String fingerprint) {
        buf.writeBoolean(fingerprint != null);
        if (fingerprint != null) {
            buf.writeUtf(fingerprint);
        }
    }

    public static String readFingerprint(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    public static void writeCatalog(FriendlyByteBuf buf, List<StructureCatalog.Entry> entries) {
        buf.writeVarInt(entries.size());
        for (StructureCatalog.Entry e : entries) {
            buf.writeResourceLocation(e.id());
            writeNullableId(buf, e.type());
            writeJson(buf, e.definition());
            buf.writeVarInt(e.sets().size());
            for (StructureCatalog.SetInfo set : e.sets()) {
                buf.writeResourceLocation(set.setId());
                writeJson(buf, set.placement());
                buf.writeVarInt(set.weight());
            }
            writeInfo(buf, e.info());
            buf.writeEnum(e.availability().reason());
            buf.writeBoolean(e.availability().by() != null);
            if (e.availability().by() != null) {
                buf.writeUtf(e.availability().by());
            }
            writeNullableId(buf, e.availability().replacedBy());
            buf.writeVarInt(e.dimensions().size());
            for (ResourceLocation dimension : e.dimensions()) {
                buf.writeResourceLocation(dimension);
            }
        }
    }

    private static void writeInfo(FriendlyByteBuf buf, StructureInfo info) {
        buf.writeBoolean(info.notes() != null);
        if (info.notes() != null) {
            writeComponent(buf, info.notes());
        }
        buf.writeBoolean(info.author() != null);
        if (info.author() != null) {
            buf.writeUtf(info.author());
        }
        buf.writeBoolean(info.hideLootLocations());
    }

    private static StructureInfo readInfo(FriendlyByteBuf buf) {
        Component notes = buf.readBoolean() ? readComponent(buf) : null;
        String author = buf.readBoolean() ? buf.readUtf() : null;
        return new StructureInfo(notes, author, buf.readBoolean());
    }

    public static List<StructureCatalog.Entry> readCatalog(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<StructureCatalog.Entry> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            ResourceLocation type = readNullableId(buf);
            JsonObject definition = readJson(buf);
            int setCount = buf.readVarInt();
            List<StructureCatalog.SetInfo> sets = new ArrayList<>(setCount);
            for (int s = 0; s < setCount; s++) {
                sets.add(new StructureCatalog.SetInfo(buf.readResourceLocation(), readJson(buf), buf.readVarInt()));
            }
            StructureInfo info = readInfo(buf);
            Availability.Reason reason = buf.readEnum(Availability.Reason.class);
            String by = buf.readBoolean() ? buf.readUtf() : null;
            Availability availability = new Availability(reason, by, readNullableId(buf));
            int dimensionCount = buf.readVarInt();
            List<ResourceLocation> dimensions = new ArrayList<>(dimensionCount);
            for (int d = 0; d < dimensionCount; d++) {
                dimensions.add(buf.readResourceLocation());
            }
            out.add(new StructureCatalog.Entry(id, type, definition, sets, info, availability, dimensions));
        }
        return out;
    }

    // ------------------------------------------------------------------ Pack tools

    public static void writeSettings(FriendlyByteBuf buf, ServerConfig.Settings s) {
        writeList(buf, s.hiddenStructures().stream().sorted().toList(), FriendlyByteBuf::writeResourceLocation);
        writeList(buf, s.hiddenMods().stream().sorted().toList(), FriendlyByteBuf::writeUtf);
        buf.writeVarInt(s.locatePermission());
        buf.writeVarInt(s.teleportPermission());
        buf.writeBoolean(s.showLootLocations());
        writeList(buf, s.packTools().players(), FriendlyByteBuf::writeUtf);
        buf.writeVarInt(s.packTools().permissionLevel() + 1);
        buf.writeBoolean(s.containerChanges());
    }

    public static ServerConfig.Settings readSettings(FriendlyByteBuf buf) {
        Set<ResourceLocation> structures = Set.copyOf(readList(buf, FriendlyByteBuf::readResourceLocation));
        Set<String> mods = Set.copyOf(readList(buf, b -> b.readUtf(256)));
        int locate = buf.readVarInt();
        int teleport = buf.readVarInt();
        boolean showLoot = buf.readBoolean();
        List<String> players = List.copyOf(readList(buf, b -> b.readUtf(64)));
        int level = buf.readVarInt() - 1;
        boolean containers = buf.readBoolean();
        return new ServerConfig.Settings(structures, mods, locate, teleport, showLoot, new ServerConfig.PackTools(players, level), containers);
    }

    public static void writeTools(FriendlyByteBuf buf, PackToolsState state) {
        writeSettings(buf, state.settings());
        writeList(buf, state.pending().stream().sorted().toList(), FriendlyByteBuf::writeUtf);
        buf.writeVarInt(state.overrides().size());
        state.overrides().forEach((id, status) -> {
            buf.writeResourceLocation(id);
            buf.writeEnum(status);
        });
        buf.writeVarInt(state.patches().size());
        for (ContainerPatches.Patch patch : state.patches()) {
            buf.writeResourceLocation(patch.template());
            buf.writeBlockPos(patch.pos());
            buf.writeResourceLocation(patch.block());
            buf.writeUtf(patch.original());
            buf.writeResourceLocation(patch.table());
        }
        buf.writeVarInt(state.spawners().size());
        for (SpawnerPatches.Patch patch : state.spawners()) {
            buf.writeResourceLocation(patch.template());
            buf.writeBlockPos(patch.pos());
            buf.writeResourceLocation(patch.block());
            buf.writeUtf(patch.original());
            buf.writeVarInt(patch.others());
            buf.writeUtf(patch.mob());
            buf.writeBoolean(patch.to() != null);
            if (patch.to() != null) {
                buf.writeResourceLocation(patch.to());
            }
        }
        buf.writeVarInt(state.structures().size());
        state.structures().forEach((id, written) -> {
            buf.writeResourceLocation(id);
            writeInfo(buf, written.info());
            buf.writeBoolean(written.fromPack());
        });
        writeCatalog(buf, state.hidden());
        writeList(buf, state.tables(), FriendlyByteBuf::writeResourceLocation);
        writeMap(buf, state.names(), FriendlyByteBuf::writeUtf, (b, ids) -> writeList(b, ids, FriendlyByteBuf::writeResourceLocation));
    }

    public static PackToolsState readTools(FriendlyByteBuf buf) {
        ServerConfig.Settings settings = readSettings(buf);
        Set<String> pending = Set.copyOf(readList(buf, FriendlyByteBuf::readUtf));
        Map<ResourceLocation, LootOverrides.Status> overrides = new TreeMap<>();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            overrides.put(buf.readResourceLocation(), buf.readEnum(LootOverrides.Status.class));
        }
        List<ContainerPatches.Patch> patches = new ArrayList<>();
        count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            patches.add(new ContainerPatches.Patch(buf.readResourceLocation(), buf.readBlockPos(), buf.readResourceLocation(), buf.readUtf(),
                    buf.readResourceLocation()));
        }
        List<SpawnerPatches.Patch> spawners = new ArrayList<>();
        count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            spawners.add(new SpawnerPatches.Patch(buf.readResourceLocation(), buf.readBlockPos(), buf.readResourceLocation(), buf.readUtf(),
                    buf.readVarInt(), buf.readUtf(), buf.readBoolean() ? buf.readResourceLocation() : null));
        }
        Map<ResourceLocation, PackToolsState.Written> structures = new TreeMap<>();
        count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            StructureInfo info = readInfo(buf);
            structures.put(id, new PackToolsState.Written(info, buf.readBoolean()));
        }
        List<StructureCatalog.Entry> hidden = readCatalog(buf);
        List<ResourceLocation> tables = readList(buf, FriendlyByteBuf::readResourceLocation);
        Map<String, List<ResourceLocation>> names = readMap(buf, FriendlyByteBuf::readUtf, b -> readList(b, FriendlyByteBuf::readResourceLocation));
        return new PackToolsState(settings, pending, overrides, patches, spawners, structures, hidden, tables, names);
    }

    // ------------------------------------------------------------------ captures

    public static void writeCapture(FriendlyByteBuf buf, ResourceLocation id, long seed, CaptureResult result) {
        buf.writeResourceLocation(id);
        buf.writeLong(seed);
        buf.writeVarLong(result.millis());
        buf.writeVarInt(result.attempts().size());
        result.attempts().forEach(c -> writeComponent(buf, c));
        buf.writeBoolean(result.succeeded());
        if (result.succeeded()) {
            writeSnapshot(buf, result.snapshot());
        } else {
            writeComponent(buf, result.reason());
            buf.writeBoolean(result.temporary());
        }
    }

    /** What a client gets back for a capture request. */
    public record CaptureReply(ResourceLocation id, long seed, CaptureResult result) {
    }

    public static CaptureReply readCapture(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        long seed = buf.readLong();
        long millis = buf.readVarLong();
        int attemptCount = buf.readVarInt();
        List<Component> attempts = new ArrayList<>(attemptCount);
        for (int i = 0; i < attemptCount; i++) {
            attempts.add(readComponent(buf));
        }
        CaptureResult result;
        if (buf.readBoolean()) {
            result = CaptureResult.success(readSnapshot(buf), attempts, millis);
        } else {
            Component reason = readComponent(buf);
            result = buf.readBoolean() ? CaptureResult.temporaryFailure(reason, attempts, millis) : CaptureResult.failure(reason, attempts, millis);
        }
        return new CaptureReply(id, seed, result);
    }

    /** A capture as it's sent and saved: written, then packed small. */
    public static byte[] packCapture(RegistryAccess registries, ResourceLocation id, long seed, CaptureResult result) {
        return Blobs.deflate(Blobs.toBytes(registries, buf -> writeCapture(buf, id, seed, result)));
    }

    /** A capture as it arrives: unpacked, then read. */
    public static CaptureReply unpackCapture(RegistryAccess registries, byte[] packed) {
        return readCapture(Blobs.fromBytes(registries, Blobs.inflate(packed)));
    }

    public static void writeSnapshot(FriendlyByteBuf buf, StructureSnapshot s) {
        buf.writeResourceLocation(s.structureId());
        buf.writeLong(s.seed());
        buf.writeEnum(s.terrain());
        buf.writeBlockPos(s.origin());
        buf.writeVarInt(s.size().getX());
        buf.writeVarInt(s.size().getY());
        buf.writeVarInt(s.size().getZ());
        buf.writeVarInt(s.pieceCount());

        // Block states go by name rather than numeric id so a palette survives any id mismatch.
        buf.writeVarInt(s.palette().size());
        for (BlockState state : s.palette()) {
            buf.writeNbt(NbtUtils.writeBlockState(state));
        }
        // Each block's place goes as how far on it is from the one before, counting through the box a
        // row at a time and a layer at a time, the order blocks are kept in, so most are 1. The blocks
        // follow on their own, where runs of the same one are common. Both pack down far smaller than
        // a position and a block each.
        int count = s.blockCount();
        buf.writeVarInt(count);
        long previous = -1;
        for (int i = 0; i < count; i++) {
            long at = cellIndex(s.size(), s.packedPosition(i));
            buf.writeVarLong(zigzag(at - previous));
            previous = at;
        }
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(s.paletteIndex(i));
        }

        CompoundTag extra = new CompoundTag();
        ListTag blockEntities = new ListTag();
        blockEntities.addAll(s.blockEntities());
        ListTag entities = new ListTag();
        entities.addAll(s.entities());
        extra.put("BlockEntities", blockEntities);
        extra.put("Entities", entities);
        buf.writeNbt(extra);
    }

    public static StructureSnapshot readSnapshot(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        long seed = buf.readLong();
        SandboxTerrain terrain = buf.readEnum(SandboxTerrain.class);
        BlockPos origin = buf.readBlockPos();
        Vec3i size = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int pieces = buf.readVarInt();

        int paletteSize = buf.readVarInt();
        List<BlockState> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(NbtUtils.readBlockState(Regs.getter(BuiltInRegistries.BLOCK), buf.readNbt()));
        }
        if (Math.min(size.getX(), Math.min(size.getY(), size.getZ())) < 1
                || Math.max(size.getX(), Math.max(size.getY(), size.getZ())) > StructureSnapshot.MAX_SIZE) {
            throw new DecoderException("A structure's size is out of range: " + size);
        }
        long cells = (long) size.getX() * size.getY() * size.getZ();
        int count = buf.readVarInt();
        if (count < 0 || count > cells) {
            throw new DecoderException("A structure says it has " + count + " blocks in " + cells + " places");
        }
        int[] positions = new int[count];
        int[] states = new int[count];
        long at = -1;
        for (int i = 0; i < count; i++) {
            at += unzigzag(buf.readVarLong());
            if (at < 0 || at >= cells) {
                throw new DecoderException("A structure's block is outside it");
            }
            positions[i] = packedPosition(size, at);
        }
        for (int i = 0; i < count; i++) {
            states[i] = buf.readVarInt();
            if (states[i] < 0 || states[i] >= paletteSize) {
                throw new DecoderException("A structure's block isn't in its palette");
            }
        }

        CompoundTag extra = readAnySizeNbt(buf);
        List<CompoundTag> blockEntities = compounds(Nbt.list(extra, "BlockEntities", Tag.TAG_COMPOUND));
        List<CompoundTag> entities = compounds(Nbt.list(extra, "Entities", Tag.TAG_COMPOUND));
        return new StructureSnapshot(id, seed, terrain, origin, size, palette, positions, states, blockEntities, entities, pieces);
    }

    /** Where a block comes counting through its structure's box a row at a time and a layer at a time. */
    private static long cellIndex(Vec3i size, int packed) {
        return ((long) StructureSnapshot.unpackY(packed) * size.getZ() + StructureSnapshot.unpackZ(packed)) * size.getX()
                + StructureSnapshot.unpackX(packed);
    }

    private static int packedPosition(Vec3i size, long cell) {
        int x = (int) (cell % size.getX());
        long rest = cell / size.getX();
        return StructureSnapshot.pack(x, (int) (rest / size.getZ()), (int) (rest % size.getZ()));
    }

    /** A step that can go backwards, as a small number either way: 0, -1, 1, -2, 2 become 0, 1, 2, 3, 4. */
    private static long zigzag(long step) {
        return (step << 1) ^ (step >> 63);
    }

    private static long unzigzag(long stored) {
        return (stored >>> 1) ^ -(stored & 1);
    }

    // ------------------------------------------------------------------ loot

    public static void writeItems(FriendlyByteBuf buf, List<ItemStack> items) {
        buf.writeVarInt(items.size());
        items.forEach(s -> writeItem(buf, s));
    }

    public static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(readItem(buf));
        }
        return out;
    }

    public static void writeOdds(FriendlyByteBuf buf, LootOdds odds) {
        buf.writeResourceLocation(odds.tableId());
        buf.writeVarInt(odds.rolls());
        buf.writeVarInt(odds.emptyRolls());
        buf.writeVarInt(odds.rows().size());
        for (LootOdds.Row row : odds.rows()) {
            writeItem(buf, row.example());
            buf.writeVarInt(row.hits());
            buf.writeVarInt(row.total());
            buf.writeVarInt(row.min());
            buf.writeVarInt(row.max());
            writeMap(buf, row.variants(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        }
    }

    public static LootOdds readOdds(FriendlyByteBuf buf) {
        ResourceLocation table = buf.readResourceLocation();
        int rolls = buf.readVarInt();
        int empty = buf.readVarInt();
        int count = buf.readVarInt();
        List<LootOdds.Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new LootOdds.Row(readItem(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    new TreeMap<>(readMap(buf, b -> b.readUtf(), b -> b.readVarInt()))));
        }
        return new LootOdds(table, rolls, empty, rows);
    }

    // ------------------------------------------------------------------ loot index

    public static void writeIndex(FriendlyByteBuf buf, LootIndex index) {
        writeIdMap(buf, index.tablesByStructure());
        writeIdMap(buf, index.itemsByTable());
    }

    public static LootIndex readIndex(FriendlyByteBuf buf) {
        return new LootIndex(readIdMap(buf), readIdMap(buf));
    }

    /** The saved half of the loot index, for the server's cache. */
    public static void writeScan(FriendlyByteBuf buf, StructureScan scan) {
        writeIdMap(buf, scan.tables());
        writeIdMap(buf, scan.templates());
        buf.writeVarInt(scan.patches().size());
        scan.patches().forEach((template, patches) -> {
            buf.writeResourceLocation(template);
            buf.writeUtf(patches);
        });
        buf.writeVarInt(scan.retry().size());
        scan.retry().forEach(buf::writeResourceLocation);
    }

    public static StructureScan readScan(FriendlyByteBuf buf) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = readIdMap(buf);
        Map<ResourceLocation, Set<ResourceLocation>> templates = readIdMap(buf);
        int count = buf.readVarInt();
        Map<ResourceLocation, String> patches = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            patches.put(buf.readResourceLocation(), buf.readUtf());
        }
        int retrying = buf.readVarInt();
        Set<ResourceLocation> retry = new TreeSet<>();
        for (int i = 0; i < retrying; i++) {
            retry.add(buf.readResourceLocation());
        }
        return new StructureScan(tables, templates, patches, retry);
    }

    private static void writeIdMap(FriendlyByteBuf buf, Map<ResourceLocation, Set<ResourceLocation>> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : map.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue().size());
            e.getValue().forEach(buf::writeResourceLocation);
        }
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> readIdMap(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, Set<ResourceLocation>> out = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation key = buf.readResourceLocation();
            int size = buf.readVarInt();
            Set<ResourceLocation> values = new TreeSet<>();
            for (int v = 0; v < size; v++) {
                values.add(buf.readResourceLocation());
            }
            out.put(key, values);
        }
        return out;
    }

    // ------------------------------------------------------------------ loot table editing

    /** A loot table for the editor, or why it can't have one. */
    public record TableReply(LootOverrides.View view, Component problem) {
    }

    public static void writeTable(FriendlyByteBuf buf, TableReply reply) {
        buf.writeBoolean(reply.problem() != null);
        if (reply.problem() != null) {
            writeComponent(buf, reply.problem());
            return;
        }
        LootOverrides.View view = reply.view();
        buf.writeResourceLocation(view.id());
        buf.writeEnum(view.status());
        writeText(buf, view.current());
        writeText(buf, view.original());
        writeText(buf, view.base());
    }

    public static TableReply readTable(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return new TableReply(null, readComponent(buf));
        }
        ResourceLocation id = buf.readResourceLocation();
        LootOverrides.Status status = buf.readEnum(LootOverrides.Status.class);
        return new TableReply(new LootOverrides.View(id, readText(buf), readText(buf), readText(buf), status), null);
    }

    /** An edited table going up to the server: which table, and its JSON. */
    public static void writeDraft(FriendlyByteBuf buf, ResourceLocation id, String json) {
        buf.writeResourceLocation(id);
        writeText(buf, json);
    }

    public record Draft(ResourceLocation id, String json) {
    }

    /** An edit to roll once, into a container of {@code size} slots. */
    public record DraftRoll(Draft draft, long seed, int size) {
    }

    public static void writeDraftRoll(FriendlyByteBuf buf, ResourceLocation id, String json, long seed, int size) {
        writeDraft(buf, id, json);
        buf.writeLong(seed);
        buf.writeVarInt(size);
    }

    public static DraftRoll readDraftRoll(FriendlyByteBuf buf) {
        return new DraftRoll(readDraft(buf), buf.readLong(), buf.readVarInt());
    }

    public static Draft readDraft(FriendlyByteBuf buf) {
        return new Draft(buf.readResourceLocation(), readText(buf));
    }

    /** Text of any length, or null. Loot tables can be longer than a string packet field allows. */
    private static void writeText(FriendlyByteBuf buf, String text) {
        buf.writeBoolean(text != null);
        if (text != null) {
            buf.writeByteArray(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(FriendlyByteBuf buf) {
        return buf.readBoolean() ? new String(buf.readByteArray(), StandardCharsets.UTF_8) : null;
    }

    // ------------------------------------------------------------------ helpers

    // The buffer's own list and map helpers are gone from 26.3. These write the same: a count, then
    // each entry.
    private static <T> void writeList(FriendlyByteBuf buf, Collection<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        for (T entry : list) {
            writer.accept(buf, entry);
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int count = buf.readVarInt();
        List<T> out = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return out;
    }

    private static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> map, BiConsumer<FriendlyByteBuf, K> keys, BiConsumer<FriendlyByteBuf, V> values) {
        buf.writeVarInt(map.size());
        map.forEach((key, value) -> {
            keys.accept(buf, key);
            values.accept(buf, value);
        });
    }

    private static <K, V> Map<K, V> readMap(FriendlyByteBuf buf, Function<FriendlyByteBuf, K> keys, Function<FriendlyByteBuf, V> values) {
        int count = buf.readVarInt();
        Map<K, V> out = new HashMap<>();
        for (int i = 0; i < count; i++) {
            K key = keys.apply(buf);
            out.put(key, values.apply(buf));
        }
        return out;
    }

    private static void writeNullableId(FriendlyByteBuf buf, ResourceLocation id) {
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeResourceLocation(id);
        }
    }

    private static ResourceLocation readNullableId(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    private static void writeJson(FriendlyByteBuf buf, JsonObject json) {
        buf.writeBoolean(json != null);
        if (json != null) {
            buf.writeUtf(json.toString(), MAX_JSON);
        }
    }

    private static JsonObject readJson(FriendlyByteBuf buf) {
        return buf.readBoolean() ? JsonParser.parseString(buf.readUtf(MAX_JSON)).getAsJsonObject() : null;
    }

    private static List<CompoundTag> compounds(ListTag list) {
        List<CompoundTag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(Nbt.compound(list, i));
        }
        return out;
    }
}
