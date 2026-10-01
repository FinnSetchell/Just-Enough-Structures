package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;
import static com.finndog.justenoughstructures.gametest.fabric.Director.wheel;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * The Info tab with its details open, at the top and scrolled down, for checking the layout at
 * other GUI scales and window sizes (-Pgui, -Pwidth, -Pheight). -Pstructures picks the structure.
 */
final class DetailsScenario {
    private DetailsScenario() {
    }

    static Director build(Minecraft mc) {
        String requested = System.getProperty("jes.autoshot.structures", "");
        JesScreen.startOn(new ResourceLocation(requested.isBlank() ? "betterdeserttemples:desert_temple" : requested.split(",")[0].trim()));
        Director d = new Director(mc, null);
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                // The details are for datapack authors, so they only show with advanced tooltips (F3+H).
                .then(run(() -> mc.options.advancedItemTooltips = true))
                .then(run(() -> browser(mc).showDetails(true)))
                .then(moveTo(() -> {
                    int[] tab = browser(mc).tab("overview");
                    return new int[]{tab[0] + 20, tab[1] + 60};
                }, 6))
                .then(pause(10))
                .then(shoot("d01_details_top"))
                .then(wheel(-4))
                .then(pause(10))
                .then(shoot("d02_details_middle"))
                .then(wheel(-10))
                .then(pause(10))
                .then(shoot("d03_details_bottom"))
                .then(run(() -> mc.options.advancedItemTooltips = false));
        return d;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }
}
