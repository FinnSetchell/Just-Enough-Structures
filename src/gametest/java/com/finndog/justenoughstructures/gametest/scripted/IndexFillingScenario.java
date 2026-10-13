package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
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

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The browser while the server's loot index is still filling in: a first game with nothing saved,
 * where the index arrives early with most of the loot and the finished one follows. A search for an
 * item nothing gives and the "Found in" popup both say more is to come, and stop saying it once the
 * finished index is here. It only means anything in a game whose server has no loot index saved.
 */
final class IndexFillingScenario {
    private IndexFillingScenario() {
    }

    static Director build(Minecraft mc) {
        int[] changesWhenEarly = new int[1];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                // The browser asks for the index as it opens. With nothing saved it comes early, and says so.
                .then(until(ClientRequests::indexFilling, 20 * 60 * 10))
                .then(run(() -> {
                    expect(ClientRequests.indexFilling(), "The loot index never arrived early: this game's server had one saved, or built it in one go");
                    changesWhenEarly[0] = FoundIn.changes();
                    JustEnoughStructures.LOGGER.info("The early loot index is here at {}%", Math.round(ClientRequests.indexProgress() * 100));
                }))
                .then(moveTo(() -> browser(mc).searchBox(), 6))
                .then(click())
                .then(type("$bedrock", 1))
                .then(pause(6))
                .then(shoot("f01_nothing_matches_yet"))
                .then(run(() -> expect(ClientRequests.indexFilling(), "The finished index came before the search could be looked at")))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.DIAMOND))))
                // A long list waits a moment for its chances before it shows.
                .then(until(() -> browser(mc).foundInRow(0).isPresent(), 20 * 30))
                .then(pause(4))
                .then(shoot("f02_found_in_so_far"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.BEDROCK))))
                .then(pause(30))
                .then(shoot("f03_found_nowhere_yet"))
                .then(run(() -> JustEnoughStructures.LOGGER.info("Still filling in at {}% after the three pictures: {}",
                        Math.round(ClientRequests.indexProgress() * 100), ClientRequests.indexFilling())))
                // The finished index takes the early one's place, and everything listed from it is listed again.
                .then(until(() -> !ClientRequests.indexFilling(), 20 * 60 * 15))
                .then(run(() -> {
                    expect(ClientRequests.indexReady() && !ClientRequests.indexFilling(), "The finished loot index never came");
                    expect(FoundIn.changes() != changesWhenEarly[0], "The finished index didn't take the early one's place");
                }))
                .then(pause(30))
                .then(shoot("f04_found_nowhere"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(6))
                .then(shoot("f05_nothing_matches"))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.DIAMOND))))
                .then(until(() -> browser(mc).foundInRow(0).isPresent(), 20 * 30))
                .then(pause(4))
                .then(shoot("f06_found_in"))
                .then(pressKey(InputConstants.KEY_ESCAPE));
        return d;
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
}
