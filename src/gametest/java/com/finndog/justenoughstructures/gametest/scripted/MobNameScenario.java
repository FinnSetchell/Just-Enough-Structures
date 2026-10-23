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
import net.minecraft.world.entity.Mob;

/**
 * Another mod's mob whose name fails, as one can when its name is worked out from something only a
 * mob in a real world has. A mob in the plains village, a villager if one can be seen, is made to fail
 * when asked. With the camera turned to it, the mouse points at it, so the preview's hover text asks
 * its name, then clicks it, so its popup asks for a title. The browser has to come through both and
 * show the name of the mob's kind instead.
 *
 * <p>Forge and NeoForge have the game's entity renderer ask every mob it draws for its name, so there
 * the preview leaves the mob out instead, as it does anything it can't draw. Then nothing may point at
 * the empty spot where it stood: no marker, no hover text and no popup.
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
        // Whether the preview left it out, whether a marker stood for it before and after it was made
        // to fail, how often its hover text and popup had asked by the time it was left out, and what
        // still pointed at it then.
        boolean[] leftOut = new boolean[1];
        boolean[] marked = new boolean[2];
        int[] asked = new int[2];
        List<String> pointed = new ArrayList<>();
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
                    // Its marker is looked for while every loader still draws it, so one gone later is
                    // down to the preview leaving it out. With its popup open, markers mark mobs.
                    ClientState.markers = true;
                }))
                .then(pause(5))
                .then(run(() -> {
                    marked[0] = browser(mc).marked(mob[0]);
                    ThrowingNames.arm(mob[0]);
                }))
                // Frames drawing it, in which a loader that asks every mob it draws for its name has the preview leave it out.
                .then(pause(10))
                .then(run(() -> {
                    leftOut[0] = browser(mc).leftOut().contains(mob[0]);
                    marked[1] = browser(mc).marked(mob[0]);
                    ClientState.markers = false;
                    asked[0] = ThrowingNames.hover;
                    asked[1] = ThrowingNames.popup;
                    if (leftOut[0] && marked[1]) {
                        pointed.add("a marker still stood for it");
                    }
                }))
                .then(moveTo(() -> browser(mc).viewportCentre(), 4))
                .then(pause(10))
                .then(shoot("n01_hover_mob_name"))
                .then(run(() -> {
                    if (leftOut[0]) {
                        pointedByHover(mc, mob[0], asked[0], pointed);
                    } else {
                        checkHover(mc, mob[0]);
                    }
                }))
                .then(click())
                .then(pause(10))
                .then(shoot("n02_mob_popup_name"))
                .then(run(() -> {
                    ThrowingNames.armed = null;
                    ClientState.markers = markers[0];
                    checkMarkers(mob[0], marked, leftOut[0]);
                    if (leftOut[0]) {
                        checkLeftOut(mc, mob[0], asked[1], pointed);
                    } else {
                        checkPopup(mc, mob[0]);
                    }
                }));
        return d;
    }

    /**
     * A mob has a marker while it's drawn, and keeps its marker after its name has
     * started failing wherever the preview still draws it. Anything else in the village may not be a
     * mob, which markers leave alone.
     */
    private static void checkMarkers(Entity mob, boolean[] marked, boolean leftOut) {
        if (!(mob instanceof Mob)) {
            return;
        }
        if (!marked[0]) {
            throw new IllegalStateException("No marker stood for the mob while it was drawn, so whether it keeps one can't be told");
        }
        if (!leftOut && !marked[1]) {
            throw new IllegalStateException("A mob the preview still draws lost its marker once its name failed");
        }
    }

    /** Notes the preview finding a mob it left out under the mouse, and its hover text being made. */
    private static void pointedByHover(Minecraft mc, Entity mob, int hoverAsked, List<String> pointed) {
        int[] middle = browser(mc).viewportCentre();
        if (browser(mc).mobAt(middle[0], middle[1]) == mob) {
            pointed.add("the preview found it under the mouse");
        }
        int hovered = ThrowingNames.hover - hoverAsked;
        if (hovered > 0) {
            pointed.add("its hover text asked for its name " + hovered + " times");
        }
    }

    /**
     * After a click where a mob the preview left out stood, its popup mustn't be showing: the click
     * opens whatever is behind it, or closes the popup that was open for it when nothing is.
     */
    private static void checkLeftOut(Minecraft mc, Entity mob, int popupAsked, List<String> pointed) {
        int opened = ThrowingNames.popup - popupAsked;
        Object popup = Reflect.get(browser(mc), "mobPopup");
        if (opened > 0) {
            pointed.add("clicking where it stood opened its popup " + opened + " times");
        } else if (popup != null && Reflect.get(popup, "mob") == mob) {
            pointed.add("its popup was still open after a click where it stood");
        }
        if (!pointed.isEmpty()) {
            throw new IllegalStateException("The preview left out a mob whose name failed, but " + String.join("; ", pointed));
        }
        JustEnoughStructures.LOGGER.info("MOBNAME a {} failed when asked for its name {} times by anything but the browser, was left out of the preview, and nothing pointed at where it stood: no marker, no hover text and no popup",
                mob.getType().getDescriptionId(), ThrowingNames.other);
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
