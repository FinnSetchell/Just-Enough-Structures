package com.finndog.justenoughstructures.neoforge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassLink;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Only called with Explorer's Compass installed, so its classes are never touched otherwise. */
final class NeoForgeExplorersCompass {
    private NeoForgeExplorersCompass() {
    }

    static void register() {
        if (!ExplorersCompassLink.supported()) {
            JustEnoughStructures.LOGGER.warn("This Explorer's Compass is older than the one the browser works with, so the browser and the compass won't open each other. Updating it brings that back");
            return;
        }
        ExplorersCompassLink link = new ExplorersCompassLink();
        CompassLink.set(link);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> link.afterInit(event.getScreen(), event::addListener));
        // NeoForge has no event for a screen's tick, but the end of the client's tick comes right after it.
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                link.afterTick(minecraft.screen);
            }
        });
    }
}
