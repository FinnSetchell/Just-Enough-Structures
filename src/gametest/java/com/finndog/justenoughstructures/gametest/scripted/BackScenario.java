package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.Gui;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
//? if >=26.1 {
/*import net.minecraft.client.input.KeyEvent;
*///?}

/**
 * Back and Forward: switching tabs and going back, a jump from a Found-in list and back to the
 * list, Forward again with Shift+Backspace, then into the loot editor and out and in again with the
 * mouse's side buttons.
 */
final class BackScenario {
    private BackScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(run(() -> browser(mc).showLoot("minecraft:chests/pillager_outpost")))
                .then(moveTo(() -> browser(mc).viewportCentre(), 10))
                .then(dragBy(120, 40, 20))
                .then(moveTo(() -> browser(mc).backButton(), 10))
                .then(pause(30))
                .then(shoot("b01_nowhere_yet"))
                .then(moveTo(() -> browser(mc).tab("blocks"), 10))
                .then(click())
                .then(moveTo(() -> browser(mc).backButton(), 10))
                .then(pause(30))
                .then(shoot("b02_blocks_back_tooltip"))
                .then(click())
                .then(pause(10))
                .then(moveTo(() -> browser(mc).forwardButton(), 10))
                .then(pause(30))
                .then(shoot("b03_back_on_loot"))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.ARROW))))
                // The loot index can still be building.
                .then(until(() -> browser(mc).foundInRow(1).isPresent(), 20 * 400))
                .then(pause(20))
                .then(moveTo(() -> browser(mc).foundInRow(1).orElse(new int[]{0, 0}), 15))
                .then(click())
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(10))
                .then(shoot("b04_jumped"))
                .then(pressKey(InputConstants.KEY_BACKSPACE))
                .then(until(() -> browser(mc).idle() && browser(mc).foundInOpen(), 600))
                .then(pause(20))
                .then(shoot("b05_back_to_the_list"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(run(() -> shiftBackspace(mc)))
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(20))
                .then(shoot("b06_forward"))
                .then(run(() -> browser(mc).openNewTable()))
                .then(until(() -> mc.screen instanceof LootEditorScreen e && e.loaded(), 200))
                .then(pause(20))
                .then(shoot("b07_editor"))
                .then(run(() -> Gui.click(mc.screen, 100, 100, GLFW.GLFW_MOUSE_BUTTON_4)))
                .then(until(() -> browser(mc) != null && browser(mc).idle(), 600))
                .then(pause(20))
                .then(moveTo(() -> browser(mc).forwardButton(), 10))
                .then(pause(30))
                .then(shoot("b08_back_from_editor"))
                .then(run(() -> Gui.click(mc.screen, 100, 100, GLFW.GLFW_MOUSE_BUTTON_5)))
                .then(until(() -> mc.screen instanceof LootEditorScreen e && e.loaded(), 200))
                .then(pause(20))
                .then(shoot("b09_forward_to_editor"));
        return d;
    }

    // Shift+Backspace goes Forward.
    private static void shiftBackspace(Minecraft mc) {
        //? if >=26.1 {
        /*mc.screen.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, 0, GLFW.GLFW_MOD_SHIFT));
        *///?} else {
        mc.screen.keyPressed(InputConstants.KEY_BACKSPACE, 0, GLFW.GLFW_MOD_SHIFT);
        //?}
    }
}
