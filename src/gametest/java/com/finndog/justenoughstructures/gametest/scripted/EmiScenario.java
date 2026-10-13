package com.finndog.justenoughstructures.gametest.scripted;

//? if emi {
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
//?}
import net.minecraft.client.Minecraft;

/** How EMI and the loot index get on, in a game that has EMI (-Pviewer=emi in the dev game). */
final class EmiScenario {
    private EmiScenario() {
    }

    /**
     * With a loot index the server saved in a game before, EMI loads once: the index is there in
     * time for the load EMI starts on joining, so there's no second one to add the structures. The
     * first game with a set of mods has no saved index, and EMI can't help loading twice then.
     */
    static Director once(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        Director d = new Director(mc);
        d.then(until(JesEmiPlugin::ready, 20 * 60 * 30))
                // Long enough for another load to have started, had one been going to.
                .then(pause(200))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("EMI loaded {} times", JesEmiPlugin.loads());
                    if (JesEmiPlugin.loads() != 1) {
                        throw new IllegalStateException("EMI loaded " + JesEmiPlugin.loads() + " times");
                    }
                }));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs EMI");
        *///?}
    }
}
