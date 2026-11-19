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
 * Command only in whichever recipe viewer the dev runtime has (-Pviewer=emi, rei, or JEI by default,
 * all only with the dev mods), with Just Enough Structures chosen for chest loot, which hides other
 * mods' chest loot tabs. A saddle's page with Command only off, on, then off again. With it on, the
 * Found in structures tab is gone, and with EMI Loot in the game too (-Pemi_loot, EMI only) its chest
 * loot tab is back, as if JES weren't there. Off again, both are as they were. EMI reloads for each
 * change; JEI and REI show it at once. The choice and the setting are put back as they were at the end.
 */
final class CommandOnlyViewersScenario {
    private static final String FOUND_IN = "justenoughstructures:found_in";
    private static final String EMI_LOOT = "emi_loot:chest_loot";

    private CommandOnlyViewersScenario() {
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
        ChestLoot[] chosenBefore = new ChestLoot[1];
        boolean[] commandOnlyBefore = new boolean[1];
        BooleanSupplier[] shown = {() -> true};
        Director d = new Director(mc);
        // Command only goes off first, so the viewer has the structures in it to start with.
        d.then(run(() -> {
                    ClientState.load();
                    chosenBefore[0] = ClientState.chestLoot;
                    commandOnlyBefore[0] = ClientState.commandOnly;
                    JustEnoughStructures.LOGGER.info("Command only in {}, EMI Loot {}, chest loot {} and Command only {} to start with", name,
                            emiLoot ? "installed" : "not installed", chosenBefore[0], commandOnlyBefore[0] ? "on" : "off");
                    shown[0] = change(emi, () -> {
                        ChestLootTabs.setCommandOnly(false);
                        ChestLootTabs.choose(ChestLoot.JUST_ENOUGH_STRUCTURES);
                    });
                }))
                .then(until(() -> shown[0].getAsBoolean(), 20 * 60 * 3))
                .then(pause(40))
                .then(pressKey(InputConstants.KEY_E))
                .then(until(() -> mc.screen != null, 40))
                .then(until(viewerReady, 20 * 60 * 10))
                .then(pause(40));
        boolean[] order = {false, true, false};
        String[] shots = {"o1_" + name + "_jes_chosen", "o2_" + name + "_command_only", "o3_" + name + "_command_only_off_again"};
        for (int i = 0; i < order.length; i++) {
            boolean on = order[i];
            String shot = shots[i];
            String state = "With Just Enough Structures chosen and Command only " + (on ? "on" : "off");
            d.then(run(() -> {
                        mc.setScreen(null);
                        shown[0] = change(emi, () -> ChestLootTabs.setCommandOnly(on));
                    }))
                    .then(until(() -> shown[0].getAsBoolean(), 20 * 60 * 3))
                    .then(pause(20))
                    .then(pressKey(InputConstants.KEY_E))
                    .then(pause(10))
                    .then(run(() -> showPage.accept(new ItemStack(Items.SADDLE))))
                    .then(pause(60))
                    .then(shoot(shot))
                    .then(run(() -> {
                        expect(shown[0].getAsBoolean(), "The viewer never showed Command only going " + (on ? "on" : "off"));
                        boolean own = ownShown.get();
                        Set<String> saddle = tabs.get();
                        JustEnoughStructures.LOGGER.info("{}, Found in structures is {}; a saddle's tabs: {}", state, own ? "shown" : "hidden",
                                saddle != null ? saddle : "not known in this viewer");
                        expect(own == !on, state + ", Found in structures is " + (own ? "shown" : "hidden"));
                        if (saddle != null && emiLoot) {
                            boolean theirs = saddle.contains(EMI_LOOT);
                            expect(theirs == on, state + ", EMI Loot's chest loot tab is " + (theirs ? "shown" : "hidden"));
                        }
                    }));
        }
        // Command only first: put back on, it hides the choice, so EMI reloads once at most.
        d.then(run(() -> {
            mc.setScreen(null);
            ChestLootTabs.setCommandOnly(commandOnlyBefore[0]);
            ChestLootTabs.choose(chosenBefore[0]);
        }));
        return d;
    }

    /**
     * Makes a change to the chest loot setting or Command only, and returns whether the viewer shows it
     * yet: at once in JEI and REI, and in EMI once it has loaded again, when what the viewers show changed.
     */
    private static BooleanSupplier change(boolean emi, Runnable change) {
        //? if emi {
        if (emi) {
            boolean own = ChestLootTabs.showsOwn();
            boolean others = ChestLootTabs.hidesOthers();
            int now = JesEmiPlugin.loads();
            change.run();
            boolean reloads = own != ChestLootTabs.showsOwn() || others != ChestLootTabs.hidesOthers();
            return () -> !reloads || JesEmiPlugin.loads() > now && dev.emi.emi.runtime.EmiReloadManager.isLoaded();
        }
        //?}
        change.run();
        return () -> true;
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            throw new IllegalStateException(otherwise);
        }
    }
}
