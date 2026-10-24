package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.OddsList;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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

    /**
     * An odds tooltip's chance of at least one in several containers like this one doesn't make a
     * plural from the container's name: adding an "s" gave "shulker boxs", and other languages can't
     * do it at all. A dedicated server doesn't load JES's lang file, so the line is put together from
     * it here as the game would in English.
     */
    public static void containerNamesAreNeverMadePlural(GameTestHelper helper) {
        // Named as the chest popup names a container: its block's name in lower case.
        String name = Blocks.SHULKER_BOX.getName().getString().toLowerCase(Locale.ROOT);
        LootOdds.Row row = new LootOdds.Row(new ItemStack(Items.DIAMOND), 125, 125, 1, 1, new TreeMap<>());
        List<Component> lines = OddsList.tooltip(row, 0.125f, 3, name);
        TranslatableContents total = lines.stream().map(Component::getContents)
                .filter(c -> c instanceof TranslatableContents t && t.getKey().equals("screen.justenoughstructures.tip_total"))
                .map(TranslatableContents.class::cast).findFirst().orElse(null);
        helper.assertTrue(total != null, "a tooltip for 3 containers has no chance of at least one: " + lines);
        String line = String.format(Locale.ROOT, english(total.getKey()), total.getArgs());
        JustEnoughStructures.LOGGER.info("In the test of container names: with 3 of \"{}\" the chance of at least one reads \"{}\"", name, line);
        helper.assertFalse(line.contains("boxs") || line.contains(name + "s"), "the container's name was made plural: " + line);
        for (Object arg : total.getArgs()) {
            helper.assertTrue(line.contains(String.valueOf(arg)), "\"" + arg + "\" is passed but not shown: " + line);
        }
        // 1 - (1 - 0.125)^3 is 33.0%, to the nearest whole percent.
        helper.assertTrue(line.contains("33%"), "the chance of at least one isn't 33%: " + line);
        helper.succeed();
    }

    /** A line of JES's English lang file, read from the mod's own resources. */
    private static String english(String key) {
        try (InputStream in = JustEnoughStructures.class.getClassLoader().getResourceAsStream("assets/justenoughstructures/lang/en_us.json")) {
            if (in == null) {
                throw new AssertionError("JES's en_us.json isn't on the classpath");
            }
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!lang.has(key)) {
                throw new AssertionError("en_us.json has no " + key);
            }
            return lang.get(key).getAsString();
        } catch (IOException e) {
            throw new AssertionError("couldn't read JES's en_us.json", e);
        }
    }
}
