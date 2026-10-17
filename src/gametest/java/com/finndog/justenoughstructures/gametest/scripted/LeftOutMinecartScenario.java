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
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.render.Highlight;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.FaultyDraws;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;

/**
 * A chest minecart whose renderer fails, as another mod's can on the preview's stand-in for a world.
 * The preview leaves it out then, as it does anything it can't draw, and nothing may point at the
 * empty spot where it stood: no marker with its popup open, none lit and no tint there while another
 * chest minecart's popup is open or the Loot tab's row for them is hovered, and nothing found there by
 * the mouse. Another chest minecart, drawn as usual, keeps its marker.
 */
final class LeftOutMinecartScenario {
    /** How many chest minecarts are tried before giving up on finding one the mouse can point at. */
    private static final int TRIES = 8;

    private LeftOutMinecartScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("mineshaft"));
        List<StructureSnapshot.Container> carts = new ArrayList<>();
        // The chest minecart made to fail and another like it, the entity drawn for the first, and the
        // camera and layers it was seen with.
        StructureSnapshot.Container[] cart = new StructureSnapshot.Container[2];
        Entity[] entity = new Entity[1];
        StructureViewport.Camera[] camera = new StructureViewport.Camera[1];
        int[] layers = new int[1];
        boolean[] markers = new boolean[1];
        boolean[] otherMarked = new boolean[1];
        List<String> pointed = new ArrayList<>();
        Director d = new Director(mc);
        d.then(run(() -> {
                    if (!FaultyDraws.hooked) {
                        throw new UnsupportedOperationException("drawing entities isn't hooked here");
                    }
                    markers[0] = ClientState.markers;
                    ClientState.markers = true;
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null && browser(mc).result().succeeded(), 600))
                .then(run(() -> {
                    browser(mc).setSpin(false);
                    for (StructureSnapshot.Container c : browser(mc).result().snapshot().containers()) {
                        if (c.entity() && minecart(mc, c) != null) {
                            carts.add(c);
                        }
                    }
                    if (carts.size() < 2) {
                        throw new IllegalStateException("The mineshaft has " + carts.size() + " chest minecarts, and this needs two");
                    }
                }));
        // The camera turns to one after another until one is in the middle of the preview, with its marker showing.
        for (int i = 0; i < TRIES; i++) {
            int at = i;
            d.then(run(() -> {
                        if (cart[0] == null && at < carts.size()) {
                            browser(mc).openContainer(carts.get(at));
                        }
                    }))
                    .then(until(() -> cart[0] != null || at >= carts.size(), 40))
                    .then(run(() -> {
                        if (cart[0] == null && at < carts.size()) {
                            int[] middle = browser(mc).viewportCentre();
                            Entity e = minecart(mc, carts.get(at));
                            if (browser(mc).mobAt(middle[0], middle[1]) == e && browser(mc).marked(carts.get(at))) {
                                cart[0] = carts.get(at);
                                entity[0] = e;
                                camera[0] = viewport(mc).camera();
                                layers[0] = viewport(mc).view().sliceY();
                            }
                        }
                    }));
        }
        d.then(run(() -> {
                    if (cart[0] == null) {
                        throw new IllegalStateException("None of the first " + TRIES + " chest minecarts in the mineshaft could be pointed at with a marker showing");
                    }
                    FaultyDraws.arm(entity[0]);
                }))
                // Frames drawing it, the first of which has the preview leave it out.
                .then(pause(10))
                .then(run(() -> {
                    if (!browser(mc).leftOut().contains(entity[0])) {
                        throw new IllegalStateException("The chest minecart whose renderer fails was never left out (" + FaultyDraws.failures + " failures)");
                    }
                    if (browser(mc).marked(cart[0])) {
                        pointed.add("a marker stood for it with its popup open");
                    }
                }))
                .then(moveTo(() -> browser(mc).viewportCentre(), 4))
                .then(pause(10))
                .then(shoot("c01_left_out_minecart"))
                .then(run(() -> {
                    int[] middle = browser(mc).viewportCentre();
                    if (browser(mc).mobAt(middle[0], middle[1]) == entity[0]) {
                        pointed.add("the preview found it under the mouse");
                    }
                }))
                // Beside its popup, a click opens what's behind where it stood, or closes the popup.
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    Object popup = Reflect.get(browser(mc), "popup");
                    if (popup != null && cart[0].equals(Reflect.get(popup, "container"))) {
                        pointed.add("its popup was still open after a click where it stood");
                    }
                    for (StructureSnapshot.Container c : carts) {
                        if (c != cart[0] && cart[1] == null) {
                            cart[1] = c;
                        }
                    }
                    browser(mc).openContainer(cart[1]);
                }))
                .then(pause(30))
                .then(shoot("c02_other_minecart"))
                .then(run(() -> {
                    otherMarked[0] = browser(mc).marked(cart[1]);
                    if (highlighted(mc, cart[0])) {
                        pointed.add("it was tinted beside another chest minecart's popup");
                    }
                    if (browser(mc).marked(cart[0])) {
                        pointed.add("a marker stood for it beside another chest minecart's popup");
                    }
                    browser(mc).closeContainer();
                    // Back to where it stood, so a marker lit for it would show.
                    viewport(mc).setCamera(camera[0]);
                    browser(mc).setLayers(layers[0]);
                    browser(mc).showLootTab();
                }))
                .then(until(() -> browser(mc).highlightRow(row(cart[0])).isPresent(), 100))
                .then(moveTo(() -> browser(mc).highlightRow(row(cart[0])).orElse(new int[]{0, 0}), 10))
                .then(pause(15))
                .then(shoot("c03_loot_row"))
                .then(run(() -> {
                    FaultyDraws.armed = null;
                    ClientState.markers = markers[0];
                    if (!highlighted(mc, cart[1])) {
                        throw new IllegalStateException("The Loot tab's row for chest minecarts didn't light up the one drawn, so whether it lights the other up can't be told");
                    }
                    if (highlighted(mc, cart[0])) {
                        pointed.add("it was tinted with the Loot tab's row for it hovered");
                    }
                    if (browser(mc).marked(cart[0])) {
                        pointed.add("a marker stood for it with the Loot tab's row for it hovered");
                    }
                    if (!otherMarked[0]) {
                        throw new IllegalStateException("A chest minecart the preview still draws had no marker with its popup open");
                    }
                    if (!pointed.isEmpty()) {
                        throw new IllegalStateException("The preview left out a chest minecart whose renderer failed, but " + String.join("; ", pointed));
                    }
                    JustEnoughStructures.LOGGER.info("LEFTOUT a chest minecart failed to draw {} times, was left out of the preview, and nothing pointed at where it stood; another of {} kept its marker",
                            FaultyDraws.failures, carts.size());
                }));
        return d;
    }

    /** The chest minecart in the preview that {@code container} is, or null. */
    private static Entity minecart(Minecraft mc, StructureSnapshot.Container container) {
        for (Entity e : browser(mc).placedMobs()) {
            if (e instanceof Container && e.blockPosition().equals(container.pos())) {
                return e;
            }
        }
        return null;
    }

    private static StructureViewport viewport(Minecraft mc) {
        return (StructureViewport) Reflect.get(browser(mc), "viewport");
    }

    /** Whether the preview, as last drawn, tints where {@code container} is. */
    private static boolean highlighted(Minecraft mc, StructureSnapshot.Container container) {
        Highlight highlight = (Highlight) Reflect.get(browser(mc), "highlight");
        return highlight != null && highlight.contains(container.pos());
    }

    private static String row(StructureSnapshot.Container container) {
        return "loot:" + container.lootTable();
    }
}
