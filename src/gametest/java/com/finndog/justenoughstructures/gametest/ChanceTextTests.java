package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** How loot chances read. */
public final class ChanceTextTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:kills_only");

    private ChanceTextTests() {
    }

    /**
     * An item the loot index lists can come even when the rolls never give it, like one only a
     * player's kill drops. Its chance in Found in reads {@code <0.1%} rather than 0.0%, and so does a
     * chance too small for one decimal place. A rare item's chance of at least one in a few
     * containers doesn't read 0% either. Other chances read as they did.
     */
    public static void rareLootIsNeverShownAsNothing(GameTestHelper helper) {
        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": ["
                + "{\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"},"
                + "{\"type\": \"minecraft:item\", \"name\": \"minecraft:diamond\", \"conditions\": [{\"condition\": \"minecraft:killed_by_player\"}]}]}]}")
                .getAsJsonObject();
        String sent = LootFormat.forGame(table).toString();
        Component problem = LootOverrides.check(TABLE, sent);
        helper.assertTrue(problem == null, "the table doesn't load: " + (problem == null ? "" : problem.getString()));
        LootOdds odds = LootRolls.odds(helper.getLevel(), TABLE, LootOverrides.parse(sent), 2000, 5L);
        // Found in's chance for an item: its hits over the rolls, or 0 when the odds are in and it never came.
        double diamond = odds.rows().stream().filter(r -> r.example().is(Items.DIAMOND))
                .mapToDouble(r -> (double) r.hits() / odds.rolls()).max().orElse(0);
        String under = Component.translatable("screen.justenoughstructures.rare_chance").getString();
        String shown = FoundIn.percent(diamond);
        String rarer = FoundIn.percent(0.0002);
        JustEnoughStructures.LOGGER.info("In the test of rare loot: a diamond only a kill drops came out at {} in {} rolls and reads {} in Found in;"
                + " a 0.02% chance reads {}; under a tenth of a percent reads {}", diamond, odds.rolls(), shown, rarer, under);
        helper.assertTrue(odds.rolls() == 2000 && diamond == 0, "the diamond came out at " + diamond + " in " + odds.rolls() + " rolls, not 0 in 2000");
        helper.assertTrue(shown.equals(under), "a diamond only a kill drops reads " + shown + " in Found in, not " + under);
        helper.assertTrue(rarer.equals(under), "a 0.02% chance reads " + rarer + ", not " + under);
        String usual = FoundIn.percent(1 / 2000.0) + " " + FoundIn.percent(0.0123) + " " + FoundIn.percent(0.25) + " " + FoundIn.percent(-1);
        helper.assertTrue(usual.equals("0.1% 1.2% 25% ..."), "one in 2000, 1.23%, 25% and not known yet read " + usual);

        // The chance of at least one in an odds tooltip, worked out as it is there: once in 2000
        // rolls, with two chests.
        float once = 1f / 2000;
        String twoChests = FoundIn.wholePercent(1 - Math.pow(1 - once, 2));
        String few = FoundIn.wholePercent(0.004);
        String most = FoundIn.wholePercent(0.256);
        JustEnoughStructures.LOGGER.info("In the test of rare loot: at least one in two chests reads {}, 0.4% reads {}, 25.6% reads {}", twoChests, few, most);
        helper.assertTrue(twoChests.equals("0.1%") && few.equals("0.4%"), "at least one in two chests reads " + twoChests + " and 0.4% reads " + few);
        helper.assertTrue(most.equals(String.format("%.0f%%", 25.6)), "25.6% reads " + most);
        helper.succeed();
    }
}
