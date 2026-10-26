package com.finndog.justenoughstructures.compat.emi;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.compat.foundin.ChestLootPages;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.compat.foundin.FoundInRow;
import com.finndog.justenoughstructures.loot.LootIndex;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a "Found in structures" category to EMI, so looking up how to get an item also lists the
 * structures whose loot can give it. The recipes come from the loot index, which the server sends
 * after the world is joined. EMI only reads recipes while it loads, which starts as the world is
 * joined, so its load waits briefly for an index on its way. An index that arrives later, or
 * changes, makes EMI reload, unless it's an early one: then EMI waits for the finished one. A load
 * without an index has a waiting row for each item chest loot can give, if the server has said
 * which, so the page is there with the server's progress until the index comes.
 *
 * <p>With EMI Loot, each loot table also gets a row for each structure that uses it, made for the
 * stack EMI Loot's chest loot page stands for, so clicking the table's name there lists them.
 *
 * <p>The player's chest loot setting can hide other mods' chest loot tabs, or leave out JES's rows,
 * in which case EMI isn't reloaded for an index either. Changing it reloads EMI.
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

    /**
     * How long EMI's load waits for an index that's on its way. Reloading EMI takes longer. EMI gives
     * each plugin 10 seconds before it says on screen that loading is delayed, so this stays well
     * short of that.
     */
    private static final long INDEX_WAIT_MS = 5_000;
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
    private static volatile int waitingRows;
    private static volatile int tableRows;
    private static boolean settingWatched;

    @Override
    public void register(EmiRegistry registry) {
        loads++;
        loading = true;
        asked = null;
        try {
            registry.addCategory(FOUND_IN);
            // EMI drops these once every plugin has added its recipes, so other mods' tabs go
            // whichever order the plugins load in. A tab left with no recipes isn't shown.
            if (ChestLootTabs.hidesOthers()) {
                registry.removeRecipes(JesEmiPlugin::otherChestLoot);
            }
            if (!ChestLootTabs.showsOwn()) {
                loaded = null;
                waitingRows = 0;
                tableRows = 0;
                return;
            }
            LootIndex index = ClientRequests.awaitIndex(INDEX_WAIT_MS);
            loaded = index;
            // Waiting rows only come with a load: reloading EMI for them would take longer than many
            // indexes take to arrive.
            List<FoundInRecipe> recipes = index != null ? FoundInRecipe.fromIndex(index, StructureNames::structure)
                    : FoundInRecipe.waitingFor(ClientRequests.chestItems());
            waitingRows = index != null ? 0 : recipes.size();
            for (FoundInRecipe recipe : recipes) {
                registry.addRecipe(new FoundInEmiRecipe(recipe));
            }
            tableRows = addTableRows(registry, index);
        } finally {
            loading = false;
            // After the load ends, so an index that arrived during it is checked once, afterwards.
            Minecraft.getInstance().execute(JesEmiPlugin::listen);
        }
    }

    /** Adds the rows for EMI Loot's chest loot pages, if it's installed, and says how many. */
    private static int addTableRows(EmiRegistry registry, LootIndex index) {
        int added = 0;
        try {
            for (ChestLootPages.Page page : ChestLootPages.pages(index, StructureNames::structure)) {
                if (page.stack() instanceof EmiStack stack) {
                    for (FoundInRecipe row : page.rows()) {
                        registry.addRecipe(new FoundInEmiRecipe(row, stack));
                        added++;
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            JesLog.warnOnce("emi-loot-pages", "Couldn't add the structures to EMI Loot's chest loot pages", e);
        }
        return added;
    }

    /** Whether a recipe is in another mod's chest loot tab. */
    private static boolean otherChestLoot(EmiRecipe recipe) {
        try {
            EmiRecipeCategory category = recipe.getCategory();
            return category != null && category.getId() != null && ChestLootTabs.hides(category.getId().toString());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Runs on the client thread after each load: subscribes to every later index, and catches up with one that arrived since. */
    private static void listen() {
        if (!settingWatched) {
            settingWatched = true;
            ChestLootTabs.onChange(JesEmiPlugin::settingChanged);
        }
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

    /** EMI only reads recipes while it loads, so it loads again. Out of a world, the next join loads it anyway. */
    private static void settingChanged() {
        if (Minecraft.getInstance().level == null || loads == 0) {
            return;
        }
        JesLog.debug("EMI is loaded again, for the chest loot setting");
        try {
            EmiReloadManager.reload();
        } catch (LinkageError | RuntimeException e) {
            JesLog.warnOnce("emi-reload-setting", "Couldn't reload EMI for the chest loot setting; it applies when the world is joined again", e);
        }
    }

    private static void arrived(LootIndex index) {
        // With JES's rows left out, an index changes nothing.
        if (!ChestLootTabs.showsOwn()) {
            return;
        }
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

    /** How many waiting rows EMI last loaded with. Test hook for the scripted scenarios. */
    public static int waitingRows() {
        return waitingRows;
    }

    /** How many rows for EMI Loot's chest loot pages EMI last loaded with. Test hook for the scripted scenarios. */
    public static int tableRows() {
        return tableRows;
    }

    /** How many times EMI has loaded since the game started. Test hook for the scripted scenarios. */
    public static int loads() {
        return loads;
    }

    /** Where the first row's structure is on screen, or null before EMI has shown one. Test hook for the scripted scenarios. */
    public static int[] firstRow() {
        return FoundInEmiRecipe.firstRow;
    }

    /** Forgets where the first row was, so the next page shown sets it again. Test hook for the scripted scenarios. */
    public static void forgetFirstRow() {
        FoundInEmiRecipe.firstRow = null;
    }

    /** The structures of the Found in structures rows among these recipes, in order. Test hook for the scripted scenarios. */
    public static List<ResourceLocation> structuresIn(List<EmiRecipe> recipes) {
        return recipes.stream().filter(r -> r instanceof FoundInEmiRecipe).map(r -> ((FoundInEmiRecipe) r).structure()).toList();
    }

    /** The tabs EMI has for how to get an item, by id. Test hook for the scripted scenarios. */
    public static Set<String> tabsFor(ItemStack stack) {
        Set<String> ids = new TreeSet<>();
        for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipesByOutput(EmiStack.of(stack))) {
            if (recipe.getCategory() != null) {
                ids.add(recipe.getCategory().getId().toString());
            }
        }
        return ids;
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
