package com.finndog.justenoughstructures.gametest.fabric;

//? if emi {
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.ClientState.ChestLoot;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
//?}
import com.finndog.justenoughstructures.gametest.scripted.Director;
import net.minecraft.client.Minecraft;

/**
 * EMI Loot's chest loot pages with EMI and the real EMI Loot (-Pviewer=emi -Pemi_loot, with the dev
 * mods): clicking the desert pyramid table's name lists the structures that use it, and clicking
 * the top one opens the browser on it with the table picked in the Loot tab. The ruined portal table
 * lists every portal. Then with Just Enough Structures no rows are made, as EMI Loot's tab is hidden, and
 * with Other mods none either, so the name opens nothing, as it did before.
 */
final class EmiLootPagesScenario {
    private EmiLootPagesScenario() {
    }

    //? if emi {
    private static final ResourceLocation PYRAMID = Ids.parse("desert_pyramid");
    private static final ResourceLocation PYRAMID_TABLE = Ids.parse("chests/desert_pyramid");
    private static final ResourceLocation PORTAL_TABLE = Ids.parse("chests/ruined_portal");
    private static final String FOUND_IN = "justenoughstructures:found_in";
    private static final String EMI_LOOT = "emi_loot:chest_loot";

    static Director build(Minecraft mc) {
        if (!FabricLoader.getInstance().isModLoaded("emi") || !FabricLoader.getInstance().isModLoaded("emi_loot")) {
            throw new UnsupportedOperationException("needs EMI and EMI Loot, which the dev game has with -Pviewer=emi -Pemi_loot");
        }
        ChestLoot[] before = new ChestLoot[1];
        int[] loads = new int[1];
        // The structure of the top row, which is clicked.
        ResourceLocation[] top = new ResourceLocation[1];
        Director d = new Director(mc);
        d.then(run(() -> {
                    ClientState.load();
                    before[0] = ClientState.chestLoot;
                    JustEnoughStructures.LOGGER.info("EMI Loot's pages, with the chest loot setting {} to start with", before[0]);
                    ChestLootTabs.choose(ChestLoot.BOTH);
                }))
                .then(pause(40))
                .then(pressKey(InputConstants.KEY_E))
                .then(until(() -> mc.screen != null, 40))
                .then(until(() -> JesEmiPlugin.ready() && JesEmiPlugin.tableRows() > 0, 20 * 60 * 10))
                .then(pause(40))
                .then(run(() -> {
                    expect(JesEmiPlugin.ready() && JesEmiPlugin.tableRows() > 0, "EMI never loaded with rows for EMI Loot's pages");
                    loads[0] = JesEmiPlugin.loads();
                    JustEnoughStructures.LOGGER.info("EMI loaded {} times, the last with {} rows for EMI Loot's pages", loads[0], JesEmiPlugin.tableRows());
                    showPage(PYRAMID_TABLE);
                }))
                .then(toTitle(mc, PYRAMID_TABLE))
                .then(pause(20))
                .then(shoot("p01_emi_loot_desert_pyramid"))
                .then(click())
                .then(until(() -> showing(mc, FOUND_IN) && JesEmiPlugin.firstRow() != null, 100))
                .then(pause(20))
                .then(shoot("p02_desert_pyramid_structures"))
                .then(run(() -> {
                    expect(showing(mc, FOUND_IN), "Clicking the desert pyramid table's name opened " + describe(mc) + ", not Found in structures");
                    expect(JesEmiPlugin.firstRow() != null, "No row was drawn");
                    List<ResourceLocation> shown = JesEmiPlugin.structuresIn(pageRecipes(mc));
                    Set<ResourceLocation> expected = FoundIn.structuresUsing(PYRAMID_TABLE);
                    JustEnoughStructures.LOGGER.info("Clicking the desert pyramid table's name lists {}; the loot index has {}", shown, expected);
                    expect(shown.contains(PYRAMID), "The desert pyramid isn't listed");
                    expect(shown.size() == expected.size() && new HashSet<>(shown).equals(expected), "The page lists " + shown + ", not " + expected);
                    top[0] = shown.get(0);
                }))
                .then(moveTo(JesEmiPlugin::firstRow, 8))
                .then(pause(20))
                .then(shoot("p03_desert_pyramid_row"))
                .then(click())
                .then(until(() -> mc.screen instanceof JesScreen browser && browser.idle(), 20 * 60))
                .then(pause(40))
                .then(shoot("p04_browser_desert_pyramid"))
                .then(run(() -> {
                    expect(mc.screen instanceof JesScreen, "Clicking the row opened " + describe(mc) + ", not the browser");
                    JesScreen browser = (JesScreen) mc.screen;
                    JustEnoughStructures.LOGGER.info("The browser opened on {}, on its {} tab, with {} picked", browser.selectedId(), browser.detailsTab(),
                            browser.selectedTable());
                    expect(top[0].equals(browser.selectedId()), "Clicking the row for " + top[0] + " opened the browser on " + browser.selectedId());
                    expect("LOOT".equals(browser.detailsTab()), "The browser opened on its " + browser.detailsTab() + " tab");
                    expect(PYRAMID_TABLE.toString().equals(browser.selectedTable()), "The browser has " + browser.selectedTable() + " picked");
                    browser.onClose();
                }))
                .then(pause(20))
                .then(run(() -> {
                    expect(showing(mc, FOUND_IN), "Closing the browser went to " + describe(mc) + ", not back to EMI");
                    showPage(PORTAL_TABLE);
                }))
                .then(toTitle(mc, PORTAL_TABLE))
                .then(click())
                .then(until(() -> showing(mc, FOUND_IN) && JesEmiPlugin.firstRow() != null, 100))
                .then(pause(20))
                .then(shoot("p05_ruined_portal_structures"))
                .then(run(() -> {
                    expect(showing(mc, FOUND_IN), "Clicking the ruined portal table's name opened " + describe(mc) + ", not Found in structures");
                    List<ResourceLocation> rows = JesEmiPlugin.structuresIn(EmiApi.getRecipeManager().getRecipesByOutput(stackOf(page(PORTAL_TABLE))));
                    Set<ResourceLocation> expected = FoundIn.structuresUsing(PORTAL_TABLE);
                    JustEnoughStructures.LOGGER.info("The ruined portal table's name lists {} on its first page; EMI has rows for {}; the loot index has {}",
                            JesEmiPlugin.structuresIn(pageRecipes(mc)), rows, expected);
                    expect(expected.size() > 1, "The loot index has only " + expected + " using the ruined portal table");
                    expect(rows.size() == expected.size() && new HashSet<>(rows).equals(expected), "EMI has rows for " + rows + ", not " + expected);
                    expect(!JesEmiPlugin.structuresIn(pageRecipes(mc)).isEmpty(), "The page shows none of them");
                    expect(JesEmiPlugin.loads() == loads[0], "EMI loaded " + (JesEmiPlugin.loads() - loads[0]) + " more times");
                }));

        // With Just Enough Structures EMI Loot's page is hidden and no rows are made.
        d.then(choose(mc, ChestLoot.JUST_ENOUGH_STRUCTURES))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("With Just Enough Structures, EMI has {} rows for EMI Loot's pages, and its desert pyramid page is {}",
                            JesEmiPlugin.tableRows(), page(PYRAMID_TABLE) == null ? "hidden" : "shown");
                    expect(JesEmiPlugin.tableRows() == 0, "With Just Enough Structures, EMI has " + JesEmiPlugin.tableRows() + " rows for EMI Loot's pages");
                    expect(page(PYRAMID_TABLE) == null, "With Just Enough Structures, EMI Loot's desert pyramid page is still there");
                }));

        // With Other mods EMI Loot's page is there, but its name opens nothing.
        d.then(choose(mc, ChestLoot.OTHER_MODS))
                .then(pressKey(InputConstants.KEY_E))
                .then(pause(10))
                .then(run(() -> {
                    expect(JesEmiPlugin.tableRows() == 0, "With Other mods, EMI has " + JesEmiPlugin.tableRows() + " rows for EMI Loot's pages");
                    showPage(PYRAMID_TABLE);
                }))
                .then(toTitle(mc, PYRAMID_TABLE))
                .then(click())
                .then(pause(40))
                .then(shoot("p06_other_mods_after_click"))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("With Other mods, clicking the desert pyramid table's name leaves {}", describe(mc));
                    expect(showing(mc, EMI_LOOT), "With Other mods, clicking the table's name opened " + describe(mc));
                }))
                .then(run(() -> {
                    mc.setScreen(null);
                    ChestLootTabs.choose(before[0]);
                }));
        return d;
    }

    /** Shows EMI Loot's page for a table. */
    private static void showPage(ResourceLocation table) {
        EmiRecipe page = page(table);
        expect(page != null, "EMI Loot has no chest loot page for " + table);
        JustEnoughStructures.LOGGER.info("EMI Loot's page for {} is the recipe {}", table, page.getId());
        JesEmiPlugin.forgetFirstRow();
        EmiApi.displayRecipe(page);
    }

    /** Moves the mouse to EMI Loot's name of a table, once the page shows it. */
    private static Director.Action toTitle(Minecraft mc, ResourceLocation table) {
        return Director.chain(
                until(() -> titleOf(mc, table) != null, 100),
                run(() -> expect(titleOf(mc, table) != null, "EMI Loot's page for " + table + " never showed its name, but " + describe(mc))),
                moveTo(() -> titleOf(mc, table), 8));
    }

    /** Sets the chest loot setting with no screen open, and waits for EMI to load for it. */
    private static Director.Action choose(Minecraft mc, ChestLoot value) {
        int[] loads = new int[1];
        return Director.chain(
                run(() -> {
                    mc.setScreen(null);
                    loads[0] = JesEmiPlugin.loads();
                    ChestLootTabs.choose(value);
                }),
                until(() -> JesEmiPlugin.loads() > loads[0] && EmiReloadManager.isLoaded(), 20 * 60 * 3),
                pause(20),
                run(() -> expect(JesEmiPlugin.loads() > loads[0] && EmiReloadManager.isLoaded(), "EMI never loaded for " + value)));
    }

    /** EMI Loot's chest loot recipe for a table: the one whose stack, which its name stands for, is the table. Null without one. */
    private static EmiRecipe page(ResourceLocation table) {
        for (EmiRecipeCategory category : EmiApi.getRecipeManager().getCategories()) {
            if (!EMI_LOOT.equals(category.getId().toString())) {
                continue;
            }
            for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes(category)) {
                EmiStack stack = stackOf(recipe);
                if (stack != null && table.toString().equals(stack.getKey())) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /** The stack an EMI Loot chest loot recipe is for, its only input. */
    private static EmiStack stackOf(EmiRecipe recipe) {
        if (recipe == null || recipe.getInputs().isEmpty() || recipe.getInputs().get(0).getEmiStacks().isEmpty()) {
            return null;
        }
        return recipe.getInputs().get(0).getEmiStacks().get(0);
    }

    /** The recipes on the EMI page that's open, with where each is drawn. */
    private static List<WidgetGroup> groups(Minecraft mc) {
        if (!(mc.screen instanceof RecipeScreen screen)) {
            return List.of();
        }
        try {
            Field field = RecipeScreen.class.getDeclaredField("currentPage");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<WidgetGroup> page = (List<WidgetGroup>) field.get(screen);
            return page == null ? List.of() : List.copyOf(page);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Couldn't read EMI's recipe page", e);
        }
    }

    private static List<EmiRecipe> pageRecipes(Minecraft mc) {
        return groups(mc).stream().map(group -> group.recipe).toList();
    }

    /** Where EMI Loot's name of a table is drawn on the open page, or null if it isn't. */
    private static int[] titleOf(Minecraft mc, ResourceLocation table) {
        for (WidgetGroup group : groups(mc)) {
            EmiStack stack = stackOf(group.recipe);
            if (stack == null || !table.toString().equals(stack.getKey())) {
                continue;
            }
            for (Widget widget : group.widgets) {
                if (widget.getClass().getSimpleName().equals("InteractableTextWidget")) {
                    Bounds b = widget.getBounds();
                    return new int[]{group.x + b.x() + Math.min(b.width(), 60) / 2, group.y + b.y() + b.height() / 2};
                }
            }
        }
        return null;
    }

    /** Whether EMI's page is open on the tab with this id. */
    private static boolean showing(Minecraft mc, String tab) {
        return mc.screen instanceof RecipeScreen screen && screen.getFocusedCategory() != null
                && tab.equals(screen.getFocusedCategory().getId().toString());
    }

    private static String describe(Minecraft mc) {
        if (mc.screen instanceof RecipeScreen screen) {
            return "EMI's page on " + (screen.getFocusedCategory() == null ? "no tab" : screen.getFocusedCategory().getId());
        }
        return mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName();
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
    //?} else {
    /*static Director build(Minecraft mc) {
        throw new UnsupportedOperationException("needs EMI, which this version doesn't have");
    }
    *///?}
}
