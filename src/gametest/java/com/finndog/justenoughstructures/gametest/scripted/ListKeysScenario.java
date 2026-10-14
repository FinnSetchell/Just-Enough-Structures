package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Up and Down in the browser's structure list: from the igloo to the structures beside it, then, while
 * typing in the search, to the first structure it found.
 */
final class ListKeysScenario {
    private ListKeysScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("igloo"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(pressKey(InputConstants.KEY_DOWN))
                .then(pause(5))
                .then(run(() -> expect(mc, "jungle_pyramid", "Down from the igloo")))
                .then(pressKey(InputConstants.KEY_UP))
                .then(pressKey(InputConstants.KEY_UP))
                .then(pause(5))
                .then(run(() -> expect(mc, "fortress", "Up twice from the jungle pyramid")))
                .then(until(() -> browser(mc).idle(), 600))
                .then(shoot("k01_fortress_by_keys"))
                // While typing in the search the keys go through what it found, from the first.
                .then(moveTo(() -> browser(mc).searchBox(), 10))
                .then(click())
                .then(type("ruin", 1))
                .then(pause(10))
                .then(pressKey(InputConstants.KEY_DOWN))
                .then(pause(5))
                .then(run(() -> expect(mc, "ocean_ruin_cold", "Down while searching for ruin")))
                .then(until(() -> browser(mc).idle(), 600))
                .then(shoot("k02_found_by_keys"));
        return d;
    }

    private static void expect(Minecraft mc, String path, String how) {
        ResourceLocation now = browser(mc).selectedStructure();
        if (!Ids.parse(path).equals(now)) {
            throw new IllegalStateException(how + " went to " + now);
        }
    }
}
