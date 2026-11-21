package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.chain;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.record;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.skipIf;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.FlyInputs;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The preview spins only at its default view: when a structure opens, after Reset view and on a new
 * layout. Turning, panning, zooming or flying the camera stops it until Reset view, and Back to a
 * camera the player turned keeps it still. The layer slider, a chest's popup and a click whose mouse
 * wobbles a pixel leave it spinning, and the setting still turns it off. Every check is made with
 * the mouse away from the preview, as it never spins with the mouse over it.
 */
final class SpinHomeScenario {
    private static final ResourceLocation OUTPOST = Ids.parse("pillager_outpost");
    /** Small structures to jump to for Back, the first one in the list on screen. */
    private static final List<String> OTHERS = List.of("minecraft:igloo", "minecraft:swamp_hut", "minecraft:ruined_portal",
            "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:shipwreck", "minecraft:village_plains");

    private SpinHomeScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(OUTPOST);
        boolean spin = ClientState.spin;
        boolean markers = ClientState.markers;
        int[] layers = new int[1];
        StructureSnapshot.Container[] chest = new StructureSnapshot.Container[1];
        StructureViewport.Camera[] turned = new StructureViewport.Camera[1];
        ResourceLocation[] other = new ResourceLocation[1];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> Screens.ready(mc), 600))
                .then(run(() -> {
                    browser(mc).setSpin(true);
                    // The chest is clicked by its marker.
                    ClientState.markers = true;
                    layers[0] = view(mc).sliceY();
                    chest[0] = browser(mc).result().snapshot().containers().stream().filter(c -> !c.entity() && c.lootTable() != null)
                            .findFirst().orElseThrow(() -> new IllegalStateException("The pillager outpost has no chest with a loot table"));
                }))
                .then(spins(mc, true, "the preview didn't spin when the structure opened"))

                // The setting still turns it off.
                .then(run(() -> browser(mc).setSpin(false)))
                .then(spins(mc, false, "the preview spun with spinning turned off"))
                .then(run(() -> browser(mc).setSpin(true)))
                .then(spins(mc, true, "the preview didn't spin again when spinning was turned back on"))

                // The layer slider doesn't move the camera.
                .then(moveTo(() -> browser(mc).sliderAt(1f), 8))
                .then(dragTo(() -> browser(mc).sliderAt(0.5f), 10, InputConstants.MOUSE_BUTTON_LEFT))
                .then(check(() -> view(mc).sliceY() < layers[0], "dragging the layer slider didn't hide any layers"))
                .then(spins(mc, true, "the preview stopped spinning when the layer slider was moved"))
                .then(moveTo(() -> browser(mc).sliderAt(0.5f), 8))
                .then(dragTo(() -> browser(mc).sliderAt(1f), 10, InputConstants.MOUSE_BUTTON_LEFT))
                .then(check(() -> view(mc).sliceY() == layers[0], "dragging the layer slider back didn't show every layer"))

                // Nor does a chest: its popup turns the camera to it and back, which isn't the player moving it.
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(pause(5))
                .then(moveTo(() -> browser(mc).marker(chest[0].lootTable())
                        .orElseThrow(() -> new IllegalStateException("The chest has no marker in the preview")), 8))
                .then(pause(3))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(check(() -> browser(mc).containerOpen(), "a click on the chest's marker didn't open it"))
                .then(pause(20))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> !anyPopup(mc), 40))
                .then(pause(20))
                .then(spins(mc, true, "the preview stopped spinning after a chest's popup was opened and closed"))

                // Nor a click in the preview whose mouse wobbles a pixel on the way.
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(pause(3))
                .then(dragBy(1, 0, 2))
                .then(pause(20))
                .then(skipIf(() -> !anyPopup(mc), pressKey(InputConstants.KEY_ESCAPE)))
                .then(until(() -> !anyPopup(mc), 40))
                .then(pause(20))
                .then(spins(mc, true, "the preview stopped spinning after a click whose mouse wobbled a pixel"))

                // A new layout is the default view again.
                .then(moveTo(() -> browser(mc).button("reroll"), 8))
                .then(click())
                .then(pause(5))
                .then(until(() -> Screens.ready(mc), 600))
                .then(spins(mc, true, "the preview didn't spin on a new layout"))

                // Turning, panning, zooming and flying each stop it, until Reset view.
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(dragBy(80, 30, 15))
                .then(spins(mc, false, "the preview kept spinning after the camera was turned"))
                .then(reset(mc, "turning the camera"))
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(dragTo(() -> Screens.offset(browser(mc).viewportCentre(), 60, 20), 15, InputConstants.MOUSE_BUTTON_RIGHT))
                .then(spins(mc, false, "the preview kept spinning after the camera was panned"))
                .then(reset(mc, "panning the camera"))
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(wheel(2))
                .then(pause(3))
                .then(spins(mc, false, "the preview kept spinning after the camera was zoomed"))
                .then(reset(mc, "zooming the camera"))
                // Flying takes the real cursor without the run's hook on the camera's input, so it's left out without it.
                .then(skipIf(() -> !FlyInputs.hooked(), chain(
                        run(FlyInputs::clear),
                        moveTo(() -> browser(mc).button("fly"), 8),
                        click(),
                        pause(5),
                        check(() -> browser(mc).flying(), "the fly button didn't start flying"),
                        pressKey(InputConstants.KEY_ESCAPE),
                        pause(5),
                        check(() -> !browser(mc).flying(), "Escape didn't stop flying"),
                        spins(mc, false, "the preview spun after flying"),
                        reset(mc, "flying"),
                        run(FlyInputs::clear))))

                // Another structure spins when it opens, and Back to a camera the player turned keeps it still.
                .then(moveTo(() -> browser(mc).viewportCentre(), 8))
                .then(dragBy(80, 30, 15))
                .then(run(() -> turned[0] = viewport(mc).camera()))
                .then(moveTo(() -> {
                    other[0] = otherRow(mc);
                    int[] row = browser(mc).listRowsDrawn().get(other[0].toString());
                    return new int[]{(row[0] + row[2]) / 2, (row[1] + row[3]) / 2};
                }, 10))
                .then(click())
                .then(pause(5))
                .then(until(() -> other[0].equals(browser(mc).selectedId()) && Screens.ready(mc), 1200))
                .then(check(() -> other[0].equals(browser(mc).selectedId()), "a click on another structure's row didn't open it"))
                .then(spins(mc, true, "another structure didn't spin when it opened"))
                .then(moveTo(() -> browser(mc).backButton(), 8))
                .then(click())
                .then(pause(5))
                .then(until(() -> OUTPOST.equals(browser(mc).selectedId()) && Screens.ready(mc), 1200))
                .then(pause(5))
                .then(run(() -> {
                    StructureViewport.Camera now = viewport(mc).camera();
                    if (!OUTPOST.equals(browser(mc).selectedId()) || !now.equals(turned[0])) {
                        throw new IllegalStateException("Back didn't bring back the turned camera " + turned[0] + " on the outpost, but "
                                + now + " on " + browser(mc).selectedId());
                    }
                }))
                .then(spins(mc, false, "the preview spun after Back brought back a camera the player had turned"))
                .then(reset(mc, "Back"))
                .then(run(() -> {
                    ClientState.spin = spin;
                    ClientState.markers = markers;
                }));
        return d;
    }

    /**
     * About ten seconds for a GIF: the preview spinning on its own, turned with the mouse and staying
     * where it was left once the mouse is away, then spinning again after Reset view.
     */
    static Director clip(Minecraft mc) {
        JesScreen.startOn(OUTPOST);
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> Screens.ready(mc), 600))
                .then(run(() -> browser(mc).setSpin(true)))
                .then(moveTo(away(mc), 4))
                .then(pause(20))
                .then(record("spin_home"))
                .then(pause(60))
                .then(moveTo(() -> Screens.offset(browser(mc).viewportCentre(), 40, 0), 15))
                .then(pause(5))
                .then(dragBy(-120, 30, 30))
                .then(pause(5))
                .then(moveTo(away(mc), 15))
                .then(pause(60))
                .then(moveTo(() -> browser(mc).button("reset"), 20))
                .then(pause(5))
                .then(click())
                .then(pause(5))
                .then(moveTo(away(mc), 15))
                .then(pause(60));
        return d;
    }

    /**
     * Moves the mouse away from the preview, then checks whether it turns on its own over the next
     * second. The yaw only changes there by spinning.
     */
    private static Director.Action spins(Minecraft mc, boolean expected, String failure) {
        float[] yaw = new float[1];
        return chain(
                moveTo(away(mc), 8),
                pause(5),
                run(() -> yaw[0] = viewport(mc).camera().yaw()),
                pause(20),
                run(() -> {
                    float turned = Math.abs(viewport(mc).camera().yaw() - yaw[0]);
                    if (expected ? turned < 2f : turned > 0.01f) {
                        throw new IllegalStateException(failure + ": it turned " + turned + " degrees in a second with the mouse away");
                    }
                }));
    }

    /** Clicks Reset view, after which it spins again. */
    private static Director.Action reset(Minecraft mc, String after) {
        return chain(
                moveTo(() -> browser(mc).button("reset"), 8),
                click(),
                pause(3),
                spins(mc, true, "Reset view didn't start the preview spinning again after " + after));
    }

    /** The search box: away from the preview, where its spin goes on. */
    private static java.util.function.Supplier<int[]> away(Minecraft mc) {
        return () -> browser(mc).searchBox();
    }

    /** The first of {@link #OTHERS} with a row on screen in the list, or else the top row that isn't the outpost or a heading. */
    private static ResourceLocation otherRow(Minecraft mc) {
        Map<String, int[]> rows = browser(mc).listRowsDrawn();
        String pick = OTHERS.stream().filter(rows::containsKey).findFirst()
                .orElseGet(() -> rows.entrySet().stream().filter(e -> !e.getKey().startsWith("header:") && !e.getKey().equals(OUTPOST.toString()))
                        .min(Comparator.comparingInt(e -> e.getValue()[1])).map(Map.Entry::getKey)
                        .orElseThrow(() -> new IllegalStateException("The structure list shows no other structure to go to")));
        return ResourceLocation.tryParse(pick);
    }

    private static boolean anyPopup(Minecraft mc) {
        JesScreen b = browser(mc);
        return b != null && (Reflect.get(b, "popup") != null || Reflect.get(b, "spawnerPopup") != null || Reflect.get(b, "mobPopup") != null
                || Reflect.get(b, "blockPopup") != null);
    }

    private static StructureViewport viewport(Minecraft mc) {
        return (StructureViewport) Reflect.get(browser(mc), "viewport");
    }

    private static SnapshotView view(Minecraft mc) {
        return (SnapshotView) Reflect.get(browser(mc), "view");
    }

    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
