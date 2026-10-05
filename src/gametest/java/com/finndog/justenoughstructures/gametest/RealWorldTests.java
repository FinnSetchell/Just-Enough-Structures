package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.capture.RealWorldGuard;
import com.finndog.justenoughstructures.capture.StructureCapture;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Loader-neutral test bodies for keeping captures away from the real world. */
public final class RealWorldTests {
    private RealWorldTests() {
    }

    /**
     * Structure code that reaches past the sandbox to the real level gets the sandbox: what it reads
     * and places, the mob it adds, the tick it schedules and the entities it looks for all stay out of
     * the world, and no real chunk is loaded for it. Run on another thread, as captures are, so a
     * chunk the guard let through would be loaded by the server while this waits.
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
        // On the server's own thread too, where a chunk would otherwise be loaded straight away.
        String onServerThread = StructureCapture.inSandbox(level, centre, () -> {
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
        });
    }
}
