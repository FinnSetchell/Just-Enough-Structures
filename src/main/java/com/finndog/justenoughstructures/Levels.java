package com.finndog.justenoughstructures;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;

/** Build heights and chunk positions the same way on every version. */
public final class Levels {
    private Levels() {
    }

    /** The lowest y a block can be at. */
    public static int minY(LevelHeightAccessor level) {
        //? if >=1.21.2 {
        /*return level.getMinY();
        *///?} else {
        return level.getMinBuildHeight();
        //?}
    }

    /** The highest y a block can be at. Before 1.21.2 the game gave the y just above that instead. */
    public static int maxY(LevelHeightAccessor level) {
        //? if >=1.21.2 {
        /*return level.getMaxY();
        *///?} else {
        return level.getMaxBuildHeight() - 1;
        //?}
    }

    // On 26.1 a chunk position is a record, so its x and z are read through methods.

    public static int chunkX(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.x();
        *///?} else {
        return pos.x;
        //?}
    }

    public static int chunkZ(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.z();
        *///?} else {
        return pos.z;
        //?}
    }

    /** The chunk position as one number, for use as a map key. */
    public static long pack(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }

    public static long pack(int chunkX, int chunkZ) {
        //? if >=26.1 {
        /*return ChunkPos.pack(chunkX, chunkZ);
        *///?} else {
        return ChunkPos.asLong(chunkX, chunkZ);
        //?}
    }
}
