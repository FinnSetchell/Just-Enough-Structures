package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Jumping to another structure from a Found-in list and going back: the outpost turned and on its
 * Loot tab, the list for arrows, the structure picked from it with the Back button showing, and
 * the outpost again as it was left, list and all.
 */
final class BackScenario {
    private BackScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(new ResourceLocation("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(run(() -> browser(mc).showLoot("minecraft:chests/pillager_outpost")))
                .then(moveTo(() -> browser(mc).viewportCentre(), 10))
                .then(dragBy(120, 40, 20))
                .then(pause(10))
                .then(shoot("b01_before"))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.ARROW))))
                // The loot index can still be building.
                .then(until(() -> browser(mc).foundInRow(1).isPresent(), 20 * 400))
                .then(pause(20))
                .then(shoot("b02_found_in"))
                .then(pause(300))
                .then(shoot("b02b_found_in_later"))
                .then(moveTo(() -> browser(mc).foundInRow(1).orElse(new int[]{0, 0}), 15))
                .then(click())
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(10))
                .then(moveTo(() -> browser(mc).backButton(), 15))
                .then(pause(15))
                .then(shoot("b03_jumped"))
                .then(click())
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(20))
                .then(shoot("b04_back"))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
                .then(pause(10))
                .then(shoot("b05_list_closed"));
        return d;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }
}
