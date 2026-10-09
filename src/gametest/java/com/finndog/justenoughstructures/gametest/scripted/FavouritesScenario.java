package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Starring structures in the list: the grey star on the row under the mouse, the Favourites
 * section the starred ones then show in, and taking one off again. The favourites it adds are
 * taken off at the end.
 */
final class FavouritesScenario {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final ResourceLocation MANSION = Ids.parse("mansion");

    private FavouritesScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> star(mc, IGLOO), 20))
                .then(pause(10))
                .then(shoot("f01_star_on_hover"))
                .then(click())
                .then(pause(10))
                .then(moveTo(() -> star(mc, MANSION), 20))
                .then(click())
                .then(pause(10))
                .then(moveTo(() -> browser(mc).viewportCentre(), 15))
                .then(pause(10))
                .then(shoot("f02_favourites_section"))
                .then(moveTo(() -> star(mc, IGLOO), 20))
                .then(pause(10))
                .then(shoot("f03_remove_hint"))
                .then(click())
                .then(pause(10))
                .then(shoot("f04_one_left"))
                .then(run(() -> {
                    if (ClientState.isFavourite(MANSION)) {
                        ClientState.toggleFavourite(MANSION);
                    }
                    if (ClientState.isFavourite(IGLOO)) {
                        ClientState.toggleFavourite(IGLOO);
                    }
                }));
        return d;
    }

    private static int[] star(Minecraft mc, ResourceLocation id) {
        return browser(mc).favouriteStar(id).orElse(new int[]{0, 0});
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }
}
