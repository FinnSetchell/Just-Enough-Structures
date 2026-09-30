package com.finndog.justenoughstructures.network;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
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

/** Wire formats for everything the server sends back. */
public final class Codecs {
    private static final int MAX_JSON = 1 << 20;

    private Codecs() {
    }

    // ------------------------------------------------------------------ catalog

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
            StructureInfo info = e.info();
            buf.writeBoolean(info.notes() != null);
            if (info.notes() != null) {
                buf.writeComponent(info.notes());
            }
            buf.writeBoolean(info.author() != null);
            if (info.author() != null) {
                buf.writeUtf(info.author());
            }
            buf.writeBoolean(info.hideLootLocations());
        }
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
            Component notes = buf.readBoolean() ? buf.readComponent() : null;
            String author = buf.readBoolean() ? buf.readUtf() : null;
            StructureInfo info = new StructureInfo(notes, author, buf.readBoolean());
            out.add(new StructureCatalog.Entry(id, type, definition, sets, info));
        }
        return out;
    }

    // ------------------------------------------------------------------ captures

    public static void writeCapture(FriendlyByteBuf buf, ResourceLocation id, long seed, CaptureResult result) {
        buf.writeResourceLocation(id);
        buf.writeLong(seed);
        buf.writeVarLong(result.millis());
        buf.writeVarInt(result.attempts().size());
        result.attempts().forEach(buf::writeUtf);
        buf.writeBoolean(result.succeeded());
        if (result.succeeded()) {
            writeSnapshot(buf, result.snapshot());
        } else {
            buf.writeUtf(result.error());
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
        List<String> attempts = new ArrayList<>(attemptCount);
        for (int i = 0; i < attemptCount; i++) {
            attempts.add(buf.readUtf());
        }
        CaptureResult result = buf.readBoolean()
                ? CaptureResult.success(readSnapshot(buf), attempts, millis)
                : CaptureResult.failure(buf.readUtf(), attempts, millis);
        return new CaptureReply(id, seed, result);
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
        buf.writeVarInt(s.blockCount());
        for (int i = 0; i < s.blockCount(); i++) {
            buf.writeVarInt(s.packedPosition(i));
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
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), buf.readNbt()));
        }
        int count = buf.readVarInt();
        int[] positions = new int[count];
        int[] states = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = buf.readVarInt();
            states[i] = buf.readVarInt();
        }

        CompoundTag extra = buf.readAnySizeNbt();
        List<CompoundTag> blockEntities = compounds(extra.getList("BlockEntities", Tag.TAG_COMPOUND));
        List<CompoundTag> entities = compounds(extra.getList("Entities", Tag.TAG_COMPOUND));
        return new StructureSnapshot(id, seed, terrain, origin, size, palette, positions, states, blockEntities, entities, pieces);
    }

    // ------------------------------------------------------------------ loot

    public static void writeItems(FriendlyByteBuf buf, List<ItemStack> items) {
        buf.writeVarInt(items.size());
        items.forEach(buf::writeItem);
    }

    public static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(buf.readItem());
        }
        return out;
    }

    public static void writeOdds(FriendlyByteBuf buf, LootOdds odds) {
        buf.writeResourceLocation(odds.tableId());
        buf.writeVarInt(odds.rolls());
        buf.writeVarInt(odds.emptyRolls());
        buf.writeVarInt(odds.rows().size());
        for (LootOdds.Row row : odds.rows()) {
            buf.writeItem(row.example());
            buf.writeVarInt(row.hits());
            buf.writeVarInt(row.total());
            buf.writeVarInt(row.min());
            buf.writeVarInt(row.max());
            buf.writeMap(row.variants(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        }
    }

    public static LootOdds readOdds(FriendlyByteBuf buf) {
        ResourceLocation table = buf.readResourceLocation();
        int rolls = buf.readVarInt();
        int empty = buf.readVarInt();
        int count = buf.readVarInt();
        List<LootOdds.Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new LootOdds.Row(buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    new TreeMap<>(buf.readMap(b -> b.readUtf(), b -> b.readVarInt()))));
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

    // ------------------------------------------------------------------ helpers

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
            out.add(list.getCompound(i));
        }
        return out;
    }
}
