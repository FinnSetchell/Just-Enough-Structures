package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassLink;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

/** Only called with Explorer's Compass installed, so its classes are never touched otherwise. */
final class FabricExplorersCompass {
    private FabricExplorersCompass() {
    }

    static void register() {
        if (!ExplorersCompassLink.supported()) {
            JustEnoughStructures.LOGGER.warn("This Explorer's Compass is older than the one the browser works with, so the browser and the compass won't open each other. Updating it brings that back");
            return;
        }
        ExplorersCompassLink link = new ExplorersCompassLink();
        CompassLink.set(link);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            link.afterInit(screen, widget -> Screens.getButtons(screen).add(widget));
            ScreenEvents.afterTick(screen).register(link::afterTick);
        });
    }
}
