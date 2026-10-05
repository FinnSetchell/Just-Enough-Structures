package com.finndog.justenoughstructures.gametest.neoforge;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.gametest.Gallery;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
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
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            carryOnPastWarnings(mc.screen);
            gallery.tick(mc);
        });
    }

    // NeoForge stops at a list of the warnings mods loaded with until someone carries on, which a hidden
    // window never does. From 26.2 it warns about mods' own metadata, which some of the dev runtime's
    // structure mods set off. A list with errors is left alone, as the game can't go on from it.
    private static void carryOnPastWarnings(Screen screen) {
        if (!(screen instanceof LoadingErrorScreen)) {
            return;
        }
        try {
            Field errors = LoadingErrorScreen.class.getDeclaredField("modLoadErrors");
            Field next = LoadingErrorScreen.class.getDeclaredField("nextScreenTask");
            errors.setAccessible(true);
            next.setAccessible(true);
            if (((List<?>) errors.get(screen)).isEmpty()) {
                ((Runnable) next.get(screen)).run();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.debug("Couldn't carry on past NeoForge's mod loading warnings", e);
        }
    }
}
