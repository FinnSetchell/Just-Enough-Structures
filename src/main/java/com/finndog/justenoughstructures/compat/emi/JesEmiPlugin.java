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
 * after the world is joined. EMI only takes recipes while it loads, so once the index is here EMI is
 * loaded again with them.
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

    // The newest index, and the one EMI last loaded. EMI loads on a thread of its own, so they're
    // handed over through these rather than read from ClientRequests there.
    private static volatile LootIndex latest;
    private static volatile LootIndex loaded;
    private static boolean listening;

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(FOUND_IN);
        Minecraft.getInstance().execute(JesEmiPlugin::listen);
        LootIndex index = latest;
        loaded = index;
        if (index == null) {
            return;
        }
        for (FoundInRecipe recipe : FoundInRecipe.fromIndex(index, StructureNames::structure)) {
            registry.addRecipe(new FoundInEmiRecipe(recipe));
        }
    }

    /** On the game's thread: hears about every index, including one that's already here. */
    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        ClientRequests.onEachIndex(JesEmiPlugin::arrived);
        ClientRequests.wantIndex();
        if (ClientRequests.indexReady()) {
            arrived(ClientRequests.index().join());
        }
    }

    private static void arrived(LootIndex index) {
        latest = index;
        if (index != loaded) {
            reload();
        }
    }

    /** Whether EMI has loaded with the structures in it. For the screenshot harness. */
    public static boolean ready() {
        return loaded != null && EmiReloadManager.isLoaded();
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
