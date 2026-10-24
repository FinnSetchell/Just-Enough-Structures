package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import com.finndog.justenoughstructures.compat.jei.JesJeiPlugin;
import com.finndog.justenoughstructures.compat.rei.JesReiPlugin;
import com.finndog.justenoughstructures.gametest.scripted.Director;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
 * The "Found in structures" page while the server is still looking through structures' loot, in
 * whichever recipe viewer the dev runtime has (-Pviewer=emi, rei, or JEI by default, all only with
 * the dev mods). A saddle's page, which has no other recipes, has a waiting row with the server's
 * count, which goes up as it gets further, and once the index is here the structures instead. EMI
 * keeps its waiting row past the early index, until the finished one. Only meaningful when the
 * server has no saved loot index.
 */
final class WaitingScenario {
    private static final String MODE = "found_in_waiting";
    /** How many structures the server's build may finish before it waits, so the shots come before the index. */
    private static final AtomicInteger ALLOWED = new AtomicInteger(Integer.MAX_VALUE);
    /** Whether the build waits once this game has the early index, so the finished one doesn't follow straight away. */
    private static final AtomicBoolean AFTER_EARLY = new AtomicBoolean();

    private WaitingScenario() {
    }

    /**
     * Holds the server's loot index build after its second structure, if this scenario is to be
     * played. A vanilla index takes seconds, so this is set before the world is made. Let go after
     * ten minutes in case the scenario never gets there.
     */
    static void holdIfPlayed() {
        String mode = System.getProperty("jes.autoshot.mode", "");
        boolean played = mode.equals(MODE)
                || (mode.equals("suite") && Arrays.asList(System.getProperty("jes.autoshot.suite", "").split(",")).contains(MODE));
        if (!played) {
            return;
        }
        ALLOWED.set(2);
        AFTER_EARLY.set(true);
        long until = System.nanoTime() + 10 * 60 * 1_000_000_000L;
        // On the index's thread, after the server has said how far it has got.
        LootIndexStore.watchProgress(done -> {
            while ((done >= ALLOWED.get() || AFTER_EARLY.get() && ClientRequests.latestIndex() != null) && System.nanoTime() < until) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    static Director build(Minecraft mc) {
        boolean emi = FabricLoader.getInstance().isModLoaded("emi");
        boolean rei = !emi && FabricLoader.getInstance().isModLoaded("roughlyenoughitems");
        if (!emi && !rei && !FabricLoader.getInstance().isModLoaded("jei")) {
            // Without a viewer nothing asks for the index, so the server never says how it's going.
            letGo();
            throw new UnsupportedOperationException("needs EMI, JEI or REI, which the dev game only has with its dev mods");
        }
        String name = emi ? "emi" : rei ? "rei" : "jei";
        Consumer<ItemStack> show = rei ? JesReiPlugin::showFoundIn : stack -> JesJeiPlugin.showFoundIn(stack);
        Supplier<int[]> firstRow = rei ? JesReiPlugin::firstRow : () -> null;
        BooleanSupplier loadedWaiting = () -> true;
        BooleanSupplier loadedFinished = () -> true;
        int[] loads = {0};
        //? if emi {
        if (emi) {
            show = JesEmiPlugin::showFoundIn;
            firstRow = JesEmiPlugin::firstRow;
            loadedWaiting = () -> JesEmiPlugin.waitingRows() > 0 && dev.emi.emi.runtime.EmiReloadManager.isLoaded();
            loadedFinished = JesEmiPlugin::ready;
        }
        //?}
        if (ALLOWED.get() == Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("the loot index isn't held, so this scenario can't show what comes before it");
        }
        Consumer<ItemStack> showPage = show;
        Supplier<int[]> structureRow = firstRow;
        BooleanSupplier waitingLoaded = loadedWaiting;
        BooleanSupplier finishedLoaded = loadedFinished;
        long[] since = new long[1];
        String[] first = new String[1];
        Director d = new Director(mc);
        d.then(pressKey(InputConstants.KEY_E))
                .then(until(() -> mc.screen != null, 40))
                // Told how far the server has got, with the items chest loot can give.
                .then(until(() -> !ClientRequests.chestItems().isEmpty() && ClientRequests.indexProgress() >= 0, 20 * 60 * 2))
                .then(until(waitingLoaded, 20 * 60 * 2))
                .then(run(() -> {
                    expect(!ClientRequests.chestItems().isEmpty(), "The server never said which items chest loot can give");
                    expect(ClientRequests.latestIndex() == null, "The loot index came before the waiting row could be looked at");
                    expect(waitingLoaded.getAsBoolean(), "The viewer never loaded with waiting rows");
                    //? if emi {
                    loads[0] = emi ? JesEmiPlugin.loads() : 0;
                    if (emi) {
                        JustEnoughStructures.LOGGER.info("EMI loaded {} times, with {} waiting rows", loads[0], JesEmiPlugin.waitingRows());
                    }
                    //?}
                    JustEnoughStructures.LOGGER.info("The server says chest loot can give {} items", ClientRequests.chestItems().size());
                    since[0] = System.nanoTime();
                }))
                .then(showUntil(showPage, () -> FoundInRow.waitingDrawn(since[0]) != null, 20 * 30))
                .then(pause(20))
                .then(shoot("w01_" + name + "_waiting"))
                .then(run(() -> {
                    first[0] = FoundInRow.waitingDrawn(since[0]);
                    expect(first[0] != null, "No waiting row was drawn on a saddle's page");
                    expectCount(first[0]);
                    JustEnoughStructures.LOGGER.info("The waiting row says \"{}\"", first[0]);
                    // A few more structures, so the count goes up.
                    ALLOWED.set(6);
                }))
                .then(until(() -> !first[0].equals(FoundInRow.waitingDrawn(since[0])), 20 * 30))
                .then(pause(10))
                .then(shoot("w02_" + name + "_counting"))
                .then(run(() -> {
                    String now = FoundInRow.waitingDrawn(since[0]);
                    expect(!first[0].equals(now), "The waiting row's count didn't move from \"" + first[0] + "\"");
                    expectCount(now);
                    JustEnoughStructures.LOGGER.info("Then it says \"{}\"", now);
                    ALLOWED.set(Integer.MAX_VALUE);
                }))
                // The early index, after which the server waits again: JEI and REI show its structures,
                // EMI waits for the finished one.
                .then(until(ClientRequests::indexFilling, 20 * 60 * 5))
                .then(run(() -> {
                    expect(ClientRequests.indexFilling(), "The early loot index never came");
                    mc.setScreen(null);
                }))
                .then(pause(10))
                .then(pressKey(InputConstants.KEY_E))
                .then(pause(10))
                .then(run(() -> {
                    since[0] = System.nanoTime();
                    showPage.accept(new ItemStack(Items.SADDLE));
                }))
                .then(pause(40))
                .then(shoot("w03_" + name + "_early_index"))
                .then(run(() -> {
                    String drawn = FoundInRow.waitingDrawn(since[0]);
                    JustEnoughStructures.LOGGER.info("With the early index here, the page has {}", drawn != null ? "a waiting row saying \"" + drawn + "\"" : "no waiting row");
                    expect(ClientRequests.indexFilling(), "The finished index came before the early one could be looked at");
                    if (emi) {
                        expect(drawn != null, "EMI dropped its waiting row before the finished index");
                        expectCount(drawn);
                    } else {
                        expect(drawn == null, "A waiting row was still drawn with the early index here");
                        expect(!rei || structureRow.get() != null, "No structure's row was drawn on a saddle's page");
                    }
                    mc.setScreen(null);
                    AFTER_EARLY.set(false);
                }))
                .then(until(() -> ClientRequests.indexReady() && !ClientRequests.indexFilling(), 20 * 60 * 5))
                .then(pause(100))
                .then(until(finishedLoaded, 20 * 60 * 5))
                .then(pressKey(InputConstants.KEY_E))
                .then(pause(10))
                .then(run(() -> {
                    since[0] = System.nanoTime();
                    showPage.accept(new ItemStack(Items.SADDLE));
                }))
                .then(pause(60))
                .then(shoot("w04_" + name + "_found_in"))
                .then(run(() -> {
                    expect(ClientRequests.indexReady() && !ClientRequests.indexFilling(), "The finished loot index never came");
                    expect(FoundInRow.waitingDrawn(since[0]) == null, "A waiting row was still drawn with the finished index here");
                    expect(!(rei || emi) || structureRow.get() != null, "No structure's row was drawn on a saddle's page");
                    //? if emi {
                    if (emi) {
                        JustEnoughStructures.LOGGER.info("EMI loaded {} times, {} of them by the time it had waiting rows", JesEmiPlugin.loads(), loads[0]);
                        expect(JesEmiPlugin.waitingRows() == 0, "EMI still has " + JesEmiPlugin.waitingRows() + " waiting rows");
                        expect(JesEmiPlugin.loads() == loads[0] + 1, "EMI loaded " + (JesEmiPlugin.loads() - loads[0])
                                + " times after its waiting rows, not once for the finished index");
                    }
                    //?}
                    mc.setScreen(null);
                }));
        return d;
    }

    /** Opens a saddle's page, and again every two seconds until {@code drawn}, as the viewer may not be ready at first. */
    private static Director.Action showUntil(Consumer<ItemStack> show, BooleanSupplier drawn, int frames) {
        return (d, frame) -> {
            if (frame % 40 == 0) {
                show.accept(new ItemStack(Items.SADDLE));
            }
            return drawn.getAsBoolean() || frame >= frames;
        };
    }

    /** The waiting row says how many structures the server has looked through, out of how many. */
    private static void expectCount(String text) {
        expect(text != null && text.matches(".*\\.\\.\\. \\d+ of \\d+"), "The waiting row says \"" + text + "\", without the server's count");
        expect(text.equals(ClientRequests.viewerWaitText().getString()), "The waiting row says \"" + text + "\", not the server's latest count");
    }

    private static void expect(boolean fine, String otherwise) {
        if (!fine) {
            // So the index still gets built for whatever comes next.
            letGo();
            throw new IllegalStateException(otherwise);
        }
    }

    /** Stops holding the server's build. */
    private static void letGo() {
        ALLOWED.set(Integer.MAX_VALUE);
        AFTER_EARLY.set(false);
    }
}
