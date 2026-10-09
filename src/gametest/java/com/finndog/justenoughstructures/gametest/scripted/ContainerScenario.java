package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tablePicker;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Pointing one container at another loot table, from Pack tools: picking the chest in the browser,
 * its popup's Change link, the table picker, the chest in Pack tools waiting for /reload and after
 * it, Undo, and the Edit table link on a chest placed by code.
 */
final class ContainerScenario {
    private static final int[] reloadsBefore = new int[1];

    private ContainerScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> captured(mc), 400))
                .then(run(() -> browser(mc).pickForTools()))
                .then(run(() -> open(mc, c -> c.source() != null)))
                .then(pause(40))
                .then(moveTo(() -> link(mc, "tools_container"), 10))
                .then(pause(10))
                .then(shoot("k01_change_link"))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 400))
                .then(pause(20))
                .then(moveTo(() -> button(mc, "Change"), 10))
                .then(click())
                .then(until(() -> tablePicker(mc) != null, 40))
                .then(until(() -> tablePicker(mc).ready(), 3600))
                .then(pause(10))
                .then(shoot("k02_picker"))
                .then(type("igloo", 2))
                .then(run(() -> tablePicker(mc).pick(0)))
                .then(pause(10))
                .then(shoot("k03_picked"))
                .then(run(() -> tablePicker(mc).useIt()))
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 100))
                .then(pause(30))
                .then(shoot("k04_saved"))
                .then(run(() -> reload(mc)))
                .then(until(() -> ClientRequests.reloads() > reloadsBefore[0], 1200))
                .then(pause(40))
                .then(moveTo(() -> button(mc, "Undo"), 10))
                .then(pause(10))
                .then(shoot("k05_changed"))
                .then(click())
                .then(pause(30))
                .then(shoot("k06_undone"))
                .then(run(() -> reload(mc)))
                .then(until(() -> ClientRequests.reloads() > reloadsBefore[0], 1200))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                // The test datapack hides the jungle pyramid's loot, so the desert pyramid shows a chest placed by code.
                .then(run(() -> browser(mc).select(Ids.parse("desert_pyramid"))))
                // The one picked before can still show for a moment, until the new one is asked for.
                .then(until(() -> captured(mc) && browser(mc).result().snapshot().structureId().equals(Ids.parse("desert_pyramid")), 600))
                .then(run(() -> browser(mc).pickForTools()))
                .then(run(() -> open(mc, c -> c.source() == null && c.lootTable() != null)))
                .then(pause(40))
                .then(moveTo(() -> link(mc, "tools_container"), 10))
                .then(pause(10))
                .then(shoot("k07_edit_table"));
        return d;
    }

    /** Where a button in Pack tools is, or the middle of the screen if it isn't shown. */
    private static int[] button(Minecraft mc, String label) {
        int[] at = tools(mc) == null ? null : tools(mc).buttonAt(label);
        return at != null ? at : new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2};
    }

    /** Where a popup link is, or the middle of the screen if it isn't shown, so a missing link shows up in the shot. */
    private static int[] link(Minecraft mc, String name) {
        int[] at = browser(mc) == null ? null : browser(mc).popupLink(name);
        return at != null ? at : new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2};
    }

    private static boolean captured(Minecraft mc) {
        JesScreen browser = browser(mc);
        return browser != null && browser.idle() && browser.result() != null && browser.result().succeeded();
    }

    private static void open(Minecraft mc, Predicate<StructureSnapshot.Container> which) {
        browser(mc).result().snapshot().containers().stream().filter(which).findFirst().ifPresent(browser(mc)::openContainer);
    }

    private static void reload(Minecraft mc) {
        reloadsBefore[0] = ClientRequests.reloads();
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload"));
    }
}
