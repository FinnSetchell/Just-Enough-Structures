package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassLink;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Only called with Explorer's Compass installed, so its classes are never touched otherwise. */
final class ForgeExplorersCompass {
    private ForgeExplorersCompass() {
    }

    static void register() {
        if (!ExplorersCompassLink.supported()) {
            JustEnoughStructures.LOGGER.warn("This Explorer's Compass is older than the one the browser works with, so the browser and the compass won't open each other. Updating it brings that back");
            return;
        }
        ExplorersCompassLink link = new ExplorersCompassLink();
        CompassLink.set(link);
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> link.afterInit(event.getScreen(), event::addListener));
        // Forge has no event for a screen's tick, but the end of the client's tick comes right after it.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.phase == TickEvent.Phase.END && minecraft.screen != null) {
                link.afterTick(minecraft.screen);
            }
        });
    }
}
