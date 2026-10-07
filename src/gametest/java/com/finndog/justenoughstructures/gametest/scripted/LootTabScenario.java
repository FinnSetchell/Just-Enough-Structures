package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * The Loot tab as players see it: the containers in the layout, every item's chance across the
 * whole structure with its tooltip, a chest's popup on One roll and on Chances, and a loot table
 * on its own in a popup, as one from another layout opens.
 */
final class LootTabScenario {
    private LootTabScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).tab("loot"), 8))
                .then(click())
                .then(until(() -> browser(mc).oddsRow(Items.DIAMOND).isPresent(), 400))
                .then(pause(10))
                .then(shoot("l01_loot"))
                .then(moveTo(() -> browser(mc).oddsRow(Items.DIAMOND).orElse(new int[]{0, 0}), 10))
                .then(pause(10))
                .then(shoot("l02_loot_tooltip"))
                .then(moveTo(() -> browser(mc).highlightRow("loot:minecraft:chests/desert_pyramid").orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(pause(20))
                .then(shoot("l03_popup_roll"))
                .then(moveTo(() -> orZero(browser(mc).popupLink("odds")), 10))
                .then(click())
                .then(pause(20))
                .then(shoot("l04_popup_odds"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(run(() -> browser(mc).openTable("minecraft:chests/igloo_chest")))
                .then(pause(30))
                .then(shoot("l05_table_popup"))
                .then(moveTo(() -> orZero(browser(mc).popupLink("roll")), 10))
                .then(click())
                .then(pause(20))
                .then(shoot("l06_table_popup_roll"));
        return d;
    }

    private static int[] orZero(int[] at) {
        return at == null ? new int[]{0, 0} : at;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }
}
