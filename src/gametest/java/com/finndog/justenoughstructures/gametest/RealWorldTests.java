package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.capture.RealWorldGuard;
import com.finndog.justenoughstructures.capture.StructureCapture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
//? if >=26.1 {
/*import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.pig.Pig;
*///?} else {
import net.minecraft.world.entity.animal.Pig;
//?}
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Loader-neutral test bodies for keeping captures away from the real world. */
public final class RealWorldTests {
    private RealWorldTests() {
    }

    /**
     * Structure code that never returns is stopped once its time is up. Four endless loops run in a
     * sandbox: walking up to find a roof, through the sandbox and through the real level (which
     * answers from the sandbox), walking up to find water, and waiting for the ground to rise above
     * the world. The sandbox loops read its chunks in different ways, which Lithium and Canary on
     * 1.20.1 answer without asking the sandbox for the chunk. Each runs on a separate thread, as
     * captures do, so one that isn't stopped fails the test instead of hanging the server.
     */
    public static void structureCodeThatNeverReturnsIsStopped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos real = helper.absolutePos(new BlockPos(1, 2, 1));
        ChunkPos centre = new ChunkPos(SectionPos.blockToSectionCoord(real.getX()), SectionPos.blockToSectionCoord(real.getZ()));
        // Set once the test has its answer, so a walk that wasn't stopped doesn't go on for the rest of the run.
        AtomicBoolean over = new AtomicBoolean();
        Map<String, CompletableFuture<String>> walks = new LinkedHashMap<>();
        walks.put("a walk up through the sandbox", forEver(level, centre, over, (sandbox, pos) -> sandbox.getBlockState(pos.move(Direction.UP)).isAir()));
        walks.put("a walk up through the real level", forEver(level, centre, over, (sandbox, pos) -> level.getBlockState(pos.move(Direction.UP)).isAir()));
        walks.put("a walk up looking for water", forEver(level, centre, over, (sandbox, pos) -> sandbox.getFluidState(pos.move(Direction.UP)).isEmpty()));
        walks.put("waiting for the ground to rise", forEver(level, centre, over,
                (sandbox, pos) -> sandbox.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) <= Levels.maxY(sandbox) + 1));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        List<String> notStopped = new ArrayList<>();
        walks.forEach((what, walk) -> {
            String ended;
            try {
                ended = walk.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                ended = "still going after 20 seconds";
            } catch (InterruptedException | ExecutionException e) {
                ended = String.valueOf(e);
            }
            if (!ended.equals("stopped")) {
                notStopped.add(what + ": " + ended);
            }
        });
        over.set(true);
        helper.assertTrue(notStopped.isEmpty(), "structure code that never returns wasn't stopped: " + String.join("; ", notStopped));
        helper.succeed();
    }

    /**
     * Structure code in a sandbox around {@code centre}, given a quarter of a second, taking one step
     * after another from above the ground for as long as {@code step} says to go on.
     */
    private static CompletableFuture<String> forEver(ServerLevel level, ChunkPos centre, AtomicBoolean over,
                                                     BiPredicate<WorldGenLevel, BlockPos.MutableBlockPos> step) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return StructureCapture.inSandbox(level, centre, 250_000_000L, sandbox -> {
                    BlockPos.MutableBlockPos pos = centre.getMiddleBlockPosition(200).mutable();
                    while (step.test(sandbox, pos)) {
                        if (over.get()) {
                            return "still going when the test ended";
                        }
                    }
                    return "it ended by itself at " + pos;
                });
            } catch (RuntimeException e) {
                // The exception that stops it is private to StructureCapture, so it's matched by name.
                return e.getClass().getSimpleName().equals("TookTooLong") ? "stopped" : String.valueOf(e);
            }
        }, walk -> {
            // A thread each, so they all run at once however few processors the test server has.
            Thread thread = new Thread(walk, "jes-test-endless-code");
            thread.setDaemon(true);
            thread.start();
        });
    }

    /**
     * A datapack reload can come while the server is still starting, before it has an overworld.
     * The chunk guard can't be checked then, and that mustn't count as having checked it, or it
     * never is.
     */
    public static void chunkGuardCheckWaitsForTheWorld(GameTestHelper helper) {
        helper.assertFalse(StructureCapture.checkChunkGuard((ServerLevel) null), "a chunk guard check with no overworld yet counted as done");
        helper.succeed();
    }

    /**
     * The chunk guard check must ask about a loaded chunk, wherever it is, because a hook ahead of the
     * guard may answer only for loaded chunks (C2ME's does from 1.21). Here the only loaded End chunk
     * is one forced far out, and the check's answer must match what a capture there is handed. With
     * -Pc2me this tests C2ME's real hook. The check loads nothing.
     */
    public static void chunkGuardCheckAsksAboutALoadedChunk(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the test world has no End to try");
        helper.assertFalse(end.getChunkSource().hasChunk(0, 0), "chunk 0,0 is loaded in the End, so this can't tell");
        int x = 1500;
        int z = -1500;
        end.setChunkForced(x, z, true);
        AtomicReference<CompletableFuture<String>> problem = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (problem.get() == null) {
                LevelChunk real = end.getChunkSource().getChunkNow(x, z);
                helper.assertTrue(real != null, "the forced chunk hasn't loaded");
                CompletableFuture<Boolean> said = StructureCapture.chunkGuardHolds(end);
                helper.assertTrue(said != null, "the chunk guard check found no loaded chunk to ask about");
                problem.set(said.thenCombine(handedOver(end, real), (guarded, handed) -> {
                    // The game's summary of a failed test leaves out the reason on some versions.
                    JustEnoughStructures.LOGGER.info("In the test of the chunk guard check: it said the guard {}, and a capture at {}, {} in the End was handed {}",
                            guarded ? "comes first" : "doesn't come first", x, z, handed);
                    if (guarded && !handed.equals("the sandbox's own chunk")) {
                        return "the chunk guard check said the guard comes first, but a capture there was handed " + handed;
                    }
                    if (!guarded && handed.equals("the sandbox's own chunk")) {
                        return "the chunk guard check said a mod comes before the guard, but a capture there was handed the sandbox's own chunk";
                    }
                    return null;
                }));
            }
            helper.assertTrue(problem.get().isDone(), "the chunk guard check is still running");
            end.setChunkForced(x, z, false);
            String found = problem.get().join();
            helper.assertTrue(found == null, String.valueOf(found));
            helper.assertFalse(end.getChunkSource().hasChunk(0, 0), "chunk 0,0 was loaded in the End");
        });
    }

    /** What the chunk source hands a capture's code in a sandbox around {@code real}, on a separate thread as captures run. */
    private static CompletableFuture<String> handedOver(ServerLevel level, LevelChunk real) {
        ChunkPos pos = real.getPos();
        return CompletableFuture.supplyAsync(() -> StructureCapture.inSandbox(level, pos, () -> {
            ChunkAccess handed = level.getChunkSource().getChunk(Levels.chunkX(pos), Levels.chunkZ(pos), ChunkStatus.FULL, false);
            if (handed == real) {
                return "the real chunk";
            }
            return handed != null && handed == RealWorldGuard.current().chunk(level, Levels.chunkX(pos), Levels.chunkZ(pos))
                    ? "the sandbox's own chunk" : "something else: " + handed;
        }), check -> {
            Thread thread = new Thread(check, "jes-test-chunk-guard");
            thread.setDaemon(true);
            thread.start();
        });
    }

    /**
     * Structure code that reaches past the sandbox to the real level gets the sandbox: what it reads
     * and places, the mob it adds, the tick it schedules and the entities it looks for all stay out of
     * the world, no real chunk is loaded for it, and work it hands the server never runs, though work
     * it waits for still comes back. Run on another thread, as captures are, so a chunk the guard let
     * through would be loaded by the server while this waits.
     */
    public static void previewsLeaveTheRealWorldAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos real = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos above = real.above();
        level.setBlock(real, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        //? if >=26.1 {
        /*ArmorStand stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.snapTo(above.getX() + 0.5, above.getY() + 1, above.getZ() + 0.5);
        level.addFreshEntity(stand);
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.snapTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        *///?} else {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        stand.moveTo(above.getX() + 0.5, above.getY() + 1, above.getZ() + 0.5);
        level.addFreshEntity(stand);
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        //?}

        ChunkPos centre = new ChunkPos(SectionPos.blockToSectionCoord(real.getX()), SectionPos.blockToSectionCoord(real.getZ()));
        // A long way out, where nothing has ever been loaded.
        ChunkPos far = new ChunkPos(Levels.chunkX(centre) + 4000, Levels.chunkZ(centre) + 4000);
        BlockPos farPos = far.getMiddleBlockPosition(64);
        // Work handed to the server, as Integrated API's trainer processor summons trainers with.
        AtomicBoolean handedOver = new AtomicBoolean();
        // On the server's own thread too, where a chunk would otherwise be loaded straight away, and
        // work handed to the server done straight away.
        String onServerThread = StructureCapture.inSandbox(level, centre, () -> {
            level.getServer().execute(() -> handedOver.set(true));
            if (handedOver.get()) {
                return "work handed to the server ran on the server thread";
            }
            try {
                level.getChunk(Levels.chunkX(far), Levels.chunkZ(far));
                return "got a chunk outside the sandbox on the server thread";
            } catch (RuntimeException expected) {
                return level.getBlockState(real).is(Blocks.GOLD_BLOCK) ? "read the real block on the server thread" : null;
            }
        });
        if (onServerThread != null) {
            helper.fail(onServerThread);
            return;
        }
        CompletableFuture<String> problem = CompletableFuture.supplyAsync(() -> StructureCapture.inSandbox(level, centre, () -> {
            if (level.getBlockState(real).is(Blocks.GOLD_BLOCK)) {
                return "read the real block instead of the sandbox's";
            }
            if (!level.setBlock(above, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3) || !level.getBlockState(above).is(Blocks.DIAMOND_BLOCK)) {
                return "the sandbox didn't keep a block placed in it";
            }
            if (!level.addFreshEntity(pig)) {
                return "the sandbox didn't take the pig";
            }
            level.getServer().execute(() -> handedOver.set(true));
            // Dropping work the code waits for would leave the capture waiting forever.
            if (!cameBack(level.getServer().submit(() -> true))) {
                return "work the sandbox waited for never ran";
            }
            if (!level.getEntities((Entity) null, new AABB(real).inflate(8), e -> true).isEmpty()) {
                return "found the real world's entities";
            }
            level.scheduleTick(above, Blocks.DIAMOND_BLOCK, 5);
            if (!level.getBlockState(farPos).isAir() || level.setBlock(farPos, Blocks.STONE.defaultBlockState(), 3) || level.isLoaded(farPos)) {
                return "outside the sandbox wasn't empty";
            }
            try {
                level.getChunk(Levels.chunkX(far), Levels.chunkZ(far));
                return "got a chunk outside the sandbox";
            } catch (RuntimeException expected) {
                // Code that insists on a real chunk fails, rather than loading one.
            }
            // Straight from the chunk source, as some structure code asks, past the level.
            if (level.getChunkSource().getChunk(Levels.chunkX(far), Levels.chunkZ(far), ChunkStatus.FULL, true) != null) {
                return "the chunk source handed over a chunk outside the sandbox";
            }
            if (level.getChunkSource().getChunk(Levels.chunkX(centre), Levels.chunkZ(centre), ChunkStatus.FULL, false)
                    != RealWorldGuard.current().chunk(level, Levels.chunkX(centre), Levels.chunkZ(centre))) {
                return "the chunk source didn't hand over the sandbox's own chunk";
            }
            return null;
        }));

        helper.succeedWhen(() -> {
            helper.assertTrue(problem.isDone(), "the sandboxed code is still running");
            String found = problem.join();
            helper.assertTrue(found == null, String.valueOf(found));
            helper.assertTrue(level.getBlockState(real).is(Blocks.GOLD_BLOCK), "the real block changed");
            helper.assertFalse(level.getBlockState(above).is(Blocks.DIAMOND_BLOCK), "the block placed in the sandbox is in the real world");
            helper.assertTrue(level.getEntitiesOfClass(Pig.class, new AABB(real).inflate(8)).isEmpty(), "the pig is in the real world");
            helper.assertFalse(stand.isRemoved(), "the real armor stand was removed");
            helper.assertFalse(level.getBlockTicks().hasScheduledTick(above, Blocks.DIAMOND_BLOCK), "the tick was scheduled in the real world");
            helper.assertFalse(level.getChunkSource().hasChunk(Levels.chunkX(far), Levels.chunkZ(far)), "a real chunk was loaded for the sandbox");
            helper.assertTrue(RealWorldGuard.current() == null, "the server thread thinks it's capturing");
            helper.assertFalse(handedOver.get(), "work handed to the server from the sandbox ran");
        });
    }

    /** Whether work handed to the server comes back, giving up after a while. */
    private static boolean cameBack(CompletableFuture<Boolean> work) {
        try {
            // Generous, because other tests' captures can hold the server up on a busy machine.
            return Boolean.TRUE.equals(work.get(3, TimeUnit.MINUTES));
        } catch (ExecutionException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
