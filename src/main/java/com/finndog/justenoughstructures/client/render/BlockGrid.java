package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.Arrays;
import net.minecraft.core.Vec3i;

/**
 * A snapshot's blocks by position, for drawing and picking.
 *
 * <p>Blocks are kept per 16x16x16 section, and only for sections that have any, so memory follows
 * the blocks rather than the structure's bounding box. One grid for the whole box would need about
 * 800 MB for a structure 1024 blocks across and 384 tall, however little of it is filled. A section
 * with only a few blocks keeps them as a short sorted list rather than a full section, so blocks
 * spread thinly through a big box don't each cost a whole section either.
 */
public final class BlockGrid {
    /** Past this many blocks a section keeps a full array, which is quicker to read. */
    private static final int SPARSE_LIMIT = 256;
    private static final int SECTION = 16 * 16 * 16;

    private final int sectionsX;
    private final int sectionsZ;
    /** Per section: every cell's palette index plus one, or null. */
    private final short[][] full;
    /** Per section: the cells that have a block, in order, and their palette index plus one, or null. */
    private final short[][] sparseCells;
    private final short[][] sparseStates;

    public BlockGrid(StructureSnapshot snapshot) {
        Vec3i size = snapshot.size();
        sectionsX = (size.getX() + 15) >> 4;
        int sectionsY = (size.getY() + 15) >> 4;
        sectionsZ = (size.getZ() + 15) >> 4;
        int sections = sectionsX * sectionsY * sectionsZ;
        int[] counts = new int[sections];
        for (int i = 0; i < snapshot.blockCount(); i++) {
            int packed = snapshot.packedPosition(i);
            counts[section(StructureSnapshot.unpackX(packed), StructureSnapshot.unpackY(packed), StructureSnapshot.unpackZ(packed))]++;
        }
        full = new short[sections][];
        sparseCells = new short[sections][];
        sparseStates = new short[sections][];
        int[] filled = new int[sections];
        for (int i = 0; i < snapshot.blockCount(); i++) {
            int packed = snapshot.packedPosition(i);
            int x = StructureSnapshot.unpackX(packed);
            int y = StructureSnapshot.unpackY(packed);
            int z = StructureSnapshot.unpackZ(packed);
            int s = section(x, y, z);
            short state = (short) (snapshot.paletteIndex(i) + 1);
            if (counts[s] > SPARSE_LIMIT) {
                if (full[s] == null) {
                    full[s] = new short[SECTION];
                }
                full[s][cell(x, y, z)] = state;
            } else {
                if (sparseCells[s] == null) {
                    sparseCells[s] = new short[counts[s]];
                    sparseStates[s] = new short[counts[s]];
                }
                sparseCells[s][filled[s]] = (short) cell(x, y, z);
                sparseStates[s][filled[s]] = state;
                filled[s]++;
            }
        }
        for (int s = 0; s < sections; s++) {
            if (sparseCells[s] != null) {
                sortPairs(sparseCells[s], sparseStates[s]);
            }
        }
    }

    /** The palette index plus one at a position in the structure's box, or 0 where there's nothing. */
    public int get(int x, int y, int z) {
        int s = section(x, y, z);
        int cell = cell(x, y, z);
        short[] f = full[s];
        if (f != null) {
            return f[cell] & 0xFFFF;
        }
        short[] cells = sparseCells[s];
        if (cells == null) {
            return 0;
        }
        int at = Arrays.binarySearch(cells, (short) cell);
        return at >= 0 ? sparseStates[s][at] & 0xFFFF : 0;
    }

    /** Roughly how many bytes the blocks take up. */
    public long bytes() {
        long total = (long) full.length * 3 * 8;
        for (int s = 0; s < full.length; s++) {
            if (full[s] != null) {
                total += SECTION * 2L;
            }
            if (sparseCells[s] != null) {
                total += sparseCells[s].length * 4L;
            }
        }
        return total;
    }

    private int section(int x, int y, int z) {
        return ((y >> 4) * sectionsZ + (z >> 4)) * sectionsX + (x >> 4);
    }

    private static int cell(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    /** Sorts a section's cells, carrying their states along. They usually arrive in order already. */
    private static void sortPairs(short[] cells, short[] states) {
        for (int i = 1; i < cells.length; i++) {
            short cell = cells[i];
            short state = states[i];
            int j = i - 1;
            while (j >= 0 && cells[j] > cell) {
                cells[j + 1] = cells[j];
                states[j + 1] = states[j];
                j--;
            }
            cells[j + 1] = cell;
            states[j + 1] = state;
        }
    }
}
