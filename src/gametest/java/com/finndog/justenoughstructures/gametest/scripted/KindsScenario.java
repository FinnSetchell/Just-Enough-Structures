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
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import com.finndog.justenoughstructures.loot.ItemKinds;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if emi {
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.screen.RecipeScreen;
//?}
//? if jei {
import com.finndog.justenoughstructures.compat.jei.JesJeiPlugin;
//?}
//? if rei {
import com.finndog.justenoughstructures.compat.rei.JesReiPlugin;
//?}

/**
 * Combined loot rows: an item's row shows each of its kinds in turn and its tooltip names them, in
 * the Loot tab, a chest's chances and Found in, while a chest's one roll stays as it was rolled. With
 * a recipe viewer, a looked-up potion or named book finds as many Found in structures rows as JES
 * has for it. Shoots a few frames of each for GIFs ("k" shots).
 */
final class KindsScenario {
    /** How long apart the frames for a GIF are, and how many. */
    private static final long FRAME_MILLIS = 350;
    private static final int FRAMES = 10;

    private KindsScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("ancient_city"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(moveTo(() -> browser(mc).tab("loot"), 8))
                .then(click())
                .then(until(() -> browser(mc).oddsRow(Items.ENCHANTED_BOOK).isPresent(), 600))
                .then(pause(10))
                .then(moveTo(() -> onIcon(browser(mc).oddsRow(Items.ENCHANTED_BOOK).orElse(new int[]{0, 0})), 10))
                .then(frames("k01_loot_book"))
                // A chest's one roll, as rolled, then its chances.
                .then(moveTo(() -> browser(mc).highlightRow("loot:minecraft:chests/ancient_city").orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(pause(20))
                .then(shoot("k02_chest_roll"))
                .then(moveTo(() -> orZero(browser(mc).popupLink("odds")), 10))
                .then(click())
                .then(until(() -> browser(mc).popupOddsRow(Items.ENCHANTED_BOOK).isPresent(), 200))
                .then(moveTo(() -> onIcon(browser(mc).popupOddsRow(Items.ENCHANTED_BOOK).orElse(new int[]{0, 0})), 10))
                .then(frames("k03_chest_chances_book"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(10))
                // Found in, opened from the Loot tab's book row.
                .then(moveTo(() -> browser(mc).oddsRow(Items.ENCHANTED_BOOK).orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(until(() -> browser(mc).foundInOpen(), 100))
                .then(pause(60))
                .then(shoot("k04_found_in_book"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(10));
        //? if >=1.21 {
        /*// Trial chambers' supply chest gives two tipped arrows and two potions, all as likely. Its table
        // opens in a popup on its chances.
        d.then(run(() -> browser(mc).openTable("minecraft:chests/trial_chambers/supply")))
                .then(until(() -> browser(mc).popupOddsRow(Items.POTION).isPresent(), 400))
                .then(pause(20))
                .then(moveTo(() -> onIcon(browser(mc).popupOddsRow(Items.POTION).orElse(new int[]{0, 0})), 10))
                .then(frames("k06_supply_potion"))
                .then(popupScrollTo(mc, Items.TIPPED_ARROW))
                .then(moveTo(() -> onIcon(browser(mc).popupOddsRow(Items.TIPPED_ARROW).orElse(new int[]{0, 0})), 10))
                .then(frames("k07_supply_tipped_arrow"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(10));
        *///?}
        d.then(pressKey(InputConstants.KEY_ESCAPE)).then(pause(20));
        //? if emi {
        if (JustEnoughStructures.modVersions().containsKey("emi")) {
            // The rows are checked against the finished index, which a viewer shows once it has loaded again for it.
            d.then(until(JesEmiPlugin::showsLatestIndex, 20 * 60 * 10))
                    .then(run(() -> JesEmiPlugin.showFoundIn(potion("water_breathing"))))
                    .then(pause(20))
                    // EMI opens on its first tab, brewing for a potion, so Found in structures is picked.
                    .then(run(() -> {
                        if (mc.screen instanceof RecipeScreen screen) {
                            EmiApi.getRecipeManager().getCategories().stream()
                                    .filter(c -> c.getId().toString().equals("justenoughstructures:found_in")).findFirst().ifPresent(screen::focusCategory);
                        }
                    }))
                    .then(until(() -> JesEmiPlugin.firstRow() != null, 100))
                    .then(moveTo(() -> {
                        int[] row = JesEmiPlugin.firstRow();
                        return row == null ? new int[]{0, 0} : new int[]{row[0] - 30 + FoundInRow.ITEM_X + 8, row[1]};
                    }, 10))
                    .then(frames("k05_emi_potion"))
                    .then(pressKey(InputConstants.KEY_ESCAPE))
                    .then(run(() -> check("EMI", JesEmiPlugin::foundInFor, false)));
        }
        //?}
        //? if jei {
        if (JustEnoughStructures.modVersions().containsKey("jei") && JeiIndexScenario.emiShowsJesRows()) {
            // JEI would never get the rows, so it would wait for ever: EMI's half checked them.
            JustEnoughStructures.LOGGER.info("In the kinds scenario: JEI is left out, as EMI shows JES's rows and doesn't let JEI have them");
        } else if (JustEnoughStructures.modVersions().containsKey("jei")) {
            // The rows are checked against the finished index, which a viewer shows once it has loaded again for it.
            d.then(until(JesJeiPlugin::showsLatestIndex, 20 * 60 * 10))
                    .then(run(() -> JesJeiPlugin.showFoundIn(potion("water_breathing"))))
                    .then(pause(60))
                    .then(frames("k05_jei_potion"))
                    .then(pressKey(InputConstants.KEY_ESCAPE))
                    .then(run(() -> check("JEI", JesJeiPlugin::foundInFor, false)));
        }
        //?}
        //? if rei {
        if (JustEnoughStructures.modVersions().containsKey("roughlyenoughitems")) {
            // The rows are checked against the finished index, which a viewer shows once it has loaded again for it.
            d.then(until(JesReiPlugin::showsLatestIndex, 20 * 60 * 10))
                    .then(run(() -> JesReiPlugin.showFoundIn(potion("water_breathing"))))
                    .then(pause(60))
                    .then(frames("k05_rei_potion"))
                    .then(pressKey(InputConstants.KEY_ESCAPE))
                    .then(run(() -> check("REI", JesReiPlugin::foundInFor, true)));
        }
        //?}
        return d;
    }

    /**
     * A point on a row's picture rather than its name, where the row hooks point, so the tooltip opens
     * beside the picture instead of over it and the picture can be seen changing.
     */
    private static int[] onIcon(int[] row) {
        return row[0] == 0 && row[1] == 0 ? row : new int[]{row[0] - 52, row[1]};
    }

    /** Scrolls the open popup's chances a little at a time, under the mouse, until the item's row is on screen. */
    private static Director.Action popupScrollTo(Minecraft mc, Item item) {
        return (d, frame) -> {
            if (browser(mc).popupOddsRow(item).isPresent()) {
                return true;
            }
            if (frame % 4 == 1) {
                d.scroll(-1);
            }
            return frame > 400;
        };
    }

    /** {@code FRAMES} shots of what's on screen, {@code FRAME_MILLIS} apart, named {@code <name>_<n>}. */
    private static Director.Action frames(String name) {
        List<Director.Action> steps = new ArrayList<>();
        for (int i = 0; i < FRAMES; i++) {
            steps.add(shoot(String.format("%s_%02d", name, i)));
            steps.add(waitMillis(FRAME_MILLIS));
        }
        return chain(steps.toArray(Director.Action[]::new));
    }

    private static Director.Action waitMillis(long millis) {
        long[] until = new long[1];
        return (d, frame) -> {
            if (frame == 1) {
                until[0] = System.currentTimeMillis() + millis;
            }
            return System.currentTimeMillis() >= until[0];
        };
    }

    /**
     * A viewer finds as many Found in structures rows for a looked-up stack as JES has rows that name
     * it, by the loot index: Water Breathing, which buried treasure gives, Regeneration II and the
     * Swift Sneak III book, which ancient cities give, and Luck, which few structures do. A row whose
     * potions or books the index couldn't read gives a plain stack, which EMI and JEI never match to
     * one potion; REI shows those for any ({@code lenient}). At least one row each for Water
     * Breathing and Swift Sneak, which vanilla gives.
     */
    private static void check(String viewer, ToLongFunction<ItemStack> rows, boolean lenient) {
        LootIndex index = ClientRequests.latestIndex();
        if (index == null) {
            throw new IllegalStateException("there's no loot index to check " + viewer + "'s rows against");
        }
        List<FoundInRecipe> own = FoundInRecipe.fromIndex(index, ResourceLocation::toString);
        List<String> problems = new ArrayList<>();
        for (ItemStack stack : List.of(potion("water_breathing"), potion("strong_regeneration"), potion("luck"), swiftSneak())) {
            long expected = own.stream().filter(r -> !r.isWaiting() && (lenient || !r.kinds().isEmpty()) && r.gives(stack)).count();
            long found = rows.applyAsLong(stack);
            JustEnoughStructures.LOGGER.info("In the kinds scenario: {} finds {} Found in structures rows for {} ({}), and JES has {}",
                    viewer, found, stack.getHoverName().getString(), ItemKinds.of(stack), expected);
            if (found != expected) {
                problems.add(found + " rows for " + ItemKinds.of(stack) + ", not " + expected);
            }
        }
        for (ItemStack stack : List.of(potion("water_breathing"), swiftSneak())) {
            if (rows.applyAsLong(stack) == 0) {
                problems.add("no row for " + ItemKinds.of(stack) + ", which vanilla structures give");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(viewer + " looks up potions and books wrongly: " + String.join("; ", problems));
        }
    }

    private static ItemStack potion(String potion) {
        return ItemKinds.stacks(Items.POTION, Set.of(ItemKinds.POTION + "minecraft:" + potion), FoundIn.registries()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no Potion of " + potion + " in this game"));
    }

    /** A Swift Sneak III book, as the viewers list it. */
    private static ItemStack swiftSneak() {
        String kind = ItemKinds.ENCHANTMENT + "minecraft:swift_sneak";
        return ItemKinds.stacks(Items.ENCHANTED_BOOK, Set.of(kind), FoundIn.registries()).stream()
                .filter(s -> ItemKinds.of(s).getOrDefault(kind, 0) == 3).findFirst()
                .orElseThrow(() -> new IllegalStateException("no Swift Sneak III book in this game"));
    }
}
