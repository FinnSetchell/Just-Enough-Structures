package com.finndog.justenoughstructures.capture;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flat ground a structure is generated on. Structures look at the terrain while they generate
 * (shipwrecks sit on the sea floor, end cities refuse to spawn below y 60, mineshafts want to be
 * under sea level), so each profile imitates the dimension the structure normally lives in.
 */
public enum SandboxTerrain {
    LAND(63),
    OCEAN(63),
    NETHER(32),
    END(0),
    VOID(0);

    private final int seaLevel;

    SandboxTerrain(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public BlockState stateAt(int y) {
        return switch (this) {
            case LAND -> y <= 59 ? Blocks.STONE.defaultBlockState()
                    : y <= 62 ? Blocks.DIRT.defaultBlockState()
                    : y == 63 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            case OCEAN -> y <= 39 ? Blocks.STONE.defaultBlockState()
                    : y <= 42 ? Blocks.SAND.defaultBlockState()
                    : y <= 62 ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            // Above the lava sea (32): nether fossils only settle on ground higher than it.
            case NETHER -> y <= 40 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case END -> y >= 0 && y <= 64 ? Blocks.END_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case VOID -> Blocks.AIR.defaultBlockState();
        };
    }
}
