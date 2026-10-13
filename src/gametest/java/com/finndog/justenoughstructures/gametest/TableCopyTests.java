package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.TableCopy;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A new loot table started as a copy of another. */
public final class TableCopyTests {
    private TableCopyTests() {
    }

    /**
     * A copy is offered an id no table has, the next one free for the table it's a copy of, and
     * starts as that table without its random sequence, which the game loads under the new id.
     */
    public static void copiesGetAnIdAndASequenceOfTheirOwn(GameTestHelper helper) {
        ResourceLocation dungeon = Ids.parse("chests/simple_dungeon");
        ResourceLocation first = Ids.parse("justenoughstructures:chests/simple_dungeon_copy");
        ResourceLocation second = Ids.parse("justenoughstructures:chests/simple_dungeon_copy2");
        ResourceLocation third = Ids.parse("justenoughstructures:chests/simple_dungeon_copy3");
        same(helper, TableCopy.id(dungeon, List.of()), first);
        same(helper, TableCopy.id(dungeon, List.of(first)), second);
        same(helper, TableCopy.id(dungeon, List.of(first, second)), third);
        // A copy of a copy is the table's next copy, not a copy_copy.
        same(helper, TableCopy.id(first, List.of(first)), second);
        same(helper, TableCopy.id(second, List.of(first, second)), third);

        String original = LootOverrides.original(helper.getLevel().getServer().getResourceManager(), dungeon);
        helper.assertTrue(original != null && original.contains("random_sequence"),
                "the game's dungeon chest names no random sequence, so this test needs another table");
        JsonObject was = JsonParser.parseString(original).getAsJsonObject();
        JsonObject copy = JsonParser.parseString(TableCopy.start(original)).getAsJsonObject();
        helper.assertTrue(!copy.has("random_sequence"), "the copy still rolls on the dungeon chest's random sequence: " + copy.get("random_sequence"));
        was.remove("random_sequence");
        helper.assertTrue(copy.equals(was), "the copy isn't the dungeon chest's table without its sequence");
        Component problem = LootOverrides.check(first, copy.toString());
        helper.assertTrue(problem == null, "the copy doesn't load under its new id: " + (problem == null ? "" : problem.getString()));
        helper.assertTrue(TableCopy.start("not a table").equals("not a table"), "text that isn't a table was changed");
        helper.succeed();
    }

    private static void same(GameTestHelper helper, ResourceLocation got, ResourceLocation wanted) {
        helper.assertTrue(got.equals(wanted), "a copy was offered " + got + " where " + wanted + " was free");
    }
}
