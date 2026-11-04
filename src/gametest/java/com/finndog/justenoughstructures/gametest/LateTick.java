package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.Arrays;
//? if emi {
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
import dev.emi.emi.runtime.EmiReloadManager;
//?}

/**
 * Holds JES's client tick back after joining until EMI's first load has finished, as a busy client
 * does when another mod's work on the client thread (JEI starting, on Fabric) keeps its next tick
 * waiting while EMI's load runs on its own thread. Only for the emi_late scenario, which checks that
 * EMI still loads once: the index has to be asked for as the server is joined, not on a tick.
 */
public final class LateTick {
    /** How long the tick is held at most, so a game whose EMI never loads isn't held for good. */
    private static final long MAX_MS = 60_000;
    private static final boolean WANTED = wanted();
    private static volatile long heldSince;
    private static volatile boolean held;
    private static volatile boolean letGo;

    private LateTick() {
    }

    private static boolean wanted() {
        String mode = System.getProperty("jes.autoshot.mode", "");
        return mode.equals("emi_late")
                || (mode.equals("suite") && Arrays.asList(System.getProperty("jes.autoshot.suite", "").split(",")).contains("emi_late"));
    }

    /** Whether this tick of JES's is held back. Called at the start of each one. */
    public static boolean holds() {
        if (!WANTED || letGo) {
            return false;
        }
        //? if emi {
        boolean loaded = JesEmiPlugin.loads() > 0 && EmiReloadManager.isLoaded();
        //?} else {
        /*boolean loaded = true;
        *///?}
        long now = System.currentTimeMillis();
        if (heldSince == 0) {
            heldSince = now;
            JustEnoughStructures.LOGGER.info("The test holds JES's client tick back until EMI has loaded");
        }
        if (loaded || now - heldSince > MAX_MS) {
            letGo = true;
            JustEnoughStructures.LOGGER.info("The test lets JES's client tick go after {} ms, {}", now - heldSince,
                    loaded ? "with EMI loaded " + loadsSoFar() + " time(s)" : "as EMI never loaded");
            return false;
        }
        held = true;
        return true;
    }

    private static int loadsSoFar() {
        //? if emi {
        return JesEmiPlugin.loads();
        //?} else {
        /*return 0;
        *///?}
    }

    /** Whether a tick was held back and then let go once EMI had loaded, so the check means something. */
    public static boolean heldUntilLoaded() {
        return held && letGo;
    }
}
