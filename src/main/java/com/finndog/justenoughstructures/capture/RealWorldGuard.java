package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.JesLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * Keeps a capture away from the real world.
 *
 * <p>Structure code is handed the sandbox, but some of it reaches past it to a real level: through
 * {@code WorldGenLevel.getLevel()}, a mob's own level, or the server. Captures are placed around
 * chunk 0, 0, next to spawn, so reading blocks there would read spawn's, placing blocks would change
 * it, adding a mob would put it there for real, and anything further out would load or generate
 * real chunks on the server thread.
 *
 * <p>While a thread is capturing, the real levels answer that thread from the sandbox instead. In
 * the area being captured, blocks, block entities, heights, ticks and new entities come from and go
 * to the sandbox, just as if the code had used it. Anywhere else, and in other dimensions, there's
 * only air, nothing is kept, and no chunk is loaded. Entity searches find nothing, and sounds,
 * particles and block events go nowhere. Other threads, the server's own included, never see any
 * of this.
 */
public final class RealWorldGuard {
    private static final AtomicInteger CAPTURING = new AtomicInteger();
    private static final ThreadLocal<Sandbox> CURRENT = new ThreadLocal<>();

    private RealWorldGuard() {
    }

    /** The sandbox standing in for the real levels on this thread, or null if it isn't capturing. */
    public static Sandbox current() {
        return CAPTURING.get() == 0 ? null : CURRENT.get();
    }

    /** Starts standing in for the real levels on this thread, while {@code level} is being captured in. */
    static Sandbox begin(ServerLevel level, String what) {
        Sandbox sandbox = new Sandbox(level, what, CURRENT.get());
        CURRENT.set(sandbox);
        CAPTURING.incrementAndGet();
        return sandbox;
    }

    static void end(Sandbox sandbox) {
        if (CURRENT.get() != sandbox) {
            return;
        }
        // Back to whatever this thread was doing before, should a capture ever start inside another.
        if (sandbox.outer != null) {
            CURRENT.set(sandbox.outer);
        } else {
            CURRENT.remove();
        }
        CAPTURING.decrementAndGet();
        sandbox.report();
    }

    /** What a capture's real levels answer with. Only ever used on the capturing thread. */
    public static final class Sandbox {
        private final ServerLevel level;
        private final String what;
        private final Sandbox outer;
        private CaptureRegion region;
        /** How often each kind of reach was turned away, and where the first one came from. */
        private final Map<String, Integer> reaches = new LinkedHashMap<>();
        private final Map<String, String> firstFrom = new LinkedHashMap<>();

        private Sandbox(ServerLevel level, String what, Sandbox outer) {
            this.level = level;
            this.what = what;
            this.outer = outer;
        }

        /** The structure's own area, once it's being placed. Before that there's nothing to stand in with. */
        void placing(CaptureRegion region) {
            this.region = region;
        }

        /** The sandbox area if this level is the one being captured in and the chunk is part of it, or null. */
        private CaptureRegion regionFor(Level asked, int chunkX, int chunkZ) {
            return asked == level && region != null && region.hasChunk(chunkX, chunkZ) ? region : null;
        }

        private CaptureRegion regionFor(Level asked, BlockPos pos) {
            return asked.isOutsideBuildHeight(pos) ? null
                    : regionFor(asked, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        }

        public BlockState blockState(Level asked, BlockPos pos) {
            note("read blocks");
            CaptureRegion r = regionFor(asked, pos);
            if (r != null) {
                return r.getBlockState(pos);
            }
            return asked.isOutsideBuildHeight(pos) ? Blocks.VOID_AIR.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        public FluidState fluidState(Level asked, BlockPos pos) {
            note("read blocks");
            CaptureRegion r = regionFor(asked, pos);
            return r != null ? r.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }

        public boolean setBlock(Level asked, BlockPos pos, BlockState state, int flags, int recursionLeft) {
            note("place blocks");
            CaptureRegion r = regionFor(asked, pos);
            return r != null && r.setBlock(pos, state, flags, recursionLeft);
        }

        public BlockEntity blockEntity(Level asked, BlockPos pos) {
            note("read block entities");
            CaptureRegion r = regionFor(asked, pos);
            return r != null ? r.getBlockEntity(pos) : null;
        }

        public int height(Level asked, Heightmap.Types type, int x, int z) {
            note("read heights");
            CaptureRegion r = regionFor(asked, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            return r != null ? r.getHeight(type, x, z) : asked.getMinBuildHeight();
        }

        public boolean hasChunk(Level asked, int chunkX, int chunkZ) {
            return regionFor(asked, chunkX, chunkZ) != null;
        }

        /** A chunk of the sandbox, for code asking a real level's chunk source, or null for any other. */
        public ChunkAccess chunk(Level asked, int chunkX, int chunkZ) {
            note("load chunks");
            CaptureRegion r = regionFor(asked, chunkX, chunkZ);
            return r != null ? r.getChunk(chunkX, chunkZ) : null;
        }

        /** A chunk asked for some other way, which isn't loaded. */
        public void refuseChunk() {
            note("load chunks");
        }

        /** Puts a mob or item meant for a real level into the preview instead, if it's in the area being captured. */
        public boolean addEntity(Level asked, Entity entity) {
            note("add entities");
            CaptureRegion r = regionFor(asked, entity.blockPosition());
            return r != null && r.addFreshEntity(entity);
        }

        /** Entity searches on a real level find nothing: the mobs and players there aren't part of the structure. */
        public void searchEntities() {
            note("search for entities");
        }

        /** Sounds, particles, block events and game events on a real level go nowhere. */
        public void effect() {
            note("play sounds or effects");
        }

        /** A tick scheduled on a real level goes to the sandbox, where it's kept with the chunk like any other. */
        @SuppressWarnings("unchecked")
        public void schedule(LevelTicks<?> ticks, ScheduledTick<?> tick) {
            note("schedule ticks");
            CaptureRegion r = regionFor(level, tick.pos());
            if (r == null) {
                return;
            }
            if (ticks == level.getBlockTicks()) {
                r.getBlockTicks().schedule((ScheduledTick<Block>) tick);
            } else if (ticks == level.getFluidTicks()) {
                r.getFluidTicks().schedule((ScheduledTick<Fluid>) tick);
            }
        }

        private void note(String kind) {
            Integer count = reaches.merge(kind, 1, Integer::sum);
            if (count == 1) {
                firstFrom.put(kind, caller());
            }
        }

        private void report() {
            if (!reaches.isEmpty()) {
                StringBuilder found = new StringBuilder();
                reaches.forEach((kind, count) -> found.append(found.isEmpty() ? "" : ", ").append(kind).append(" x").append(count)
                        .append(" (first from ").append(firstFrom.get(kind)).append(')'));
                JesLog.debug("While previewing {}, structure code reached for the real world and got the sandbox instead: {}", what, found);
            }
        }

        /** The first frame outside the game and this mod: the mod whose code reached for the real world. */
        private static String caller() {
            return StackWalker.getInstance().walk(frames -> frames
                    .map(StackWalker.StackFrame::getClassName)
                    .filter(name -> !name.startsWith("net.minecraft.") && !name.startsWith("com.mojang.")
                            && !name.startsWith("com.finndog.justenoughstructures.") && !name.startsWith("java.")
                            && !name.startsWith("jdk.") && !name.startsWith("sun.") && !name.startsWith("org.spongepowered.")
                            && !name.startsWith("com.llamalad7.") && !name.startsWith("net.fabricmc."))
                    .findFirst().orElse("the game"));
        }
    }
}
