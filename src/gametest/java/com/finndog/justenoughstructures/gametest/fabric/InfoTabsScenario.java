package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;
import static com.finndog.justenoughstructures.gametest.fabric.Director.wheel;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Each of the Info panel's tabs: the Loot tab with a table picked, at the top and scrolled to its
 * odds, then Blocks and Mobs. For checking their layout at other GUI scales and window sizes
 * (-Pgui, -Pwidth, -Pheight). -Pstructures picks the structure.
 */
final class InfoTabsScenario {
    private InfoTabsScenario() {
    }

    static Director build(Minecraft mc) {
        String requested = System.getProperty("jes.autoshot.structures", "");
        JesScreen.startOn(new ResourceLocation(requested.isBlank() ? "minecraft:pillager_outpost" : requested.split(",")[0].trim()));
        Director d = new Director(mc, null);
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).showLoot(firstTable(mc))))
                .then(moveTo(panel(mc), 6))
                .then(pause(60))
                .then(shoot("t01_loot"))
                .then(wheel(-6))
                .then(pause(10))
                .then(shoot("t02_loot_odds"))
                .then(moveTo(() -> browser(mc).tab("blocks"), 6))
                .then(click())
                .then(moveTo(panel(mc), 6))
                .then(pause(10))
                .then(shoot("t03_blocks"))
                .then(moveTo(() -> browser(mc).tab("entities"), 6))
                .then(click())
                .then(moveTo(panel(mc), 6))
                .then(pause(10))
                .then(shoot("t04_mobs"));
        return d;
    }

    /** A spot inside the panel's body, below the tabs, for the cursor to rest while scrolling. */
    private static Supplier<int[]> panel(Minecraft mc) {
        return () -> {
            int[] tab = browser(mc).tab("overview");
            return new int[]{tab[0] + 20, tab[1] + 60};
        };
    }

    private static String firstTable(Minecraft mc) {
        for (StructureSnapshot.Container c : browser(mc).result().snapshot().containers()) {
            if (c.lootTable() != null) {
                return c.lootTable();
            }
        }
        return null;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }
}
