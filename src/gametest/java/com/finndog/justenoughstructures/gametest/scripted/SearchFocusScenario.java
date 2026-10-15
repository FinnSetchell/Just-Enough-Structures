package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

/**
 * Typing in the search, then clicking a structure in the list: the search lets go, so what's typed
 * next isn't added to it, as with any text box once you click somewhere else.
 */
final class SearchFocusScenario {
    private SearchFocusScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("igloo"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> browser(mc).searchBox(), 10))
                .then(click())
                .then(type("ruin", 1))
                .then(pause(10))
                .then(run(() -> {
                    if (!search(mc).isFocused() || !search(mc).getValue().equals("ruin")) {
                        throw new IllegalStateException("The search didn't take the typing: it says \"" + search(mc).getValue() + "\"");
                    }
                }))
                .then(moveTo(() -> browser(mc).structureRow(Ids.parse("ocean_ruin_warm")).orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).idle(), 600))
                .then(type("x", 1))
                .then(pause(5))
                .then(shoot("q01_search_let_go"))
                .then(run(() -> {
                    if (!Ids.parse("ocean_ruin_warm").equals(browser(mc).selectedStructure())) {
                        throw new IllegalStateException("The click in the list went to " + browser(mc).selectedStructure());
                    }
                    if (search(mc).isFocused() || !search(mc).getValue().equals("ruin")) {
                        throw new IllegalStateException("After a click in the list the search still takes typing: it says \""
                                + search(mc).getValue() + "\"");
                    }
                }));
        return d;
    }

    private static EditBox search(Minecraft mc) {
        return (EditBox) Reflect.get(browser(mc), "search");
    }
}
