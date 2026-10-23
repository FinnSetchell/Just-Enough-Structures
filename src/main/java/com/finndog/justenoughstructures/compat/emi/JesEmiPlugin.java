package com.finndog.justenoughstructures.compat.emi;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import com.finndog.justenoughstructures.loot.LootIndex;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to EMI, so looking up how to get an item also lists the
 * structures whose loot can give it. The recipes come from the loot index, which the server sends
 * after the world is joined. EMI only reads recipes while it loads, which starts as the world is
 * joined, so its load waits briefly for an index on its way. An index that arrives later, or
 * changes, makes EMI reload, unless it's an early one: then EMI waits for the finished one.
 */
// The annotation is how EMI finds it on Forge and NeoForge; Fabric uses the entrypoint in fabric.mod.json.
@EmiEntrypoint
public final class JesEmiPlugin implements EmiPlugin {
    static final EmiRecipeCategory FOUND_IN = new EmiRecipeCategory(JustEnoughStructures.id("found_in"), EmiStack.of(FoundInRow.MAP)) {
        @Override
        public Component getName() {
            return FoundInRow.title();
        }
    };

    /** How long EMI's load waits for an index that's on its way. Reloading EMI takes longer. */
    private static final long INDEX_WAIT_MS = 10_000;
    /**
     * How long the server can stay silent about an early index before EMI loads it anyway, as the
     * server may have stopped building the rest. While building it reports progress as structures
     * finish, but a slow structure, or a wait for memory, can keep it quiet for a while.
     * Public for the scripted scenarios.
     */
    public static final long QUIET_MS = 5 * 60_000;

    // The index EMI last loaded with, and whether it's loading now, which it does on a separate thread.
    private static volatile LootIndex loaded;
    private static volatile boolean loading;
    // The index EMI was last asked to reload for, until that load starts. Asking again before then
    // would make EMI restart the load.
    private static volatile LootIndex asked;
    private static boolean listening;
    // Whether a recheck of an early index is scheduled. Client thread only, like listening.
    private static boolean lookDue;
    private static volatile int loads;

    @Override
    public void register(EmiRegistry registry) {
        loads++;
        loading = true;
        asked = null;
        try {
            registry.addCategory(FOUND_IN);
            LootIndex index = ClientRequests.awaitIndex(INDEX_WAIT_MS);
            loaded = index;
            if (index != null) {
                for (FoundInRecipe recipe : FoundInRecipe.fromIndex(index, StructureNames::structure)) {
                    registry.addRecipe(new FoundInEmiRecipe(recipe));
                }
            }
        } finally {
            loading = false;
            // After the load ends, so an index that arrived during it is checked once, afterwards.
            Minecraft.getInstance().execute(JesEmiPlugin::listen);
        }
    }

    /** Runs on the client thread after each load: subscribes to every later index, and catches up with one that arrived since. */
    private static void listen() {
        if (!listening) {
            listening = true;
            ClientRequests.onEachIndex(JesEmiPlugin::arrived);
            // A server whose build fails sends nothing after an early index, so EMI loads that one now.
            ClientRequests.onIndexStopped(JesEmiPlugin::listen);
            ClientRequests.wantIndex();
        }
        LootIndex latest = ClientRequests.latestIndex();
        if (latest != null) {
            arrived(latest);
        }
    }

    private static void arrived(LootIndex index) {
        // An index that arrives while EMI loads is left for the check after the load. The server resends
        // its index each time it has checked it, and one equal to what EMI has, or is about to load,
        // isn't worth a reload.
        if (loading || index.equals(loaded) || index.equals(asked)) {
            return;
        }
        // Nor is an early one, since the finished one follows. If the server goes quiet for QUIET_MS,
        // EMI loads the early one anyway.
        long wait = ClientRequests.finishedIndexWait(QUIET_MS);
        if (wait > 0) {
            lookAgainIn(wait);
            return;
        }
        reload(index);
    }

    /** Client thread only: checks the index again in {@code millis}. Only one check is scheduled at a time. */
    private static void lookAgainIn(long millis) {
        if (lookDue) {
            return;
        }
        lookDue = true;
        JesLog.debug("EMI waits for the finished loot index, and looks again in {} s", millis / 1000);
        CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS, Minecraft.getInstance()).execute(() -> {
            lookDue = false;
            LootIndex latest = ClientRequests.latestIndex();
            if (latest != null) {
                arrived(latest);
            }
        });
    }

    /** Whether EMI has loaded with the structures in it. Test hook for the scripted scenarios. */
    public static boolean ready() {
        return loaded != null && EmiReloadManager.isLoaded();
    }

    /** How many times EMI has loaded since the game started. Test hook for the scripted scenarios. */
    public static int loads() {
        return loads;
    }

    /** Where the first row's structure is on screen, or null before EMI has shown one. Test hook for the scripted scenarios. */
    public static int[] firstRow() {
        return FoundInEmiRecipe.firstRow;
    }

    /** Opens EMI on how to get an item. Test hook for the scripted scenarios. */
    public static void showFoundIn(ItemStack stack) {
        EmiApi.displayRecipes(EmiStack.of(stack));
    }

    // EMI's API can't add recipes once it's loaded, so this calls EMI's internal reload. If a later
    // EMI changes that, the page stays empty.
    private static void reload(LootIndex index) {
        asked = index;
        JesLog.debug("EMI is loaded again, for a loot index with {} structures", index.tablesByStructure().size());
        try {
            EmiReloadManager.reload();
        } catch (LinkageError | RuntimeException e) {
            JesLog.warnOnce("emi-reload", "Couldn't reload EMI to add the structures items are found in", e);
        }
    }
}
