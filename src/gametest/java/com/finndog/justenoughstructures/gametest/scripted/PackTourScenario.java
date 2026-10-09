package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.chain;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.erase;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.record;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.skipIf;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Reflect.call;
import static com.finndog.justenoughstructures.gametest.scripted.Reflect.get;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.offset;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.ready;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * A tour of the browser in whatever modpack it runs in, recorded into screenshots/tour: a structure
 * from each of the mods with the most, found by filtering the list to that mod, with a chest's loot,
 * the Loot and Mobs tabs, an item search across every structure, and Pack tools. The structures are
 * picked from what the pack has, so nothing here names one.
 */
final class PackTourScenario {
    private static final int MODS = 4;

    private PackTourScenario() {
    }

    static Director build(Minecraft mc) {
        Director d = new Director(mc, null);
        List<ResourceLocation> picks = new ArrayList<>();
        d.then(run(() -> {
                    ClientState.spin = true;
                    ClientState.markers = true;
                }))
                .then(run(() -> ClientRequests.catalog()))
                .then(until(() -> ClientRequests.catalog().isDone(), 20 * 300))
                .then(run(() -> picks.addAll(choose())))
                .then(run(() -> JesScreen.startOn(picks.get(0))))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 100))
                .then(run(() -> {
                    if (browser(mc) == null) {
                        throw new IllegalStateException("TOUR the browser's key opened "
                                + (mc.screen == null ? "nothing" : mc.screen.getClass().getName()) + " instead");
                    }
                }))
                .then(until(() -> ready(mc), 3600))
                // Item search needs every structure's loot looked through first, which a big pack takes a while over.
                .then(until(FoundIn::ready, 20 * 1200))
                .then(pause(20))
                .then(moveTo(() -> offset(browser(mc).viewportCentre(), 200, 120), 1))
                .then(record("tour"))
                .then(pause(40));
        for (int i = 0; i < MODS; i++) {
            int which = i;
            d.then(skipIf(() -> picks.size() <= which, chain(
                    findInList(mc, () -> picks.get(which)),
                    pause(70),
                    which % 2 == 0 ? openChest(mc) : tabs(mc))));
        }
        d.then(itemSearch(mc, "$diamond"))
                .then(moveTo(() -> orCentre(mc, browser(mc) == null ? null : browser(mc).button("tools")), 24))
                .then(pause(6))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(60))
                .then(shoot("tour_pack_tools"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 60))
                .then(until(() -> ready(mc), 1200))
                .then(pause(40))
                .then(record("tour_end"))
                .then(pause(4));
        return d;
    }

    /** A structure from each of the mods with the most, from the middle of that mod's list. */
    private static List<ResourceLocation> choose() {
        Map<String, List<ResourceLocation>> byMod = new LinkedHashMap<>();
        for (StructureCatalog.Entry e : ClientRequests.catalog().join()) {
            if (!e.id().getNamespace().equals("minecraft")) {
                byMod.computeIfAbsent(e.id().getNamespace(), k -> new ArrayList<>()).add(e.id());
            }
        }
        List<ResourceLocation> out = byMod.values().stream()
                .sorted(Comparator.comparingInt((List<ResourceLocation> l) -> -l.size()))
                .limit(MODS)
                .map(l -> l.get(l.size() / 2))
                .toList();
        JustEnoughStructures.LOGGER.info("TOUR picks {} from {} mods", out, byMod.size());
        return out;
    }

    /** Filters the list to a structure's mod with "@", then clicks its row, or picks it if it's off screen. */
    private static Director.Action findInList(Minecraft mc, Supplier<ResourceLocation> id) {
        return chain(
                moveTo(() -> orCentre(mc, browser(mc).searchBox()), 22),
                pause(6),
                click(),
                pause(4),
                erase(24, 1),
                (d, frame) -> type("@" + id.get().getNamespace(), 3).step(d, frame),
                pause(20),
                moveTo(() -> orCentre(mc, browser(mc).structureRow(id.get()).orElse(null)), 22),
                pause(8),
                (d, frame) -> {
                    if (frame == 0 && browser(mc).structureRow(id.get()).isEmpty()) {
                        browser(mc).select(id.get());
                        return true;
                    }
                    return click().step(d, frame);
                },
                pause(2),
                until(() -> ready(mc), 3600));
    }

    /** Clicks a chest's marker, shows its chances, and closes it. */
    private static Director.Action openChest(Minecraft mc) {
        int[][] marker = new int[1][];
        return chain(
                run(() -> marker[0] = chestMarker(mc)),
                skipIf(() -> marker[0] == null, chain(
                        moveTo(() -> marker[0], 24),
                        pause(10),
                        click(),
                        until(() -> browser(mc).containerOpen(), 60),
                        pause(40),
                        moveTo(() -> orCentre(mc, browser(mc).popupLink("odds")), 18),
                        pause(6),
                        click(),
                        pause(60),
                        run(() -> browser(mc).closeContainer()),
                        pause(20))));
    }

    /** The Loot tab, then the Mobs tab, then back to the details. */
    private static Director.Action tabs(Minecraft mc) {
        return chain(
                moveTo(() -> orCentre(mc, browser(mc).tab("loot")), 22),
                pause(6),
                click(),
                pause(70),
                moveTo(() -> orCentre(mc, browser(mc).tab("entities")), 22),
                pause(6),
                click(),
                pause(70),
                moveTo(() -> orCentre(mc, browser(mc).tab("overview")), 22),
                pause(6),
                click(),
                pause(20));
    }

    /** Searches every structure's loot for an item, best chance first. */
    private static Director.Action itemSearch(Minecraft mc, String text) {
        return chain(
                moveTo(() -> orCentre(mc, browser(mc).searchBox()), 22),
                pause(6),
                click(),
                pause(4),
                erase(24, 1),
                type(text, 4),
                pause(90),
                shoot("tour_item_search"),
                erase(24, 1),
                pause(20));
    }

    // ------------------------------------------------------------------ helpers

    /** The marker for one chest on its own with a loot table, nearest the middle of the preview, or null. */
    private static int[] chestMarker(Minecraft mc) {
        int[] centre = browser(mc).viewportCentre();
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object m : (List<?>) get(browser(mc), "markerRects")) {
            List<?> containers = (List<?>) call(m, "containers");
            if (containers.size() != 1 || !(containers.get(0) instanceof StructureSnapshot.Container c) || c.lootTable() == null) {
                continue;
            }
            float x = (float) call(m, "x") + (int) call(m, "size") / 2f;
            float y = (float) call(m, "y") + (int) call(m, "size") / 2f;
            double distance = Math.hypot(x - centre[0], y - centre[1]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new int[]{Math.round(x), Math.round(y)};
            }
        }
        return best;
    }

    private static int[] orCentre(Minecraft mc, int[] p) {
        if (p != null) {
            return p;
        }
        JustEnoughStructures.LOGGER.info("TOUR missing target on {}", mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName());
        return new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2};
    }
}
