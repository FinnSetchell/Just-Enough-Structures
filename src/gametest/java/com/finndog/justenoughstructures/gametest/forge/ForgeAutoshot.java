package com.finndog.justenoughstructures.gametest.forge;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.finndog.justenoughstructures.gametest.scripted.ScriptRun;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.event.TickEvent;
//? if <1.21 {
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
//?}
//? if <26.1 {
import net.minecraftforge.common.MinecraftForge;
//?}

/**
 * Runs the screenshot {@link Gallery} on Forge, or on 1.20.1 one of the scripted scenarios through
 * {@link ScriptRun}, when {@code -Djes.autoshot=<folder>} asks for it. Forge 1.21 dropped the HUD
 * event the scripts count frames by.
 */
final class ForgeAutoshot {
    private ForgeAutoshot() {
    }

    static void install() {
        //? if <1.21 {
        // A scripted scenario, played as on Fabric, drawing its cursor and counting frames after every
        // screen and the HUD.
        ScriptRun run = ScriptRun.fromProperties();
        if (run != null) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    run.tick(Minecraft.getInstance());
                }
            });
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> run.afterScreen(graphics(event)));
            MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> run.afterHud(graphics(event)));
            return;
        }
        //?}
        Gallery gallery = Gallery.fromProperties();
        if (gallery == null) {
            return;
        }
        //? if >=26.1 {
        /*TickEvent.ClientTickEvent.Post.BUS.addListener(event -> gallery.tick(Minecraft.getInstance()));
        *///?} else {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                gallery.tick(Minecraft.getInstance());
            }
        });
        //?}
    }

    private static final Map<Class<?>, Method> GRAPHICS = new HashMap<>();

    /**
     * What an event draws with, found by what its getter returns rather than by its name, so the same
     * code works whatever a Forge version calls it.
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
}
