package com.finndog.justenoughstructures.gametest.scripted;

//? if emi {
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.server.LootIndexStore;
import net.minecraft.server.MinecraftServer;
//?}
import net.minecraft.client.Minecraft;

/** How often EMI reloads as the loot index arrives. Needs EMI (-Pviewer=emi in the dev game). */
final class EmiScenario {
    private EmiScenario() {
    }

    /**
     * With a loot index saved from an earlier game, EMI loads once: the index arrives in time for the
     * load EMI starts on joining. The first game with a set of mods has no saved index, so EMI loads
     * twice there.
     */
    static Director once(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        Director d = new Director(mc);
        d.then(until(JesEmiPlugin::ready, 20 * 60 * 30))
                // Give a second load time to start, if one is coming.
                .then(pause(200))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("EMI loaded {} times", JesEmiPlugin.loads());
                    if (JesEmiPlugin.loads() != 1) {
                        throw new IllegalStateException("EMI loaded " + JesEmiPlugin.loads() + " times");
                    }
                }));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs EMI");
        *///?}
    }

    /**
     * A first game with nothing saved, where the loot index comes early and the finished one
     * follows: EMI loads as the world is joined and again for the finished index, but not for the
     * early one in between. Only meaningful when the server has no saved loot index and the finished
     * index holds more than the early one.
     */
    static Director early(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        int[] loadsWhenEarly = new int[1];
        LootIndex[] early = new LootIndex[1];
        Director d = new Director(mc);
        // EMI requests the index on joining. With nothing saved, an early index arrives first,
        // followed by progress updates.
        d.then(until(ClientRequests::indexFilling, 20 * 60 * 10))
                .then(run(() -> {
                    if (!ClientRequests.indexFilling()) {
                        throw new IllegalStateException("The loot index never arrived early: this game's server had one saved, or built it in one go");
                    }
                    early[0] = ClientRequests.latestIndex();
                    loadsWhenEarly[0] = JesEmiPlugin.loads();
                    JustEnoughStructures.LOGGER.info("The early loot index is here at {}%, and EMI has loaded {} times",
                            Math.round(ClientRequests.indexProgress() * 100), loadsWhenEarly[0]);
                }))
                .then(until(() -> ClientRequests.indexReady() && !ClientRequests.indexFilling(), 20 * 60 * 15))
                .then(run(() -> {
                    LootIndex finished = ClientRequests.latestIndex();
                    if (ClientRequests.indexFilling() || finished == null || finished == early[0]) {
                        throw new IllegalStateException("The finished loot index never came");
                    }
                    if (finished.equals(early[0])) {
                        throw new IllegalStateException("The finished loot index holds no more than the early one, so this game can't show whether EMI waited for it");
                    }
                }))
                // Give EMI time to start loading for the finished index.
                .then(pause(200))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("EMI loaded {} times, {} of them by the time the early loot index was here", JesEmiPlugin.loads(), loadsWhenEarly[0]);
                    if (!JesEmiPlugin.ready()) {
                        throw new IllegalStateException("EMI never loaded with the finished loot index");
                    }
                    if (JesEmiPlugin.loads() != 2) {
                        throw new IllegalStateException("EMI loaded " + JesEmiPlugin.loads() + " times, not once as the world was joined and once for the finished loot index");
                    }
                }));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs EMI");
        *///?}
    }

    /**
     * If the server stops building after the early loot index, as when its build fails, EMI loads
     * with the early index once the server has been quiet for JesEmiPlugin.QUIET_MS, and not before.
     * Here the build is stopped as soon as the early index arrives. Only meaningful when the server
     * has no saved loot index.
     */
    static Director quiet(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new UnsupportedOperationException("needs a world of its own");
        }
        int[] loadsWhenEarly = new int[1];
        LootIndex[] early = new LootIndex[1];
        long[] stopped = new long[1];
        int quietTicks = (int) (JesEmiPlugin.QUIET_MS / 50);
        Director d = new Director(mc);
        d.then(until(ClientRequests::indexFilling, 20 * 60 * 10))
                .then(run(() -> {
                    if (!ClientRequests.indexFilling()) {
                        throw new IllegalStateException("The loot index never arrived early: this game's server had one saved, or built it in one go");
                    }
                    early[0] = ClientRequests.latestIndex();
                    loadsWhenEarly[0] = JesEmiPlugin.loads();
                    // As when the build fails: the server says nothing more, and the finished index never comes.
                    server.execute(LootIndexStore::stop);
                    stopped[0] = System.nanoTime();
                    JustEnoughStructures.LOGGER.info("The early loot index is here and the server's build is stopped, with EMI loaded {} times", loadsWhenEarly[0]);
                }))
                .then(until(() -> JesEmiPlugin.loads() > loadsWhenEarly[0], quietTicks + 20 * 60 * 2))
                .then(run(() -> {
                    long after = (System.nanoTime() - stopped[0]) / 1_000_000_000L;
                    JustEnoughStructures.LOGGER.info("EMI loaded again {} s after the server stopped building the loot index", after);
                    if (JesEmiPlugin.loads() == loadsWhenEarly[0]) {
                        throw new IllegalStateException("EMI never loaded with the early loot index once the server had stopped saying anything of it");
                    }
                    // The server's last progress update can come a moment before its build is stopped.
                    if (after < JesEmiPlugin.QUIET_MS / 1000 - 10) {
                        throw new IllegalStateException("EMI loaded again " + after + " s after the server stopped building, before it had been quiet for "
                                + JesEmiPlugin.QUIET_MS / 1000 + " s");
                    }
                }))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                // Give a second load time to start, if one is coming.
                .then(pause(200))
                .then(run(() -> {
                    if (!JesEmiPlugin.ready() || ClientRequests.latestIndex() != early[0]) {
                        throw new IllegalStateException("EMI didn't end up loaded with the early loot index");
                    }
                    if (JesEmiPlugin.loads() != loadsWhenEarly[0] + 1) {
                        throw new IllegalStateException("EMI loaded " + (JesEmiPlugin.loads() - loadsWhenEarly[0]) + " times once the server had stopped, not once");
                    }
                }));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs EMI");
        *///?}
    }

    /**
     * An unchanged loot index arriving again doesn't reload EMI. The server resends its index after
     * each check that it's still valid, which here it's told to do though nothing has changed.
     */
    static Director same(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new UnsupportedOperationException("needs a world of its own");
        }
        int[] loads = new int[1];
        LootIndex[] before = new LootIndex[1];
        Director d = new Director(mc);
        d.then(until(JesEmiPlugin::ready, 20 * 60 * 30))
                .then(pause(200))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                .then(run(() -> {
                    loads[0] = JesEmiPlugin.loads();
                    before[0] = ClientRequests.latestIndex();
                    if (before[0] == null) {
                        throw new IllegalStateException("EMI has the structures, but there's no loot index here");
                    }
                    server.execute(() -> LootIndexStore.refresh(server));
                }))
                .then(until(() -> ClientRequests.latestIndex() != before[0], 20 * 60 * 5))
                .then(run(() -> {
                    LootIndex again = ClientRequests.latestIndex();
                    if (again == before[0] || again == null) {
                        throw new IllegalStateException("The server didn't send its loot index again");
                    }
                    if (!again.equals(before[0])) {
                        throw new IllegalStateException("The loot index came back different, with nothing changed");
                    }
                }))
                // Give EMI time to start loading again, if it's going to.
                .then(pause(200))
                .then(until(JesEmiPlugin::ready, 20 * 60 * 5))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("EMI had loaded {} times, and {} after the same loot index arrived again", loads[0], JesEmiPlugin.loads());
                    if (JesEmiPlugin.loads() != loads[0]) {
                        throw new IllegalStateException("EMI loaded again for a loot index that hadn't changed");
                    }
                }));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs EMI");
        *///?}
    }
}
