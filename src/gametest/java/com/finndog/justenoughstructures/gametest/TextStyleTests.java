package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.TextStyle;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A name's colour and style, as the loot editor's boxes write them. */
public final class TextStyleTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:styled");

    private TextStyleTests() {
    }

    /**
     * A colour and a style are written as the plainest text component that says them, and come off
     * again leaving plain text. The game gives an item a name written that way as it's meant: in
     * that colour, and upright where it's told not to slant it.
     */
    public static void namesTakeAColourAndAStyle(GameTestHelper helper) {
        JsonElement name = new JsonPrimitive("Fang");
        helper.assertTrue(TextStyle.simple(name) && TextStyle.text(name).equals("Fang") && TextStyle.colour(name).isEmpty() && TextStyle.flag(name, "bold") == null,
                "plain text wasn't read as plain text");
        JsonElement gold = TextStyle.with(name, "color", new JsonPrimitive("gold"));
        JsonElement upright = TextStyle.with(gold, "italic", new JsonPrimitive(false));
        helper.assertTrue(upright.toString().equals("{\"text\":\"Fang\",\"color\":\"gold\",\"italic\":false}"), "gold and upright was written as " + upright);
        helper.assertTrue(TextStyle.simple(upright) && TextStyle.colour(upright).equals("gold") && Boolean.FALSE.equals(TextStyle.flag(upright, "italic")),
                "what was written wasn't read back");
        JsonElement renamed = TextStyle.with(upright, "text", new JsonPrimitive("Claw"));
        helper.assertTrue(renamed.toString().equals("{\"text\":\"Claw\",\"color\":\"gold\",\"italic\":false}"), "retyping the words gave " + renamed);
        JsonElement plain = TextStyle.with(TextStyle.with(upright, "color", null), "italic", null);
        helper.assertTrue(plain.equals(name), "with its colour and style off again it was " + plain);
        for (String more : List.of("{\"translate\": \"item.minecraft.stick\"}", "{\"text\": \"a\", \"extra\": [\"b\"]}", "[\"a\", \"b\"]", "{\"text\": \"a\", \"bold\": \"yes\"}")) {
            helper.assertTrue(!TextStyle.simple(JsonParser.parseString(more)), more + " was taken for text the boxes can show all of");
        }

        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", "
                + "\"name\": \"minecraft:stick\", \"functions\": [{\"function\": \"minecraft:set_name\"}]}]}]}").getAsJsonObject();
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("functions").get(0).getAsJsonObject().add("name", upright);
        // In the game's own shape, as the editor sends a table.
        String sent = LootFormat.forGame(table).toString();
        Component problem = LootOverrides.check(TABLE, sent);
        helper.assertTrue(problem == null, "a name with a colour and a style doesn't load: " + (problem == null ? "" : problem.getString()));
        List<ItemStack> rolled = LootRolls.fill(helper.getLevel(), TABLE, LootOverrides.parse(sent), 3L, 27).stream().filter(stack -> !stack.isEmpty()).toList();
        helper.assertTrue(rolled.size() == 1, "the table gave " + rolled);
        Component given = rolled.get(0).getHoverName();
        helper.assertTrue(given.getString().equals("Fang") && given.getStyle().getColor() != null && given.getStyle().getColor().serialize().equals("gold")
                && !given.getStyle().isItalic(), "the stick was named " + given.getString() + " in the style " + given.getStyle());
        helper.succeed();
    }
}
