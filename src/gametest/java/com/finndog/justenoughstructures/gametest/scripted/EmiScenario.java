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

/** How EMI and the loot index get on, in a game that has EMI (-Pviewer=emi in the dev game). */
final class EmiScenario {
    private EmiScenario() {
    }

    /**
     * With a loot index the server saved in a game before, EMI loads once: the index is there in
     * time for the load EMI starts on joining, so there's no second one to add the structures. The
     * first game with a set of mods has no saved index, and EMI can't help loading twice then.
     */
    static Director once(Minecraft mc) {
        //? if emi {
        if (!JustEnoughStructures.modVersions().containsKey("emi")) {
            throw new UnsupportedOperationException("needs EMI");
        }
        Director d = new Director(mc);
        d.then(until(JesEmiPlugin::ready, 20 * 60 * 30))
                // Long enough for another load to have started, had one been going to.
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
     * The same loot index arriving again leaves EMI alone: only one that differs is worth loading
     * EMI all over again for. The server sends its index again whenever it has checked it still
     * holds, which here it's told to do though nothing has changed.
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
                // Long enough for EMI to have started loading again, had it been going to.
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
