package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.ticks.ProtoChunkTicks;
//? if >=26.1 {
/*import net.minecraft.world.level.chunk.PalettedContainerFactory;
*///?}

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
     * One capture at a time. Previews, the list's pictures and the loot index run on different
     * threads, and structure code leans on caches that aren't safe to share. Fair, so a preview is
     * next once the capture in progress stops for it.
     */
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    /** Threads making captures someone is waiting to see, which captures nobody's looking at stop for. */
    private static final Set<Thread> FOREGROUND = ConcurrentHashMap.newKeySet();
    /** Whether the capture on this thread is one nobody's looking at yet. */
    private static final ThreadLocal<Boolean> BACKGROUND = ThreadLocal.withInitial(() -> false);
    /** What says the capture on this thread is no longer wanted. */
    private static final ThreadLocal<BooleanSupplier> UNWANTED = new ThreadLocal<>();
    /** Templates that couldn't be loaded during the capture on this thread. */
    private static final ThreadLocal<Set<ResourceLocation>> NOT_LOADED = new ThreadLocal<>();
    /** The climates made for a world that has none, by dimension, and the server they're for. */
    private static final Map<ResourceKey<Level>, RandomState> CLIMATES = new HashMap<>();
    private static WeakReference<MinecraftServer> climatesFor = new WeakReference<>(null);
    /**
     * When a structure finds nowhere to start on any terrain: how many other seeds and spots are tried
     * on each, how many of its biomes at most, and how many places in all. A number of places rather
     * than a time, so a structure previews the same on a slow machine as on a fast one. The time limit
     * is only a safety net, and running into it counts as a failure that may not happen again.
     */
    private static final int OTHER_SPOTS = 24;
    private static final int MOST_BIOMES = 64;
    private static final int OTHER_PLACES = 192;
    private static final long OTHER_PLACES_NANOS = 30_000_000_000L;
    /** The sandbox's structures while this thread is placing a capture, for {@code ServerLevelMixin}. */
    private static final ThreadLocal<StructureManager> SANDBOX_STRUCTURES = new ThreadLocal<>();
    /** What draws from the real world's random come from while this thread is placing a capture. */
    private static final ThreadLocal<RandomSource> SANDBOX_RANDOM = new ThreadLocal<>();

    private StructureCapture() {
    }

    /**
     * Where a structure is asked to start: on what ground, in which of its biomes, at what chunk, with
     * what seed, with what climate and in what dimension. A null biome, climate or dimension is the
     * usual one for the ground.
     */
    private record Place(SandboxTerrain terrain, Holder<Biome> biome, ChunkPos chunk, long seed, RandomState climate, ServerLevel dimension) {
        Place(SandboxTerrain terrain, long seed, ServerLevel dimension) {
            this(terrain, null, START_CHUNK, seed, null, dimension);
        }

        Place(SandboxTerrain terrain, Holder<Biome> biome, ChunkPos chunk, long seed, ServerLevel dimension) {
            this(terrain, biome, chunk, seed, null, dimension);
        }

        Place withClimate(RandomState climate) {
            return new Place(terrain, biome, chunk, seed, climate, dimension);
        }

        ServerLevel level(MinecraftServer server) {
            return dimension != null ? dimension : levelFor(server, terrain);
        }
    }

    /** The seed a structure is shown with before anyone rerolls it, so the first preview is always the same. */
    public static long defaultSeed(ResourceLocation structureId) {
        return structureId.toString().hashCode() * 0x9E3779B97F4A7C15L;
    }

    public static CaptureResult capture(MinecraftServer server, ResourceLocation structureId, long seed) {
        return capture(server, structureId, seed, () -> false);
    }

    /**
     * A capture that {@code unwanted} says when nobody wants any more, like when the player who asked
     * has moved on or left, or the world has closed. It's asked again and again as the structure is
     * placed, and a capture that's no longer wanted stops there and gives back null, letting go of the
     * world rather than keeping hold of it until it's done.
     */
    public static CaptureResult capture(MinecraftServer server, ResourceLocation structureId, long seed, BooleanSupplier unwanted) {
        boolean foreground = !BACKGROUND.get();
        if (foreground) {
            FOREGROUND.add(Thread.currentThread());
        }
        try {
            try {
                // Only ever a wait for one other capture, so this is a safety net rather than a limit.
                if (!LOCK.tryLock(3, TimeUnit.MINUTES)) {
                    return CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.still_generating"), List.of(), 0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.interrupted"), List.of(), 0);
            }
            NOT_LOADED.set(new HashSet<>());
            UNWANTED.set(unwanted);
            JesLog.ranOutOfMemory();
            boolean outOfMemory = false;
            try {
                if (unwanted.getAsBoolean()) {
                    return null;
                }
                // Other mods' pieces make vanilla log warnings by the thousand as they load. They're
                // not this mod's problem, so they go to the debug log.
                CaptureResult result = JesLog.quietly(() -> captureLocked(server, structureId, seed));
                outOfMemory = JesLog.ranOutOfMemory();
                return outOfMemory && !result.succeeded()
                        ? CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.out_of_memory"), result.attempts(), result.millis())
                        : result;
            } catch (Unwanted e) {
                return null;
            } catch (OutOfMemoryError e) {
                outOfMemory = true;
                throw e;
            } finally {
                // The game moves on from a template it ran out of memory loading as though it were
                // missing, and remembers it that way, real world included. So it's forgotten again,
                // to be loaded when there's room.
                if (outOfMemory) {
                    NOT_LOADED.get().forEach(server.getStructureManager()::remove);
                }
                NOT_LOADED.remove();
                UNWANTED.remove();
                LOCK.unlock();
            }
        } finally {
            if (foreground) {
                FOREGROUND.remove(Thread.currentThread());
            }
        }
    }

    /**
     * A capture nobody is looking at yet, like a picture for the list or one for the loot index. It
     * stops for a preview someone is waiting on, which can be minutes for the biggest structures, and
     * starts again once that's done.
     */
    public static CaptureResult captureInBackground(MinecraftServer server, ResourceLocation structureId, long seed) {
        return captureInBackground(server, structureId, seed, () -> false);
    }

    /** The same, stopping once it's {@code unwanted}, as {@link #capture(MinecraftServer, ResourceLocation, long, BooleanSupplier)} does. */
    public static CaptureResult captureInBackground(MinecraftServer server, ResourceLocation structureId, long seed, BooleanSupplier unwanted) {
        BACKGROUND.set(true);
        try {
            while (true) {
                try {
                    return capture(server, structureId, seed, unwanted);
                } catch (GaveWay e) {
                    // The lock is fair, so this queues up behind the preview.
                }
            }
        } finally {
            BACKGROUND.remove();
        }
    }

    /** Notes a template that couldn't be loaded, when it was for a capture. */
    public static void couldntLoad(ResourceLocation id) {
        Set<ResourceLocation> notLoaded = NOT_LOADED.get();
        if (notLoaded != null) {
            notLoaded.add(id);
        }
    }

    /**
     * Checks once the server's up that the chunk guard is in place with the mods installed: from a
     * thread of its own, as captures run, inside a sandbox, the overworld's chunk source must hand
     * over the sandbox's chunk. Only a chunk that's already there is asked for, so nothing is loaded
     * either way. A mod that replaces how the game looks chunks up can leave the guard out, and that's
     * worth saying, as a preview's structure code could then load real chunks.
     */
    public static void checkChunkGuard(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Thread check = new Thread(() -> {
            try {
                boolean guarded = inSandbox(level, new ChunkPos(0, 0), () -> {
                    ChunkAccess own = RealWorldGuard.current().chunk(level, 0, 0);
                    return own != null && level.getChunkSource().getChunk(0, 0, ChunkStatus.FULL, false) == own;
                });
                if (!guarded) {
                    JustEnoughStructures.LOGGER.warn("A mod here changes how the game looks chunks up, so structure code building a preview could load real chunks");
                }
            } catch (RuntimeException | LinkageError e) {
                JesLog.debug("Couldn't check the chunk guard", e);
            }
        }, "Just Enough Structures chunk guard check");
        check.setDaemon(true);
        check.start();
    }

    /** Whether a capture is being made right now. For tests. */
    public static boolean busy() {
        return LOCK.isLocked();
    }

    /**
     * Where a capture can stop part way: when nobody wants it any more, and when it's one nobody's
     * looking at and someone is waiting on one they are.
     */
    private static void checkpoint() {
        BooleanSupplier unwanted = UNWANTED.get();
        if (unwanted != null && unwanted.getAsBoolean()) {
            throw new Unwanted();
        }
        if (!BACKGROUND.get()) {
            return;
        }
        for (Thread thread : FOREGROUND) {
            if (LOCK.hasQueuedThread(thread)) {
                throw new GaveWay();
            }
        }
    }

    /** A capture stopping part way, which goes straight past everything that catches a structure's failures. */
    private abstract static class Stopped extends RuntimeException {
        Stopped() {
            super(null, null, false, false);
        }
    }

    /** A capture nobody's looking at stopping for one somebody is. */
    private static final class GaveWay extends Stopped {
    }

    /** A capture nobody wants any more stopping. */
    private static final class Unwanted extends Stopped {
    }

    private static CaptureResult captureLocked(MinecraftServer server, ResourceLocation structureId, long seed) {
        long started = System.nanoTime();
        List<Component> attempts = new ArrayList<>();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> holder = Regs.holder(registry, ResourceKey.create(Registries.STRUCTURE, structureId));
        if (holder.isEmpty()) {
            return CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.unknown_structure", structureId.toString()), attempts, elapsed(started));
        }
        Structure structure = holder.get().value();
        List<SandboxTerrain> terrains = terrainsFor(structure);

        Round usual = round(server, structureId, holder.get(), terrains, seed, null, attempts, started);
        if (usual.result() != null) {
            return usual.result();
        }
        // Some only work in a dimension of their own, like Twilight Forest's troll cave on 1.20.1,
        // which asks the dimension it's in for the height of its sea, and crashes anywhere else. One
        // that finds nowhere to start gets a try there too, as its own dimension can be another height.
        Component crash = usual.crash();
        ServerLevel dimension = null;
        ServerLevel own = ownDimension(server, structure);
        if (own != null) {
            attempts.add(Component.translatable("screen.justenoughstructures.attempt.own_dimension", Ids.of(own.dimension()).toString()));
            Round there = round(server, structureId, holder.get(), terrains, seed, own, attempts, started);
            if (there.result() != null) {
                return there.result();
            }
            if (there.crash() == null) {
                dimension = own;
                crash = null;
            } else if (crash != null) {
                crash = there.crash();
            }
        }
        if (crash != null) {
            return CaptureResult.failure(crash, attempts, elapsed(started));
        }

        // Some structures only start on a share of seeds, like the End City with BetterEnd, which turns
        // most of them down. Others check for a biome of their own choosing, the height of the ground,
        // or the world's climate where they'd go. So other biomes, high ground, and other seeds at other
        // spots are tried, quickly, as a start that can't be made fails before anything's built.
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        long deadline = System.nanoTime() + OTHER_PLACES_NANOS;
        int tried = 0;
        int placedNothing = 0;
        for (Place other : otherPlaces(structure, terrains, seed, biomes, dimension)) {
            if (tried++ >= OTHER_PLACES || placedNothing >= 3) {
                break;
            }
            if (System.nanoTime() > deadline) {
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.out_of_time"));
                return CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.nowhere_to_generate"),
                        attempts, elapsed(started));
            }
            try {
                Place place = other.withClimate(climate(server, other.level(server)));
                if (!startsOn(server, structureId, holder.get(), place)) {
                    continue;
                }
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.other_place", other.terrain().name()));
                StructureSnapshot snapshot = attempt(server, structureId, holder.get(), place, attempts);
                if (snapshot != null) {
                    return CaptureResult.success(snapshot, attempts, elapsed(started));
                }
                placedNothing++;
            } catch (Stopped e) {
                throw e;
            } catch (TooLargeException e) {
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.failed", other.terrain().name(), e.reason));
                return CaptureResult.failure(e.reason, attempts, elapsed(started));
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                // Into the game log once for each structure and kind of failure, so a report about it has the cause.
                JesLog.warnOnce("capture-crash:" + structureId + "|" + e.getClass().getName(), "Capturing {} on {} terrain somewhere else failed",
                        structureId, other.terrain(), e);
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.crashed", other.terrain().name(), String.valueOf(e)));
                return CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.crashed", String.valueOf(e)),
                        attempts, elapsed(started));
            }
        }
        attempts.add(Component.translatable("screen.justenoughstructures.attempt.other_places"));
        return CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.nowhere_to_generate"), attempts, elapsed(started));
    }

    /** How trying every terrain went: a result to give back, if it came to one, and what it crashed with, if it did. */
    private record Round(CaptureResult result, Component crash) {
    }

    /** Tries the structure on each of its terrains in turn, in its usual dimension for each or the one given. */
    private static Round round(MinecraftServer server, ResourceLocation structureId, Holder<Structure> holder, List<SandboxTerrain> terrains,
                               long seed, ServerLevel dimension, List<Component> attempts, long started) {
        Component crash = null;
        for (SandboxTerrain terrain : terrains) {
            try {
                StructureSnapshot snapshot = attempt(server, structureId, holder, new Place(terrain, seed, dimension), attempts);
                if (snapshot != null) {
                    return new Round(CaptureResult.success(snapshot, attempts, elapsed(started)), null);
                }
            } catch (Stopped e) {
                throw e;
            } catch (TooLargeException e) {
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.failed", terrain.name(), e.reason));
                return new Round(CaptureResult.failure(e.reason, attempts, elapsed(started)), null);
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                // Into the game log once for each structure and kind of failure, so a report about it has the cause.
                JesLog.warnOnce("capture-crash:" + structureId + "|" + e.getClass().getName(), "Capturing {} on {} terrain failed", structureId, terrain, e);
                attempts.add(Component.translatable("screen.justenoughstructures.attempt.crashed", terrain.name(), String.valueOf(e)));
                crash = Component.translatable("screen.justenoughstructures.error.crashed", String.valueOf(e));
            }
        }
        return new Round(null, crash);
    }

    /**
     * Where else to try a structure, most likely first: its other biomes on its likeliest ground, high
     * ground in its land biomes, mountains first, other seeds at other spots, a cave under its
     * likeliest ground, and then the same on the rest of its terrains.
     */
    private static List<Place> otherPlaces(Structure structure, List<SandboxTerrain> terrains, long seed, Registry<Biome> biomes,
                                           ServerLevel dimension) {
        List<Place> out = new ArrayList<>();
        SandboxTerrain first = terrains.get(0);
        addBiomes(out, structure, first, seed, biomes, dimension);
        if (terrains.contains(SandboxTerrain.LAND)) {
            for (Holder<Biome> biome : fitting(structure, SandboxTerrain.HIGH_LAND, true)) {
                out.add(new Place(SandboxTerrain.HIGH_LAND, biome, START_CHUNK, seed, dimension));
            }
        }
        addSpots(out, first, seed, dimension);
        SandboxTerrain cave = first.kind() == SandboxTerrain.NETHER ? SandboxTerrain.NETHER_CAVE
                : first.kind() == SandboxTerrain.LAND ? SandboxTerrain.CAVE : null;
        if (cave != null) {
            out.add(new Place(cave, null, START_CHUNK, seed, dimension));
            addSpots(out, cave, seed, dimension);
        }
        for (SandboxTerrain terrain : terrains.subList(1, terrains.size())) {
            addBiomes(out, structure, terrain, seed, biomes, dimension);
        }
        for (SandboxTerrain terrain : terrains.subList(1, terrains.size())) {
            addSpots(out, terrain, seed, dimension);
        }
        return out;
    }

    private static void addBiomes(List<Place> out, Structure structure, SandboxTerrain terrain, long seed, Registry<Biome> biomes,
                                  ServerLevel dimension) {
        Holder<Biome> usual = biomeFor(structure, terrain, biomes);
        for (Holder<Biome> biome : fitting(structure, terrain, false)) {
            if (!biome.equals(usual)) {
                out.add(new Place(terrain, biome, START_CHUNK, seed, dimension));
            }
        }
    }

    /** Other seeds, each at a spot of its own, picked the same way every time for the same seed. */
    private static void addSpots(List<Place> out, SandboxTerrain terrain, long seed, ServerLevel dimension) {
        Random random = new Random(seed * 31 + terrain.ordinal());
        for (int i = 1; i <= OTHER_SPOTS; i++) {
            ChunkPos chunk = new ChunkPos(random.nextInt(2001) - 1000, random.nextInt(2001) - 1000);
            out.add(new Place(terrain, null, chunk, seed + i * 0x9E3779B97F4A7C15L, dimension));
        }
    }

    /**
     * The dimension a structure's biomes are mostly in, when that's one a mod adds. Null for the
     * Overworld, the Nether and the End, as those are where structures are tried anyway.
     */
    private static ServerLevel ownDimension(MinecraftServer server, Structure structure) {
        ServerLevel best = null;
        long most = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Set<Holder<Biome>> possible = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
            long count = structure.biomes().stream().filter(possible::contains).count();
            if (count > most) {
                best = level;
                most = count;
            }
        }
        if (best == null || best.dimension() == Level.OVERWORLD || best.dimension() == Level.NETHER || best.dimension() == Level.END) {
            return null;
        }
        return best;
    }

    /** The structure's biomes that suit a terrain, each once, mountains first if asked. */
    private static List<Holder<Biome>> fitting(Structure structure, SandboxTerrain terrain, boolean mountainsFirst) {
        List<Holder<Biome>> out = new ArrayList<>();
        for (Holder<Biome> biome : structure.biomes()) {
            if (fits(biome, terrain) && !out.contains(biome)) {
                out.add(biome);
            }
        }
        if (mountainsFirst) {
            out.sort(Comparator.comparing(biome -> !biome.is(BiomeTags.IS_MOUNTAIN)));
        }
        return out.size() > MOST_BIOMES ? out.subList(0, MOST_BIOMES) : out;
    }

    private static boolean fits(Holder<Biome> biome, SandboxTerrain terrain) {
        return switch (terrain.kind()) {
            case NETHER -> biome.is(BiomeTags.IS_NETHER);
            case END -> biome.is(BiomeTags.IS_END);
            case OCEAN -> biome.is(BiomeTags.IS_OCEAN);
            default -> !biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END) && !biome.is(BiomeTags.IS_OCEAN);
        };
    }

    /**
     * The climate a structure that checks it reads, like Supplementaries' galleon, which only sails where
     * the sea is deep. A superflat world has none to speak of, so there the dimension's usual climate
     * stands in. Some mods make one slow to make, so each is kept for as long as the world is open.
     */
    private static RandomState climate(MinecraftServer server, ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator) {
            return level.getChunkSource().randomState();
        }
        // Only ever reached holding the lock, so these need nothing more.
        if (climatesFor.get() != server) {
            CLIMATES.clear();
            climatesFor = new WeakReference<>(server);
        }
        long seed = level.getSeed();
        return CLIMATES.computeIfAbsent(level.dimension(), dimension -> {
            ResourceKey<NoiseGeneratorSettings> key = dimension == Level.NETHER ? NoiseGeneratorSettings.NETHER
                    : dimension == Level.END ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD;
            NoiseGeneratorSettings settings = Regs.value(server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS), key);
            //? if >=26.3 {
            /*return RandomState.create(Regs.getter(server.registryAccess().registryOrThrow(Registries.NOISE)), seed, settings);
            *///?} else {
            return RandomState.create(settings, Regs.getter(server.registryAccess().registryOrThrow(Registries.NOISE)), seed);
            //?}
        });
    }

    /** One place: what was placed, or null if it found nowhere to start or placed nothing. */
    private static StructureSnapshot attempt(MinecraftServer server, ResourceLocation structureId, Holder<Structure> structure,
                                             Place place, List<Component> attempts) {
        checkpoint();
        return captureOn(server, structureId, structure, place, attempts);
    }

    /** Whether the structure finds somewhere to start at a place, building nothing. */
    private static boolean startsOn(MinecraftServer server, ResourceLocation structureId, Holder<Structure> structure, Place place) {
        checkpoint();
        ServerLevel level = place.level(server);
        RealWorldGuard.Sandbox guard = RealWorldGuard.begin(level, structureId + " on " + place.terrain().name().toLowerCase(Locale.ROOT) + " terrain");
        try {
            Holder<Biome> biome = place.biome() != null ? place.biome()
                    : biomeFor(structure.value(), place.terrain(), level.registryAccess().registryOrThrow(Registries.BIOME));
            FixedBiomeSource biomeSource = new FixedBiomeSource(biome);
            return start(server, structure, level, new SandboxChunkGenerator(biomeSource, place.terrain(), level), biomeSource, place).isValid();
        } finally {
            RealWorldGuard.end(guard);
        }
    }

    /**
     * Terrains to try, most likely first, judged by where the structure is allowed to spawn. The
     * other depths of sea and the lava sea only come into it when the usual ground gets nothing.
     * One with only some of its biomes in the Nether or the End, like a Nether fossil that Biomes
     * O' Plenty also lets into one of its Overworld biomes, gets that terrain too, after the rest.
     */
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
        List<SandboxTerrain> order;
        if (total > 0 && nether * 2 > total) {
            order = List.of(SandboxTerrain.NETHER, SandboxTerrain.LAVA_SEA, SandboxTerrain.VOID, SandboxTerrain.LAND);
        } else if (total > 0 && end * 2 > total) {
            order = List.of(SandboxTerrain.END, SandboxTerrain.VOID, SandboxTerrain.LAND);
        } else if (total > 0 && ocean * 2 > total) {
            order = List.of(SandboxTerrain.OCEAN, SandboxTerrain.DEEP_OCEAN, SandboxTerrain.SHALLOW_OCEAN, SandboxTerrain.LAND, SandboxTerrain.VOID);
        } else {
            order = List.of(SandboxTerrain.LAND, SandboxTerrain.OCEAN, SandboxTerrain.SHALLOW_OCEAN, SandboxTerrain.DEEP_OCEAN, SandboxTerrain.VOID);
        }
        List<SandboxTerrain> out = new ArrayList<>(order);
        if (nether > 0 && !out.contains(SandboxTerrain.NETHER)) {
            out.add(SandboxTerrain.NETHER);
        }
        if (end > 0 && !out.contains(SandboxTerrain.END)) {
            out.add(SandboxTerrain.END);
        }
        return out;
    }

    private static StructureSnapshot captureOn(MinecraftServer server, ResourceLocation structureId, Holder<Structure> structure,
                                               Place place, List<Component> attempts) {
        ServerLevel level = place.level(server);
        // Structure code that reaches past the sandbox to the real world gets the sandbox anyway,
        // from laying the structure out to the last block placed.
        RealWorldGuard.Sandbox guard = RealWorldGuard.begin(level, structureId + " on " + place.terrain().name().toLowerCase(Locale.ROOT) + " terrain");
        try {
            return captureGuarded(server, structureId, structure, place, attempts, level, guard);
        } finally {
            RealWorldGuard.end(guard);
        }
    }

    /** Lays the structure out at the place's chunk, as the place command does, building nothing yet. */
    private static StructureStart start(MinecraftServer server, Holder<Structure> holder, ServerLevel level, SandboxChunkGenerator generator,
                                        FixedBiomeSource biomeSource, Place place) {
        Structure structure = holder.value();
        RandomState randomState = place.climate() != null ? place.climate() : level.getChunkSource().randomState();
        long seed = place.seed();
        ChunkPos chunk = place.chunk();
        //? if >=26.3 {
        /*// From 26.3 a structure reads the climate through a sampler of its own, made as the place command makes it.
        return structure.generate(holder, level.dimension(), server.registryAccess(), generator, biomeSource,
                randomState.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED),
                randomState, server.getStructureManager(), seed, chunk, 0, level, b -> true);
        *///?} else if >=26.1 {
        /*return structure.generate(holder, level.dimension(), server.registryAccess(), generator, biomeSource,
                randomState, server.getStructureManager(), seed, chunk, 0, level, b -> true);
        *///?} else {
        return structure.generate(server.registryAccess(), generator, biomeSource,
                randomState, server.getStructureManager(), seed, chunk, 0, level, b -> true);
        //?}
    }

    private static StructureSnapshot captureGuarded(MinecraftServer server, ResourceLocation structureId, Holder<Structure> holder, Place place,
                                                    List<Component> attempts, ServerLevel level, RealWorldGuard.Sandbox guard) {
        Structure structure = holder.value();
        SandboxTerrain terrain = place.terrain();
        long seed = place.seed();
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> biome = place.biome() != null ? place.biome() : biomeFor(structure, terrain, biomes);
        FixedBiomeSource biomeSource = new FixedBiomeSource(biome);
        SandboxChunkGenerator generator = new SandboxChunkGenerator(biomeSource, terrain, level);

        StructureStart start = start(server, holder, level, generator, biomeSource, place);
        if (!start.isValid()) {
            attempts.add(Component.translatable("screen.justenoughstructures.attempt.no_start", terrain.name()));
            return null;
        }

        BoundingBox box = start.getBoundingBox();
        int minChunkX = SectionPos.blockToSectionCoord(box.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
        int across = Math.max(maxChunkX - minChunkX, maxChunkZ - minChunkZ) + 1;
        if (across > MAX_CHUNKS_ACROSS) {
            throw new TooLargeException(Component.translatable("screen.justenoughstructures.error.too_wide", across, MAX_CHUNKS_ACROSS));
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
        guard.placing(region);
        StructureManager structureManager = level.structureManager().forWorldGenRegion(region);
        SANDBOX_STRUCTURES.set(structureManager);
        SANDBOX_RANDOM.set(new XoroshiroRandomSource(seed));
        TemplatePlacements.begin();
        SpawnerPools.begin();
        SpawnerPools.Recorded recorded = SpawnerPools.Recorded.NONE;
        try {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    // Seeded the way ChunkGenerator.applyBiomeDecoration seeds structure placement.
                    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
                    long decorationSeed = random.setDecorationSeed(seed, pos.getMinBlockX(), pos.getMinBlockZ());
                    random.setFeatureSeed(decorationSeed, 0, structure.step().ordinal());
                    BoundingBox writable = new BoundingBox(pos.getMinBlockX(), Levels.minY(level), pos.getMinBlockZ(),
                            pos.getMaxBlockX(), Levels.maxY(level), pos.getMaxBlockZ());
                    checkpoint();
                    region.placing(pos);
                    start.placeInChunk(region, structureManager, generator, random, writable, pos);
                }
            }
            region.placing(null);
            checkpoint();
            postProcess(region, chunks);
        } finally {
            SANDBOX_STRUCTURES.remove();
            SANDBOX_RANDOM.remove();
            TemplatePlacements.end();
            recorded = SpawnerPools.end();
        }

        ContainerSources.Found sources = ContainerSources.find(start, level.getStructureManager(), region.filledBy(), level.getServer().getResourceManager());
        Map<Long, ListTag> spawnerPools = SpawnerPools.resolve(recorded.pools(), level.getServer().getResourceManager());
        StructureSnapshot snapshot = snapshot(structureId, seed, terrain, region, chunks, start.getPieces().size(), sources, spawnerPools,
                recorded.touched());
        if (snapshot == null) {
            attempts.add(Component.translatable("screen.justenoughstructures.attempt.placed_nothing", terrain.name()));
            return null;
        }
        attempts.add(Component.translatable("screen.justenoughstructures.attempt.blocks", terrain.name(), snapshot.blockCount()));
        return snapshot;
    }

    /** What {@code ServerLevel.structureManager()} should answer on this thread, or null to leave it be. */
    public static StructureManager sandboxStructures() {
        return SANDBOX_STRUCTURES.get();
    }

    /** What {@code LegacyRandomSourceMixin} draws from in place of a real world's random, or null outside a capture. */
    public static RandomSource sandboxRandom() {
        return SANDBOX_RANDOM.get();
    }

    /**
     * Runs {@code action} on this thread as though a structure were being placed into a sandbox of
     * plain land three chunks across around {@code centre}, with the real world guarded the way it
     * is during a capture. For tests.
     */
    public static <T> T inSandbox(ServerLevel level, ChunkPos centre, Supplier<T> action) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> biome = Regs.holderOrThrow(biomes, Biomes.PLAINS);
        SandboxChunkGenerator generator = new SandboxChunkGenerator(new FixedBiomeSource(biome), SandboxTerrain.LAND, level);
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                chunks.add(sandboxChunk(new ChunkPos(Levels.chunkX(centre) + dx, Levels.chunkZ(centre) + dz), level, generator, biomes, biome));
            }
        }
        CaptureRegion region = new CaptureRegion(level, chunks, 1);
        RealWorldGuard.Sandbox guard = RealWorldGuard.begin(level, "a test sandbox");
        SANDBOX_STRUCTURES.set(level.structureManager().forWorldGenRegion(region));
        SANDBOX_RANDOM.set(new XoroshiroRandomSource(0));
        try {
            guard.placing(region);
            return action.get();
        } finally {
            SANDBOX_STRUCTURES.remove();
            SANDBOX_RANDOM.remove();
            RealWorldGuard.end(guard);
        }
    }

    private static ServerLevel levelFor(MinecraftServer server, SandboxTerrain terrain) {
        ServerLevel level = switch (terrain.kind()) {
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
            if (fits(biome, terrain)) {
                return biome;
            }
        }
        if (any != null) {
            return any;
        }
        return Regs.holderOrThrow(biomes, switch (terrain.kind()) {
            case NETHER -> Biomes.NETHER_WASTES;
            case END -> Biomes.THE_END;
            case OCEAN -> Biomes.OCEAN;
            default -> Biomes.PLAINS;
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
            //? if >=26.1 {
            /*PalettedContainerFactory containers = level.palettedContainerFactory();
            LevelChunkSection section = new LevelChunkSection(
                    new PalettedContainer<>(uniform ? bottom : Blocks.AIR.defaultBlockState(), containers.blockStatesStrategy()),
                    new PalettedContainer<>(biome, containers.biomeStrategy()));
            *///?} else {
            LevelChunkSection section = new LevelChunkSection(
                    new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, uniform ? bottom : Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES),
                    new PalettedContainer<>(biomes.asHolderIdMap(), biome, PalettedContainer.Strategy.SECTION_BIOMES));
            //?}
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
        //? if >=26.1 {
        /*ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), level,
                level.palettedContainerFactory(), null);
        *///?} else {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), level, biomes, null);
        //?}
        // FEATURES is the step structures are placed in. Anything later wants a light engine.
        //? if >=1.21 {
        /*chunk.setPersistedStatus(ChunkStatus.FEATURES);
        *///?} else {
        chunk.setStatus(ChunkStatus.FEATURES);
        //?}
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
                                              CaptureRegion region, List<ChunkAccess> chunks, int pieces, ContainerSources.Found sources,
                                              Map<Long, ListTag> spawnerPools, LongSet touched) {
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
            throw new TooLargeException(Component.translatable("screen.justenoughstructures.error.too_big", size.getX(), size.getY(), size.getZ()));
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
                    //? if >=1.21 {
                    /*CompoundTag tag = blockEntity.saveWithFullMetadata(region.registryAccess());
                    *///?} else {
                    CompoundTag tag = blockEntity.saveWithFullMetadata();
                    //?}
                    tag.putInt("x", pos.getX() - minX);
                    tag.putInt("y", pos.getY() - minY);
                    tag.putInt("z", pos.getZ() - minZ);
                    CompoundTag source = sources.containers().get(pos.asLong());
                    if (source != null && ContainerSources.matches(source, tag)) {
                        tag.put(ContainerSources.TAG, source.copy());
                    }
                    ListTag pool = spawnerPools.get(pos.asLong());
                    if (pool != null && SpawnerPools.matches(pool, tag)) {
                        tag.put(SpawnerPools.TAG, pool.copy());
                    }
                    TrialSpawners.describe(tag, region.getLevel().getServer().getResourceManager());
                    // A spawner a processor changed gets its mob from the processor, not the template.
                    CompoundTag spawner = sources.spawners().get(pos.asLong());
                    if (spawner != null && !touched.contains(pos.asLong()) && ContainerSources.spawnerMatches(spawner, state, tag)) {
                        tag.put(ContainerSources.SPAWNER_TAG, spawner.copy());
                    }
                    blockEntities.add(tag);
                }
            }
        }

        List<CompoundTag> entities = new ArrayList<>();
        for (ChunkAccess chunk : chunks) {
            for (CompoundTag entity : ((ProtoChunk) chunk).getEntities()) {
                CompoundTag copy = entity.copy();
                ListTag pos = Nbt.list(copy, "Pos", Tag.TAG_DOUBLE);
                if (pos.size() == 3) {
                    ListTag local = new ListTag();
                    local.add(DoubleTag.valueOf(Nbt.getDouble(pos, 0) - minX));
                    local.add(DoubleTag.valueOf(Nbt.getDouble(pos, 1) - minY));
                    local.add(DoubleTag.valueOf(Nbt.getDouble(pos, 2) - minZ));
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
        private final Component reason;

        TooLargeException(Component reason) {
            this.reason = reason;
        }
    }
}
