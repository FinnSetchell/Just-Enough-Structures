package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.JustEnoughStructures;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * Generates a structure with its own code and records what it placed.
 *
 * <p>The structure's {@link Structure#generate} picks the layout, then {@link StructureStart#placeInChunk}
 * places every chunk it covers into a {@link CaptureRegion} made of chunks that belong to no world.
 * This is the same path {@code /place structure} and normal world generation take, so processors,
 * data markers, custom structure types and loot all behave as they would in a real world, and the
 * real world is never touched.
 */
public final class StructureCapture {
    private static final ChunkPos START_CHUNK = new ChunkPos(0, 0);
    private static final int MAX_CHUNKS_ACROSS = StructureSnapshot.MAX_SIZE / 16;
    /**
     * One capture at a time. Previews and the loot index run on different threads, and structure
     * code leans on caches that aren't safe to share, like the block lists templates keep. Fair, so
     * a preview only ever waits for the one index capture in progress.
     */
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    /** The sandbox's structures while this thread is placing a capture, for {@code ServerLevelMixin}. */
    private static final ThreadLocal<StructureManager> SANDBOX_STRUCTURES = new ThreadLocal<>();

    private StructureCapture() {
    }

    /** The seed a structure is shown with before anyone rerolls it, so the first preview is always the same. */
    public static long defaultSeed(ResourceLocation structureId) {
        return structureId.toString().hashCode() * 0x9E3779B97F4A7C15L;
    }

    public static CaptureResult capture(MinecraftServer server, ResourceLocation structureId, long seed) {
        try {
            // Only ever a wait for one other capture, so this is a safety net rather than a limit.
            if (!LOCK.tryLock(3, TimeUnit.MINUTES)) {
                return CaptureResult.failure("Another preview is still generating, try again in a moment", List.of(), 0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CaptureResult.failure("Interrupted", List.of(), 0);
        }
        try {
            return captureLocked(server, structureId, seed);
        } finally {
            LOCK.unlock();
        }
    }

    private static CaptureResult captureLocked(MinecraftServer server, ResourceLocation structureId, long seed) {
        long started = System.nanoTime();
        List<String> attempts = new ArrayList<>();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, structureId));
        if (holder.isEmpty()) {
            return CaptureResult.failure("Unknown structure " + structureId, attempts, elapsed(started));
        }
        Structure structure = holder.get().value();

        String lastError = "It didn't find anywhere to generate on any terrain";
        for (SandboxTerrain terrain : terrainsFor(structure)) {
            try {
                StructureSnapshot snapshot;
                try {
                    snapshot = captureOn(server, structureId, structure, terrain, seed, attempts);
                } catch (ConcurrentModificationException e) {
                    // The world's own generation threads can still race us over those caches. It
                    // says nothing about the terrain, so try the same one again.
                    attempts.add(terrain + ": ran into world generation, trying again");
                    snapshot = captureOn(server, structureId, structure, terrain, seed, attempts);
                }
                if (snapshot != null) {
                    return CaptureResult.success(snapshot, attempts, elapsed(started));
                }
            } catch (TooLargeException e) {
                attempts.add(terrain + ": " + e.getMessage());
                return CaptureResult.failure(e.getMessage(), attempts, elapsed(started));
            } catch (RuntimeException | LinkageError e) {
                JustEnoughStructures.LOGGER.warn("Capturing {} on {} terrain failed", structureId, terrain, e);
                attempts.add(terrain + ": crashed with " + e);
                lastError = "It crashed while generating: " + e;
            }
        }
        return CaptureResult.failure(lastError, attempts, elapsed(started));
    }

    /** Terrains to try, most likely first, judged by where the structure is allowed to spawn. */
    static List<SandboxTerrain> terrainsFor(Structure structure) {
        int nether = 0;
        int end = 0;
        int ocean = 0;
        int total = 0;
        for (Holder<Biome> biome : structure.biomes()) {
            total++;
            if (biome.is(BiomeTags.IS_NETHER)) {
                nether++;
            } else if (biome.is(BiomeTags.IS_END)) {
                end++;
            } else if (biome.is(BiomeTags.IS_OCEAN)) {
                ocean++;
            }
        }
        if (total > 0 && nether * 2 > total) {
            return List.of(SandboxTerrain.NETHER, SandboxTerrain.VOID, SandboxTerrain.LAND);
        }
        if (total > 0 && end * 2 > total) {
            return List.of(SandboxTerrain.END, SandboxTerrain.VOID, SandboxTerrain.LAND);
        }
        if (total > 0 && ocean * 2 > total) {
            return List.of(SandboxTerrain.OCEAN, SandboxTerrain.LAND, SandboxTerrain.VOID);
        }
        return List.of(SandboxTerrain.LAND, SandboxTerrain.OCEAN, SandboxTerrain.VOID);
    }

    private static StructureSnapshot captureOn(MinecraftServer server, ResourceLocation structureId, Structure structure,
                                               SandboxTerrain terrain, long seed, List<String> attempts) {
        ServerLevel level = levelFor(server, terrain);
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> biome = biomeFor(structure, terrain, biomes);
        FixedBiomeSource biomeSource = new FixedBiomeSource(biome);
        SandboxChunkGenerator generator = new SandboxChunkGenerator(biomeSource, terrain, level);

        StructureStart start = structure.generate(server.registryAccess(), generator, biomeSource,
                level.getChunkSource().randomState(), server.getStructureManager(), seed, START_CHUNK, 0, level, b -> true);
        if (!start.isValid()) {
            attempts.add(terrain + ": no valid start");
            return null;
        }

        BoundingBox box = start.getBoundingBox();
        int minChunkX = SectionPos.blockToSectionCoord(box.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
        int across = Math.max(maxChunkX - minChunkX, maxChunkZ - minChunkZ) + 1;
        if (across > MAX_CHUNKS_ACROSS) {
            throw new TooLargeException("It's " + across + " chunks across, more than the " + MAX_CHUNKS_ACROSS + " we can show");
        }

        // One spare chunk on every side so pieces can look at their neighbours, and an odd width so
        // the region has a true centre chunk.
        int side = across + 2;
        if (side % 2 == 0) {
            side++;
        }
        int firstX = (minChunkX + maxChunkX) / 2 - side / 2;
        int firstZ = (minChunkZ + maxChunkZ) / 2 - side / 2;
        List<ChunkAccess> chunks = new ArrayList<>(side * side);
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                chunks.add(sandboxChunk(new ChunkPos(firstX + dx, firstZ + dz), level, generator, biomes, biome));
            }
        }

        CaptureRegion region = new CaptureRegion(level, chunks, side);
        region.setCurrentlyGenerating(() -> "Just Enough Structures preview of " + structureId);
        StructureManager structureManager = level.structureManager().forWorldGenRegion(region);
        SANDBOX_STRUCTURES.set(structureManager);
        try {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    // Seeded the way ChunkGenerator.applyBiomeDecoration seeds structure placement.
                    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
                    long decorationSeed = random.setDecorationSeed(seed, pos.getMinBlockX(), pos.getMinBlockZ());
                    random.setFeatureSeed(decorationSeed, 0, structure.step().ordinal());
                    BoundingBox writable = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                            pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
                    region.placing(pos);
                    start.placeInChunk(region, structureManager, generator, random, writable, pos);
                }
            }
            region.placing(null);
            postProcess(region, chunks);
        } finally {
            SANDBOX_STRUCTURES.remove();
        }

        StructureSnapshot snapshot = snapshot(structureId, seed, terrain, region, chunks, start.getPieces().size());
        if (snapshot == null) {
            attempts.add(terrain + ": placed nothing");
            return null;
        }
        attempts.add(terrain + ": " + snapshot.blockCount() + " blocks");
        return snapshot;
    }

    /** What {@code ServerLevel.structureManager()} should answer on this thread, or null to leave it be. */
    public static StructureManager sandboxStructures() {
        return SANDBOX_STRUCTURES.get();
    }

    private static ServerLevel levelFor(MinecraftServer server, SandboxTerrain terrain) {
        ServerLevel level = switch (terrain) {
            case NETHER -> server.getLevel(Level.NETHER);
            case END -> server.getLevel(Level.END);
            default -> null;
        };
        return level != null ? level : server.overworld();
    }

    private static Holder<Biome> biomeFor(Structure structure, SandboxTerrain terrain, Registry<Biome> biomes) {
        Holder<Biome> any = null;
        for (Holder<Biome> biome : structure.biomes()) {
            if (any == null) {
                any = biome;
            }
            boolean fits = switch (terrain) {
                case NETHER -> biome.is(BiomeTags.IS_NETHER);
                case END -> biome.is(BiomeTags.IS_END);
                case OCEAN -> biome.is(BiomeTags.IS_OCEAN);
                case LAND, VOID -> !biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END) && !biome.is(BiomeTags.IS_OCEAN);
            };
            if (fits) {
                return biome;
            }
        }
        if (any != null) {
            return any;
        }
        return biomes.getHolderOrThrow(switch (terrain) {
            case NETHER -> Biomes.NETHER_WASTES;
            case END -> Biomes.THE_END;
            case OCEAN -> Biomes.OCEAN;
            case LAND, VOID -> Biomes.PLAINS;
        });
    }

    private static ProtoChunk sandboxChunk(ChunkPos pos, ServerLevel level, SandboxChunkGenerator generator,
                                           Registry<Biome> biomes, Holder<Biome> biome) {
        SandboxTerrain terrain = generator.terrain();
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
        for (int i = 0; i < sections.length; i++) {
            int minY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(i));
            BlockState bottom = terrain.stateAt(minY);
            boolean uniform = true;
            for (int y = 1; y < 16 && uniform; y++) {
                uniform = terrain.stateAt(minY + y) == bottom;
            }
            LevelChunkSection section = new LevelChunkSection(
                    new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, uniform ? bottom : Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES),
                    new PalettedContainer<>(biomes.asHolderIdMap(), biome, PalettedContainer.Strategy.SECTION_BIOMES));
            if (!uniform) {
                for (int y = 0; y < 16; y++) {
                    BlockState state = terrain.stateAt(minY + y);
                    if (state.isAir()) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            section.setBlockState(x, y, z, state, false);
                        }
                    }
                }
            }
            sections[i] = section;
        }
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), level, biomes, null);
        // FEATURES is the step structures are placed in. Anything later wants a light engine.
        chunk.setStatus(ChunkStatus.FEATURES);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        return chunk;
    }

    /** The shape updates a chunk gets when it's promoted to a full chunk, e.g. fences joining up. */
    private static void postProcess(CaptureRegion region, List<ChunkAccess> chunks) {
        for (ChunkAccess chunk : chunks) {
            ShortList[] lists = chunk.getPostProcessing();
            for (int i = 0; i < lists.length; i++) {
                if (lists[i] == null || lists[i].isEmpty()) {
                    continue;
                }
                short[] packed = lists[i].toShortArray();
                lists[i].clear();
                int sectionY = chunk.getSectionYFromSectionIndex(i);
                for (short s : packed) {
                    BlockPos pos = ProtoChunk.unpackOffsetCoordinates(s, sectionY, chunk.getPos());
                    BlockState state = region.getBlockState(pos);
                    if (state.getBlock() instanceof LiquidBlock) {
                        continue;
                    }
                    BlockState updated = Block.updateFromNeighbourShapes(state, region, pos);
                    if (updated != state) {
                        region.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /**
     * Blocks that aren't the flat terrain the chunks started with. Some structure code writes
     * straight into chunk sections instead of through the region, like the legs, pillars and arches
     * YUNG's and Repurposed Structures' processors add, so those never show up in written(). A
     * section whose palette holds nothing the terrain doesn't is skipped without looking inside.
     */
    private static LongArrayList changedFromTerrain(SandboxTerrain terrain, List<ChunkAccess> chunks) {
        LongArrayList changed = new LongArrayList();
        for (ChunkAccess chunk : chunks) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                int minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                Set<BlockState> baseline = new HashSet<>();
                for (int y = 0; y < 16; y++) {
                    baseline.add(terrain.stateAt(minY + y));
                }
                if (!sections[i].getStates().maybeHas(state -> !state.isAir() && !baseline.contains(state))) {
                    continue;
                }
                int baseX = chunk.getPos().getMinBlockX();
                int baseZ = chunk.getPos().getMinBlockZ();
                for (int y = 0; y < 16; y++) {
                    BlockState ground = terrain.stateAt(minY + y);
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = sections[i].getBlockState(x, y, z);
                            if (!state.isAir() && state != ground) {
                                changed.add(BlockPos.asLong(baseX + x, minY + y, baseZ + z));
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static StructureSnapshot snapshot(ResourceLocation structureId, long seed, SandboxTerrain terrain,
                                              CaptureRegion region, List<ChunkAccess> chunks, int pieces) {
        LongArrayList solid = new LongArrayList();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        LongOpenHashSet placed = new LongOpenHashSet(region.written());
        placed.addAll(changedFromTerrain(terrain, chunks));
        for (long packed : placed) {
            BlockPos pos = BlockPos.of(packed);
            if (region.getBlockState(pos).isAir()) {
                continue;
            }
            solid.add(packed);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (solid.isEmpty()) {
            return null;
        }
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        if (size.getX() > StructureSnapshot.MAX_SIZE || size.getY() > StructureSnapshot.MAX_SIZE || size.getZ() > StructureSnapshot.MAX_SIZE) {
            throw new TooLargeException("It's " + size.getX() + " x " + size.getY() + " x " + size.getZ() + " blocks, too big to show");
        }
        BlockPos origin = new BlockPos(minX, minY, minZ);

        // Stable order (y, then z, then x) so the same seed always gives the same snapshot.
        solid.sort((a, b) -> {
            int c = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
            if (c == 0) c = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
            if (c == 0) c = Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
            return c;
        });

        List<BlockState> palette = new ArrayList<>();
        Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
        int[] positions = new int[solid.size()];
        int[] states = new int[solid.size()];
        List<CompoundTag> blockEntities = new ArrayList<>();
        for (int i = 0; i < solid.size(); i++) {
            BlockPos pos = BlockPos.of(solid.getLong(i));
            BlockState state = region.getBlockState(pos);
            int index = paletteIndex.getOrDefault(state, -1);
            if (index < 0) {
                index = palette.size();
                palette.add(state);
                paletteIndex.put(state, index);
            }
            positions[i] = StructureSnapshot.pack(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ);
            states[i] = index;
            if (state.hasBlockEntity()) {
                BlockEntity blockEntity = region.getBlockEntity(pos);
                if (blockEntity != null) {
                    CompoundTag tag = blockEntity.saveWithFullMetadata();
                    tag.putInt("x", pos.getX() - minX);
                    tag.putInt("y", pos.getY() - minY);
                    tag.putInt("z", pos.getZ() - minZ);
                    blockEntities.add(tag);
                }
            }
        }

        List<CompoundTag> entities = new ArrayList<>();
        for (ChunkAccess chunk : chunks) {
            for (CompoundTag entity : ((ProtoChunk) chunk).getEntities()) {
                CompoundTag copy = entity.copy();
                ListTag pos = copy.getList("Pos", Tag.TAG_DOUBLE);
                if (pos.size() == 3) {
                    ListTag local = new ListTag();
                    local.add(DoubleTag.valueOf(pos.getDouble(0) - minX));
                    local.add(DoubleTag.valueOf(pos.getDouble(1) - minY));
                    local.add(DoubleTag.valueOf(pos.getDouble(2) - minZ));
                    copy.put("Pos", local);
                }
                entities.add(copy);
            }
        }

        return new StructureSnapshot(structureId, seed, terrain, origin, size, palette, positions, states, blockEntities, entities, pieces);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static final class TooLargeException extends RuntimeException {
        TooLargeException(String message) {
            super(message);
        }
    }
}
