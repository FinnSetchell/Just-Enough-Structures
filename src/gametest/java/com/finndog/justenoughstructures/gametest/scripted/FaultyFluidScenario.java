package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.FaultyFluids;
import net.minecraft.client.Minecraft;

/**
 * A fluid renderer that fails part way through a vertex while a preview builds its mesh.
 * The plains village's water is drawn with it, and the game has to keep going and finish the preview.
 * On 1.20.1 the half vertex is dropped when that part of the mesh is finished; from 1.21 finishing it
 * fails, and the preview leaves that part out.
 */
final class FaultyFluidScenario {
    private FaultyFluidScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("village_plains"));
        Director d = new Director(mc);
        d.then(run(() -> {
                    if (!FaultyFluids.hooked) {
                        throw new UnsupportedOperationException("the game's fluid drawing isn't hooked here");
                    }
                    FaultyFluids.start();
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(40))
                .then(shoot("f01_after_fluid_failed"))
                .then(run(() -> {
                    FaultyFluids.stop();
                    if (FaultyFluids.failures == 0) {
                        throw new IllegalStateException("No water was drawn, so the faulty fluid renderer never ran");
                    }
                }));
        return d;
    }
}
