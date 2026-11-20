package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootMath;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chances worked out from a loot table match the game's own rolls of it ({@link LootMath}), on every
 * build's shape of loot table: every vanilla chest table, and the test mod's table of alternatives,
 * groups, sequences, a table within a table, counts that can go below 0 and a pool that only sometimes
 * rolls. Each table is rolled as the odds roll it, and an item's count of rolls is binomial, so one is
 * wrong only when that count is less likely than {@link #UNLIKELY} given the worked-out chance: with a
 * thousand or so items checked, a right answer fails about once in a thousand runs of this test.
 */
public final class LootMathTests {
    private static final ResourceLocation WORKED_OUT = Ids.parse("justenoughstructures_gametest:chests/worked_out");
    private static final int ROLLS = 3000;
    private static final double UNLIKELY = 1e-6;

    private LootMathTests() {
    }

    public static void workedOutChancesMatchRolls(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // The test mod adds a nether star to every ice box as it's rolled (everywhere but Fabric before
        // 26.1), which the table can't show: such tables are what JES still rolls. So it's left out here.
        ResourceLocation changedByCode = Ids.parse("chests/ancient_city_ice_box");
        List<ResourceLocation> tables = new ArrayList<>();
        for (ResourceLocation id : LootIndex.tableIds(server)) {
            if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("chests/") && !id.equals(changedByCode)) {
                tables.add(id);
            }
        }
        helper.assertTrue(tables.size() > 30, "only " + tables.size() + " vanilla chest tables are loaded");
        helper.assertTrue(LootRolls.exists(LootRolls.level(server), WORKED_OUT), WORKED_OUT + " isn't loaded");
        tables.add(WORKED_OUT);
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ResourceLocation table : tables) {
            LootMath.Result math = LootMath.of(server, table);
            if (!math.exact()) {
                wrong.add(table + " has parts that can't be worked out: " + math.unknown());
                continue;
            }
            LootOdds odds = LootRolls.odds(LootRolls.level(server), table, ROLLS, table.hashCode());
            Map<ResourceLocation, Integer> hits = new HashMap<>();
            for (LootOdds.Row row : odds.rows()) {
                hits.put(BuiltInRegistries.ITEM.getKey(row.example().getItem()), row.hits());
            }
            for (ResourceLocation item : hits.keySet()) {
                if (!math.items().containsKey(item)) {
                    wrong.add(table + " rolled " + item + " " + hits.get(item) + " times, which it can't give worked out");
                }
            }
            for (Map.Entry<ResourceLocation, LootMath.Stat> e : math.items().entrySet()) {
                checked++;
                int n = hits.getOrDefault(e.getKey(), 0);
                double tail = LootMath.tail(n, odds.rolls(), e.getValue().chance());
                if (tail < UNLIKELY) {
                    wrong.add(String.format("%s gives %s in %d of %d rolls, worked out as %.4f", table, e.getKey(), n, odds.rolls(),
                            e.getValue().chance()));
                }
            }
            double emptyTail = LootMath.tail(odds.emptyRolls(), odds.rolls(), math.empty());
            if (emptyTail < UNLIKELY) {
                wrong.add(String.format("%s gave nothing in %d of %d rolls, worked out as %.4f", table, odds.emptyRolls(), odds.rolls(), math.empty()));
            }
        }
        JustEnoughStructures.LOGGER.info("Worked out {} tables' chances and checked {} items against {} rolls each", tables.size(), checked, ROLLS);
        helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong.subList(0, Math.min(8, wrong.size()))));
        helper.succeed();
    }

    /**
     * Odds rolled for a table nothing changes as it's rolled come back with the worked-out chances, the
     * rolled examples and their enchantments. Odds with an item the table can't give, as a loot modifier
     * would add, stay as rolled.
     */
    public static void workedOutOddsReplaceRolledOnesOnlyWhenTheyFit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation pyramid = Ids.parse("chests/desert_pyramid");
        LootOdds rolled = LootRolls.odds(LootRolls.level(server), pyramid, 2000, pyramid.hashCode());
        LootOdds worked = LootMath.withWorkedOutChances(server, rolled);
        helper.assertTrue(worked.workedOut(), pyramid + "'s odds weren't worked out");
        helper.assertTrue(worked.rolls() == LootMath.WORKED_OUT_ROLLS, pyramid + "'s worked-out odds are out of " + worked.rolls());
        LootOdds.Row book = worked.rows().stream().filter(r -> r.example().getItem() == Items.ENCHANTED_BOOK).findFirst().orElse(null);
        helper.assertTrue(book != null && !book.variants().isEmpty(), "the worked-out enchanted book lost the enchantments its rolls came with");
        List<LootOdds.Row> plusOne = new ArrayList<>(rolled.rows());
        plusOne.add(new LootOdds.Row(new ItemStack(Items.NETHER_STAR), 7, 7, 1, 1, new java.util.TreeMap<>()));
        LootOdds changed = new LootOdds(pyramid, rolled.rolls(), rolled.emptyRolls(), plusOne, 0);
        helper.assertFalse(LootMath.withWorkedOutChances(server, changed).workedOut(),
                "odds with an item the table can't give were worked out anyway");
        helper.succeed();
    }
}
