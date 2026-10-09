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
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;

/**
 * Hovering rows in the details panel tints what they're about in the preview: a kind of block on the
 * Blocks tab, the outpost's chest on the Loot tab, the layers slider cutting the tint too, and a
 * spawner on the Mobs tab.
 */
final class HighlightScenario {
    private HighlightScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).tab("blocks"), 8))
                .then(click())
                .then(until(() -> browser(mc).highlightRow("block:minecraft:dark_oak_log").isPresent(), 100))
                .then(moveTo(() -> browser(mc).highlightRow("block:minecraft:dark_oak_log").orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("h01_blocks_row"))
                .then(moveTo(() -> browser(mc).highlightRow("block:minecraft:white_wool").orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("h02_wool_row"))
                .then(moveTo(() -> browser(mc).tab("loot"), 8))
                .then(click())
                .then(until(() -> browser(mc).highlightRow("loot:minecraft:chests/pillager_outpost").isPresent(), 100))
                .then(moveTo(() -> browser(mc).highlightRow("loot:minecraft:chests/pillager_outpost").orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("h03_loot_row"))
                .then(moveTo(() -> browser(mc).tab("blocks"), 8))
                .then(click())
                .then(run(() -> browser(mc).setLayers(12)))
                .then(until(() -> browser(mc).highlightRow("block:minecraft:dark_oak_log").isPresent(), 100))
                .then(moveTo(() -> browser(mc).highlightRow("block:minecraft:dark_oak_log").orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("h04_sliced"))
                .then(run(() -> browser(mc).setLayers(1000)))
                .then(run(() -> browser(mc).select(Ids.parse("repurposed_structures:stronghold_nether"))))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> browser(mc).tab("entities"), 8))
                .then(click())
                .then(until(() -> browser(mc).highlightRow("spawners:minecraft:blaze").isPresent(), 100))
                .then(moveTo(() -> browser(mc).highlightRow("spawners:minecraft:blaze").orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("h05_spawner_row"));
        return d;
    }
}
