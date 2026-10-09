package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;

/**
 * The Mobs tab for a structure whose spawners pick their mob from a list. New layouts are made
 * until one has such a spawner. -Pstructures picks the structure.
 */
final class SpawnersScenario {
    private static final int TRIES = 30;

    private SpawnersScenario() {
    }

    static Director build(Minecraft mc) {
        String requested = System.getProperty("jes.autoshot.structures", "");
        JesScreen.startOn(Ids.parse(requested.isBlank() ? "repurposed_structures:stronghold_nether" : requested.split(",")[0].trim()));
        Director d = new Director(mc);
        int[] tries = {0};
        CaptureResult[] checked = new CaptureResult[1];
        long[] deadline = {0};
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> {
                    JesScreen browser = browser(mc);
                    if (browser == null) {
                        return false;
                    }
                    // A hidden window draws frames very fast, so this gives up after a while rather than a number of frames.
                    if (deadline[0] == 0) {
                        deadline[0] = System.currentTimeMillis() + 300_000;
                    } else if (System.currentTimeMillis() > deadline[0]) {
                        return true;
                    }
                    // Wait for each new layout before looking at it.
                    if (!browser.idle() || browser.result() == null || browser.result() == checked[0]) {
                        return false;
                    }
                    checked[0] = browser.result();
                    if (hasPool(browser.result()) || tries[0]++ >= TRIES) {
                        return true;
                    }
                    int[] reroll = browser.button("reroll");
                    browser.mouseClicked(reroll[0], reroll[1], 0);
                    browser.mouseReleased(reroll[0], reroll[1], 0);
                    return false;
                }, Integer.MAX_VALUE))
                .then(moveTo(() -> browser(mc).tab("entities"), 6))
                .then(click())
                .then(moveTo(() -> {
                    int[] tab = browser(mc).tab("overview");
                    return new int[]{tab[0] + 20, tab[1] + 60};
                }, 6))
                .then(pause(10))
                .then(shoot("s01_spawners"))
                .then(wheel(-6))
                .then(pause(10))
                .then(shoot("s02_spawners_scrolled"));
        return d;
    }

    private static boolean hasPool(CaptureResult result) {
        return result != null && result.succeeded() && result.snapshot().blockEntities().stream()
                .anyMatch(tag -> Nbt.list(tag, SpawnerPools.TAG, Tag.TAG_COMPOUND).size() > 1);
    }
}
