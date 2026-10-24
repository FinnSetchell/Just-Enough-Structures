package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.ClientState.ChestLoot;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.finndog.justenoughstructures.compat.jei.JesJeiPlugin;
import com.finndog.justenoughstructures.compat.rei.JesReiPlugin;
import com.finndog.justenoughstructures.gametest.scripted.Director;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if emi {
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
//?}

/**
 * The chest loot setting in whichever recipe viewer the dev runtime has (-Pviewer=emi, rei, or JEI by
 * default, all only with the dev mods): a saddle's page with each of Both, Just Enough Structures and
 * Other mods, then Both again. The Found in structures tab is there or not as the setting says, and
 * with EMI Loot in the game too (-Pemi_loot, EMI only) its chest loot tab is hidden or not. EMI
 * reloads for each change; JEI and REI show it at once.
 */
final class ChestLootTabsScenario {
    private static final String FOUND_IN = "justenoughstructures:found_in";
    private static final String EMI_LOOT = "emi_loot:chest_loot";

    private ChestLootTabsScenario() {
    }

    static Director build(Minecraft mc) {
        boolean emi = FabricLoader.getInstance().isModLoaded("emi");
        boolean rei = !emi && FabricLoader.getInstance().isModLoaded("roughlyenoughitems");
        if (!emi && !rei && !FabricLoader.getInstance().isModLoaded("jei")) {
            throw new UnsupportedOperationException("needs EMI, JEI or REI, which the dev game only has with its dev mods");
        }
        boolean emiLoot = FabricLoader.getInstance().isModLoaded("emi_loot");
        String name = emi ? "emi" : rei ? "rei" : "jei";
        // When the viewer is ready, whether it shows Found in structures, and how it opens a saddle's page.
        // A viewer that may not be installed is called from a lambda, as a method reference loads its class at once.
        BooleanSupplier ready = rei ? JesReiPlugin::ready : () -> ClientRequests.indexReady() && JesJeiPlugin.foundInShown();
        Supplier<Boolean> foundInShown = rei ? JesReiPlugin::foundInShown : () -> JesJeiPlugin.foundInShown();
        Supplier<Set<String>> saddleTabs = () -> null;
        Consumer<ItemStack> show = rei ? JesReiPlugin::showFoundIn : stack -> JesJeiPlugin.showRecipesFor(stack);
        BooleanSupplier[] shown = {() -> true};
        //? if emi {
        if (emi) {
            ready = JesEmiPlugin::ready;
            foundInShown = () -> JesEmiPlugin.tabsFor(new ItemStack(Items.SADDLE)).contains(FOUND_IN);
            saddleTabs = () -> JesEmiPlugin.tabsFor(new ItemStack(Items.SADDLE));
            show = JesEmiPlugin::showFoundIn;
        }
        //?}
        BooleanSupplier viewerReady = ready;
        Supplier<Boolean> ownShown = foundInShown;
        Supplier<Set<String>> tabs = saddleTabs;
        Consumer<ItemStack> showPage = show;
        ChestLoot[] before = new ChestLoot[1];
        Director d = new Director(mc);
        d.then(run(() -> {
                    ClientState.load();
                    before[0] = ClientState.chestLoot;
                    JustEnoughStructures.LOGGER.info("Chest loot tabs in {}, EMI Loot {}, setting {} to start with", name,
                            emiLoot ? "installed" : "not installed", before[0]);
                    ChestLootTabs.choose(ChestLoot.BOTH);
                }))
                .then(pause(40))
                .then(pressKey(InputConstants.KEY_E))
                .then(until(() -> mc.screen != null, 40))
                .then(until(viewerReady, 20 * 60 * 10))
                .then(pause(40));
        ChestLoot[] order = {ChestLoot.BOTH, ChestLoot.JUST_ENOUGH_STRUCTURES, ChestLoot.OTHER_MODS, ChestLoot.BOTH};
        for (int i = 0; i < order.length; i++) {
            ChestLoot value = order[i];
            String shot = "c" + (i + 1) + "_" + name + "_" + value.key() + (i > 0 && value == ChestLoot.BOTH ? "_again" : "");
            d.then(run(() -> {
                        mc.setScreen(null);
                        shown[0] = () -> true;
                        //? if emi {
                        if (emi) {
                            int now = JesEmiPlugin.loads();
                            boolean reloads = value != ClientState.chestLoot;
                            shown[0] = () -> !reloads || JesEmiPlugin.loads() > now && dev.emi.emi.runtime.EmiReloadManager.isLoaded();
                        }
                        //?}
                        ChestLootTabs.choose(value);
                    }))
                    .then(until(() -> shown[0].getAsBoolean(), 20 * 60 * 3))
                    .then(pause(20))
                    .then(pressKey(InputConstants.KEY_E))
                    .then(pause(10))
                    .then(run(() -> showPage.accept(new ItemStack(Items.SADDLE))))
                    .then(pause(60))
                    .then(shoot(shot))
                    .then(run(() -> {
                        expect(shown[0].getAsBoolean(), "The viewer never showed the change to " + value);
                        boolean own = ownShown.get();
                        Set<String> saddle = tabs.get();
                        JustEnoughStructures.LOGGER.info("With {}, Found in structures is {}; a saddle's tabs: {}", value, own ? "shown" : "hidden",
                                saddle != null ? saddle : "not known in this viewer");
                        expect(own == (value != ChestLoot.OTHER_MODS), "With " + value + " Found in structures is " + (own ? "shown" : "hidden"));
                        if (saddle != null && emiLoot) {
                            boolean theirs = saddle.contains(EMI_LOOT);
                            expect(theirs == (value != ChestLoot.JUST_ENOUGH_STRUCTURES), "With " + value + " EMI Loot's chest loot tab is "
                                    + (theirs ? "shown" : "hidden"));
                        }
                    }));
        }
        d.then(run(() -> {
            mc.setScreen(null);
            ChestLootTabs.choose(before[0]);
        }));
        return d;
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
}
