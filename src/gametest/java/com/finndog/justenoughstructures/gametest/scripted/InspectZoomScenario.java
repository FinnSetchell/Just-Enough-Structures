package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The wheel while a popup has the camera turned to a chest or a mob: over the preview it zooms on what
 * is being looked at, the next chest the arrows go to keeps that zoom, and closing the popup goes back
 * to the zoom from before. The desert pyramid's chests share a table, so its arrows have somewhere to
 * go; the swamp hut always has its witch.
 */
final class InspectZoomScenario {
    private InspectZoomScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        float[] before = new float[1];
        float[] seen = new float[1];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> captured(mc, "desert_pyramid"), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(pause(20))
                .then(run(() -> {
                    before[0] = distance(mc);
                    browser(mc).result().snapshot().containers().stream().filter(c -> c.lootTable() != null).findFirst()
                            .ifPresentOrElse(browser(mc)::openContainer, () -> {
                                throw new IllegalStateException("The desert pyramid has no chest with a loot table");
                            });
                }))
                .then(pause(40))
                .then(run(() -> seen[0] = distance(mc)))
                .then(moveTo(() -> browser(mc).viewportCentre(), 10))
                .then(wheel(3))
                .then(pause(40))
                .then(shoot("z01_zoomed_on_chest"))
                .then(run(() -> {
                    float now = distance(mc);
                    if (!browser(mc).containerOpen() || !(now < seen[0] * 0.8f)) {
                        throw new IllegalStateException("The wheel over the preview didn't zoom in on the chest: " + seen[0] + " before, " + now + " after");
                    }
                    seen[0] = now;
                }))
                .then(moveTo(() -> browser(mc).button("next"), 10))
                .then(click())
                .then(pause(40))
                .then(shoot("z02_next_chest"))
                .then(run(() -> {
                    float now = distance(mc);
                    if (Math.abs(now - seen[0]) > 0.01f) {
                        throw new IllegalStateException("The next chest didn't keep the zoom: " + seen[0] + " then " + now);
                    }
                }))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(40))
                .then(run(() -> {
                    float now = distance(mc);
                    if (browser(mc).containerOpen() || Math.abs(now - before[0]) > 0.05f) {
                        throw new IllegalStateException("Closing the popup didn't go back to the zoom from before: " + before[0] + " then " + now);
                    }
                }))
                .then(run(() -> browser(mc).select(Ids.parse("swamp_hut"))))
                .then(until(() -> captured(mc, "swamp_hut"), 600))
                .then(pause(20))
                .then(run(() -> {
                    Entity mob = browser(mc).placedMobs().stream().findFirst()
                            .orElseThrow(() -> new IllegalStateException("The swamp hut has no mobs"));
                    browser(mc).openMobPopup(mob);
                }))
                .then(pause(40))
                .then(run(() -> seen[0] = distance(mc)))
                .then(moveTo(() -> browser(mc).viewportCentre(), 10))
                .then(wheel(-3))
                .then(pause(40))
                .then(shoot("z03_zoomed_out_on_mob"))
                .then(run(() -> {
                    float now = distance(mc);
                    if (!(now > seen[0] * 1.2f)) {
                        throw new IllegalStateException("The wheel over the preview didn't zoom out from the mob: " + seen[0] + " before, " + now + " after");
                    }
                }))
                .then(pressKey(InputConstants.KEY_ESCAPE));
        return d;
    }

    /** How far the preview's camera is from what it looks at. */
    private static float distance(Minecraft mc) {
        return ((StructureViewport) Reflect.get(browser(mc), "viewport")).camera().distance();
    }

    private static boolean captured(Minecraft mc, String id) {
        JesScreen browser = browser(mc);
        return browser != null && browser.idle() && browser.result() != null && browser.result().succeeded()
                && browser.result().snapshot().structureId().equals(Ids.parse(id));
    }
}
