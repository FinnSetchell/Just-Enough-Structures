package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.client.screen.PackToolsScreen;
import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.type;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.client.screen.TablePickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * The loot editing rough edges a hands-on test turned up: tables from other layouts on the Loot
 * tab, a JSON mistake explained by line, an item that isn't one refused on save, and a new table's
 * header with its id.
 */
final class LootFixesScenario {
    private static final ResourceLocation TABLE = new ResourceLocation("chests/village/village_plains_house");
    /** A table with its closing brackets missing, as a hand edit might leave it. */
    private static final String BROKEN = """
            {
              "type": "minecraft:chest",
              "pools": [
                {"rolls": 1, "entries": []}
            """;

    private LootFixesScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(new ResourceLocation("village_plains"));
        Director d = new Director(mc, null);
        // The browser, to open the editors over and to come back to.
        JesScreen[] opened = new JesScreen[1];
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> opened[0] = browser(mc)))
                .then(run(() -> browser(mc).showLootTab()))
                .then(until(FoundIn::ready, 12000))
                .then(pause(40))
                .then(shoot("f01_other_layouts"))
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, TABLE, "Village Plains House"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(run(() -> editor(mc).showJson(BROKEN)))
                .then(pause(10))
                .then(run(() -> editor(mc).saveNow()))
                .then(pause(30))
                .then(shoot("f02_json_mistake"))
                .then(run(() -> mc.setScreen(new LootEditorScreen(opened[0], TABLE, "Village Plains House"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(run(() -> editor(mc).pick(0, 0)))
                .then(run(() -> editor(mc).typeItem("minecraft:fake_item_xyz")))
                .then(pause(10))
                .then(run(() -> editor(mc).saveNow()))
                .then(pause(20))
                .then(shoot("f03_bad_item"))
                .then(run(() -> mc.setScreen(new LootEditorScreen(opened[0],
                        new ResourceLocation("justenoughstructures", "chests/test_vault"), "Test Vault"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(20))
                .then(shoot("f04_new_table"))
                // A table id that doesn't exist, typed into a chest's picker: picked straight away,
                // and the server's no stays in the picker to be fixed.
                .then(run(() -> mc.setScreen(opened[0])))
                .then(run(() -> browser(mc).select(new ResourceLocation("pillager_outpost"))))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null, 600))
                .then(run(() -> browser(mc).pickForTools()))
                .then(run(() -> browser(mc).result().snapshot().containers().stream().filter(c -> c.source() != null).findFirst()
                        .ifPresent(browser(mc)::openContainer)))
                .then(pause(30))
                .then(moveTo(() -> browser(mc).popupLink("tools_container"), 8))
                .then(click())
                .then(until(() -> mc.screen instanceof PackToolsScreen tools && tools.loaded(), 400))
                .then(pause(20))
                .then(moveTo(() -> mc.screen instanceof PackToolsScreen tools && tools.buttonAt("Change") != null ? tools.buttonAt("Change") : new int[]{0, 0}, 8))
                .then(click())
                .then(until(() -> picker(mc) != null, 40))
                .then(type("nosuchmod:chests/nothing_here", 1))
                .then(pause(10))
                .then(shoot("f05_typed_id"))
                .then(run(() -> picker(mc).useIt()))
                .then(pause(30))
                .then(shoot("f06_no_such_table"));
        return d;
    }

    private static TablePickerScreen picker(Minecraft mc) {
        return mc.screen instanceof TablePickerScreen s ? s : null;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static LootEditorScreen editor(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen s ? s : null;
    }
}
