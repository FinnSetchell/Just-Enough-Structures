package com.finndog.justenoughstructures.gametest;

/**
 * The other mods a game test run was given on purpose, so a test of JES's hooks into one can tell a
 * run without it from a run where it failed to load. -Pc2me gives the run the real C2ME.
 */
public final class TestMods {
    /** The random C2ME gives each world in place of the game's own. */
    public static final String C2ME_RANDOM = "com.ishland.c2me.fixes.worldgen.threading_issues.common.CheckedThreadLocalRandom";

    private TestMods() {
    }

    /** Whether the run was given C2ME. */
    public static boolean c2me() {
        return Boolean.getBoolean("jes.gametest.c2me");
    }
}
