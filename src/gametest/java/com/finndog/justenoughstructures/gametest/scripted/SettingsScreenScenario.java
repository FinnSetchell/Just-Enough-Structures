package com.finndog.justenoughstructures.gametest.scripted;

//? if cloth_config {
import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.compat.cloth.JesConfigScreen;
import net.minecraft.client.Minecraft;

/**
 * JES's settings screen, which Cloth Config draws: its Browser settings at the top, and again
 * scrolled to the end. No gallery or suite shot it before Command only was added there.
 */
final class SettingsScreenScenario {
    private SettingsScreenScenario() {
    }

    static Director build(Minecraft mc) {
        if (!JustEnoughStructures.modVersions().containsKey("cloth-config") && !JustEnoughStructures.modVersions().containsKey("cloth_config")) {
            throw new UnsupportedOperationException("needs Cloth Config");
        }
        Director d = new Director(mc);
        d.then(run(() -> mc.setScreen(JesConfigScreen.create(null))))
                .then(until(() -> mc.screen != null, 40))
                .then(pause(20))
                .then(shoot("s01_settings_top"))
                .then(moveTo(() -> new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2}, 10))
                .then(wheel(-20))
                .then(pause(20))
                .then(shoot("s02_settings_end"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40));
        return d;
    }
}
//?}
