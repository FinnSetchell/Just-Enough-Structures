package com.finndog.justenoughstructures.gametest;

import java.util.List;

/**
 * The test mod's mixin plugin in the test jar for a real game: it notes which mixins were applied, so
 * a check knows whether it can arm one, and adds none of the game tests' own.
 */
public final class RealGameMixinPlugin extends GameTestMixinPlugin {
    @Override
    public List<String> getMixins() {
        return null;
    }
}
