package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
    /** The most blocks along a list picture's longest side. A picture is a couple of hundred pixels across, so more wouldn't show. */
    public static final int PICTURE_MOST = 96;
    private static final String VAULT = "minecraft:vault";
    private static final String VAULT_DEFAULT_TABLE = "minecraft:chests/trial_chambers/reward";

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
    private volatile Int2IntOpenHashMap lookup;
    private volatile List<Container> containers;
    private volatile List<Spawner> spawners;

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

    /** Where a block comes in the order a capture keeps them: a layer at a time, then a row at a time. */
    private static int captureOrder(int packed) {
        return unpackY(packed) << 20 | unpackZ(packed) << 10 | unpackX(packed);
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
        int state = lookup().get(pack(pos.getX(), pos.getY(), pos.getZ()));
        return state < 0 ? null : palette.get(state);
    }

    /** Builds what {@link #stateAt} looks blocks up in, which takes a moment for a big structure, ahead of time. */
    public void prepareLookup() {
        lookup();
    }

    private Int2IntOpenHashMap lookup() {
        Int2IntOpenHashMap found = lookup;
        if (found == null) {
            found = new Int2IntOpenHashMap(positions.length);
            found.defaultReturnValue(-1);
            for (int i = 0; i < positions.length; i++) {
                found.put(positions[i], states[i]);
            }
            lookup = found;
        }
        return found;
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

    /**
     * A lighter copy for a list picture, which never shows what's saved in containers. A structure
     * longer than {@link #PICTURE_MOST} blocks is drawn with fewer, bigger blocks instead, as a picture
     * is only a couple of hundred pixels across: each takes the block found most in its cube, leaving
     * out air so thin walls stay. Its block entities and entities go too, as they'd no longer sit on
     * the blocks they belong to.
     */
    public StructureSnapshot forPicture() {
        int longest = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        int step = (longest + PICTURE_MOST - 1) / PICTURE_MOST;
        if (step <= 1) {
            return withoutLoot();
        }
        // How many of each block are in each cube, then the commonest for each.
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (int i = 0; i < positions.length; i++) {
            if (palette.get(states[i]).isAir()) {
                continue;
            }
            int p = positions[i];
            int cell = pack(unpackX(p) / step, unpackY(p) / step, unpackZ(p) / step);
            counts.addTo(((long) cell << 32) | states[i], 1);
        }
        Int2LongOpenHashMap best = new Int2LongOpenHashMap();
        for (Long2IntMap.Entry entry : counts.long2IntEntrySet()) {
            int cell = (int) (entry.getLongKey() >>> 32);
            long candidate = ((long) entry.getIntValue() << 32) | (int) entry.getLongKey();
            long current = best.getOrDefault(cell, -1L);
            // The most of, and on a tie the one first in the palette, so it comes out the same every time.
            if (current < 0 || (candidate >>> 32) > (current >>> 32) || ((candidate >>> 32) == (current >>> 32) && (int) candidate < (int) current)) {
                best.put(cell, candidate);
            }
        }
        int[] cells = best.keySet().toIntArray();
        // In the order a capture keeps its blocks, which is also the one they're sent in most cheaply.
        IntArrays.quickSort(cells, (a, b) -> Integer.compare(captureOrder(a), captureOrder(b)));
        List<BlockState> used = new ArrayList<>();
        Int2IntOpenHashMap remap = new Int2IntOpenHashMap();
        int[] cellStates = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int state = (int) best.get(cells[i]);
            if (!remap.containsKey(state)) {
                remap.put(state, used.size());
                used.add(palette.get(state));
            }
            cellStates[i] = remap.get(state);
        }
        Vec3i smaller = new Vec3i((size.getX() + step - 1) / step, (size.getY() + step - 1) / step, (size.getZ() + step - 1) / step);
        return new StructureSnapshot(structureId, seed, terrain, origin, smaller, used, cells, cellStates, List.of(), List.of(), pieceCount);
    }

    private static List<CompoundTag> withoutLoot(List<CompoundTag> tags) {
        List<CompoundTag> out = new ArrayList<>(tags.size());
        for (CompoundTag tag : tags) {
            CompoundTag copy = tag.copy();
            copy.remove("LootTable");
            copy.remove("LootTableSeed");
            copy.remove("Items");
            copy.remove(ContainerSources.TAG);
            TrialSpawners.hideLoot(copy);
            // A vault keeps its table in its config, and has one even without, so it goes altogether.
            if (Nbt.string(copy, "id").equals(VAULT)) {
                continue;
            }
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
            String table = Nbt.hasString(tag, "LootTable") ? Nbt.string(tag, "LootTable") : vaultTable(tag);
            boolean hasItems = Nbt.hasList(tag, "Items") && !Nbt.list(tag, "Items", Tag.TAG_COMPOUND).isEmpty();
            if (table != null || hasItems) {
                out.add(new Container(new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z")), Nbt.string(tag, "id"),
                        table, Nbt.getLong(tag, "LootTableSeed"), false, Source.read(Nbt.compound(tag, ContainerSources.TAG))));
            }
        }
        for (CompoundTag tag : entities) {
            if (Nbt.hasString(tag, "LootTable")) {
                ListTag pos = Nbt.list(tag, "Pos", Tag.TAG_DOUBLE);
                BlockPos at = BlockPos.containing(Nbt.getDouble(pos, 0), Nbt.getDouble(pos, 1), Nbt.getDouble(pos, 2));
                out.add(new Container(at, Nbt.string(tag, "id"), Nbt.string(tag, "LootTable"), Nbt.getLong(tag, "LootTableSeed"), true, null));
            }
        }
        return out;
    }

    /**
     * A vault's loot table, which it keeps in its config, or the trial chambers' reward when it has
     * none of its own, as the game does. Null for anything else.
     */
    private static String vaultTable(CompoundTag tag) {
        if (!Nbt.string(tag, "id").equals(VAULT)) {
            return null;
        }
        String table = Nbt.string(Nbt.compound(tag, "config"), "loot_table");
        return table.isEmpty() ? VAULT_DEFAULT_TABLE : table;
    }

    /** Every spawner, worked out once like {@link #containers()}. */
    public List<Spawner> spawners() {
        List<Spawner> found = spawners;
        if (found == null) {
            List<Spawner> out = new ArrayList<>();
            for (CompoundTag tag : blockEntities) {
                BlockPos pos = new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z"));
                if (Nbt.hasCompound(tag, "SpawnData")) {
                    out.add(new Spawner(pos, SpawnerPatches.mobOf(tag), SpawnerPatches.othersOf(tag),
                            Source.read(Nbt.compound(tag, ContainerSources.SPAWNER_TAG))));
                } else if (Nbt.hasList(tag, TrialSpawners.TAG)) {
                    // A trial spawner: its likeliest mob, and how many others.
                    ListTag mobs = Nbt.list(tag, TrialSpawners.TAG, Tag.TAG_COMPOUND);
                    String mob = "";
                    int best = 0;
                    for (Tag t : mobs) {
                        if (Nbt.getInt((CompoundTag) t, "weight") > best) {
                            best = Nbt.getInt((CompoundTag) t, "weight");
                            mob = Nbt.string((CompoundTag) t, "entity");
                        }
                    }
                    out.add(new Spawner(pos, mob, Math.max(0, mobs.size() - 1), Source.read(Nbt.compound(tag, ContainerSources.SPAWNER_TAG))));
                }
            }
            found = List.copyOf(out);
            spawners = found;
        }
        return found;
    }

    /**
     * A spawner found in the snapshot: its mob, or "" for none, and how many others it makes as well.
     * {@code source} is where in which template it came from, with what mob it had before a dev
     * changed it, or null when it can't be given another mob: structure code placed it, or picked
     * its mob as it generated.
     */
    public record Spawner(BlockPos pos, String mob, int others, Source source) {
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
     * had before a dev changed it, or null if it's not been changed. For a spawner, the mob it had, and
     * the block it was before a dev made it the other kind of spawner.
     */
    public record Source(ResourceLocation template, BlockPos pos, ResourceLocation block, String patchedFrom) {
        static Source read(CompoundTag tag) {
            ResourceLocation template = ResourceLocation.tryParse(Nbt.string(tag, "template"));
            ResourceLocation block = ResourceLocation.tryParse(Nbt.string(tag, "block"));
            if (tag.isEmpty() || template == null || block == null) {
                return null;
            }
            return new Source(template, new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z")), block,
                    tag.contains("patched_from") ? Nbt.string(tag, "patched_from") : null);
        }
    }
}
