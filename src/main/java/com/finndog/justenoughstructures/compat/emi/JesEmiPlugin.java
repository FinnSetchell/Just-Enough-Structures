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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to EMI, so looking up how to get an item also lists the
 * structures whose loot can give it. The recipes come from the loot index, which the server sends
 * after the world is joined. EMI only takes recipes while it loads, which it starts as the world is
 * joined, so its load waits a little for an index that's on its way. One that comes later, or
 * changes, has EMI loaded again.
 */
// The annotation is how EMI finds it on Forge; Fabric uses the entrypoint in fabric.mod.json.
@EmiEntrypoint
public final class JesEmiPlugin implements EmiPlugin {
    static final EmiRecipeCategory FOUND_IN = new EmiRecipeCategory(JustEnoughStructures.id("found_in"), EmiStack.of(FoundInRow.MAP)) {
        @Override
        public Component getName() {
            return FoundInRow.title();
        }
    };

    /** How long EMI's load waits for an index that's on its way. Loading EMI all over again takes longer. */
    private static final long INDEX_WAIT_MS = 10_000;

    // The index EMI last loaded with, and whether it's loading now, which it does on a thread of its own.
    private static volatile LootIndex loaded;
    private static volatile boolean loading;
    private static boolean listening;
    private static volatile int loads;

    @Override
    public void register(EmiRegistry registry) {
        loads++;
        loading = true;
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
            // Only now that the load is over, so an index that came during it is looked at once, after it.
            Minecraft.getInstance().execute(JesEmiPlugin::listen);
        }
    }

    /** On the game's thread, after each load: hears about every index from now on, and catches up with one that's come since. */
    private static void listen() {
        if (!listening) {
            listening = true;
            ClientRequests.onEachIndex(JesEmiPlugin::arrived);
            ClientRequests.wantIndex();
        }
        LootIndex latest = ClientRequests.latestIndex();
        if (latest != null) {
            arrived(latest);
        }
    }

    private static void arrived(LootIndex index) {
        // One that comes while EMI loads is left for the look that follows the load.
        if (!loading && index != loaded) {
            reload();
        }
    }

    /** Whether EMI has loaded with the structures in it. For the screenshot harness. */
    public static boolean ready() {
        return loaded != null && EmiReloadManager.isLoaded();
    }

    /** How many times EMI has loaded since the game started. For the screenshot harness. */
    public static int loads() {
        return loads;
    }

    /** Where the first row's structure is on screen, or null before EMI has shown one. For the screenshot harness. */
    public static int[] firstRow() {
        return FoundInEmiRecipe.firstRow;
    }

    /** Opens EMI on how to get an item. For the screenshot harness. */
    public static void showFoundIn(ItemStack stack) {
        EmiApi.displayRecipes(EmiStack.of(stack));
    }

    // EMI's API has no way to add recipes once it's loaded, so this uses its own reload. If a later
    // EMI changes it, the page just stays empty.
    private static void reload() {
        try {
            EmiReloadManager.reload();
        } catch (LinkageError | RuntimeException e) {
            JesLog.warnOnce("emi-reload", "Couldn't reload EMI to add the structures items are found in", e);
        }
    }
}
