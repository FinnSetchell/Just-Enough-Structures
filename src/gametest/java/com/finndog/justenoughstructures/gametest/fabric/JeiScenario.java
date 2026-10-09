package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.compat.jei.JesJeiPlugin;
import com.finndog.justenoughstructures.gametest.scripted.Director;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** JEI's "Found in structures" page for a diamond, a row's tooltip, and clicking it through to the browser. */
final class JeiScenario {
    private JeiScenario() {
    }

    static Director build(Minecraft mc) {
        Director d = new Director(mc);
        d.then(pause(40))
                .then(pressKey(InputConstants.KEY_E))
                .then(until(() -> mc.screen != null, 40))
                .then(pause(40))
                .then(shoot("j01_inventory"))
                .then(until(FoundIn::ready, 12000))
                .then(run(() -> JesJeiPlugin.showFoundIn(new ItemStack(Items.DIAMOND))))
                .then(pause(60))
                .then(shoot("j02_found_in_diamond"))
                // The first row's name, where JEI puts it in a 1600 x 900 window at GUI scale 2.
                .then(moveTo(() -> new int[]{mc.getWindow().getGuiScaledWidth() / 2 - 20, 121}, 6))
                .then(pause(10))
                .then(shoot("j03_row_tooltip"))
                .then(click())
                .then(until(() -> mc.screen instanceof JesScreen, 40))
                .then(until(() -> mc.screen instanceof JesScreen s && s.idle(), 400))
                .then(shoot("j04_opened_in_browser"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(20))
                .then(shoot("j05_back_in_jei"));
        return d;
    }
}
