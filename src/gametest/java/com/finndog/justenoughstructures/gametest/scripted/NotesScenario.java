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
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The player's own notes on a structure: the line on the Info tab asking for some, the popup
 * they're written in over the browser, and the notes on the Info tab after, kept whether the popup
 * is closed by Escape, by Done or by a click outside it. Whatever notes the igloo had are put back
 * at the end.
 */
final class NotesScenario {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final String FIRST = "Found one past the village.\nThe basement has the potion.";

    private NotesScenario() {
    }

    static Director build(Minecraft mc) {
        String[] before = {""};
        JesScreen.startOn(IGLOO);
        Director d = new Director(mc);
        d.then(run(() -> {
                    before[0] = ClientState.note(IGLOO);
                    ClientState.setNote(IGLOO, "");
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> orZero(browser(mc).notesSpot()), 20))
                .then(pause(10))
                .then(shoot("n01_add_notes"))
                .then(click())
                .then(until(() -> browser(mc).notesOpen(), 40))
                .then(pause(5))
                .then(type("Found one past the village.", 1))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(type("The basement has the potion.", 1))
                .then(pause(10))
                .then(shoot("n02_writing"))
                // Escape closes the popup, and what was written is kept.
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> !browser(mc).notesOpen(), 40))
                .then(run(() -> check(FIRST, "Escape")))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> orZero(browser(mc).notesSpot()), 20))
                .then(pause(10))
                .then(shoot("n03_notes_shown"))
                // So does Done, after more is written.
                .then(click())
                .then(until(() -> browser(mc).notesOpen(), 40))
                .then(pause(5))
                .then(type(" Took it.", 1))
                .then(moveTo(() -> orZero(browser(mc).notesDone()), 15))
                .then(click())
                .then(until(() -> !browser(mc).notesOpen(), 40))
                .then(run(() -> check(FIRST + " Took it.", "Done")))
                // And a click outside the popup.
                .then(moveTo(() -> orZero(browser(mc).notesSpot()), 15))
                .then(click())
                .then(until(() -> browser(mc).notesOpen(), 40))
                .then(pause(5))
                .then(type(" Twice.", 1))
                .then(moveTo(() -> browser(mc).searchBox(), 15))
                .then(click())
                .then(until(() -> !browser(mc).notesOpen(), 40))
                .then(run(() -> check(FIRST + " Took it. Twice.", "A click outside")))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> browser(mc).viewportCentre(), 15))
                .then(pause(10))
                .then(shoot("n04_after_done"))
                .then(run(() -> ClientState.setNote(IGLOO, before[0])));
        return d;
    }

    private static void check(String expected, String how) {
        String kept = ClientState.note(IGLOO);
        if (!kept.equals(expected)) {
            throw new IllegalStateException(how + " kept these notes: " + kept);
        }
    }
}
