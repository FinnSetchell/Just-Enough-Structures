package com.finndog.justenoughstructures.capture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
//? if >=1.21 {
/*import com.mojang.serialization.MapCodec;
*///?} else {
import com.mojang.serialization.Codec;
import java.util.concurrent.Executor;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Answers the terrain questions structures ask while they pick a spot (surface height, sea level,
 * what's in a column) from a flat {@link SandboxTerrain}. It never generates chunks itself.
 */
final class SandboxChunkGenerator extends ChunkGenerator {
    private final SandboxTerrain terrain;
    private final int minY;
    private final BlockState[] column;

    SandboxChunkGenerator(BiomeSource biomeSource, SandboxTerrain terrain, LevelHeightAccessor height) {
        super(biomeSource);
        this.terrain = terrain;
        this.minY = height.getMinBuildHeight();
        this.column = new BlockState[height.getHeight()];
        for (int i = 0; i < column.length; i++) {
            column[i] = terrain.stateAt(minY + i);
        }
    }

    SandboxTerrain terrain() {
        return terrain;
    }

    // Never saved or sent anywhere.
    //? if >=1.21 {
    /*@Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(this);
    }
    *///?} else {
    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return Codec.unit(this);
    }
    //?}

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return column.length;
    }

    //? if >=1.21 {
    /*@Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }
    *///?} else {
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }
    //?}

    @Override
    public int getSeaLevel() {
        return terrain.seaLevel();
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        for (int i = column.length - 1; i >= 0; i--) {
            if (type.isOpaque().test(column[i])) {
                return minY + i + 1;
            }
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(minY, column.clone());
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
    }
}
