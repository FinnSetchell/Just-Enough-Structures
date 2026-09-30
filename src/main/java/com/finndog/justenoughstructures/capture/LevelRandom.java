package com.finndog.justenoughstructures.capture;

/** Added to the random each real world draws from, so {@code LegacyRandomSourceMixin} can tell it apart. */
public interface LevelRandom {
    void justenoughstructures$markLevelRandom();

    boolean justenoughstructures$isLevelRandom();
}
