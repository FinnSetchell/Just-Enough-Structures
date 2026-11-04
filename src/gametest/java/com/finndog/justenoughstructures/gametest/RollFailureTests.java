package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * Other mods' loot can throw now and then, or on every roll when it reads something JES's rolls
 * leave out, like where the chest is. A roll that throws says nothing about what the table gives, so
 * it mustn't end the rolling, count as a roll that gave nothing, or be kept as the table's odds.
 */
public final class RollFailureTests {
    /** How many times the server rolls a table for its odds. */
    private static final int ODDS_ROLLS = 2000;
    /** How many times the loot index rolls each table. */
    private static final int INDEX_ROLLS = 500;
    /** Every this many rolls, one throws. */
    private static final int EVERY = 10;

    private RollFailureTests() {
    }

    /** The odds of a table that throws now and then are from every roll that didn't, and are kept as they are. */
    public static void oddsSkipRollsThatThrow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<String, AtomicReference<LootOdds>> first = new LinkedHashMap<>();
        Map<String, AtomicReference<LootOdds>> again = new LinkedHashMap<>();
        LootGuardTests.failures().forEach((name, failure) -> {
            AtomicReference<LootOdds> got = new AtomicReference<>();
            first.put(name, got);
            again.put(name, new AtomicReference<>());
            JesServer.odds(server, id("sometimes", name), ThrowingLoot.sometimes(EVERY, failure), got::set);
        });
        helper.succeedWhen(() -> first.forEach((name, got) -> {
            String table = "a table that throws " + name + " on every " + EVERY + "th roll";
            LootOdds odds = got.get();
            helper.assertTrue(odds != null, "the odds of " + table + " haven't arrived");
            if (again.get(name).get() == null) {
                // Asked again, the kept odds come back without rolling: a table that rolls differently shows which.
                JesServer.odds(server, id("sometimes", name), ThrowingLoot.sometimes(2, LootGuardTests.failures().get(name)), again.get(name)::set);
            }
            String kept = again.get(name).get() == odds ? ", and asking again gave the same odds, kept from then"
                    : ", and asking again gave " + describe(again.get(name).get());
            helper.assertTrue(odds.rolls() == ODDS_ROLLS - ODDS_ROLLS / EVERY,
                    "the odds of " + table + " are from " + describe(odds) + ", not the " + (ODDS_ROLLS - ODDS_ROLLS / EVERY)
                            + " rolls of " + ODDS_ROLLS + " that worked" + kept);
            helper.assertTrue(odds.emptyRolls() == 0 && odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.STONE)
                    && odds.rows().get(0).hits() == odds.rolls(), "the odds of " + table + " don't give stone in every roll that worked: " + describe(odds));
            helper.assertTrue(again.get(name).get() == odds, "the odds of " + table + " weren't kept" + kept);
        }));
    }

    /**
     * A table that throws on every roll, as one does that reads where the chest is, has odds that
     * say none of its rolls worked, not that it never gives anything.
     */
    public static void oddsSayWhenNoRollWorks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<String, AtomicReference<LootOdds>> got = new LinkedHashMap<>();
        LootGuardTests.failures().forEach((name, failure) -> {
            AtomicReference<LootOdds> odds = new AtomicReference<>();
            got.put(name, odds);
            JesServer.odds(server, id("always", name), ThrowingLoot.table(failure), odds::set);
        });
        helper.succeedWhen(() -> got.forEach((name, arrived) -> {
            String table = "a table that throws " + name + " on every roll";
            LootOdds odds = arrived.get();
            helper.assertTrue(odds != null, "the odds of " + table + " haven't arrived");
            // The browser shows odds with no rows as "Never gives anything" (InfoPanel, ChestPopup).
            helper.assertTrue(odds.rolls() == 0 && odds.emptyRolls() == 0 && odds.rows().isEmpty(),
                    "the odds of " + table + " read as never giving anything, from " + describe(odds) + ", not as no roll working");
        }));
    }

    /** The rolls the loot index makes go on past a roll that throws, and count only those that worked. */
    public static void rollsGoOnPastOneThatThrows(GameTestHelper helper) {
        LootGuardTests.failures().forEach((name, failure) -> {
            String table = "a table that throws " + name + " on every " + EVERY + "th roll";
            LootOdds odds = null;
            Throwable got = null;
            try {
                odds = LootRolls.odds(helper.getLevel(), id("index", name), ThrowingLoot.sometimes(EVERY, failure), INDEX_ROLLS, 1L);
            } catch (Throwable t) {
                got = t;
            }
            helper.assertTrue(got == null, "rolling " + table + " stopped at the first roll that threw: " + got);
            helper.assertTrue(odds.rolls() == INDEX_ROLLS - INDEX_ROLLS / EVERY && odds.emptyRolls() == 0,
                    "rolling " + table + " " + INDEX_ROLLS + " times gave " + describe(odds));
        });
        helper.succeed();
    }

    private static ResourceLocation id(String kind, String failure) {
        return Ids.parse("justenoughstructures_gametest:" + kind + "_" + failure.replace(' ', '_'));
    }

    private static String describe(LootOdds odds) {
        if (odds == null) {
            return "nothing yet";
        }
        String rows = odds.rows().stream().map(r -> r.example().getItem() + " in " + r.hits())
                .collect(Collectors.joining(", "));
        return String.format(Locale.ROOT, "%d rolls (%d gave nothing; %s)", odds.rolls(), odds.emptyRolls(), rows.isEmpty() ? "no items" : rows);
    }
}
