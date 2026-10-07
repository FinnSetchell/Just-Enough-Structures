package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.finndog.justenoughstructures.gametest.scripted.Scenarios;
import com.finndog.justenoughstructures.gametest.scripted.ScriptRun;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
/*import com.finndog.justenoughstructures.Ids;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * Dev-only screenshot run on Fabric: either the {@link Gallery}, or one of the scripted scenarios,
 * which {@link ScriptRun} plays the same on every loader. Enabled with {@code -Djes.autoshot=<folder>};
 * see the runAutoshot task.
 */
public final class Autoshot implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String folder = System.getProperty("jes.autoshot");
        if (folder == null || folder.isBlank()) {
            return;
        }
        // The scenarios only Fabric's dev runtime has the mods for.
        Scenarios.add("settings", ConfigScenario::build);
        Scenarios.add("viewer", ViewerScenario::build);
        Scenarios.add("jei", JeiScenario::build);
        ScriptRun run = ScriptRun.fromProperties();
        if (run == null) {
            ClientTickEvents.END_CLIENT_TICK.register(Gallery.fromProperties()::tick);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(run::tick);
        //? if >=26.1 {
        /*ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
                ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partial) -> run.afterScreen(graphics)));
        HudElementRegistry.addLast(Ids.of("justenoughstructures_gametest", "director"), (graphics, delta) -> run.afterHud(graphics));
        *///?} else {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, partial) -> run.afterScreen(graphics)));
        HudRenderCallback.EVENT.register((graphics, partial) -> run.afterHud(graphics));
        //?}
    }
}
