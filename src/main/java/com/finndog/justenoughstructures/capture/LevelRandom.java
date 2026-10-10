package com.finndog.justenoughstructures.capture;

/**
 * Added to the random each real world draws from, so {@code LegacyRandomSourceMixin} can tell it apart,
 * and to C2ME's own kind by {@code C2meRandomMixin}.
 */
public interface LevelRandom {
    void justenoughstructures$markLevelRandom();

    boolean justenoughstructures$isLevelRandom();
}
