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
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ConfigScreens;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The settings button over the preview. None for buried treasure, as no mod's settings govern it. On a
 * Moog's Voyager house it opens Moog's Structure Lib's settings, which list that mod's structures,
 * and Esc comes back to the browser on the same house. Needs those mods, so only with the dev mods,
 * and only where Moog's Structure Lib has a settings screen (not on Forge 26.x).
 */
final class ConfigScenario {
    private static final ResourceLocation HOUSE = Ids.of("mvs", "desert_house");

    private ConfigScenario() {
    }

    static Director build(Minecraft mc) {
        ConfigScreens screens = ConfigScreens.get();
        if (screens == null || !screens.has("moogs_structures")) {
            throw new UnsupportedOperationException("needs Moog's Structure Lib with a settings screen");
        }
        JesScreen.startOn(Ids.parse("buried_treasure"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc).configShown() == null, "buried treasure has a settings button"))
                .then(shoot("g01_treasure_no_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> JesScreen.startOn(HOUSE)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> HOUSE.equals(browser(mc).selectedId()), "the browser didn't open on the desert house"))
                .then(check(() -> "moogs_structures".equals(browser(mc).configShown()),
                        "the desert house's settings button doesn't open Moog's Structure Lib"))
                .then(moveTo(() -> browser(mc).button("config"), 10))
                .then(pause(10))
                .then(shoot("g02_settings_button"))
                .then(click())
                .then(until(() -> mc.screen != null && browser(mc) == null, 40))
                .then(check(() -> mc.screen != null && browser(mc) == null, "clicking the settings button opened nothing"))
                .then(pause(10))
                .then(shoot("g03_msl_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc) != null && HOUSE.equals(browser(mc).selectedId()),
                        "Esc didn't come back to the browser on the desert house"))
                .then(shoot("g04_back_in_browser"));
        return d;
    }

    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
