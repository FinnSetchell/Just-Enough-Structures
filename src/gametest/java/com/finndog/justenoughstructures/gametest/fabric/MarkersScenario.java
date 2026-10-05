package com.finndog.justenoughstructures.gametest.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Edited loot tables marked in the browser: the Loot tab's row and the chest popup, the New loot
 * table link and the editor it opens, and the editor's mark for changes not saved yet. The edit it
 * makes is removed again at the end.
 */
final class MarkersScenario {
    private static final ResourceLocation TABLE = Ids.parse("chests/pillager_outpost");
    private static final String DIAMONDS_ONLY = """
            {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]}]}
            """;

    private MarkersScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        JesScreen[] opened = new JesScreen[1];
        d.then(pressKey(InputConstants.KEY_K))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> opened[0] = browser(mc)))
                .then(run(() -> onServer(mc, server -> LootOverrides.save(server.getResourceManager(), TABLE, DIAMONDS_ONLY))))
                .then(pause(20))
                .then(run(ClientRequests::requestOverrides))
                .then(until(() -> ClientRequests.overrideStatus(TABLE.toString()) != null, 100))
                .then(run(() -> browser(mc).showLoot(TABLE.toString())))
                .then(pause(30))
                .then(shoot("m01_loot_tab_edited"))
                .then(run(() -> browser(mc).result().snapshot().containers().stream().filter(c -> c.source() != null).findFirst()
                        .ifPresent(browser(mc)::openContainer)))
                .then(pause(30))
                .then(shoot("m02_popup_edited"))
                .then(run(() -> browser(mc).openNewTable()))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(20))
                .then(shoot("m03_new_table"))
                .then(run(() -> mc.setScreen(new LootEditorScreen(opened[0], TABLE, "Pillager Outpost"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(run(() -> editor(mc).pick(0, 0)))
                .then(run(() -> editor(mc).typeItem("minecraft:emerald")))
                .then(pause(20))
                .then(shoot("m04_unsaved"))
                .then(run(() -> onServer(mc, server -> LootOverrides.remove(TABLE))));
        return d;
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static LootEditorScreen editor(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen s ? s : null;
    }
}
