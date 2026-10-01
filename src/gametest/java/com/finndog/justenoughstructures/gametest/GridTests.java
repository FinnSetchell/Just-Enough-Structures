package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.render.BlockGrid;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Loader-neutral test bodies for how the preview keeps a structure's blocks. */
public final class GridTests {
    private GridTests() {
    }

    /**
     * A structure as big as previews go, mostly empty: a solid cube in one corner, a column up the far
     * corner and blocks scattered through the rest. Every block reads back, empty spots read as
     * nothing, and it takes a few megabytes rather than the 800 a grid of the whole box would.
     */
    public static void bigSparseStructuresStayCheap(GameTestHelper helper) {
        Vec3i size = new Vec3i(1024, 384, 1024);
        Int2IntOpenHashMap expected = new Int2IntOpenHashMap();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    expected.put(StructureSnapshot.pack(x, y, z), (x + y + z) % 3);
                }
            }
        }
        for (int y = 0; y < 384; y++) {
            expected.put(StructureSnapshot.pack(1023, y, 1023), 1);
        }
        Random random = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            expected.put(StructureSnapshot.pack(random.nextInt(1024), random.nextInt(384), random.nextInt(1024)), random.nextInt(3));
        }
        StructureSnapshot snapshot = snapshot(size, expected);
        BlockGrid grid = new BlockGrid(snapshot);

        String wrong = check(grid, snapshot, size, random);
        helper.assertTrue(wrong == null, wrong);
        helper.assertTrue(grid.bytes() < 8L * 1024 * 1024, "the grid takes " + grid.bytes() / 1024 + " KB");
        helper.succeed();
    }

    /** Every block of a real capture reads back from the grid as it was placed, and nothing else is there. */
    public static void gridMatchesACapturedVillage(GameTestHelper helper) {
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), new ResourceLocation("village_plains"), CaptureTests.SEED);
        helper.assertTrue(result.succeeded(), "village_plains did not capture: " + result.error());
        StructureSnapshot snapshot = result.snapshot();
        String wrong = check(new BlockGrid(snapshot), snapshot, snapshot.size(), new Random(11));
        helper.assertTrue(wrong == null, wrong);
        helper.succeed();
    }

    /** What's wrong with the grid, checking every block and many empty spots, or null if nothing is. */
    private static String check(BlockGrid grid, StructureSnapshot snapshot, Vec3i size, Random random) {
        Int2IntOpenHashMap placed = new Int2IntOpenHashMap();
        placed.defaultReturnValue(-1);
        for (int i = 0; i < snapshot.blockCount(); i++) {
            placed.put(snapshot.packedPosition(i), snapshot.paletteIndex(i));
        }
        for (int i = 0; i < snapshot.blockCount(); i++) {
            int packed = snapshot.packedPosition(i);
            int x = StructureSnapshot.unpackX(packed);
            int y = StructureSnapshot.unpackY(packed);
            int z = StructureSnapshot.unpackZ(packed);
            if (grid.get(x, y, z) != snapshot.paletteIndex(i) + 1) {
                return "the block at " + x + ", " + y + ", " + z + " reads as " + grid.get(x, y, z) + ", not " + (snapshot.paletteIndex(i) + 1);
            }
        }
        for (int i = 0; i < 50_000; i++) {
            int x = random.nextInt(size.getX());
            int y = random.nextInt(size.getY());
            int z = random.nextInt(size.getZ());
            int want = placed.get(StructureSnapshot.pack(x, y, z)) + 1;
            if (grid.get(x, y, z) != want) {
                return "the spot " + x + ", " + y + ", " + z + " reads as " + grid.get(x, y, z) + ", not " + want;
            }
        }
        return null;
    }

    private static StructureSnapshot snapshot(Vec3i size, Int2IntOpenHashMap blocks) {
        List<BlockState> palette = List.of(Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState());
        int[] positions = new int[blocks.size()];
        int[] states = new int[blocks.size()];
        int i = 0;
        for (Int2IntOpenHashMap.Entry e : blocks.int2IntEntrySet()) {
            positions[i] = e.getIntKey();
            states[i] = e.getIntValue();
            i++;
        }
        return new StructureSnapshot(new ResourceLocation("test", "sparse"), 0, SandboxTerrain.LAND, BlockPos.ZERO, size,
                palette, positions, states, new ArrayList<>(), new ArrayList<>(), 1);
    }
}
