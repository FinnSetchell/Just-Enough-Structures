package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.OddsStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    /** The test mod's table of an apple, which only this test edits. */
    private static final ResourceLocation EDITED = Ids.parse("justenoughstructures_gametest:chests/edited_on_reload");
    private static final String DIAMONDS_ONLY = "{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 1, \"entries\": "
            + "[{\"type\": \"minecraft:item\", \"name\": \"minecraft:diamond\"}]}]}";

    private KeptOddsTests() {
    }

    /**
     * After a /reload while a loot index build is going, kept odds are ready again within moments, not
     * once the build gets past the structure it's on: until then a player's odds are rolled again. The
     * build is held at a structure here, as a big one holds it, until the odds are ready or 10 s go by.
     * Whatever happens the build is let go and the watcher taken off, so no later build waits on it.
     */
    public static void keptOddsReadyWhileABuildGoesOn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        java.util.concurrent.CountDownLatch building = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean over = new AtomicBoolean();
        long started = System.nanoTime();
        // Well inside the test's time out, so the test always ends here and lets the build go.
        long deadline = started + java.util.concurrent.TimeUnit.SECONDS.toNanos(50);
        long[] reloaded = {0};
        // Why the test failed, once it has: it stays failed, though the odds are ready once the build is let go.
        String[] failed = {null};
        LootIndexStore.stop();
        try {
            Files.deleteIfExists(JustEnoughStructures.cacheDir().resolve("loot-index").resolve(LootIndexStore.fingerprintOf(server) + ".bin"));
            Files.deleteIfExists(JustEnoughStructures.cacheDir().resolve("loot-index").resolve(LootIndexStore.fingerprintOf(server) + ".partial"));
        } catch (IOException e) {
            throw new AssertionError("couldn't delete the saved index", e);
        }
        LootIndexStore.watchProgress(d -> {
            // On the index's thread: the first build after this holds at its first structure until let go,
            // and never once the test is over.
            if (over.get()) {
                return;
            }
            building.countDown();
            try {
                release.await(Math.max(0, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Runnable letGo = () -> {
            over.set(true);
            release.countDown();
            LootIndexStore.watchProgress(null);
        };
        // As the server starting does: the build starts at once.
        LootIndexStore.refresh(server);
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        helper.succeedWhen(() -> {
            if (failed[0] != null) {
                helper.assertTrue(false, failed[0]);
            }
            if (System.nanoTime() > deadline) {
                letGo.run();
                failed[0] = reloaded[0] == 0 ? "the build never reached its first structure"
                        : "kept odds weren't ready " + (System.nanoTime() - reloaded[0]) / 1_000_000L + " ms after a /reload, while a build was held";
                helper.assertTrue(false, failed[0]);
            }
            if (reloaded[0] == 0) {
                if (building.getCount() != 0) {
                    pace.keep();
                }
                helper.assertTrue(building.getCount() == 0, "the build hasn't reached its first structure yet");
                // As a /reload does, while the build is held.
                JesServer.reload(server);
                reloaded[0] = System.nanoTime();
            }
            boolean ready = OddsStore.isReady();
            long waited = (System.nanoTime() - reloaded[0]) / 1_000_000L;
            if (!ready && waited < 10_000) {
                pace.keep();
                helper.assertTrue(false, "kept odds aren't ready yet, " + waited + " ms after the /reload");
            }
            letGo.run();
            if (!ready) {
                failed[0] = "kept odds weren't ready " + waited + " ms after a /reload, while a build was held at a structure";
                helper.assertTrue(false, failed[0]);
            }
            JustEnoughStructures.LOGGER.info("In the test of kept odds during a build: ready {} ms after the /reload", waited);
        });
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
     *
     * <p>Odds are only kept while the kept odds are ready, from a start or another test's /reload until
     * the loot index's thread has read them again, which it hands over before the index is ready
     * (LootIndexStore.refresh). So the table is rolled once the index is ready, and rolled again if a
     * /reload came while it rolled and it wasn't kept. After a /reload that changed what it was built
     * from, the index is only built again when a player asks for it, or on a dedicated server, which
     * the test server isn't, so the test asks for it as a player opening the browser does. Each look
     * that finds nothing yet waits a tick's length, so the time out is the same time however fast the
     * ticks go.
     */
    public static void keptOddsReadBackInAnotherWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<LootOdds> first = new AtomicReference<>();
        AtomicBoolean asked = new AtomicBoolean();
        AtomicBoolean indexAsked = new AtomicBoolean();
        // What reading back here found, then with the items renumbered: null where nothing differs.
        AtomicReference<String[]> found = new AtomicReference<>();
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        helper.succeedWhen(() -> {
            // Items aren't renumbered while the loot index's thread may be reading them, and odds aren't kept.
            boolean ready = found.get() != null || LootIndexStore.ready();
            if (!ready) {
                if (!indexAsked.getAndSet(true)) {
                    askForTheIndex(helper);
                }
                pace.keep();
            }
            helper.assertTrue(ready, "the loot index is still being built");
            if (!asked.getAndSet(true)) {
                JesServer.forgetOdds(WITH_KINDS);
                JesServer.odds(server, WITH_KINDS, LootRolls.table(server, WITH_KINDS), first::set);
            }
            if (first.get() == null) {
                pace.keep();
            }
            helper.assertTrue(first.get() != null, "the odds of " + WITH_KINDS + " haven't been rolled yet");
            LootOdds odds = first.get();
            if (found.get() == null) {
                OddsStore.save(server);
                LootOdds kept = OddsStore.readBack(server, WITH_KINDS);
                boolean wasKept = kept != null && kept.rolls() == odds.rolls();
                if (!wasKept) {
                    first.set(null);
                    asked.set(false);
                    pace.keep();
                }
                helper.assertTrue(wasKept, "the odds of " + WITH_KINDS + " from " + odds.rolls() + " rolls weren't kept (the file has "
                        + (kept == null ? "none" : kept.rolls() + " rolls") + "), as after a /reload while they rolled, so they're rolled again");
            }
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

    /**
     * A table edited and loaded again with /reload, or Reload in Pack tools, isn't answered with the
     * odds kept for it before, also when they're asked for at once: before the loot index's thread has
     * looked at what the reload changed, which takes a few seconds in a large pack. Those odds would
     * then be kept until the next /reload. The reload hook is run here as each loader runs it, with the
     * request straight after, so the test doesn't depend on how soon the index's thread gets there.
     */
    public static void keptOddsOfAnEditedTableAreNotAnswered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir;
        try {
            dir = Files.createTempDirectory("jes-kept-odds");
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
        LootOverrides.setFolder(dir);
        AtomicReference<LootOdds> before = new AtomicReference<>();
        AtomicReference<LootOdds> after = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> edited = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> undone = new AtomicReference<>();
        JesServer.forgetOdds(EDITED);
        JesServer.odds(server, EDITED, LootRolls.table(server, EDITED), before::set);
        helper.succeedWhen(() -> {
            helper.assertTrue(before.get() != null, "the odds of " + EDITED + " haven't been rolled yet");
            if (edited.get() == null) {
                helper.assertTrue(only(before.get(), Items.APPLE), "before the edit " + EDITED + " gave " + rows(before.get()));
                LootOverrides.save(server.getResourceManager(), EDITED, DIAMONDS_ONLY);
                edited.set(TestSupport.reload(server).thenRun(() -> {
                    JesServer.reload(server);
                    JesServer.odds(server, EDITED, LootRolls.table(server, EDITED), after::set);
                }));
            }
            helper.assertTrue(edited.get().isDone(), "still reloading with the edit");
            helper.assertTrue(after.get() != null, "the edited table's odds haven't come yet");
            if (undone.get() == null) {
                LootOverrides.remove(EDITED);
                undone.set(TestSupport.reload(server));
            }
            helper.assertTrue(undone.get().isDone(), "still reloading without the edit");
            LootOverrides.setFolder(null);
            JustEnoughStructures.LOGGER.info("In the test of kept odds after an edit: {} gave {}, and after the edit and a /reload {}", EDITED,
                    rows(before.get()), rows(after.get()));
            helper.assertTrue(only(after.get(), Items.DIAMOND), "asked for at once after the edit and a /reload, " + EDITED + " was answered with "
                    + rows(after.get()) + ", the odds kept from before, not the edited table's diamonds");
        });
    }

    /**
     * Asks for the loot index as a player opening the browser does, which builds it if it isn't being
     * built. What it would send the mock player is dropped, as NeoForge refuses to send JES's messages
     * to one, and the player is gone again at once: the build goes ahead without anyone waiting for it.
     */
    private static void askForTheIndex(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender((target, channel, buf) -> {
            if (target != player) {
                before.send(target, channel, buf);
            }
        });
        try {
            JesServer.onRequestIndex(player);
        } finally {
            JesNetwork.setServerSender(before);
            JesServer.left(player);
        }
    }

    private static boolean only(LootOdds odds, Item item) {
        return odds.rows().size() == 1 && odds.rows().get(0).example().is(item);
    }

    private static List<String> rows(LootOdds odds) {
        return odds.rows().stream().map(row -> BuiltInRegistries.ITEM.getKey(row.example().getItem()) + " " + row.hits() + "/" + odds.rolls()).toList();
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
