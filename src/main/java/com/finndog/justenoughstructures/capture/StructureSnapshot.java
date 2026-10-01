package com.finndog.justenoughstructures.capture;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything a structure placed when it generated in the sandbox, in local coordinates starting at
 * 0,0,0. Blocks are stored sparsely: {@code positions[i]} packs x, y and z, and {@code states[i]} is
 * an index into the palette.
 */
public final class StructureSnapshot {
    public static final int MAX_SIZE = 1024;

    private final ResourceLocation structureId;
    private final long seed;
    private final SandboxTerrain terrain;
    private final BlockPos origin;
    private final Vec3i size;
    private final List<BlockState> palette;
    private final int[] positions;
    private final int[] states;
    private final List<CompoundTag> blockEntities;
    private final List<CompoundTag> entities;
    private final int pieceCount;
    private Int2IntOpenHashMap lookup;
    private volatile List<Container> containers;

    public StructureSnapshot(ResourceLocation structureId, long seed, SandboxTerrain terrain, BlockPos origin, Vec3i size,
                             List<BlockState> palette, int[] positions, int[] states,
                             List<CompoundTag> blockEntities, List<CompoundTag> entities, int pieceCount) {
        if (positions.length != states.length) {
            throw new IllegalArgumentException("positions and states differ in length");
        }
        this.structureId = structureId;
        this.seed = seed;
        this.terrain = terrain;
        this.origin = origin;
        this.size = size;
        this.palette = List.copyOf(palette);
        this.positions = positions;
        this.states = states;
        this.blockEntities = Collections.unmodifiableList(blockEntities);
        this.entities = Collections.unmodifiableList(entities);
        this.pieceCount = pieceCount;
    }

    public static int pack(int x, int y, int z) {
        return x | (y << 10) | (z << 20);
    }

    public static int unpackX(int packed) {
        return packed & 1023;
    }

    public static int unpackY(int packed) {
        return (packed >> 10) & 1023;
    }

    public static int unpackZ(int packed) {
        return (packed >> 20) & 1023;
    }

    public ResourceLocation structureId() {
        return structureId;
    }

    public long seed() {
        return seed;
    }

    public SandboxTerrain terrain() {
        return terrain;
    }

    /** World position that local 0,0,0 was generated at. */
    public BlockPos origin() {
        return origin;
    }

    public Vec3i size() {
        return size;
    }

    public List<BlockState> palette() {
        return palette;
    }

    public int blockCount() {
        return positions.length;
    }

    public int packedPosition(int index) {
        return positions[index];
    }

    public BlockState state(int index) {
        return palette.get(states[index]);
    }

    public int paletteIndex(int index) {
        return states[index];
    }

    /** The block at a local position, or null if the structure placed nothing there. */
    public BlockState stateAt(BlockPos pos) {
        if (lookup == null) {
            Int2IntOpenHashMap built = new Int2IntOpenHashMap(positions.length);
            built.defaultReturnValue(-1);
            for (int i = 0; i < positions.length; i++) {
                built.put(positions[i], states[i]);
            }
            lookup = built;
        }
        int state = lookup.get(pack(pos.getX(), pos.getY(), pos.getZ()));
        return state < 0 ? null : palette.get(state);
    }

    /** Block entity NBT with x, y and z rewritten to local coordinates. */
    public List<CompoundTag> blockEntities() {
        return blockEntities;
    }

    /** Entity NBT with Pos rewritten to local coordinates. */
    public List<CompoundTag> entities() {
        return entities;
    }

    public int pieceCount() {
        return pieceCount;
    }

    /**
     * The same snapshot with nothing that says where its loot is: no loot tables and no items saved
     * in containers. The blocks are all still there.
     */
    public StructureSnapshot withoutLoot() {
        return new StructureSnapshot(structureId, seed, terrain, origin, size, palette, positions, states,
                withoutLoot(blockEntities), withoutLoot(entities), pieceCount);
    }

    private static List<CompoundTag> withoutLoot(List<CompoundTag> tags) {
        List<CompoundTag> out = new ArrayList<>(tags.size());
        for (CompoundTag tag : tags) {
            CompoundTag copy = tag.copy();
            copy.remove("LootTable");
            copy.remove("LootTableSeed");
            copy.remove("Items");
            copy.remove(ContainerSources.TAG);
            out.add(copy);
        }
        return out;
    }

    /** Every block entity or entity carrying a loot table, plus containers that were saved with items in them. */
    /** Worked out once: a snapshot never changes, and the screen asks for these several times a frame. */
    public List<Container> containers() {
        List<Container> found = containers;
        if (found == null) {
            found = List.copyOf(findContainers());
            containers = found;
        }
        return found;
    }

    private List<Container> findContainers() {
        List<Container> out = new ArrayList<>();
        for (CompoundTag tag : blockEntities) {
            String table = tag.contains("LootTable", Tag.TAG_STRING) ? tag.getString("LootTable") : null;
            boolean hasItems = tag.contains("Items", Tag.TAG_LIST) && !tag.getList("Items", Tag.TAG_COMPOUND).isEmpty();
            if (table != null || hasItems) {
                out.add(new Container(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getString("id"),
                        table, tag.getLong("LootTableSeed"), false, Source.read(tag.getCompound(ContainerSources.TAG))));
            }
        }
        for (CompoundTag tag : entities) {
            if (tag.contains("LootTable", Tag.TAG_STRING)) {
                ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                BlockPos at = BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
                out.add(new Container(at, tag.getString("id"), tag.getString("LootTable"), tag.getLong("LootTableSeed"), true, null));
            }
        }
        return out;
    }

    /**
     * A container found in the snapshot. {@code lootTable} is null for one that was saved with items
     * but no table. {@code source} is where in which template it came from, or null when it wasn't
     * placed from a template's blocks.
     */
    public record Container(BlockPos pos, String id, String lootTable, long lootSeed, boolean entity, Source source) {
    }

    /**
     * The template a container came from and its spot in it, what block it is there, and the table it
     * had before a dev changed it, or null if it's not been changed.
     */
    public record Source(ResourceLocation template, BlockPos pos, ResourceLocation block, String patchedFrom) {
        static Source read(CompoundTag tag) {
            ResourceLocation template = ResourceLocation.tryParse(tag.getString("template"));
            ResourceLocation block = ResourceLocation.tryParse(tag.getString("block"));
            if (tag.isEmpty() || template == null || block == null) {
                return null;
            }
            return new Source(template, new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), block,
                    tag.contains("patched_from") ? tag.getString("patched_from") : null);
        }
    }
}
