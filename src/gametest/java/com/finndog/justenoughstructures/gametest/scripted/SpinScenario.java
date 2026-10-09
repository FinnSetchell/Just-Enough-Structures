package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;

/**
 * The preview turning on its own while the mouse is away from it, and holding still while the
 * mouse is over it: two shots a couple of seconds apart each way.
 */
final class SpinScenario {
    private SpinScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(true)))
                .then(moveTo(() -> browser(mc).searchBox(), 10))
                .then(pause(10))
                .then(shoot("p01_away"))
                .then(pause(40))
                .then(shoot("p02_away_later"))
                .then(moveTo(() -> browser(mc).viewportCentre(), 10))
                .then(pause(10))
                .then(shoot("p03_over"))
                .then(pause(40))
                .then(shoot("p04_over_later"));
        return d;
    }
}
