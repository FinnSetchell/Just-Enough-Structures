package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.chain;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.ready;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tablePicker;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.UiClicks;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The controls the browser and Pack tools draw themselves, used with the mouse and keys. Each one
 * that does something should click once, like a vanilla button, and the narrator should read it
 * while the mouse is over it. What vanilla keeps quiet stays quiet: a tab that's open already, a
 * list row and a scroll bar. Every step is checked, and what went wrong is listed at the end.
 *
 * <p>The chest it changes through the table picker is put back with Undo, before any /reload.
 */
final class UiClicksScenario {
    private static final ResourceLocation OUTPOST = Ids.parse("pillager_outpost");
    private static final List<String> problems = new ArrayList<>();
    private static int before;
    private static double scrolledBefore;

    private UiClicksScenario() {
    }

    static Director build(Minecraft mc) {
        if (!UiClicks.hooked) {
            throw new UnsupportedOperationException("the click counter isn't hooked into this game");
        }
        problems.clear();
        // Four chests share the desert pyramid's table, so a chest's popup has arrows.
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> captured(mc), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(use(mc, "an Info panel tab", () -> browser(mc).tab("blocks"), tab("screen.justenoughstructures.tab.blocks"), 1))
                .then(expect("the Blocks tab didn't open", () -> "BLOCKS".equals(browser(mc).detailsTab())))
                .then(use(mc, "the Info panel tab that's open already", () -> browser(mc).tab("blocks"), null, 0))
                .then(run(() -> open(mc, c -> c.lootTable() != null)))
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(pause(10))
                .then(use(mc, "the chest popup's Chances tab", () -> orZero(browser(mc).popupLink("odds")),
                        tab("screen.justenoughstructures.popup_odds"), 1))
                .then(use(mc, "the chest popup's tab that's open already", () -> orZero(browser(mc).popupLink("odds")), null, 0))
                .then(use(mc, "the chest popup's next arrow", () -> browser(mc).button("next"),
                        button(Component.translatable("screen.justenoughstructures.popup_next")), 1))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> !browser(mc).containerOpen(), 20))
                .then(dragList(mc))
                .then(use(mc, "a row of the structure list", () -> browser(mc).structureRow(OUTPOST).orElse(new int[]{0, 0}), null, 0))
                .then(expect("clicking the outpost's row didn't select it", () -> OUTPOST.equals(browser(mc).selectedId())))
                // The outpost's chest is in a template, so Pack tools can point it at another table.
                .then(until(() -> captured(mc) && browser(mc).result().snapshot().structureId().equals(OUTPOST), 600))
                .then(run(() -> open(mc, c -> c.source() != null)))
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(pause(10))
                .then(use(mc, "the chest popup's Pack tools shortcut", () -> orZero(browser(mc).popupLink("tools_container")),
                        button(Component.translatable("screen.justenoughstructures.tools.open_container")), 1))
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 400))
                .then(pause(20))
                .then(use(mc, "Pack tools' Change button", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.container.change"))),
                        button(Component.translatable("screen.justenoughstructures.container.change")), 1))
                .then(until(() -> tablePicker(mc) != null, 40))
                .then(until(() -> tablePicker(mc).ready(), 3600))
                // The full id is picked even before the loot index has it.
                .then(type("minecraft:chests/buried_treasure", 1))
                .then(run(() -> tablePicker(mc).pick(0)))
                .then(press(mc, "Enter in the table picker", InputConstants.KEY_RETURN, 1))
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(20))
                .then(use(mc, "Pack tools' Undo button", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.container.undo"))),
                        button(Component.translatable("screen.justenoughstructures.container.undo")), 1))
                .then(pause(10))
                .then(use(mc, "a Pack tools section", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.tools.section.loot"))),
                        tab("screen.justenoughstructures.tools.section.loot"), 1))
                .then(pause(10))
                .then(use(mc, "the Pack tools section that's open already", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.tools.section.loot"))),
                        selected("screen.justenoughstructures.tools.section.loot"), 0))
                .then(use(mc, "a Loot tables filter", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.tools.filter.all"))),
                        tab("screen.justenoughstructures.tools.filter.all"), 1))
                .then(pause(5))
                .then(use(mc, "the Loot tables filter that's chosen already", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.tools.filter.all"))),
                        selected("screen.justenoughstructures.tools.filter.all"), 0))
                .then(shoot("u01_loot_section"))
                .then(use(mc, "Pack tools' way back to the browser", () -> orZero(tools(mc).buttonAt(text("screen.justenoughstructures.tools.browser"))),
                        button(Component.translatable("screen.justenoughstructures.tools.browser")), 1))
                .then(until(() -> browser(mc) != null, 40))
                .then(run(() -> {
                    if (!problems.isEmpty()) {
                        throw new IllegalStateException(problems.size() + " problems: " + String.join("; ", problems));
                    }
                    JustEnoughStructures.LOGGER.info("UICLICKS every control clicked as it should and was narrated");
                }));
        return d;
    }

    /**
     * Moves to a control, checks the narrator would read {@code narrated} while the mouse is on it
     * (unless that's null), then clicks it and checks it clicked {@code clicks} times.
     */
    private static Director.Action use(Minecraft mc, String name, Supplier<int[]> where, String narrated, int clicks) {
        return chain(
                moveTo(where, 8),
                pause(5),
                run(() -> {
                    if (narrated != null) {
                        String said = narration(mc);
                        JustEnoughStructures.LOGGER.info("UICLICKS {}: the narrator would read \"{}\"", name, said);
                        if (!said.contains(narrated)) {
                            problems.add(name + " isn't narrated as \"" + narrated + "\" (it reads \"" + said + "\")");
                        }
                    }
                    before = UiClicks.count();
                }),
                click(),
                pause(3),
                counted(name, clicks));
    }

    /** Presses a key and checks it clicked {@code clicks} times. */
    private static Director.Action press(Minecraft mc, String name, int key, int clicks) {
        return chain(run(() -> before = UiClicks.count()), pressKey(key), pause(3), counted(name, clicks));
    }

    /** Drags the structure list's scroll bar, which should scroll it without a click. */
    private static Director.Action dragList(Minecraft mc) {
        return chain(
                moveTo(() -> orZero(browser(mc).scrollBar(false, 0.03f)), 8),
                run(() -> {
                    before = UiClicks.count();
                    scrolledBefore = browser(mc).scrolled(false);
                }),
                dragTo(() -> orZero(browser(mc).scrollBar(false, 0.5f)), 10, InputConstants.MOUSE_BUTTON_LEFT),
                pause(3),
                counted("the structure list's scroll bar", 0),
                expect("dragging the structure list's scroll bar didn't scroll it", () -> browser(mc).scrolled(false) > scrolledBefore));
    }

    private static Director.Action counted(String name, int clicks) {
        return run(() -> {
            int counted = UiClicks.count() - before;
            JustEnoughStructures.LOGGER.info("UICLICKS {}: {} clicks", name, counted);
            if (counted != clicks) {
                problems.add(name + " clicked " + counted + " times, not " + clicks);
            }
        });
    }

    private static Director.Action expect(String problem, BooleanSupplier holds) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                problems.add(problem);
            }
        });
    }

    private static boolean captured(Minecraft mc) {
        return ready(mc) && browser(mc).result().succeeded();
    }

    /** What the narrator would read on the screen that's open. */
    private static String narration(Minecraft mc) {
        if (browser(mc) != null) {
            return browser(mc).narration();
        }
        return tools(mc) != null ? tools(mc).narration() : "";
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** How vanilla narrates a tab, one that's open, and a button with this label. */
    private static String tab(String key) {
        return Component.translatable("gui.narrate.tab", Component.translatable(key)).getString();
    }

    private static String selected(String key) {
        return Component.translatable("narrator.select", Component.translatable("gui.narrate.tab", Component.translatable(key))).getString();
    }

    private static String button(Component label) {
        return Component.translatable("gui.narrate.button", label).getString();
    }

    /** Opens the popup of the first chest that matches. */
    private static void open(Minecraft mc, Predicate<StructureSnapshot.Container> which) {
        for (StructureSnapshot.Container c : browser(mc).result().snapshot().containers()) {
            if (which.test(c)) {
                browser(mc).openContainer(c);
                return;
            }
        }
        problems.add("the structure has no chest for the next step");
    }
}
