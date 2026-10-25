package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Items structures place themselves, in the browser. Searching "$elytra" lists the End city, whose
 * ships have one in an item frame, and the elytra's Found in popup lists it as placed. Clicking the
 * popup's first row opens that structure, which works for a structure with no loot table to pick.
 * What every structure places is logged, to look over what other mods' structures add.
 */
final class PlacedScenario {
    private static final ResourceLocation END_CITY = Ids.parse("end_city");

    private PlacedScenario() {
    }

    static Director build(Minecraft mc) {
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> FoundIn.ready() && !ClientRequests.indexFilling(), 20 * 60 * 15))
                .then(run(() -> {
                    expect(FoundIn.ready() && !ClientRequests.indexFilling(), "The finished loot index never came");
                    Map<ResourceLocation, Set<ResourceLocation>> placed = ClientRequests.latestIndex().placedByStructure();
                    JustEnoughStructures.LOGGER.info("PLACED {} structures place items themselves: {}", placed.size(),
                            placed.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
                    expect(FoundIn.places(END_CITY, Items.ELYTRA), "The End city isn't listed as placing an elytra");
                }))
                .then(moveTo(() -> browser(mc).searchBox(), 6))
                .then(click())
                .then(type("$elytra", 1))
                .then(pause(10))
                .then(run(() -> expect(browser(mc).structureRow(END_CITY).isPresent(), "Searching $elytra doesn't list the End city")))
                .then(shoot("p01_search_elytra"))
                .then(run(() -> browser(mc).openFoundIn(new ItemStack(Items.ELYTRA))))
                .then(until(() -> browser(mc).foundInRow(0).isPresent(), 20 * 30))
                .then(pause(10))
                .then(shoot("p02_found_in_elytra"))
                .then(moveTo(() -> browser(mc).foundInRow(0).orElse(new int[]{0, 0}), 6))
                .then(click())
                .then(until(() -> browser(mc).selectedId() != null, 20 * 10))
                .then(run(() -> {
                    ResourceLocation opened = browser(mc).selectedId();
                    JustEnoughStructures.LOGGER.info("PLACED the elytra's first Found in row opened {}, listed as placing it: {}", opened,
                            opened != null && FoundIn.places(opened, Items.ELYTRA));
                    expect(opened != null && FoundIn.structuresFor(Items.ELYTRA).containsKey(opened), "The first row opened " + opened);
                }))
                .then(until(() -> browser(mc).idle(), 600))
                .then(pause(10))
                .then(shoot("p03_first_row_opened"))
                .then(pressKey(InputConstants.KEY_ESCAPE));
        return d;
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
}
