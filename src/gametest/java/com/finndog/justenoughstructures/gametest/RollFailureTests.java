package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.OddsList;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.server.JesServer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
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
            helper.assertTrue(odds.failedRolls() == ODDS_ROLLS / EVERY,
                    "the odds of " + table + " say " + odds.failedRolls() + " rolls failed, not " + ODDS_ROLLS / EVERY);
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
            helper.assertTrue(odds.failedRolls() == ODDS_ROLLS && odds.unrollable(),
                    "the odds of " + table + " say " + odds.failedRolls() + " of " + ODDS_ROLLS + " rolls failed");
            String shown = key(OddsList.noItems(odds));
            helper.assertTrue(shown.equals("screen.justenoughstructures.cant_roll"), "the odds of " + table + " show " + shown + " in place of items");
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
            helper.assertTrue(odds.rolls() == INDEX_ROLLS - INDEX_ROLLS / EVERY && odds.emptyRolls() == 0 && odds.failedRolls() == INDEX_ROLLS / EVERY,
                    "rolling " + table + " " + INDEX_ROLLS + " times gave " + describe(odds) + ", with " + odds.failedRolls() + " failed");
        });
        helper.succeed();
    }

    /**
     * A roll is read whole before it counts, enchantments and potions included, and what each item
     * came with still reaches the odds: the desert pyramid's books, at their real levels.
     */
    public static void rollsKeepEnchantments(GameTestHelper helper) {
        LootOdds odds = LootRolls.odds(helper.getLevel(), Ids.parse("chests/desert_pyramid"), INDEX_ROLLS, 7L);
        LootOdds.Row books = odds.rows().stream().filter(r -> r.example().is(Items.ENCHANTED_BOOK)).findFirst().orElse(null);
        helper.assertTrue(books != null, "500 rolls of the desert pyramid gave no enchanted book: " + describe(odds));
        Map<String, Integer> variants = books.variants();
        helper.assertTrue(variants.size() > 5 && variants.entrySet().stream()
                        .allMatch(e -> e.getKey().startsWith("enchantment:minecraft:") && e.getValue() >= 1 && e.getValue() <= 5),
                "the desert pyramid's books came with " + variants + " in " + books.hits() + " rolls");
        helper.assertTrue(odds.failedRolls() == 0 && odds.rolls() == INDEX_ROLLS, "rolling the desert pyramid gave " + describe(odds));
        helper.succeed();
    }

    /**
     * How many rolls failed goes to players after the rest of the odds, where an older version stops
     * reading, so either side of the connection can be the older one.
     */
    public static void failedRollsTravel(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ResourceLocation table = Ids.parse("chests/igloo_chest");
        List<LootOdds.Row> rows = List.of(new LootOdds.Row(new ItemStack(Items.STONE), 1797, 1797, 1, 1, new TreeMap<>()));
        byte[] sent = written(registries, new LootOdds(table, 1800, 3, rows, 200));
        LootOdds back = Codecs.readOdds(Blobs.fromBytes(registries, sent));
        helper.assertTrue(back.failedRolls() == 200 && back.rolls() == 1800 && back.emptyRolls() == 3 && back.rows().size() == 1
                && back.rows().get(0).hits() == 1797, "odds with 200 failed rolls read back as " + describe(back) + ", " + back.failedRolls() + " failed");
        // Odds as JES 1.1.0 sends and reads them are these without the count at the end, which is one byte for none.
        byte[] none = written(registries, new LootOdds(table, 1800, 3, rows, 0));
        byte[] older = Arrays.copyOf(none, none.length - 1);
        helper.assertTrue(none[none.length - 1] == 0 && Arrays.equals(Arrays.copyOf(sent, older.length), older),
                "the odds don't start with what an older version reads, so it would misread them");
        LootOdds fromOlder = Codecs.readOdds(Blobs.fromBytes(registries, older));
        helper.assertTrue(fromOlder.failedRolls() == 0 && fromOlder.rolls() == 1800 && fromOlder.emptyRolls() == 3 && fromOlder.rows().size() == 1,
                "odds from an older server read as " + describe(fromOlder) + ", " + fromOlder.failedRolls() + " failed");
        helper.succeed();
    }

    /**
     * Found in, and the recipe viewers' rows drawn from it, say the chance isn't known for a table
     * none of whose rolls worked, rather than that it's under a tenth of a percent.
     */
    public static void foundInSaysWhenNoRollWorks(GameTestHelper helper) {
        ResourceLocation unrolled = id("found_in", "unrolled");
        ResourceLocation rolled = id("found_in", "rolled");
        ResourceLocation waiting = id("found_in", "waiting");
        LootOdds none = new LootOdds(unrolled, 0, 0, List.of(), ODDS_ROLLS);
        LootOdds half = new LootOdds(rolled, 100, 50, List.of(new LootOdds.Row(new ItemStack(Items.STONE), 50, 50, 1, 1, new TreeMap<>())), 0);
        Map<ResourceLocation, LootOdds> known = Map.of(unrolled, none, rolled, half);
        double alone = FoundIn.chance(Items.STONE, Set.of(unrolled), known::get);
        helper.assertTrue(alone == FoundIn.UNKNOWN, "a table none of whose rolls worked gave a chance of " + alone);
        String shown = FoundIn.percent(alone);
        helper.assertTrue(shown.equals(Component.translatable("screen.justenoughstructures.unknown_chance").getString()),
                "a chance that isn't known reads " + shown);
        double pending = FoundIn.chance(Items.STONE, Set.of(unrolled, waiting), known::get);
        helper.assertTrue(pending == -1, "with another table's odds still to come, the chance was " + pending + ", not still waiting");
        double both = FoundIn.chance(Items.STONE, Set.of(unrolled, rolled), known::get);
        helper.assertTrue(both == 0.5, "with a table that rolls and gives stone half the time, the chance was " + both);
        String empty = key(OddsList.noItems(new LootOdds(rolled, ODDS_ROLLS, ODDS_ROLLS, List.of())));
        helper.assertTrue(empty.equals("screen.justenoughstructures.always_empty"), "a table whose rolls all gave nothing shows " + empty);
        helper.succeed();
    }

    private static byte[] written(RegistryAccess registries, LootOdds odds) {
        FriendlyByteBuf buf = Blobs.buffer(registries);
        Codecs.writeOdds(buf, odds);
        return Blobs.bytes(buf);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : component.getString();
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
