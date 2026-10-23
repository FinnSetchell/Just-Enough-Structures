package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.editor;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        Director d = new Director(mc);
        d.then(pressBrowserKey())
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
                .then(shoot("e06_put_back"))
                // A new loot table started as a copy of the igloo chest's, which is left as it is.
                .then(run(() -> tools(mc).pickFor("loot", "minecraft:chests/igloo_chest")))
                .then(pause(20))
                .then(shoot("e07_loot_table_picked"))
                .then(moveTo(() -> at(mc, "Copy as new"), 10))
                .then(click())
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(run(() -> checkCopy(mc)))
                .then(shoot("e08_copy_as_new"));
        return d;
    }

    /** Checks the editor opened a copy of the igloo chest's table, under a new id and without its random_sequence. */
    private static void checkCopy(Minecraft mc) {
        String id = String.valueOf(Reflect.get(editor(mc), "tableId"));
        if (!id.equals("justenoughstructures:chests/igloo_chest_copy")) {
            throw new IllegalStateException("The copy was offered the id " + id);
        }
        JsonObject copy = JsonParser.parseString(editor(mc).draftText()).getAsJsonObject();
        if (copy.has("random_sequence") || !copy.toString().contains("minecraft:golden_apple")) {
            throw new IllegalStateException("The copy doesn't start as the igloo chest's table without its sequence: " + copy);
        }
    }

    private static int[] at(Minecraft mc, String label) {
        int[] at = tools(mc) == null ? null : tools(mc).buttonAt(label);
        return at == null ? new int[]{0, 0} : at;
    }
}
