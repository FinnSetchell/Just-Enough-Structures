package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.key;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Loot tables the game can't load, and which part the error names. */
public final class FaultTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:faulty");
    private static final String GOOD_POOL = "{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", \"name\": \"minecraft:bone\"}]}";

    private FaultTests() {
    }

    /**
     * The pool, the entry, or the entry inside an entry that the game can't load is the one named,
     * counted as the editor lists them, and a table that's wrong in itself names no part. A table
     * with nothing wrong has no problem at all.
     */
    public static void whatCantLoadIsNamed(GameTestHelper helper) {
        helper.assertTrue(check("{\"type\": \"minecraft:chest\", \"pools\": [" + GOOD_POOL + "]}") == null, "a table with nothing wrong was turned down");

        String badItem = "{\"type\": \"minecraft:item\", \"name\": \"minecraft:not_an_item\"}";
        Component entry = check("{\"type\": \"minecraft:chest\", \"pools\": [" + GOOD_POOL + ", {\"rolls\": 1, \"entries\": ["
                + "{\"type\": \"minecraft:item\", \"name\": \"minecraft:coal\"}, {\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"}, " + badItem + "]}]}");
        expect(helper, entry, "invalid_entry", new LootOverrides.Fault(1, 2, ""));

        Component pool = check("{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": \"lots\", \"entries\": []}, " + GOOD_POOL + "]}");
        expect(helper, pool, "invalid_pool", new LootOverrides.Fault(0, -1, ""));

        Component inside = check("{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:alternatives\", \"children\": ["
                + "{\"type\": \"minecraft:item\", \"name\": \"minecraft:coal\"}, {\"type\": \"minecraft:group\", \"children\": ["
                + "{\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"}, " + badItem + "]}]}]}]}");
        expect(helper, inside, "invalid_entry_inside", new LootOverrides.Fault(0, 0, "children.1.children.1"));

        // A problem in the table itself, here a table-level function that doesn't exist, isn't blamed on a pool or entry.
        Component whole = check("{\"type\": \"minecraft:chest\", \"functions\": [{\"function\": \"minecraft:not_a_function\"}], \"pools\": [" + GOOD_POOL + "]}");
        helper.assertTrue(whole != null && key(whole).endsWith("invalid_table") && LootOverrides.Fault.of(whole) == null,
                "a table with a function there isn't was said to be " + (whole == null ? "fine" : whole.getString()));

        // Once sent to a player its numbers may come as text, which reads the same.
        Component sent = Component.translatable("screen.justenoughstructures.override.invalid_entry_inside", "3", "2", "why", "children.0");
        helper.assertTrue(new LootOverrides.Fault(1, 2, "children.0").equals(LootOverrides.Fault.of(sent)), "a problem sent as text was read as " + LootOverrides.Fault.of(sent));
        helper.assertTrue(LootOverrides.Fault.of(Component.literal("something else")) == null, "plain text was read as naming a part");
        helper.succeed();
    }

    // Converted to the game's format, as the editor does before sending a table.
    private static Component check(String json) {
        return LootOverrides.check(TABLE, LootFormat.forGame(JsonParser.parseString(json).getAsJsonObject()).toString());
    }

    private static void expect(GameTestHelper helper, Component problem, String keyEnd, LootOverrides.Fault part) {
        helper.assertTrue(problem != null && key(problem).endsWith(keyEnd) && part.equals(LootOverrides.Fault.of(problem)),
                "wanted " + keyEnd + " about " + part + " and got " + (problem == null ? "no problem" : key(problem) + " about " + LootOverrides.Fault.of(problem)
                        + ": " + problem.getString()));
    }
}
