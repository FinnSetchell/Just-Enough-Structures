package com.finndog.justenoughstructures.gametest.neoforge;

import com.finndog.justenoughstructures.gametest.Gallery;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Runs the screenshot {@link Gallery} on NeoForge, when {@code -Djes.autoshot=<folder>} asks for it. */
final class NeoForgeAutoshot {
    private NeoForgeAutoshot() {
    }

    static void install() {
        Gallery gallery = Gallery.fromProperties();
        if (gallery == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> gallery.tick(Minecraft.getInstance()));
    }
}
