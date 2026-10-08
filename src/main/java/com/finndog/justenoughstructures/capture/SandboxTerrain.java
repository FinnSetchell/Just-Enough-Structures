package com.finndog.justenoughstructures.capture;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flat ground a structure is generated on. Structures look at the terrain while they generate
 * (shipwrecks sit on the sea floor, end cities refuse to spawn below y 60, mineshafts want to be
 * under sea level), so each profile imitates the dimension the structure normally lives in. Some
 * only fit at a certain depth, like a ruin that wants the sea floor between y 55 and 56, so the sea
 * and the Nether each have more than one, tried in turn.
 */
public enum SandboxTerrain {
    LAND(63, 64),
    OCEAN(63, 43),
    /** A sea floor just under the water, for structures that only fit near the shore. */
    SHALLOW_OCEAN(63, 56),
    /** A sea floor further down, for structures that need deep water above them. */
    DEEP_OCEAN(63, 31),
    NETHER(32, 41),
    /** The Nether's lava sea with open air over it, for structures that float on the lava. */
    LAVA_SEA(32, 32),
    END(0, 65),
    VOID(0, Integer.MIN_VALUE),
    /**
     * Land as high as a mountain's peak, for structures that only go up there, like Mowzie's Mobs'
     * monastery. This and the ones after it are kept last, as a snapshot is sent with its terrain's
     * place in this list.
     */
    HIGH_LAND(63, 150),
    /**
     * Land with caves under it, for structures that look for a cave's floor, like the Undergarden's
     * catacombs, which then go a long way down, and its camps, which only look below y 0.
     */
    CAVE(63, 64),
    /** Netherrack with a cave inside it, for structures that look for a floor under a roof, like Formations Nether's. */
    NETHER_CAVE(32, 41);

    /** The air of {@link #CAVE}'s two caves, from each floor to its roof. */
    private static final int CAVE_BOTTOM = -15;
    private static final int CAVE_TOP = 0;
    private static final int HIGH_CAVE_BOTTOM = 30;
    private static final int HIGH_CAVE_TOP = 45;
    /** The air of {@link #NETHER_CAVE}, low enough for a roof over all but the tallest structures. */
    private static final int NETHER_CAVE_BOTTOM = 8;
    private static final int NETHER_CAVE_TOP = 18;

    private final int seaLevel;
    private final int surface;

    SandboxTerrain(int seaLevel, int surface) {
        this.seaLevel = seaLevel;
        this.surface = surface;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** The height of the top of the ground, sea floor or lava, or {@link Integer#MIN_VALUE} for none. */
    public int surface() {
        return surface;
    }

    /** Which dimension this ground is like, for its biome and the level it's built in. */
    public SandboxTerrain kind() {
        return switch (this) {
            case SHALLOW_OCEAN, DEEP_OCEAN -> OCEAN;
            case LAVA_SEA -> NETHER;
            case HIGH_LAND, CAVE -> LAND;
            case NETHER_CAVE -> NETHER;
            default -> this;
        };
    }

    public BlockState stateAt(int y) {
        return switch (this) {
            case LAND -> y <= 59 ? Blocks.STONE.defaultBlockState()
                    : y <= 62 ? Blocks.DIRT.defaultBlockState()
                    : y == 63 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            case OCEAN, SHALLOW_OCEAN, DEEP_OCEAN -> y <= surface - 4 ? Blocks.STONE.defaultBlockState()
                    : y < surface ? Blocks.SAND.defaultBlockState()
                    : y < seaLevel ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            // Above the lava sea (32): nether fossils only settle on ground higher than it.
            case NETHER -> y <= 40 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case LAVA_SEA -> y <= 22 ? Blocks.NETHERRACK.defaultBlockState()
                    : y < seaLevel ? Blocks.LAVA.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            case END -> y >= 0 && y <= 64 ? Blocks.END_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case VOID -> Blocks.AIR.defaultBlockState();
            case HIGH_LAND -> y <= 145 ? Blocks.STONE.defaultBlockState()
                    : y <= 148 ? Blocks.DIRT.defaultBlockState()
                    : y == 149 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            case CAVE -> y >= CAVE_BOTTOM && y <= CAVE_TOP || y >= HIGH_CAVE_BOTTOM && y <= HIGH_CAVE_TOP
                    ? Blocks.AIR.defaultBlockState() : LAND.stateAt(y);
            case NETHER_CAVE -> y >= NETHER_CAVE_BOTTOM && y <= NETHER_CAVE_TOP ? Blocks.AIR.defaultBlockState() : NETHER.stateAt(y);
        };
    }
}
