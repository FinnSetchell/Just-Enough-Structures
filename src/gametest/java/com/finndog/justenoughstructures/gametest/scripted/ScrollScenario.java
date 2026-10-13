package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.editor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Scroll bars. In the browser, the structure list and the details are each dragged by their bar's
 * thumb, and a press on the list's bar picks no structure; then a loot table's chances in its popup
 * and the structures an item is found in, and a spawner with one mob, whose list has no bar. In Pack tools, the list of loot tables is dragged down by
 * its thumb, sent to its end by a press beside the thumb, left alone by the mouse once the button is
 * up, and moved by the wheel as before. In the loot editor, the item grid, the list under a field
 * being typed in and a list of choices, which a press on its bar leaves open. Each step says how
 * far the list is scrolled, as a bar that didn't move looks much like one that did.
 */
final class ScrollScenario {
    private ScrollScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        double[] was = new double[1];
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(run(() -> browser(mc).showLootTab()))
                .then(until(() -> browser(mc).scrollBar(false, 0) != null && browser(mc).scrollBar(true, 0) != null, 200))
                .then(pause(10))
                .then(run(() -> expect(browser(mc).scrolled(true) == 0, "The details didn't start at their top: " + browser(mc).scrolled(true))))
                // The structure list, which opens scrolled to the structure on show: up to its top by the bar, then down by the thumb.
                .then(moveTo(() -> orZero(browser(mc).scrollBar(false, 0.99f)), 10))
                .then(dragTo(() -> orZero(browser(mc).scrollBar(false, 0)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> expect(browser(mc).scrolled(false) == 0, "Dragging the structure list's bar to its top left the list at " + browser(mc).scrolled(false))))
                .then(moveTo(() -> orZero(browser(mc).scrollBar(false, 0.02f)), 10))
                .then(dragTo(() -> orZero(browser(mc).scrollBar(false, 0.6f)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> {
                    expect(browser(mc).scrolled(false) > 0, "Dragging the structure list's thumb down didn't scroll it");
                    expect(Ids.parse("desert_pyramid").equals(browser(mc).selectedId()), "Pressing the structure list's bar picked " + browser(mc).selectedId());
                }))
                .then(shoot("s10_list_dragged"))
                .then(moveTo(() -> orZero(browser(mc).scrollBar(true, 0.02f)), 10))
                .then(dragTo(() -> orZero(browser(mc).scrollBar(true, 0.7f)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> expect(browser(mc).scrolled(true) > 0, "Dragging the details' thumb down didn't scroll them")))
                .then(shoot("s11_details_dragged"))
                .then(run(() -> browser(mc).openTable("minecraft:chests/desert_pyramid")))
                .then(pause(30))
                .then(moveTo(() -> orZero(browser(mc).popupLink("odds")), 10))
                .then(click())
                .then(until(() -> browser(mc).popupBar(0) != null, 200))
                .then(drag("the popup's chances", along -> browser(mc).popupBar(along), () -> browser(mc).popupScrolled()))
                .then(shoot("s12_popup_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(5))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.IRON_INGOT))))
                // The loot index can still be building.
                .then(until(() -> browser(mc).popupBar(0) != null, 20 * 400))
                .then(pause(10))
                .then(drag("the structures iron is found in", along -> browser(mc).popupBar(along), () -> browser(mc).popupScrolled()))
                .then(shoot("s13_found_in_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(5))
                // A spawner with one mob, the stronghold's: its list fits, so it has no bar.
                .then(run(() -> browser(mc).select(Ids.parse("stronghold"))))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null && Ids.parse("stronghold").equals(browser(mc).selectedId()), 20 * 400))
                .then(run(() -> {
                    expect(browser(mc).result().succeeded() && !browser(mc).result().snapshot().spawners().isEmpty(), "The stronghold had no spawner to open");
                    browser(mc).openSpawnerPopup(browser(mc).result().snapshot().spawners().get(0));
                }))
                .then(pause(10))
                .then(shoot("s14_spawner_one_mob"))
                .then(run(() -> expect(browser(mc).popupBar(0) == null, "A spawner with one mob has a scroll bar")))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(5))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(run(() -> tools(mc).showSection("loot")))
                .then(until(() -> tools(mc).scrollBar(0, 0) != null, 100))
                .then(pause(10))
                .then(run(() -> expect(tools(mc).scrolled(0) == 0, "The list of loot tables didn't start at its top: " + tools(mc).scrolled(0))))
                .then(shoot("s00_top"))
                // By the thumb, from just under its top, half way down the bar.
                .then(moveTo(() -> orZero(tools(mc).scrollBar(0, 0.02f)), 10))
                .then(dragTo(() -> orZero(tools(mc).scrollBar(0, 0.5f)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> {
                    was[0] = tools(mc).scrolled(0);
                    expect(was[0] > 0, "Dragging the thumb down didn't scroll the list");
                }))
                .then(shoot("s01_dragged"))
                // The button is up, so the mouse moving on down the bar does nothing.
                .then(moveTo(() -> orZero(tools(mc).scrollBar(0, 0.97f)), 10))
                .then(pause(3))
                .then(run(() -> expect(tools(mc).scrolled(0) == was[0], "The list went on scrolling with the button up, from " + was[0] + " to " + tools(mc).scrolled(0))))
                // A press beside the thumb brings the thumb there.
                .then(click())
                .then(pause(5))
                .then(run(() -> {
                    expect(tools(mc).scrolled(0) > was[0], "A press near the bar's end left the list at " + tools(mc).scrolled(0) + ", from " + was[0]);
                    was[0] = tools(mc).scrolled(0);
                }))
                .then(shoot("s02_pressed_near_the_end"))
                .then(wheel(3))
                .then(pause(5))
                .then(run(() -> {
                    expect(tools(mc).scrolled(0) < was[0], "The wheel didn't move the list back up from " + was[0]);
                    was[0] = tools(mc).scrolled(0);
                }))
                // And back to the top by the thumb.
                .then(moveTo(() -> orZero(tools(mc).scrollBar(0, 0.9f)), 10))
                .then(dragTo(() -> orZero(tools(mc).scrollBar(0, 0)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> expect(tools(mc).scrolled(0) < was[0], "Dragging back up left the list at " + tools(mc).scrolled(0) + ", from " + was[0])))
                .then(shoot("s03_dragged_back"))
                // The loot editor, on the pyramid's second entry.
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, Ids.parse("chests/desert_pyramid"), "Desert Pyramid"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(20))
                .then(run(() -> editor(mc).pick(0, 1)))
                .then(pause(20))
                .then(run(() -> editor(mc).openItemPicker()))
                .then(until(() -> editor(mc).dropBar(0) != null, 100))
                .then(drag("the item grid", along -> editor(mc).dropBar(along), () -> editor(mc).dropScrolled()))
                .then(shoot("s20_item_grid_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(5))
                .then(moveTo(() -> orZero(editor(mc).fieldAt("pools.0.entries.1.name")), 8))
                .then(click())
                .then(until(() -> editor(mc).dropBar(0) != null, 100))
                .then(drag("the items listed under the field", along -> editor(mc).dropBar(along), () -> editor(mc).dropScrolled()))
                .then(shoot("s21_suggestions_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(5))
                .then(moveTo(() -> orZero(editor(mc).fieldAt("pools.0.entries.1.functions.0.function")), 8))
                .then(click())
                .then(until(() -> editor(mc).dropBar(0) != null, 100))
                .then(drag("the list of functions", along -> editor(mc).dropBar(along), () -> editor(mc).dropScrolled()))
                .then(run(() -> expect(editor(mc).dropBar(0) != null, "Pressing the list of functions' bar closed the list")))
                .then(shoot("s22_choices_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE));
        return d;
    }

    /**
     * Sends a list to its top by its bar, with a press near the bar's end and a drag up to its top,
     * then drags it down by the thumb, and says so if either did nothing. Lists that open already
     * scrolled to what's picked in them start anywhere.
     */
    private static Director.Action drag(String what, Function<Float, int[]> bar, DoubleSupplier scrolled) {
        return Director.chain(
                moveTo(() -> orZero(bar.apply(0.98f)), 10),
                dragTo(() -> orZero(bar.apply(0f)), 15, InputConstants.MOUSE_BUTTON_LEFT),
                pause(5),
                run(() -> expect(scrolled.getAsDouble() == 0, "Dragging its bar to the top left " + what + " at " + scrolled.getAsDouble())),
                moveTo(() -> orZero(bar.apply(0.02f)), 10),
                dragTo(() -> orZero(bar.apply(0.7f)), 20, InputConstants.MOUSE_BUTTON_LEFT),
                pause(5),
                run(() -> expect(scrolled.getAsDouble() > 0, "Dragging its bar's thumb down didn't scroll " + what)));
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
}
