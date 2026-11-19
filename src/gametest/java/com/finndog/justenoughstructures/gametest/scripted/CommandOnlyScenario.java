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
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command only: with it on, the browser's key does nothing, /jes open still opens the browser on
 * the structure asked for, and Pack tools still opens from it. With it off again, the key opens the
 * browser as before. The setting is put back as it was at the end. Run with Command only already on
 * in the game's saved state, it also checks the join: no loot index was asked for on joining.
 */
final class CommandOnlyScenario {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");

    private CommandOnlyScenario() {
    }

    static Director build(Minecraft mc) {
        boolean before = ClientState.isCommandOnly();
        // Read before anything here can ask for the index. Logged either way, so a run with the
        // setting off shows the hook does see the ask.
        boolean askedOnJoining = ClientRequests.indexAsked();
        JustEnoughStructures.LOGGER.info("Command only check starting: Command only was {} on joining, the loot index {} asked for",
                before ? "on" : "off", askedOnJoining ? "was" : "wasn't");
        Director d = new Director(mc);
        if (before) {
            d.then(check(() -> !askedOnJoining, "the loot index was asked for on joining with Command only on"));
        }
        d.then(run(() -> ChestLootTabs.setCommandOnly(true)))
                .then(pressBrowserKey())
                .then(pause(40))
                .then(check(() -> mc.screen == null, "the browser's key opened something with Command only on"))
                .then(shoot("k01_key_does_nothing"))
                .then(run(() -> jesOpen(mc, "jes open " + IGLOO)))
                .then(until(() -> browser(mc) != null, 100))
                .then(check(() -> browser(mc) != null, "/jes open didn't open the browser with Command only on"))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> IGLOO.equals(browser(mc).selectedId()), "/jes open didn't open the browser on the igloo"))
                .then(shoot("k02_opened_by_command"))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(pause(10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(check(() -> tools(mc) != null, "Pack tools didn't open from the browser with Command only on"))
                .then(pause(20))
                .then(shoot("k03_pack_tools"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> ChestLootTabs.setCommandOnly(false)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(check(() -> browser(mc) != null, "the browser's key didn't open it with Command only off again"))
                .then(until(() -> browser(mc).idle(), 400))
                .then(shoot("k04_key_opens_again"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> ChestLootTabs.setCommandOnly(before)));
        return d;
    }

    /** Runs a command as the player, on the server, as if typed in chat. */
    private static void jesOpen(Minecraft mc, String command) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            }
        });
    }

    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
