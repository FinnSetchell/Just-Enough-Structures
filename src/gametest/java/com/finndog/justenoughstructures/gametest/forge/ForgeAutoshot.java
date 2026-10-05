package com.finndog.justenoughstructures.gametest.forge;

import com.finndog.justenoughstructures.gametest.Gallery;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Runs the screenshot {@link Gallery} on Forge, when {@code -Djes.autoshot=<folder>} asks for it. */
final class ForgeAutoshot {
    private ForgeAutoshot() {
    }

    static void install() {
        Gallery gallery = Gallery.fromProperties();
        if (gallery == null) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                gallery.tick(Minecraft.getInstance());
            }
        });
    }
}
