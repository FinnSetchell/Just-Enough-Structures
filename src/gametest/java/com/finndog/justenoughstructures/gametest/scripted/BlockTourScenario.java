package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/**
 * Going through a kind of block there are only a few of, from its row on the Blocks tab: the desert
 * pyramid's TNT, buried under its floor. The popup opens on all nine, then its arrows turn the camera
 * to each in turn, cutting the layers down to show it, and Done puts the view back.
 */
final class BlockTourScenario {
    private static final String TNT = "block:minecraft:tnt";

    private BlockTourScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).tab("blocks"), 6))
                .then(click())
                .then(moveTo(panel(mc), 6))
                .then(pause(10))
                // There's little TNT, so its row is near the bottom of the list.
                .then(wheel(-20))
                .then(pause(10))
                .then(moveTo(() -> orZero(browser(mc).highlightRow(TNT).orElse(null)), 10))
                .then(pause(20))
                .then(shoot("b01_tnt_row"))
                .then(click())
                .then(until(() -> browser(mc).blockPopupStep() != null, 40))
                .then(run(() -> expect(mc, -1, "the popup didn't open on all of them")))
                .then(pause(20))
                .then(shoot("b02_all_tnt"))
                .then(moveTo(() -> browser(mc).button("next"), 10))
                .then(click())
                .then(run(() -> expect(mc, 0, "the first step didn't go to the first one")))
                .then(pause(40))
                .then(shoot("b03_first_tnt"))
                .then(click())
                .then(run(() -> expect(mc, 1, "the second step didn't go to the second one")))
                .then(pause(40))
                .then(shoot("b04_second_tnt"))
                .then(moveTo(() -> browser(mc).button("done"), 10))
                .then(click())
                .then(run(() -> {
                    if (browser(mc).blockPopupStep() != null) {
                        throw new IllegalStateException("Done didn't close the popup");
                    }
                }))
                .then(pause(30))
                .then(shoot("b05_back_out"));
        return d;
    }

    /** Checks that the popup shows the {@code index}th of the pyramid's nine blocks of TNT, or all of them at -1. */
    private static void expect(Minecraft mc, int index, String problem) {
        int[] step = browser(mc).blockPopupStep();
        if (step == null || step[0] != index || step[1] != 9) {
            throw new IllegalStateException(problem + ": " + Arrays.toString(step));
        }
    }

    /** A spot inside the panel's body, below the tabs, for the cursor to rest while scrolling. */
    private static Supplier<int[]> panel(Minecraft mc) {
        return () -> {
            int[] tab = browser(mc).tab("overview");
            return new int[]{tab[0] + 20, tab[1] + 60};
        };
    }
}
