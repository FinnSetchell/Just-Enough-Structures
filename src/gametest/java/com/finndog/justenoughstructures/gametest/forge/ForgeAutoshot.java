package com.finndog.justenoughstructures.gametest.forge;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.finndog.justenoughstructures.gametest.scripted.ScriptRun;
import net.minecraft.client.Minecraft;
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
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> run.afterScreen(ScriptRun.graphics(event)));
            MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> run.afterHud(ScriptRun.graphics(event)));
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
}
