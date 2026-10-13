package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.EditHistory;
import com.finndog.justenoughstructures.client.screen.EntryTree;
import com.finndog.justenoughstructures.client.screen.JsonPaths;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** Entries inside alternatives, groups and sequences, as the loot editor lists and finds them. */
public final class EntryTreeTests {
    private EntryTreeTests() {
    }

    /**
     * Every entry is listed, those inside others after the one they're in and a step deeper, and
     * each one's path leads to it in the table. One changed through its path leaves a table the
     * game loads, and going back in the editor's history remembers which one was picked.
     */
    public static void entriesInsideOthersAreListedAndFound(GameTestHelper helper) {
        JsonObject table = JsonParser.parseString("""
                {"type": "minecraft:generic", "pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone"},
                  {"type": "minecraft:alternatives", "children": [
                    {"type": "minecraft:item", "name": "minecraft:diamond",
                     "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}]},
                    {"type": "minecraft:group", "children": [
                      {"type": "minecraft:item", "name": "minecraft:coal"},
                      {"type": "minecraft:item", "name": "minecraft:stick"}]}]},
                  {"type": "minecraft:item", "name": "minecraft:apple"}]}]}
                """).getAsJsonObject();
        List<EntryTree.Node> nodes = EntryTree.of(JsonPaths.array(table, "pools.0.entries"));
        List<String> listed = new ArrayList<>();
        for (EntryTree.Node node : nodes) {
            listed.add(node.entry() + "/" + node.child() + "/" + node.depth());
            helper.assertTrue(JsonPaths.object(table, EntryTree.path(0, node.entry(), node.child())) == node.object(),
                    "the path to " + node.child() + " in entry " + node.entry() + " doesn't lead to it");
        }
        List<String> wanted = List.of("0//0", "1//0", "1/children.0/1", "1/children.1/1", "1/children.1.children.0/2", "1/children.1.children.1/2", "2//0");
        helper.assertTrue(listed.equals(wanted), "the entries were listed as " + listed);
        helper.assertTrue(EntryTree.parent("children.1.children.0").equals("children.1") && EntryTree.parent("children.1").isEmpty(),
                "the entry one is inside wasn't found");
        helper.assertTrue(EntryTree.beside("children.1.children.0", 1).equals("children.1.children.1") && EntryTree.beside("children.1", 0).equals("children.0"),
                "the entry beside one wasn't found");
        helper.assertTrue(EntryTree.inside("", 2).equals("children.2") && EntryTree.inside("children.1", 0).equals("children.1.children.0"),
                "the entry inside one wasn't found");

        String coal = EntryTree.path(0, 1, "children.1.children.0");
        JsonPaths.set(table, coal + ".weight", new JsonPrimitive(7));
        helper.assertTrue(table.toString().contains("\"minecraft:coal\",\"weight\":7"), "the coal's weight wasn't set: " + table);
        // In the game's own shape, as the editor sends a table.
        Component problem = LootOverrides.check(Ids.parse("justenoughstructures_gametest:entries_inside"), LootFormat.forGame(table).toString());
        helper.assertTrue(problem == null, "the table doesn't load after changing an entry inside another: " + (problem == null ? "" : problem.getString()));

        EditHistory history = new EditHistory();
        history.start("a");
        history.changed("b", 0, 1, "children.1.children.0");
        EditHistory.Step back = history.undo();
        helper.assertTrue(new EditHistory.Step("a", 0, 1, "children.1.children.0").equals(back), "going back gave " + back);
        helper.succeed();
    }
}
