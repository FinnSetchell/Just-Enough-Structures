package com.finndog.justenoughstructures.gametest.scripted;

//? if jei {
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.compat.jei.JesJeiPlugin;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.server.LootIndexStore;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
//?}
import net.minecraft.client.Minecraft;

/**
 * Whether JEI takes the structures' rows again for a loot index that hasn't changed. JEI can't
 * remove a recipe it has been given, only hide it, so rows replaced by equal ones stay there, hidden,
 * until JEI restarts. Needs JEI, the dev game's default recipe viewer.
 */
final class JeiIndexScenario {
    private JeiIndexScenario() {
    }

    /**
     * The server sends its unchanged loot index again, as after each check that it's still valid,
     * which here it's told to do though nothing has changed. JEI keeps the rows it has.
     */
    static Director same(Minecraft mc) {
        //? if jei {
        MinecraftServer server = server(mc);
        long[] rows = new long[2];
        LootIndex[] before = new LootIndex[1];
        Director d = new Director(mc);
        d.then(until(JeiIndexScenario::finished, 20 * 60 * 30))
                .then(pause(20))
                .then(run(() -> {
                    before[0] = ClientRequests.latestIndex();
                    if (before[0] == null) {
                        throw new IllegalStateException("JEI has the structures, but there's no loot index here");
                    }
                    rows[0] = JesJeiPlugin.structureRows(false);
                    rows[1] = JesJeiPlugin.structureRows(true);
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
                // Anything else the server sends for the check arrives in this time.
                .then(pause(40))
                .then(run(() -> check(rows, "the same loot index arrived again", false)));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs JEI");
        *///?}
    }

    /**
     * A /reload: the server checks its loot index and sends it again, twice, and JEI may restart for
     * the recipes and tags the reload sends, before or after the index comes. Nothing the game reads
     * has changed, but the index can still come back different, as some tables read differently after
     * a reload (seen on 26.3 with the dev mods). JEI takes the index again only if it did, so at most
     * the rows it showed before are left hidden, once.
     */
    static Director reload(Minecraft mc) {
        //? if jei {
        MinecraftServer server = server(mc);
        long[] rows = new long[2];
        LootIndex[] before = new LootIndex[1];
        int[] reloads = new int[1];
        AtomicBoolean checked = new AtomicBoolean();
        Director d = new Director(mc);
        d.then(until(JeiIndexScenario::finished, 20 * 60 * 30))
                .then(pause(20))
                .then(run(() -> {
                    before[0] = ClientRequests.latestIndex();
                    if (before[0] == null) {
                        throw new IllegalStateException("JEI has the structures, but there's no loot index here");
                    }
                    rows[0] = JesJeiPlugin.structureRows(false);
                    rows[1] = JesJeiPlugin.structureRows(true);
                    reloads[0] = ClientRequests.reloads();
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload"));
                }))
                .then(until(() -> ClientRequests.reloads() > reloads[0], 20 * 60))
                .then(until(JeiIndexScenario::finished, 20 * 60 * 5))
                // The server can send the index twice after a /reload: once in answer to being asked,
                // and again when its check is done, in the same task that makes it ready again.
                .then(until(() -> {
                    server.execute(() -> checked.set(LootIndexStore.ready()));
                    return checked.get();
                }, 20 * 60 * 5))
                .then(pause(40))
                .then(run(() -> check(rows, "a /reload", !before[0].equals(ClientRequests.latestIndex()))));
        return d;
        //?} else {
        /*throw new UnsupportedOperationException("needs JEI");
        *///?}
    }

    //? if jei {
    /**
     * JEI shows the finished loot index. In a first game an early one comes first, and the finished
     * one, which differs, after the server has rolled the tables.
     */
    private static boolean finished() {
        return JesJeiPlugin.ready() && ClientRequests.indexReady() && !ClientRequests.indexFilling() && ClientRequests.latestIndex() != null;
    }

    private static MinecraftServer server(Minecraft mc) {
        if (!JustEnoughStructures.modVersions().containsKey("jei")) {
            throw new UnsupportedOperationException("needs JEI");
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new UnsupportedOperationException("needs a world of its own");
        }
        return server;
    }

    /**
     * JEI hides no more rows than before, and if the index {@code changed}, no more than the rows it
     * showed before as well, replaced once. Unchanged, it shows as many as before. Rows of an early
     * index the finished one replaced may be hidden from the start, and a JEI that restarted has none.
     */
    private static void check(long[] rows, String after, boolean changed) {
        long shown = JesJeiPlugin.structureRows(false);
        long held = JesJeiPlugin.structureRows(true);
        long allowed = rows[1] - rows[0] + (changed ? rows[0] : 0);
        JustEnoughStructures.LOGGER.info("JEI showed {} of the structures' rows and held {}, and after {}, with the loot index {}, shows {} and holds {}",
                rows[0], rows[1], after, changed ? "changed" : "the same", shown, held);
        if (!changed && shown != rows[0]) {
            throw new IllegalStateException("JEI shows " + shown + " of the structures' rows after " + after + ", not the " + rows[0] + " it showed before");
        }
        if (held - shown > allowed) {
            throw new IllegalStateException("JEI took the structures' rows again after " + after + ": it holds " + held + ", " + (held - shown)
                    + " of them hidden, where " + allowed + " could be" + (changed ? ", the loot index having changed" : ""));
        }
    }
    //?}
}
