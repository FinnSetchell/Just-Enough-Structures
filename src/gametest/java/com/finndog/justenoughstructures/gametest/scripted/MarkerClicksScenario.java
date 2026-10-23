package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.chain;
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
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The preview zoomed in on a chest, then moved so the chest's marker lands partly over the preview's
 * left edge, then wholly over a row of the structure list. Only the part of a marker inside the
 * preview may answer the mouse: outside it there's no tooltip for the chest, and a click on the row
 * picks that structure instead of opening the chest. The marker is first clicked inside the preview,
 * so a marker that isn't where the scenario thinks can't pass for one that doesn't answer.
 */
final class MarkerClicksScenario {
    /** How far the camera is from the chest's marker, close enough for markers at their biggest. */
    private static final float CLOSE = 8f;

    private MarkerClicksScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("pillager_outpost"));
        StructureSnapshot.Container[] chest = new StructureSnapshot.Container[1];
        boolean[] markers = new boolean[1];
        int[] size = new int[1];
        ResourceLocation[] row = new ResourceLocation[1];
        ResourceLocation[] before = new ResourceLocation[1];
        List<String> problems = new ArrayList<>();
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> captured(mc), 600))
                .then(run(() -> {
                    JesScreen browser = browser(mc);
                    browser.setSpin(false);
                    browser.showInfoTab();
                    markers[0] = ClientState.markers;
                    ClientState.markers = true;
                    if (!(Boolean) Reflect.get(browser, "sides")) {
                        throw new IllegalStateException("The browser has no structure list beside the preview, which this needs");
                    }
                    chest[0] = browser.result().snapshot().containers().stream().filter(c -> !c.entity() && c.lootTable() != null).findFirst()
                            .orElseThrow(() -> new IllegalStateException("The pillager outpost has no chest with a loot table"));
                    before[0] = browser.selectedId();
                }))

                // Inside the preview the marker answers the mouse, and a click on it opens the chest.
                .then(aim(mc, chest, () -> {
                    int[] r = rect(mc);
                    return new int[]{r[0] + r[2] / 2 - 60, r[1] + r[3] / 2};
                }))
                .then(run(() -> {
                    size[0] = (Integer) Reflect.call(browser(mc), "markerSize");
                    if (size[0] < 8) {
                        throw new IllegalStateException("Markers are " + size[0] + " pixels across, too small to aim at halves of");
                    }
                }))
                .then(moveTo(() -> probe(mc, chest[0], size[0], 0), 8))
                .then(pause(4))
                .then(run(() -> {
                    int[] at = probe(mc, chest[0], size[0], 0);
                    int[] drawn = browser(mc).marker(chest[0].lootTable()).orElseThrow(
                            () -> new IllegalStateException("The chest has no marker in the middle of the preview"));
                    if (Math.abs(drawn[0] - at[0]) > 1 || Math.abs(drawn[1] - at[1]) > 1) {
                        throw new IllegalStateException("The chest's marker is drawn around " + drawn[0] + "," + drawn[1]
                                + ", not around " + at[0] + "," + at[1] + " where the scenario aims");
                    }
                    if (!answers(mc, chest[0])) {
                        throw new IllegalStateException("The chest's marker in the middle of the preview didn't answer the mouse, but "
                                + browser(mc).previewHover());
                    }
                }))
                .then(click())
                .then(until(() -> popupOpen(mc), 40))
                .then(run(() -> {
                    if (!popupOpen(mc)) {
                        throw new IllegalStateException("A click on the chest's marker in the middle of the preview didn't open its popup");
                    }
                }))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> !popupOpen(mc), 40))
                .then(pause(30))

                // Partly over the preview's left edge: still drawn, answering inside the preview only.
                .then(aim(mc, chest, () -> {
                    int[] r = rect(mc);
                    return new int[]{r[0] + 1, r[1] + r[3] / 2};
                }))
                .then(moveTo(() -> probe(mc, chest[0], size[0], size[0] / 4), 8))
                .then(pause(4))
                .then(run(() -> {
                    if (browser(mc).marker(chest[0].lootTable()).isEmpty()) {
                        problems.add("a marker partly inside the preview wasn't drawn");
                    }
                    if (!answers(mc, chest[0])) {
                        problems.add("a marker partly inside the preview didn't answer the mouse on its inside half, but " + browser(mc).previewHover());
                    }
                }))
                .then(moveTo(() -> probe(mc, chest[0], size[0], -size[0] / 4), 4))
                .then(pause(4))
                .then(shoot("mk01_marker_on_edge"))
                .then(run(() -> {
                    if (browser(mc).previewHover() != null) {
                        problems.add("the half of a marker outside the preview answered the mouse with " + browser(mc).previewHover());
                    }
                }))

                // Wholly outside, over a row of the structure list.
                .then(run(() -> {
                    for (Object id : (List<?>) Reflect.call(Reflect.get(browser(mc), "list"), "visible")) {
                        if (row[0] == null && !id.equals(before[0]) && browser(mc).structureRow((ResourceLocation) id).isPresent()) {
                            row[0] = (ResourceLocation) id;
                        }
                    }
                    if (row[0] == null) {
                        throw new IllegalStateException("The structure list shows no row but the pillager outpost's");
                    }
                }))
                .then(aim(mc, chest, () -> {
                    int[] centre = rowCentre(mc, row[0]);
                    return new int[]{centre[0], centre[1] + size[0] / 2};
                }))
                .then(run(() -> {
                    int[] r = rect(mc);
                    float[] foot = onScreen(mc, chest[0]);
                    if (foot[0] + size[0] / 2f >= r[0]) {
                        throw new IllegalStateException("The row is too close to the preview for the marker to be wholly outside it");
                    }
                }))
                .then(moveTo(() -> rowCentre(mc, row[0]), 10))
                .then(pause(4))
                .then(shoot("mk02_marker_over_list"))
                .then(run(() -> {
                    if (browser(mc).marker(chest[0].lootTable()).isPresent()) {
                        problems.add("a marker wholly outside the preview was still drawn");
                    }
                    if (browser(mc).previewHover() != null) {
                        problems.add("over the structure list the preview answered the mouse with " + browser(mc).previewHover());
                    }
                }))
                .then(click())
                .then(until(() -> popupOpen(mc) || !before[0].equals(browser(mc).selectedId()), 40))
                .then(pause(4))
                .then(shoot("mk03_after_list_click"))
                .then(run(() -> {
                    if (popupOpen(mc)) {
                        problems.add("a click on the structure list opened the popup of the chest whose marker would be under it");
                    }
                    if (!row[0].equals(browser(mc).selectedId())) {
                        problems.add("a click on the row for " + row[0] + " left " + browser(mc).selectedId() + " selected");
                    }
                    ClientState.markers = markers[0];
                    if (!problems.isEmpty()) {
                        throw new IllegalStateException("Markers outside the preview still took the mouse: " + String.join("; ", problems));
                    }
                    JustEnoughStructures.LOGGER.info("MARKERCLICKS a {} pixel marker answered inside the preview only, and the list row under one picked {}",
                            size[0], row[0]);
                }));
        return d;
    }

    /**
     * Moves the camera close to the chest's marker, looking at the point it stands on, then pans so that
     * point lands on {@code target}. That point is the marker's bottom middle. Panning moves whatever is
     * at the camera's focus by exactly the pixels asked, so it lands there exactly.
     */
    private static Director.Action aim(Minecraft mc, StructureSnapshot.Container[] chest, Supplier<int[]> target) {
        return chain(
                run(() -> {
                    StructureViewport.Camera now = viewport(mc).camera();
                    float[] foot = foot(chest[0]);
                    viewport(mc).setCamera(new StructureViewport.Camera(now.yaw(), now.pitch(), CLOSE, foot[0], foot[1], foot[2]));
                }),
                pause(2),
                run(() -> {
                    int[] to = target.get();
                    float[] at = onScreen(mc, chest[0]);
                    viewport(mc).pan(to[0] - at[0], to[1] - at[1]);
                }),
                pause(2),
                run(() -> {
                    int[] to = target.get();
                    float[] at = onScreen(mc, chest[0]);
                    if (Math.abs(at[0] - to[0]) > 1 || Math.abs(at[1] - to[1]) > 1) {
                        throw new IllegalStateException("The chest's marker went to " + at[0] + "," + at[1] + ", not " + to[0] + "," + to[1]);
                    }
                }));
    }

    /** The point a container's marker stands on, as the browser places one marking it alone. */
    private static float[] foot(StructureSnapshot.Container chest) {
        return new float[]{chest.pos().getX() + 0.5f, chest.pos().getY() + 1.1f, chest.pos().getZ() + 0.5f};
    }

    /** Where that point is on screen. */
    private static float[] onScreen(Minecraft mc, StructureSnapshot.Container chest) {
        float[] foot = foot(chest);
        return viewport(mc).project(foot[0], foot[1], foot[2])
                .orElseThrow(() -> new IllegalStateException("The chest's marker is behind the camera"));
    }

    /** A point halfway up the chest's marker, {@code dx} right of its middle. */
    private static int[] probe(Minecraft mc, StructureSnapshot.Container chest, int size, int dx) {
        float[] at = onScreen(mc, chest);
        return new int[]{Math.round(at[0]) + dx, Math.round(at[1] - size / 2f)};
    }

    /** Whether the preview answered the mouse with the chest. */
    private static boolean answers(Minecraft mc, StructureSnapshot.Container chest) {
        StructureViewport.Hit hit = browser(mc).previewHover();
        return hit != null && hit.pos().equals(chest.pos());
    }

    private static int[] rowCentre(Minecraft mc, ResourceLocation id) {
        return browser(mc).structureRow(id).orElseThrow(() -> new IllegalStateException("The row for " + id + " isn't showing"));
    }

    /** The preview's left, top, width and height. */
    private static int[] rect(Minecraft mc) {
        JesScreen browser = browser(mc);
        return new int[]{Reflect.getInt(browser, "viewX"), Reflect.getInt(browser, "viewY"), Reflect.getInt(browser, "viewW"), Reflect.getInt(browser, "viewH")};
    }

    private static boolean popupOpen(Minecraft mc) {
        return browser(mc) != null && Reflect.get(browser(mc), "popup") != null;
    }

    private static StructureViewport viewport(Minecraft mc) {
        return (StructureViewport) Reflect.get(browser(mc), "viewport");
    }

    private static boolean captured(Minecraft mc) {
        JesScreen browser = browser(mc);
        return browser != null && browser.idle() && browser.result() != null && browser.result().succeeded();
    }
}
