package com.finndog.justenoughstructures.gametest.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.PackToolsScreen;
import net.minecraft.client.Minecraft;

/**
 * Saving from Pack tools: notes for players on the igloo, its loot kept a secret, then the igloo
 * hidden from the browser, each seen as players would. Everything is put back at the end.
 */
final class PackToolsEditsScenario {
    private PackToolsEditsScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("igloo"));
        Director d = new Director(mc, null);
        d.then(pressKey(InputConstants.KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(run(() -> tools(mc).pickFor("structures", "minecraft:igloo")))
                .then(pause(10))
                .then(run(() -> tools(mc).typeNotes("Bring a pickaxe: the basement is under the carpet.")))
                .then(pause(10))
                .then(shoot("e01_notes_typed"))
                .then(moveTo(() -> at(mc, "Save notes"), 10))
                .then(click())
                .then(pause(30))
                .then(moveTo(() -> at(mc, "Keep where its loot is a secret"), 10))
                .then(click())
                .then(pause(30))
                .then(shoot("e02_saved"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null && browser(mc).idle(), 600))
                .then(run(() -> browser(mc).showInfoTab()))
                .then(pause(30))
                .then(shoot("e03_players_see_notes"))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(run(() -> tools(mc).pickFor("structures", "minecraft:igloo")))
                .then(pause(10))
                .then(moveTo(() -> at(mc, "Shown in the browser"), 10))
                .then(click())
                .then(pause(30))
                .then(shoot("e04_hidden"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null && browser(mc).idle(), 600))
                .then(pause(30))
                .then(shoot("e05_browser_without_igloo"))
                // Put it all back.
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(run(() -> tools(mc).pickFor("structures", "minecraft:igloo")))
                .then(pause(10))
                .then(moveTo(() -> at(mc, "Shown in the browser"), 10))
                .then(click())
                .then(pause(30))
                .then(moveTo(() -> at(mc, "Keep where its loot is a secret"), 10))
                .then(click())
                .then(pause(30))
                .then(run(() -> tools(mc).typeNotes("")))
                .then(pause(5))
                .then(moveTo(() -> at(mc, "Save notes"), 10))
                .then(click())
                .then(pause(30))
                .then(shoot("e06_put_back"));
        return d;
    }

    private static int[] at(Minecraft mc, String label) {
        int[] at = tools(mc) == null ? null : tools(mc).buttonAt(label);
        return at == null ? new int[]{0, 0} : at;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static PackToolsScreen tools(Minecraft mc) {
        return mc.screen instanceof PackToolsScreen s ? s : null;
    }
}
