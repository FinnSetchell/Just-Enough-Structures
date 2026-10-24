package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.ClientState.ChestLoot;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.compat.foundin.ChestLootPages;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The rows that make EMI Loot's chest loot pages list the structures using a loot table, without EMI or EMI Loot installed. */
public final class ChestLootPagesTests {
    private static final ResourceLocation ALPHA = Ids.of("test", "alpha");
    private static final ResourceLocation BETA = Ids.of("test", "beta");
    private static final ResourceLocation GAMMA = Ids.of("test", "gamma");
    private static final ResourceLocation SHARED = Ids.of("test", "chests/shared");
    private static final ResourceLocation ALPHA_ONLY = Ids.of("test", "chests/alpha_only");
    private static final ResourceLocation NO_STACK = Ids.of("test", "chests/no_stack");
    private static final LootIndex INDEX = new LootIndex(
            Map.of(ALPHA, Set.of(SHARED, ALPHA_ONLY), BETA, Set.of(SHARED), GAMMA, Set.of(NO_STACK)),
            Map.of(SHARED, Set.of(Ids.parse("diamond")), ALPHA_ONLY, Set.of(Ids.parse("gold_ingot")), NO_STACK, Set.of(Ids.parse("emerald"))));
    // Beta's name comes first, so the shared table lists it before alpha.
    private static final Function<ResourceLocation, String> NAMES = id -> id.equals(ALPHA) ? "Zebra Tower" : id.equals(BETA) ? "Aardvark Hut" : "Gamma";
    /** A stand-in for EMI Loot's stack: any object will do, as only EMI looks inside it. None for one table. */
    private static final Function<ResourceLocation, Object> STACKS = table -> table.equals(NO_STACK) ? null : "stack for " + table;

    private ChestLootPagesTests() {
    }

    /**
     * Each loot table gets a row for each structure the index says uses it, structures in the order
     * of their names, and none for a table there's no stack for. The rows have no item, so they read
     * as table rows, never as an item's or a waiting row.
     */
    public static void chestLootPagesListEachTablesStructures(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        try {
            ClientState.chestLoot = ChestLoot.BOTH;
            List<ChestLootPages.Page> pages = ChestLootPages.pages(INDEX, NAMES, STACKS);
            List<String> got = pages.stream().map(p -> p.table() + " " + p.rows().stream().map(r -> r.structure().getPath()).toList()).toList();
            helper.assertTrue(got.equals(List.of("test:chests/alpha_only [alpha]", "test:chests/shared [beta, alpha]")),
                    "expected alpha_only with alpha, then shared with beta and alpha, got " + got);
            Map<ResourceLocation, Set<ResourceLocation>> users = FoundIn.structuresByTable(INDEX);
            for (ChestLootPages.Page page : pages) {
                helper.assertTrue(("stack for " + page.table()).equals(page.stack()), page.table() + " has the stack " + page.stack());
                Set<ResourceLocation> listed = new HashSet<>();
                for (FoundInRecipe row : page.rows()) {
                    helper.assertTrue(row.isTable() && !row.isWaiting() && row.item().isEmpty() && row.tables().equals(Set.of(page.table())),
                            "a row of " + page.table() + " isn't a table row for it: " + row);
                    listed.add(row.structure());
                }
                helper.assertTrue(listed.equals(users.get(page.table())), page.table() + " lists " + listed + ", but the index has " + users.get(page.table()));
            }

            // An item's row and a waiting row aren't table rows.
            FoundInRecipe itemRow = FoundInRecipe.fromIndex(INDEX, NAMES).get(0);
            helper.assertFalse(itemRow.isTable(), "an item's row reads as a table row");
            helper.assertFalse(FoundInRecipe.waiting(new ItemStack(Items.DIAMOND)).isTable(), "a waiting row reads as a table row");
            helper.assertTrue(ChestLootPages.pages(null, NAMES, STACKS).isEmpty(), "rows were made without an index");
        } finally {
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }

    /**
     * Only Both makes the rows: Other mods leaves out JES's rows, and Just Enough Structures hides
     * EMI Loot's tab, so there's no page to link.
     */
    public static void chestLootPagesFollowTheSetting(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        try {
            for (ChestLoot value : ChestLoot.values()) {
                ClientState.chestLoot = value;
                boolean made = !ChestLootPages.pages(INDEX, NAMES, STACKS).isEmpty();
                helper.assertTrue(made == (value == ChestLoot.BOTH), "with " + value + " rows were " + (made ? "" : "not ") + "made");
            }
        } finally {
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }

    /** Without EMI Loot, which the game tests never have, no rows are made, and nothing goes wrong looking for it. */
    public static void chestLootPagesNeedEmiLoot(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        try {
            ClientState.chestLoot = ChestLoot.BOTH;
            helper.assertFalse(ChestLootPages.installed(), "EMI Loot is installed in the game test run");
            List<ChestLootPages.Page> pages = ChestLootPages.pages(INDEX, NAMES);
            helper.assertTrue(pages.isEmpty(), "without EMI Loot, rows were made for " + pages.stream().map(ChestLootPages.Page::table).toList());
        } finally {
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }
}
