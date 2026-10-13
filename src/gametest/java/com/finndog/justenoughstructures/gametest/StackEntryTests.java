package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.StackEntry;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.21 {
/*import net.minecraft.core.component.DataComponents;
*///?}

/** A loot entry filled from a stack, as the loot editor does with the item a player is holding. */
public final class StackEntryTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:from_a_stack");

    private StackEntryTests() {
    }

    /**
     * An entry filled from a stack is one the game loads, and rolling it gives that stack back: the
     * same item, as many of it, with the same name and damage. What the entry had that isn't the
     * item's own, its weight and its conditions, is still there.
     */
    public static void entriesGiveBackTheirStack(GameTestHelper helper) {
        ItemStack arrows = new ItemStack(Items.ARROW, 16);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(7);
        //? if >=1.21 {
        /*sword.set(DataComponents.CUSTOM_NAME, Component.literal("A test's sword"));
        *///?} else {
        sword.setHoverName(Component.literal("A test's sword"));
        //?}
        for (ItemStack stack : List.of(arrows, sword, new ItemStack(Items.BONE))) {
            JsonObject entry = JsonParser.parseString("{\"type\": \"minecraft:tag\", \"name\": \"minecraft:arrows\", \"expand\": true, \"weight\": 3, "
                    + "\"functions\": [{\"function\": \"minecraft:set_count\", \"count\": 40}, {\"function\": \"minecraft:furnace_smelt\"}], "
                    + "\"conditions\": [{\"condition\": \"minecraft:random_chance\", \"chance\": 1.0}]}").getAsJsonObject();
            StackEntry.fill(entry, stack, helper.getLevel().registryAccess());
            helper.assertTrue(entry.get("weight").getAsInt() == 3 && entry.has("conditions") && !entry.has("expand")
                    && entry.toString().contains("furnace_smelt") && !entry.toString().contains("\"count\":40"), "filling from " + stack + " left the entry as " + entry);
            JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
            table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
            // In the game's own shape, as the editor sends a table: from 26.3 that isn't the shape it's edited in.
            String sent = LootFormat.forGame(table).toString();
            Component problem = LootOverrides.check(TABLE, sent);
            helper.assertTrue(problem == null, "the entry made from " + stack + " doesn't load: " + (problem == null ? "" : problem.getString()) + " " + entry);
            List<ItemStack> rolled = LootRolls.fill(helper.getLevel(), TABLE, LootOverrides.parse(sent), 5L, 27).stream().filter(s -> !s.isEmpty()).toList();
            int count = rolled.stream().mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(count == stack.getCount() && rolled.stream().allMatch(s -> same(s, stack)), "the entry made from " + stack + " rolled " + rolled);
        }
        helper.succeed();
    }

    /** The same item with the same data, whatever the count. */
    private static boolean same(ItemStack a, ItemStack b) {
        //? if >=1.21 {
        /*return ItemStack.isSameItemSameComponents(a, b);
        *///?} else {
        return ItemStack.isSameItemSameTags(a, b);
        //?}
    }
}
