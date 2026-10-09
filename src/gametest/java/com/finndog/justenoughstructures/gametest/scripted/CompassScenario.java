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

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Explorer's Compass and the browser, both ways: the browser opens the compass on a structure,
 * the compass's Preview button opens that structure in the browser, and closing the browser goes
 * back to the compass as it was. Then Ctrl-click, which sets the compass searching straight away.
 * Needs Explorer's Compass, so only with the dev mods.
 */
final class CompassScenario {
    private CompassScenario() {
    }

    static Director build(Minecraft mc) {
        if (!JustEnoughStructures.modVersions().containsKey("explorerscompass")) {
            throw new UnsupportedOperationException("needs Explorer's Compass");
        }
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        d.then(run(() -> giveCompass(mc)))
                .then(pause(20))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(moveTo(() -> browser(mc).button("compass"), 10))
                .then(pause(10))
                .then(shoot("c01_browser_holding_compass"))
                .then(click())
                .then(until(() -> onCompassScreen(mc), 40))
                // The compass asks the server what it may search for, then picks the structure.
                .then(pause(60))
                .then(shoot("c02_compass_picked_it"))
                .then(moveTo(() -> new int[]{65, mc.getWindow().getGuiScaledHeight() - 45}, 10))
                .then(pause(5))
                .then(click())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(shoot("c03_preview_from_compass"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> onCompassScreen(mc), 40))
                .then(pause(10))
                .then(shoot("c04_back_in_compass"))
                // Ctrl-click: the compass starts searching straight away. Only villages and
                // strongholds can generate in the superflat world.
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> JesScreen.startOn(Ids.parse("village_plains"))))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(run(() -> browser(mc).pointCompassNow()))
                .then(until(() -> mc.screen == null, 100))
                .then(pause(20))
                .then(shoot("c05_compass_searching"));
        return d;
    }

    private static boolean onCompassScreen(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getSimpleName().equals("ExplorersCompassScreen");
    }

    /** Puts an Explorer's Compass in the player's hand, on the server so it sticks. */
    private static void giveCompass(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        ItemStack compass = new ItemStack(Regs.value(BuiltInRegistries.ITEM, Ids.of("explorerscompass", "explorerscompass")));
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.setItemInHand(InteractionHand.MAIN_HAND, compass);
            }
        });
    }
}
