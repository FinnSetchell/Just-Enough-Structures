package com.finndog.justenoughstructures.gametest.forge;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.finndog.justenoughstructures.gametest.scripted.ScriptRun;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
//? if <1.21 {
import net.minecraftforge.client.event.RenderGuiEvent;
//?} else {
/*import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
*///?}
//? if <26.1 {
import net.minecraftforge.common.MinecraftForge;
//?}

/**
 * Runs the screenshot {@link Gallery}, or one of the scripted scenarios through {@link ScriptRun}, on
 * Forge, when {@code -Djes.autoshot=<folder>} asks for it. Forge 1.21 dropped the event for the HUD
 * having been drawn, which the scripts count frames by. From then they go by the one Forge sends as
 * it's about to draw the chat: that comes once for every frame the HUD is drawn in, with the same
 * thing to draw on, before anything has been moved for the chat.
 */
final class ForgeAutoshot {
    private ForgeAutoshot() {
    }

    static void install() {
        // A scripted scenario, played as on Fabric, drawing its cursor and counting frames after every
        // screen and the HUD.
        ScriptRun run = ScriptRun.fromProperties();
        if (run != null) {
            //? if >=26.1 {
            /*TickEvent.ClientTickEvent.Post.BUS.addListener(event -> run.tick(Minecraft.getInstance()));
            ScreenEvent.Render.Post.BUS.addListener(event -> run.afterScreen(ScriptRun.graphics(event)));
            CustomizeGuiOverlayEvent.Chat.BUS.addListener(event -> run.afterHud(ScriptRun.graphics(event)));
            *///?} else {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    run.tick(Minecraft.getInstance());
                }
            });
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> run.afterScreen(ScriptRun.graphics(event)));
            //?}
            //? if <1.21 {
            MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> run.afterHud(ScriptRun.graphics(event)));
            //?} else if <26.1 {
            /*MinecraftForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat event) -> run.afterHud(ScriptRun.graphics(event)));
            *///?}
            return;
        }
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
