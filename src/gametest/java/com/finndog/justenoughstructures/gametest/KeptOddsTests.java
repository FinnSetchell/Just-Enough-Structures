package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.OddsStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if fabric {
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.impl.registry.sync.RemapException;
import net.fabricmc.fabric.impl.registry.sync.RemappableRegistry;
//?}

/**
 * Odds rolled for players are kept on disk under the table's hash, so after a restart the same table's
 * odds are answered at once instead of rolled again.
 */
public final class KeptOddsTests {
    private static final ResourceLocation TABLE = Ids.parse("chests/igloo_chest");
    /** Kinds of enchanted book among other loot, so a file of its odds holds items both in rows and in kinds. */
    private static final ResourceLocation WITH_KINDS = Ids.parse("chests/ancient_city");

    private KeptOddsTests() {
    }

    public static void keptOddsAnswerWithoutRolling(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<LootOdds> first = new AtomicReference<>();
        JesServer.forgetOdds(TABLE);
        JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), first::set);
        helper.succeedWhen(() -> {
            helper.assertTrue(first.get() != null, "the odds of " + TABLE + " haven't been rolled yet");
            OddsStore.save(server);
            Path file = OddsStore.fileNow();
            helper.assertTrue(file != null && Files.exists(file), "no file of kept odds after rolling " + TABLE + ": " + file);
            // As after a restart: nothing in memory, and the answer has to come without a single tick of rolling.
            JesServer.forgetOdds(TABLE);
            AtomicReference<LootOdds> again = new AtomicReference<>();
            JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), again::set);
            helper.assertTrue(again.get() != null, "the kept odds of " + TABLE + " weren't used: they're being rolled again");
            helper.assertTrue(again.get().rows().size() == first.get().rows().size() && again.get().rolls() == first.get().rolls(),
                    "the kept odds of " + TABLE + " differ from the rolled ones");
        });
    }

    /**
     * Odds kept on disk read back as the same items, with the same kinds, in another world of the same
     * game, which can number items its own way: Forge gives each world the numbers it was made with, and
     * from 1.21 a world's datapacks number their enchantments. Every build reads the file back as a
     * start does. On Fabric it's read again with two of the items given each other's numbers, as
     * Fabric's own registry sync numbers them for a world or a server that has them that way.
     */
    public static void keptOddsReadBackInAnotherWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<LootOdds> first = new AtomicReference<>();
        // What reading back here found, then with the items renumbered: null where nothing differs.
        AtomicReference<String[]> found = new AtomicReference<>();
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        JesServer.forgetOdds(WITH_KINDS);
        JesServer.odds(server, WITH_KINDS, LootRolls.table(server, WITH_KINDS), first::set);
        helper.succeedWhen(() -> {
            helper.assertTrue(first.get() != null, "the odds of " + WITH_KINDS + " haven't been rolled yet");
            // Items aren't renumbered while the loot index's thread may be reading them.
            boolean ready = LootIndexStore.ready();
            if (!ready) {
                pace.keep();
            }
            helper.assertTrue(ready, "the loot index is still being built");
            LootOdds odds = first.get();
            helper.assertTrue(odds.rows().size() >= 2 && odds.rows().stream().anyMatch(row -> !row.kinds().isEmpty()),
                    WITH_KINDS + " gave no rows with kinds, so this shows nothing: " + odds.rows().size() + " rows");
            Item one = odds.rows().get(0).example().getItem();
            Item two = odds.rows().get(1).example().getItem();
            ResourceLocation a = BuiltInRegistries.ITEM.getKey(one);
            ResourceLocation b = BuiltInRegistries.ITEM.getKey(two);
            if (found.get() == null) {
                OddsStore.save(server);
                String[] differences = {difference(odds, OddsStore.readBack(server, WITH_KINDS)), null};
                boolean renumbered = withSwapped(one, two,
                        () -> differences[1] = difference(odds, OddsStore.readBack(server, WITH_KINDS)));
                found.set(differences);
                JustEnoughStructures.LOGGER.info("In the test of kept odds in another world: {} rows read back from {}{}", odds.rows().size(), OddsStore.fileNow(),
                        renumbered ? ", and again with " + a + " and " + b + " numbered the other way round" : "; this loader can't renumber items while the server runs");
            }
            helper.assertTrue(found.get()[0] == null, "the kept odds of " + WITH_KINDS + " read back from " + OddsStore.fileNow() + " differ: " + found.get()[0]);
            helper.assertTrue(found.get()[1] == null, "with " + a + " and " + b + " numbered the other way round, as another world can have them, the kept odds of "
                    + WITH_KINDS + " read back differently: " + found.get()[1]);
        });
    }

    /** How {@code b} differs from {@code a}, row by row and kind by kind, or null when it doesn't. */
    private static String difference(LootOdds a, LootOdds b) {
        if (b == null) {
            return "nothing was read back";
        }
        if (a.rolls() != b.rolls() || a.emptyRolls() != b.emptyRolls() || a.workedOut() != b.workedOut() || a.rows().size() != b.rows().size()) {
            return a.rows().size() + " rows from " + a.rolls() + " rolls, read back as " + b.rows().size() + " from " + b.rolls();
        }
        for (int i = 0; i < a.rows().size(); i++) {
            LootOdds.Row x = a.rows().get(i);
            LootOdds.Row y = b.rows().get(i);
            if (!ItemStack.matches(x.example(), y.example()) || x.hits() != y.hits() || x.total() != y.total() || x.min() != y.min()
                    || x.max() != y.max() || !x.variants().equals(y.variants())) {
                return "row " + i + " was " + x.example() + " in " + x.hits() + " rolls, read back as " + y.example() + " in " + y.hits();
            }
            if (!x.kinds().keySet().equals(y.kinds().keySet())) {
                return "row " + i + "'s kinds were " + x.kinds().keySet() + ", read back as " + y.kinds().keySet();
            }
            for (String key : x.kinds().keySet()) {
                LootOdds.Row.Kind p = x.kinds().get(key);
                LootOdds.Row.Kind q = y.kinds().get(key);
                if (p.hits() != q.hits() || !ItemStack.matches(p.example(), q.example())) {
                    return "row " + i + "'s kind " + key + " was " + p.example() + ", read back as " + q.example();
                }
            }
        }
        return null;
    }

    /**
     * Gives two items each other's numbers, runs {@code then}, and puts them back, all on the server
     * thread. Says whether it could: only Fabric can renumber items while the server runs.
     */
    private static boolean withSwapped(Item a, Item b, Supplier<?> then) {
        //? if fabric {
        Object2IntMap<ResourceLocation> now = new Object2IntOpenHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            now.put(BuiltInRegistries.ITEM.getKey(item), BuiltInRegistries.ITEM.getId(item));
        }
        Object2IntMap<ResourceLocation> swapped = new Object2IntOpenHashMap<>(now);
        swapped.put(BuiltInRegistries.ITEM.getKey(a), now.getInt(BuiltInRegistries.ITEM.getKey(b)));
        swapped.put(BuiltInRegistries.ITEM.getKey(b), now.getInt(BuiltInRegistries.ITEM.getKey(a)));
        remap(swapped);
        try {
            check(BuiltInRegistries.ITEM.getId(a) == now.getInt(BuiltInRegistries.ITEM.getKey(b)), "the items weren't renumbered");
            then.get();
        } finally {
            remap(now);
        }
        check(BuiltInRegistries.ITEM.getId(a) == now.getInt(BuiltInRegistries.ITEM.getKey(a)), "the items' numbers weren't put back");
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    private static void check(boolean holds, String problem) {
        if (!holds) {
            throw new IllegalStateException(problem);
        }
    }

    //? if fabric && >=26.1 {
    /*private static void remap(Object2IntMap<ResourceLocation> ids) {
        try {
            ((RemappableRegistry) BuiltInRegistries.ITEM).remap(ids, RemappableRegistry.RemapMode.AUTHORITATIVE);
        } catch (RemapException e) {
            throw new IllegalStateException("couldn't renumber the items", e);
        }
    }
    *///?} else if fabric {
    private static void remap(Object2IntMap<ResourceLocation> ids) {
        try {
            ((RemappableRegistry) BuiltInRegistries.ITEM).remap("JES test", ids, RemappableRegistry.RemapMode.AUTHORITATIVE);
        } catch (RemapException e) {
            throw new IllegalStateException("couldn't renumber the items", e);
        }
    }
    //?}
}
