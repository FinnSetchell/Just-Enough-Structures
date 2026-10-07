package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;

import com.finndog.justenoughstructures.fabric.JesModMenu;
import com.finndog.justenoughstructures.gametest.scripted.Director;
import net.minecraft.client.Minecraft;

/** The settings screen, opened the way Mod Menu's Config button opens it, with both of its tabs. */
final class ConfigScenario {
    private ConfigScenario() {
    }

    static Director build(Minecraft mc) {
        Director d = new Director(mc, null);
        d.then(pause(20))
                .then(run(() -> mc.setScreen(new JesModMenu().getModConfigScreenFactory().create(null))))
                .then(pause(20))
                .then(shoot("g01_settings_browser"))
                // Cloth Config's tabs sit side by side under the title, from the left; the second is the server's.
                .then(moveTo(() -> new int[]{97, 52}, 10))
                .then(click())
                .then(pause(20))
                .then(shoot("g02_settings_server"));
        return d;
    }
}
