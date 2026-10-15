package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.ThrowingNames;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Another mod's mob whose name fails, as one can when its name is worked out from something only a
 * mob in a real world has. A mob in the plains village, a villager if one can be seen, is made to fail
 * when asked. With the camera turned to it, the mouse points at it, so the preview's hover text asks
 * its name, then clicks it, so its popup asks for a title. The browser has to come through both and
 * show the name of the mob's kind instead.
 */
final class MobNameScenario {
    /** How many mobs are tried before giving up on finding one the mouse can point at. */
    private static final int TRIES = 8;

    private MobNameScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("village_plains"));
        List<Entity> candidates = new ArrayList<>();
        Entity[] mob = new Entity[1];
        boolean[] markers = new boolean[1];
        Director d = new Director(mc);
        d.then(run(() -> {
                    if (!ThrowingNames.hooked) {
                        throw new UnsupportedOperationException("mobs' names aren't hooked here");
                    }
                    // So the click lands on the mob itself, not a marker over it.
                    markers[0] = ClientState.markers;
                    ClientState.markers = false;
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null && browser(mc).result().succeeded(), 600))
                .then(run(() -> {
                    browser(mc).setSpin(false);
                    // Villagers first: a villager's own name is its job, which isn't its kind's name.
                    for (Entity e : browser(mc).placedMobs()) {
                        if (villager(e)) {
                            candidates.add(e);
                        }
                    }
                    for (Entity e : browser(mc).placedMobs()) {
                        if (!villager(e)) {
                            candidates.add(e);
                        }
                    }
                    if (candidates.isEmpty()) {
                        throw new IllegalStateException("The plains village has no mobs to try");
                    }
                }));
        // The camera turns to one after another until one is in the middle of the preview, not behind something.
        for (int i = 0; i < TRIES; i++) {
            int at = i;
            d.then(run(() -> {
                        if (mob[0] == null && at < candidates.size()) {
                            browser(mc).openMobPopup(candidates.get(at));
                        }
                    }))
                    .then(until(() -> mob[0] != null || at >= candidates.size(), 40))
                    .then(run(() -> {
                        if (mob[0] == null && at < candidates.size()) {
                            int[] middle = browser(mc).viewportCentre();
                            if (browser(mc).mobAt(middle[0], middle[1]) == candidates.get(at)) {
                                mob[0] = candidates.get(at);
                            }
                        }
                    }));
        }
        d.then(run(() -> {
                    if (mob[0] == null) {
                        throw new IllegalStateException("None of the first " + TRIES + " mobs in the plains village could be pointed at");
                    }
                    ThrowingNames.arm(mob[0]);
                }))
                .then(moveTo(() -> browser(mc).viewportCentre(), 4))
                .then(pause(10))
                .then(shoot("n01_hover_mob_name"))
                .then(run(() -> checkHover(mc, mob[0])))
                .then(click())
                .then(pause(10))
                .then(shoot("n02_mob_popup_name"))
                .then(run(() -> {
                    ThrowingNames.armed = null;
                    ClientState.markers = markers[0];
                    checkPopup(mc, mob[0]);
                }));
        return d;
    }

    private static boolean villager(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("villager");
    }

    private static void checkHover(Minecraft mc, Entity mob) {
        if (ThrowingNames.hover == 0) {
            throw new IllegalStateException("The hover text never asked the mob for its name, so nothing was tried");
        }
        int[] middle = browser(mc).viewportCentre();
        List<Component> text = browser(mc).hoverTextAt(middle[0], middle[1]);
        String kind = mob.getType().getDescription().getString();
        if (text.isEmpty() || !text.get(0).getString().equals(kind)) {
            throw new IllegalStateException("The hover text for a mob whose name failed starts with "
                    + (text.isEmpty() ? "nothing" : "\"" + text.get(0).getString() + "\"") + ", not \"" + kind + "\"");
        }
    }

    private static void checkPopup(Minecraft mc, Entity mob) {
        if (ThrowingNames.popup == 0) {
            throw new IllegalStateException("The mob's popup never asked it for its name, so nothing was tried");
        }
        Object popup = Reflect.get(browser(mc), "mobPopup");
        if (popup == null || Reflect.get(popup, "mob") != mob) {
            throw new IllegalStateException("Clicking the mob didn't open its popup");
        }
        String title = ((Component) Reflect.get(popup, "title")).getString();
        String kind = mob.getType().getDescription().getString();
        if (!title.equals(kind)) {
            throw new IllegalStateException("The popup of a mob whose name failed is called \"" + title + "\", not \"" + kind + "\"");
        }
        JustEnoughStructures.LOGGER.info("MOBNAME a {} failed when asked for its name {} times by the hover text, {} by its popup and {} by anything else, and the browser is still up",
                mob.getType().getDescriptionId(), ThrowingNames.hover, ThrowingNames.popup, ThrowingNames.other);
    }
}
