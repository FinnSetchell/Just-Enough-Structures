package com.finndog.justenoughstructures.forge;

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
