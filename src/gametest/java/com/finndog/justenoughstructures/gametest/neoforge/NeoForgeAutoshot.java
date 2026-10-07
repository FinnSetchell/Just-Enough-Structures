package com.finndog.justenoughstructures.gametest.neoforge;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.gametest.Gallery;
import com.finndog.justenoughstructures.gametest.scripted.ScriptRun;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Runs the screenshot {@link Gallery}, or one of the scripted scenarios through {@link ScriptRun}, on
 * NeoForge, when {@code -Djes.autoshot=<folder>} asks for it.
 */
final class NeoForgeAutoshot {
    private NeoForgeAutoshot() {
    }

    static void install() {
        // A scripted scenario, played as on Fabric, drawing its cursor and counting frames after every
        // screen and the HUD.
        ScriptRun run = ScriptRun.fromProperties();
        if (run != null) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
                Minecraft mc = Minecraft.getInstance();
                carryOnPastWarnings(mc.screen);
                run.tick(mc);
            });
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> run.afterScreen(graphics(event)));
            NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> run.afterHud(graphics(event)));
            return;
        }
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

    private static final Map<Class<?>, Method> GRAPHICS = new HashMap<>();

    /**
     * What an event draws with, found by what its getter returns rather than by its name: 26.1's new
     * name for that class is put in by a text replacement everywhere, which NeoForge's getter, still
     * called by the old name, wouldn't survive.
     */
    private static GuiGraphics graphics(Object event) {
        Method getter = GRAPHICS.computeIfAbsent(event.getClass(), type -> {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == GuiGraphics.class) {
                    return method;
                }
            }
            throw new IllegalStateException("Nothing to draw with on " + type);
        });
        try {
            return (GuiGraphics) getter.invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
