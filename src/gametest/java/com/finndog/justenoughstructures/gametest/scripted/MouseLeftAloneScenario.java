package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * A test game leaves the PC's mouse alone. Closing a screen with nothing else open is when the game
 * takes the mouse, which moves the real cursor of whoever is using the PC to the middle of the game's
 * window and holds it there; the harness counts the window as the one in use, so the game would.
 */
final class MouseLeftAloneScenario {
    private MouseLeftAloneScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("igloo"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(pause(20))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(pause(5))
                .then(run(() -> {
                    if (mc.screen != null) {
                        throw new IllegalStateException("Escape left " + mc.screen.getClass().getSimpleName() + " open");
                    }
                    if (mc.mouseHandler.isMouseGrabbed()) {
                        throw new IllegalStateException("Closing the browser took the mouse, which holds the PC's real cursor in the game's window");
                    }
                }));
        return d;
    }
}
