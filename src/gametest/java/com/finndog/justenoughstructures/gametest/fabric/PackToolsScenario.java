package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.PackToolsScreen;
import com.finndog.justenoughstructures.client.screen.TablePickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Pack tools: opened from the browser's button, each section in turn, the chest popup's shortcuts
 * into it, and picking a chest in the browser to change, as far as the table picker. Nothing is
 * saved, so the world's settings stay as they were.
 */
final class PackToolsScenario {
    private PackToolsScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(new ResourceLocation("desert_pyramid"));
        Director d = new Director(mc, null);
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(pause(20))
                .then(shoot("p00_button"))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(20))
                .then(shoot("p01_overview"))
                .then(run(() -> tools(mc).pickFor("loot", "minecraft:chests/desert_pyramid")))
                .then(pause(60))
                .then(shoot("p02_loot"))
                .then(run(() -> tools(mc).showSection("chests")))
                .then(pause(20))
                .then(shoot("p03_chests"))
                .then(run(() -> tools(mc).pickFor("structures", "minecraft:igloo")))
                .then(pause(30))
                .then(shoot("p04_structures"))
                .then(run(() -> tools(mc).showSection("rules")))
                .then(pause(20))
                .then(shoot("p05_rules"))
                .then(run(() -> tools(mc).showSection("chests")))
                .then(run(() -> tools(mc).startPicking()))
                .then(until(() -> browser(mc) != null && browser(mc).idle(), 600))
                // The outpost's chest is in a template, so it can be pointed at another table.
                .then(run(() -> browser(mc).select(new ResourceLocation("pillager_outpost"))))
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(20))
                .then(shoot("p06_picking"))
                .then(moveTo(() -> browser(mc).marker("minecraft:chests/pillager_outpost").orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(pause(20))
                .then(shoot("p07_picked"))
                .then(moveTo(() -> orZero(browser(mc).popupLink("tools_container")), 10))
                .then(pause(10))
                .then(shoot("p08_change_hover"))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 400))
                .then(pause(20))
                .then(moveTo(() -> orZero(tools(mc).buttonAt("Change")), 10))
                .then(click())
                .then(until(() -> mc.screen instanceof TablePickerScreen p && p.ready(), 400))
                .then(pause(20))
                .then(shoot("p09_picker"))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
                .then(pause(10))
                .then(run(() -> browser(mc).showLootTab()))
                .then(pause(10))
                .then(moveTo(() -> browser(mc).highlightRow("loot:minecraft:chests/pillager_outpost").orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(moveTo(() -> orZero(browser(mc).popupLink("tools_container")), 10))
                .then(pause(30))
                .then(shoot("p10_popup_icons"))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(40))
                .then(shoot("p11_chest_from_popup"));
        return d;
    }

    private static int[] orZero(int[] at) {
        return at == null ? new int[]{0, 0} : at;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static PackToolsScreen tools(Minecraft mc) {
        return mc.screen instanceof PackToolsScreen s ? s : null;
    }
}
