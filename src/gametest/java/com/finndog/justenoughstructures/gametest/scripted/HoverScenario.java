package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.ThrowingShapes;
import net.minecraft.client.Minecraft;

/**
 * The camera turned to a chest, then the mouse over every part of the preview and a click on it,
 * while every block in the preview fails when asked for its shape or whether it's solid, as another
 * mod's block can. The browser has to come through it: the questions are asked in the middle of
 * drawing the screen.
 */
final class HoverScenario {
    // Before 1.21.2 a block is asked whether it's solid with the world it's in.
    //? if <1.21.2 {
    private static final boolean ASKS_SOLID = true;
    //?} else {
    /*private static final boolean ASKS_SOLID = false;
    *///?}

    private HoverScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        int[] before = new int[2];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null && browser(mc).result().succeeded(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(pause(10))
                .then(run(() -> {
                    before[0] = ThrowingShapes.shapes;
                    before[1] = ThrowingShapes.solids;
                    ThrowingShapes.armed = true;
                }))
                // Turning to a chest asks whether anything solid is in the way of it. First, while no
                // block has failed yet and been set aside.
                .then(run(() -> browser(mc).result().snapshot().containers().stream().findFirst().ifPresent(browser(mc)::openContainer)))
                .then(pause(20));
        for (int dy = -90; dy <= 90; dy += 60) {
            for (int dx = -150; dx <= 150; dx += 60) {
                int x = dx;
                int y = dy;
                d.then(moveTo(() -> {
                    int[] middle = browser(mc).viewportCentre();
                    return new int[]{middle[0] + x, middle[1] + y};
                }, 3)).then(pause(2));
            }
        }
        d.then(moveTo(() -> browser(mc).viewportCentre(), 4))
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    ThrowingShapes.armed = false;
                    int shapes = ThrowingShapes.shapes - before[0];
                    int solids = ThrowingShapes.solids - before[1];
                    JustEnoughStructures.LOGGER.info("HOVER {} shapes and {} solid checks failed and the browser is still up", shapes, solids);
                    if (ThrowingShapes.hooked && (shapes == 0 || ASKS_SOLID && solids == 0)) {
                        throw new IllegalStateException("Not every question was asked of a block, so not everything was tried");
                    }
                }))
                .then(shoot("h01_after_hover"));
        return d;
    }
}
