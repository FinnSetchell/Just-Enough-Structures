package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.SpareTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** What a player is sent while the server builds its loot index from nothing, and once that server has stopped. */
public final class IndexStoreTests {
    private IndexStoreTests() {
    }

    /**
     * One message the player was sent: an index with its early flag (null if the flag byte was
     * missing), or progress with done, total, whether the build failed and the items chest loot can give.
     */
    private record Sent(LootIndex index, Boolean early, int done, int total, boolean failed, Set<ResourceLocation> chestItems) {
    }

    /**
     * The items sent with progress, for recipe viewers' waiting rows, are what chest loot tables can
     * give: everything in a desert pyramid's chest, a diamond among them, but not a blaze rod or
     * dirt, which only a blaze and a block drop.
     */
    public static void chestItemsComeFromChestLootOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Set<ResourceLocation> items = LootIndex.chestItems(server);
        Set<ResourceLocation> pyramid = LootIndex.itemsIn(server, Ids.parse("chests/desert_pyramid"), new HashSet<>());
        helper.assertTrue(pyramid.contains(Ids.parse("diamond")), "a desert pyramid's chest can't give a diamond here, only " + pyramid);
        helper.assertTrue(items.containsAll(pyramid), "chest loot was said to give " + items.size() + " items, not all of a desert pyramid's chest " + pyramid);
        for (String[] other : new String[][]{{"entities/blaze", "blaze_rod"}, {"blocks/dirt", "dirt"}}) {
            ResourceLocation item = Ids.parse(other[1]);
            helper.assertTrue(LootIndex.itemsIn(server, Ids.parse(other[0]), new HashSet<>()).contains(item), other[0] + " doesn't give " + item + " here");
            helper.assertFalse(items.contains(item), "chest loot was said to give " + item + ", which only " + other[0] + " gives");
        }
        JustEnoughStructures.LOGGER.info("In the test of what chest loot can give: {} items", items.size());
        helper.succeed();
    }

    /**
     * A player who asks while there is no index gets one early, with most of the loot and saying
     * so, then word of how far the rest has got, then the finished index, which holds everything the
     * early one did and says it's finished. Someone asking after that is sent the finished one and
     * nothing about progress.
     *
     * <p>Straight after the early index the server is held up until every structure is done, as a
     * busy server can be, so word of progress that was on its way by then only goes out once they
     * are. It still has to say there's more to come, or the early index would pass for the finished one.
     */
    public static void indexIsHandedOutEarlyThenFinished(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registries = server.registryAccess();
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        Set<ResourceLocation> chestItems = LootIndex.chestItems(server);
        // How many structures there are, from what the player is told, and how holding the server up went.
        AtomicInteger structures = new AtomicInteger(Integer.MAX_VALUE);
        AtomicBoolean holding = new AtomicBoolean();
        AtomicBoolean waited = new AtomicBoolean();
        CountDownLatch allDone = new CountDownLatch(1);
        List<Boolean> held = new CopyOnWriteArrayList<>();
        LootIndexStore.watchProgress(d -> {
            // On the index's thread. Progress is said every couple of seconds at most, so once the
            // server is held, a wait as long has the next structure's word of it on its way.
            if (holding.get() && waited.compareAndSet(false, true)) {
                pause(2100);
            }
            if (d >= structures.get()) {
                allDone.countDown();
            }
        });
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender(recorder(player, registries, before, sent, progress -> {
            if (progress.total() > 0) {
                structures.set(progress.total());
            }
            if (sent.stream().anyMatch(s -> s.index() != null) && holding.compareAndSet(false, true)) {
                // Told straight after the early index: the server waits here until the rest are done.
                held.add(await(allDone, 240));
            }
        }));
        // As on a server that has only just started, with nothing saved to read: the build under way, if any, is dropped.
        LootIndexStore.stop();
        JesServer.onRequestIndex(player);
        // Everything sent by the time both indexes were there, then what asking once more brought.
        List<List<Sent>> seen = new ArrayList<>();
        Pace pace = new Pace(server);
        helper.succeedWhen(() -> {
            if (seen.isEmpty()) {
                List<Sent> all = List.copyOf(sent);
                long sentIndexes = all.stream().filter(s -> s.index() != null).count();
                if (sentIndexes < 2) {
                    pace.keep();
                }
                helper.assertTrue(sentIndexes >= 2, "the player has been sent " + sentIndexes + " indexes so far");
                // Asking now gets the finished index, and nothing more about progress.
                int had = sent.size();
                JesServer.onRequestIndex(player);
                seen.add(all);
                seen.add(List.copyOf(sent.subList(had, sent.size())));
                JesNetwork.setServerSender(before);
                LootIndexStore.watchProgress(null);
                // Otherwise the mock player would be sent every index after this test, which NeoForge refuses to do.
                JesServer.left(player);
                // Back to how a started server has it: what is saved is looked for, and built if it isn't there.
                LootIndexStore.refresh(server);
            }
            // Each look checks what was seen that once, so a check that fails goes on failing until the
            // test's time is up, rather than being passed over by the next look.
            List<Sent> all = seen.get(0);
            List<Sent> indexes = all.stream().filter(s -> s.index() != null).toList();
            helper.assertTrue(indexes.size() == 2, "the player was sent " + indexes.size() + " indexes, not an early one and a finished one");
            // Where the two indexes are among everything sent, found by going through it: looking each
            // one up would find the early index twice when it already held everything the finished one does.
            int firstIndex = -1;
            int lastIndex = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).index() != null && firstIndex < 0) {
                    firstIndex = i;
                } else if (all.get(i).index() != null && lastIndex < 0) {
                    lastIndex = i;
                }
            }
            LootIndex early = indexes.get(0).index();
            LootIndex finished = indexes.get(1).index();
            // Recipe viewers show a waiting row on each item chest loot can give, which comes with progress.
            helper.assertTrue(!chestItems.isEmpty() && all.subList(firstIndex + 1, lastIndex).stream().allMatch(s -> s.chestItems().equals(chestItems)),
                    "between the two indexes the player was told of " + all.subList(firstIndex + 1, lastIndex).stream().map(s -> s.chestItems().size()).toList()
                            + " items chest loot can give, not the " + chestItems.size() + " it can");
            // EMI, which loads all over again for each index, goes by these to wait for the finished one.
            helper.assertTrue(Boolean.TRUE.equals(indexes.get(0).early()), "the early index was sent saying " + indexes.get(0).early() + " to it being early");
            helper.assertTrue(Boolean.FALSE.equals(indexes.get(1).early()), "the finished index was sent saying " + indexes.get(1).early() + " to it being early");
            helper.assertTrue(held.equals(List.of(true)), "the server was held up after the early index " + held + ", not once until every structure was done");
            // Told how far along it was straight after the early index, with some still to do, and again as it went.
            List<Sent> between = all.subList(firstIndex + 1, lastIndex);
            helper.assertTrue(!between.isEmpty() && between.stream().allMatch(s -> s.total() > 0 && s.done() < s.total()),
                    "between the two indexes the player was told " + between.stream().map(s -> s.done() + "/" + s.total()).toList());
            helper.assertTrue(!early.tablesByStructure().isEmpty(), "the early index is empty");
            // It comes before anything is generated, with what pools hold, like the ancient city's
            // chests, and what's known of vanilla's structures without pools, like buried treasure.
            ResourceLocation ancientCity = Ids.parse("ancient_city");
            ResourceLocation buriedTreasure = Ids.parse("buried_treasure");
            helper.assertTrue(early.tablesByStructure().containsKey(ancientCity), "the early index lacks the ancient city's chests, which its pools hold");
            helper.assertTrue(early.tablesByStructure().getOrDefault(buriedTreasure, Set.of()).equals(finished.tablesByStructure().get(buriedTreasure)),
                    "buried treasure has " + early.tablesByStructure().get(buriedTreasure) + " in the early index and "
                            + finished.tablesByStructure().get(buriedTreasure) + " in the finished one");
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : early.tablesByStructure().entrySet()) {
                helper.assertTrue(finished.tablesByStructure().getOrDefault(entry.getKey(), Set.of()).containsAll(entry.getValue()),
                        entry.getKey() + " had loot in the early index that the finished one doesn't");
            }
            helper.assertTrue(finished.tablesByStructure().size() >= early.tablesByStructure().size(), "the finished index has fewer structures than the early one");
            // What structures place themselves comes too, like the elytra in an End city ship's item frame.
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : early.placedByStructure().entrySet()) {
                helper.assertTrue(finished.placedByStructure().getOrDefault(entry.getKey(), Set.of()).containsAll(entry.getValue()),
                        entry.getKey() + " placed items in the early index that the finished one doesn't");
            }
            Set<ResourceLocation> city = finished.placedByStructure().getOrDefault(Ids.parse("end_city"), Set.of());
            helper.assertTrue(city.contains(Ids.parse("elytra")), "the finished index lists the End city as placing " + city + ", without its elytra");
            List<Sent> again = seen.get(1);
            helper.assertTrue(again.size() == 1 && finished.equals(again.get(0).index()) && Boolean.FALSE.equals(again.get(0).early()),
                    "asking after the build was answered with " + again.size() + " messages, " + again);
        });
    }

    /**
     * In singleplayer, opening another world stops the server and starts the next one in the same
     * game, so what the index kept from before is still there. A player who asks the next server
     * before it has checked its own index is told that nothing is known yet, not how far the build
     * before got: told that one was all but done, the browser would show its percentage, and EMI
     * would stop waiting for the index the next server is about to send. That index then follows.
     */
    public static void progressFromTheLastWorldIsForgotten(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender(recorder(player, server.registryAccess(), before, sent, progress -> {
        }));
        // The last world's index, built from nothing and to the end.
        LootIndexStore.stop();
        JesServer.onRequestIndex(player);
        // What asking the next server brought.
        List<List<Sent>> seen = new ArrayList<>();
        Pace pace = new Pace(server);
        helper.succeedWhen(() -> {
            if (seen.isEmpty()) {
                boolean finished = sent.stream().anyMatch(s -> Boolean.FALSE.equals(s.early()));
                if (!finished) {
                    pace.keep();
                }
                helper.assertTrue(finished, "the last world's index isn't finished yet");
                // What the server stopping and the next one starting do with the index (JesServer's
                // stop and reload). The next one checks its index in the background, so asked in
                // the same tick it hasn't yet.
                LootIndexStore.stop();
                LootIndexStore.refresh(server);
                int had = sent.size();
                JesServer.onRequestIndex(player);
                seen.add(List.copyOf(sent.subList(had, sent.size())));
                JesNetwork.setServerSender(before);
                // Otherwise the mock player would be sent every index after this test, which NeoForge refuses to do.
                JesServer.left(player);
            }
            List<Sent> told = seen.get(0);
            helper.assertTrue(told.size() == 1 && told.get(0).index() == null && told.get(0).done() == 0 && told.get(0).total() == 0,
                    "asked before the next server had checked its index, the player was told "
                            + told.stream().map(s -> s.index() != null ? "an index" : s.done() + "/" + s.total()).toList() + ", not 0/0");
            // Its own index is there before the test ends, so no build goes on into the tests after it.
            boolean ready = LootIndexStore.ready();
            if (!ready) {
                pace.keep();
            }
            helper.assertTrue(ready, "the next server's index isn't ready yet");
        });
    }

    /**
     * Once a build fails, nothing more is built until the next /reload. A player waiting for the
     * index, or for the rest of an early one, is told it failed: when it fails, and again whenever
     * they ask. Otherwise the browser would show a stuck percentage. Progress still comes first, so
     * older clients can read it. The build is made to fail twice, each time once the player has the
     * early index, which comes before anything is generated, with the rest still to come: at its
     * second structure, and at its first.
     */
    public static void aFailedBuildSaysSo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        // 0 while the first build is made to fail at its second structure, 1 for the one after the early index.
        AtomicInteger round = new AtomicInteger();
        AtomicBoolean failed = new AtomicBoolean();
        // How much the player had been sent when each build was made to fail.
        List<Integer> failedAt = new CopyOnWriteArrayList<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender(recorder(player, server.registryAccess(), before, sent, progress -> {
        }));
        // A stale build, like one an earlier test can leave running. This watcher holds it as it
        // reports a finished structure, until this test's build is started, then passes the report on
        // to the watcher this test checks with.
        AtomicReference<IntConsumer> watching = new AtomicReference<>(d -> {
        });
        CountDownLatch going = new CountDownLatch(1);
        CountDownLatch replaced = new CountDownLatch(1);
        LootIndexStore.watchProgress(d -> {
            going.countDown();
            await(replaced, 60);
            watching.get().accept(d);
        });
        LootIndexStore.stop();
        JesServer.onRequestIndex(player);
        helper.assertTrue(await(going, 120), "the build from before didn't get through a structure");
        sent.clear();
        int[] last = {0};
        IntConsumer failing = d -> {
            // On the index's thread. What this throws ends the build there, as any error would. The
            // first round fails at its second structure: the stale build can still report one structure
            // after this one has started, but only once, and only this build goes from 1 to 2.
            boolean due = round.get() == 0 ? d == 2 && last[0] == 1 : sent.stream().anyMatch(s -> s.index() != null);
            last[0] = d;
            if (due && failed.compareAndSet(false, true)) {
                failedAt.add(sent.size());
                throw new IllegalStateException("A loot index build made to fail by a game test");
            }
        };
        watching.set(failing);
        LootIndexStore.watchProgress(failing);
        // As on a server that has only just started, with nothing saved to read.
        LootIndexStore.stop();
        replaced.countDown();
        JesServer.onRequestIndex(player);
        // For each failure: what the player was told as it failed, then what asking after it brought.
        List<List<Sent>> whenFailed = new ArrayList<>();
        List<List<Sent>> askedAfter = new ArrayList<>();
        int[] looks = {-1};
        AtomicBoolean reported = new AtomicBoolean();
        Pace pace = new Pace(server);
        helper.succeedWhen(() -> {
            if (askedAfter.size() < 2) {
                if (!failed.get()) {
                    pace.keep();
                }
                helper.assertTrue(failed.get(), "the build hasn't been made to fail yet in round " + round.get());
                if (looks[0] < 0) {
                    // The index's thread logs what was thrown and then leaves word of it for the
                    // server, which takes that up between ticks, up to a few ticks late.
                    pause(500);
                }
                looks[0]++;
                helper.assertTrue(looks[0] > 5, "the server may not have heard the build failed yet");
                int had = sent.size();
                whenFailed.add(List.copyOf(sent.subList(failedAt.get(round.get()), had)));
                JesServer.onRequestIndex(player);
                askedAfter.add(List.copyOf(sent.subList(had, sent.size())));
                looks[0] = -1;
                if (round.incrementAndGet() == 1) {
                    // A build from nothing again, which this time fails after the early index.
                    sent.clear();
                    failed.set(false);
                    LootIndexStore.stop();
                    JesServer.onRequestIndex(player);
                } else {
                    JesNetwork.setServerSender(before);
                    LootIndexStore.watchProgress(null);
                    // Back to how a started server has it, with nothing failed and no build going on into the tests after it.
                    LootIndexStore.stop();
                    LootIndexStore.refresh(server);
                }
            }
            helper.assertTrue(askedAfter.size() == 2, "the build has been made to fail " + askedAfter.size() + " times so far, not twice");
            String told = "when the build failed at its second structure the player was told " + described(whenFailed.get(0))
                    + " and asking after it brought " + described(askedAfter.get(0)) + "; when it failed after the early index, "
                    + described(whenFailed.get(1)) + " and " + described(askedAfter.get(1));
            if (!reported.getAndSet(true)) {
                // The game's summary of a failed test leaves out the reason on some versions.
                JustEnoughStructures.LOGGER.info("In the test of a failed loot index build: {}", told);
            }
            for (int i = 0; i < 2; i++) {
                // Told as it failed, and in answer to asking after: every word of progress then says it failed.
                helper.assertTrue(whenFailed.get(i).stream().anyMatch(Sent::failed), told);
                List<Sent> progress = askedAfter.get(i).stream().filter(s -> s.index() == null).toList();
                helper.assertTrue(!progress.isEmpty() && progress.stream().allMatch(s -> s.failed() && s.total() > 0 && s.done() < s.total()), told);
            }
            // Asked after the early index, the player is still sent that, as it's all there is.
            for (int i = 0; i < 2; i++) {
                helper.assertTrue(!askedAfter.get(i).isEmpty() && Boolean.TRUE.equals(askedAfter.get(i).get(0).early()), told);
            }
        });
    }

    /**
     * What a loot modifier adds to a chest is listed in Found in, under the structures with that
     * chest. On Forge and NeoForge the test mod's modifier puts a nether star in every ancient city
     * ice box, and on Fabric from 26.1 its listener to Fabric's drops event does, which reading the
     * ice box's table can't show. The index built from nothing lists it, the index saved with it lists
     * it when read back, and so does the index the next start sends. Fabric before 26.1 has no such
     * event, so there the test passes at once.
     */
    public static void lootModifierItemsAreListed(GameTestHelper helper) {
        //? if fabric && <26.1 {
        JustEnoughStructures.LOGGER.info("In the test of loot modifiers in Found in: nothing to check on Fabric before 26.1");
        helper.succeed();
        //?} else {
        /*MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation city = Ids.parse("ancient_city");
        ResourceLocation iceBox = Ids.parse("chests/ancient_city_ice_box");
        ResourceLocation star = Ids.parse("nether_star");
        // Only worth checking with the modifier at work, and with the table not giving a star itself.
        LootOdds odds = LootRolls.odds(helper.getLevel(), iceBox, 10, 1L);
        helper.assertTrue(odds.rows().stream().anyMatch(r -> r.example().is(Items.NETHER_STAR)),
                "the test mod put no nether star in an ice box, which gave " + odds.rows().stream().map(r -> r.example().getItem()).toList());
        helper.assertFalse(LootIndex.itemsIn(server, iceBox, new HashSet<>()).contains(star), "the ice box's own table lists a nether star");
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender(recorder(player, server.registryAccess(), before, sent, progress -> {
        }));
        // Built from nothing and saved, as on a server's first start: what was saved for these sources goes first.
        String fingerprint = LootIndexStore.fingerprintOf(server);
        Path saved = JustEnoughStructures.cacheDir().resolve("loot-index");
        try {
            Files.deleteIfExists(saved.resolve(fingerprint + ".bin"));
        } catch (IOException e) {
            throw new AssertionError("couldn't delete the saved index", e);
        }
        LootIndexStore.stop();
        LootIndexStore.refresh(server);
        JesServer.onRequestIndex(player);
        // The finished index the build sent, the saved one read back, and the one sent after the next start.
        Map<String, LootIndex> seen = new LinkedHashMap<>();
        boolean[] logged = new boolean[1];
        Pace pace = new Pace(server);
        helper.succeedWhen(() -> {
            if (seen.isEmpty()) {
                LootIndex built = sent.stream().filter(s -> Boolean.FALSE.equals(s.early())).map(Sent::index).findFirst().orElse(null);
                if (built == null) {
                    pace.keep();
                }
                helper.assertTrue(built != null, "the index isn't finished yet");
                seen.put("built", built);
                // As the server's next start does: what was saved is read rather than built again.
                LootIndexStore.stop();
                LootIndexStore.refresh(server);
            }
            if (seen.size() == 1) {
                boolean ready = LootIndexStore.ready();
                if (!ready) {
                    pace.keep();
                }
                helper.assertTrue(ready, "the saved index hasn't been read back yet");
                StructureScan scan = LootIndexStore.read(saved, fingerprint);
                seen.put("saved", scan == null ? null : LootIndex.of(server, scan));
                int had = sent.size();
                JesServer.onRequestIndex(player);
                seen.put("sent after the next start", sent.subList(had, sent.size()).stream().map(Sent::index).filter(Objects::nonNull).findFirst().orElse(null));
                JesNetwork.setServerSender(before);
                // Otherwise the mock player would be sent every index after this test, which NeoForge refuses to do.
                JesServer.left(player);
            }
            List<String> wrong = new ArrayList<>();
            seen.forEach((which, index) -> {
                if (index == null) {
                    wrong.add("there was no " + which + " index");
                } else if (!index.tablesByStructure().getOrDefault(city, Set.of()).contains(iceBox)) {
                    wrong.add("the " + which + " index has no ice box in the ancient city");
                } else if (!index.itemsByTable().getOrDefault(iceBox, Set.of()).contains(star)) {
                    wrong.add("the " + which + " index lists " + index.itemsByTable().get(iceBox) + " in the ice box, without the nether star");
                }
            });
            if (!logged[0]) {
                logged[0] = true;
                // The game's summary of a failed test leaves out the reason on some versions.
                JustEnoughStructures.LOGGER.info("In the test of loot modifiers in Found in: {}", wrong.isEmpty() ? "each index lists the nether star" : String.join("; ", wrong));
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        });
        *///?}
    }

    /**
     * A build cut short, here by a /reload once its early index is out and a few structures are
     * generated, keeps what it had done: the next start finds it, sends it as an early index, and
     * generates only the structures still to do, not every one again. The index it finishes lists what
     * a build from nothing does, which is made first to compare with. The build keeps what it has done
     * after every structure here, not once a minute, so the cut always finds some of it kept.
     */
    public static void aCutShortBuildIsKept(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender(recorder(player, server.registryAccess(), before, sent, progress -> {
        }));
        Path folder = JustEnoughStructures.cacheDir().resolve("loot-index");
        String fingerprint = LootIndexStore.fingerprintOf(server);
        int structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE).size();
        // 0: a build from nothing. 1: waiting for what it saves after sending its index. 2: built again
        // from nothing, to be cut short once its early index is out. 3: the start after the cut.
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger generatedAfterCut = new AtomicInteger();
        AtomicInteger generatedBeforeCut = new AtomicInteger();
        LootIndexStore.watchProgress(d -> {
            if (stage.get() == 2) {
                generatedBeforeCut.set(d);
            } else if (stage.get() == 3) {
                generatedAfterCut.incrementAndGet();
            }
        });
        long keptEvery = LootIndex.keepSoFarEvery(0);
        int[] from = new int[1];
        LootIndex[] fromNothing = new LootIndex[1];
        CompletableFuture<?>[] savedAll = new CompletableFuture<?>[1];
        boolean[] logged = new boolean[1];
        forgetSaved(folder, fingerprint);
        LootIndexStore.stop();
        LootIndexStore.refresh(server);
        JesServer.onRequestIndex(player);
        Pace pace = new Pace(server);
        helper.succeedWhen(() -> {
            if (stage.get() == 0) {
                LootIndex built = finished(sent, 0);
                if (built == null) {
                    pace.keep();
                }
                helper.assertTrue(built != null, "the build from nothing isn't finished yet");
                fromNothing[0] = built;
                // Its scan is saved again with the rolls after the index is sent. Deleted before that,
                // it would be there again for the next build to find.
                LootIndexStore.stop();
                savedAll[0] = LootIndexStore.workSoFarDone();
                stage.set(1);
            }
            if (stage.get() == 1) {
                boolean saved = savedAll[0].isDone();
                if (!saved) {
                    pace.keep();
                }
                helper.assertTrue(saved, "the build from nothing is still saving what it found");
                forgetSaved(folder, fingerprint);
                from[0] = sent.size();
                stage.set(2);
                LootIndexStore.refresh(server);
                JesServer.onRequestIndex(player);
            }
            if (stage.get() == 2) {
                boolean early = sent.subList(from[0], sent.size()).stream().anyMatch(s -> s.index() != null && Boolean.TRUE.equals(s.early()));
                if (!early) {
                    pace.keep();
                }
                helper.assertTrue(early || finished(sent, from[0]) == null,
                        "the second build sent a finished index and no early one, so it found an index saved after the first was deleted");
                helper.assertTrue(early, "the second build hasn't sent its early index yet");
                if (generatedBeforeCut.get() < 3) {
                    pace.keep();
                }
                helper.assertTrue(generatedBeforeCut.get() >= 3, "the second build has generated " + generatedBeforeCut.get() + " structures so far, not 3");
                from[0] = sent.size();
                stage.set(3);
                // As a /reload does: the build under way stops, and what's saved is looked for.
                LootIndexStore.refresh(server);
            }
            LootIndex resumed = finished(sent, from[0]);
            if (resumed == null) {
                pace.keep();
            }
            helper.assertTrue(resumed != null, "the start after the cut hasn't finished its index yet");
            boolean earlyAgain = sent.subList(from[0], sent.size()).stream().anyMatch(s -> s.index() != null && Boolean.TRUE.equals(s.early()));
            if (!logged[0]) {
                logged[0] = true;
                JesNetwork.setServerSender(before);
                LootIndexStore.watchProgress(null);
                LootIndex.keepSoFarEvery(keptEvery);
                // Otherwise the mock player would be sent every index after this test, which NeoForge refuses to do.
                JesServer.left(player);
                // The game's summary of a failed test leaves out the reason on some versions.
                JustEnoughStructures.LOGGER.info("In the test of a cut-short build: after the cut {} of {} structures were generated, {} early index was sent first, "
                                + "and the finished index {} the one built from nothing", generatedAfterCut.get(), structures, earlyAgain ? "an" : "no",
                        resumed.equals(fromNothing[0]) ? "matches" : "differs from");
            }
            helper.assertTrue(generatedAfterCut.get() < structures, "after the cut " + generatedAfterCut.get() + " of " + structures + " structures were generated");
            helper.assertTrue(earlyAgain, "the start after the cut sent no early index of what was kept");
            helper.assertTrue(resumed.tablesByStructure().equals(fromNothing[0].tablesByStructure()),
                    "after the cut the index has the loot tables of " + resumed.tablesByStructure().size() + " structures, not the same as the "
                            + fromNothing[0].tablesByStructure().size() + " a build from nothing has");
            helper.assertTrue(resumed.itemsByTable().equals(fromNothing[0].itemsByTable()), "after the cut the index lists other items than a build from nothing");
            helper.assertTrue(resumed.placedByStructure().equals(fromNothing[0].placedByStructure()),
                    "after the cut the index lists other items structures place than a build from nothing");
        });
    }

    /** The first finished index among what was sent from {@code from} on, or null. */
    private static LootIndex finished(List<Sent> sent, int from) {
        return sent.subList(from, sent.size()).stream().filter(s -> s.index() != null && Boolean.FALSE.equals(s.early())).map(Sent::index).findFirst().orElse(null);
    }

    /** Deletes the index saved under this fingerprint, and what a build cut short kept, so the next start builds from nothing. */
    private static void forgetSaved(Path folder, String fingerprint) {
        try {
            Files.deleteIfExists(folder.resolve(fingerprint + ".bin"));
            Files.deleteIfExists(folder.resolve(fingerprint + ".partial"));
        } catch (IOException e) {
            throw new AssertionError("couldn't delete the saved index", e);
        }
    }

    /**
     * Whether an index is early is one byte after the index itself, where older clients stop reading,
     * so the versions still work together. An older server sends nothing there, which reads as not
     * early.
     */
    public static void earlyMarkComesAfterTheIndex(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ResourceLocation chest = Ids.parse("chests/igloo_chest");
        LootIndex index = new LootIndex(Map.of(Ids.parse("igloo"), Set.of(chest)), Map.of(chest, Set.of(Ids.parse("golden_apple"))));
        byte[] alone = Blobs.toBytes(registries, buf -> Codecs.writeIndex(buf, index));
        for (boolean early : new boolean[]{true, false}) {
            byte[] sent = Blobs.toBytes(registries, buf -> {
                Codecs.writeIndex(buf, index);
                Codecs.writeIndexEarly(buf, early);
            });
            helper.assertTrue(sent.length == alone.length + 1 && sent[alone.length] == (early ? 1 : 0),
                    "an index sent as " + (early ? "early" : "finished") + " took " + (sent.length - alone.length) + " more bytes than the index alone, not a 1 or a 0");
            // Read as an older client reads it, then the rest as this version does.
            FriendlyByteBuf buf = Blobs.fromBytes(registries, sent);
            helper.assertTrue(Codecs.readIndex(buf).equals(index), "the index didn't read as before");
            helper.assertTrue(Codecs.readIndexEarly(buf) == early, "an index sent as " + (early ? "early" : "finished") + " read as the other");
            helper.assertFalse(buf.isReadable(), "something was left after saying whether the index is early");
        }

        // An older server that doesn't mark early indexes sends nothing after the index.
        FriendlyByteBuf old = Blobs.fromBytes(registries, alone);
        Codecs.readIndex(old);
        helper.assertFalse(Codecs.readIndexEarly(old), "an index with nothing after it read as early");
        helper.succeed();
    }

    /**
     * Stands in for sending to {@code player}: what it's sent goes in {@code sent}, and
     * {@code onProgress} is told of each word of progress once it's there, on the thread that sent
     * it. Anything for anyone else goes on to {@code others}.
     */
    private static JesNetwork.ServerSender recorder(ServerPlayer player, RegistryAccess registries, JesNetwork.ServerSender others,
            List<Sent> sent, Consumer<Codecs.IndexProgress> onProgress) {
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        return (target, channel, buf) -> {
            if (target != player) {
                others.send(target, channel, buf);
            } else if (channel.equals(JesNetwork.INDEX_PROGRESS)) {
                Codecs.IndexProgress progress = Codecs.readIndexProgress(buf);
                sent.add(new Sent(null, null, progress.done(), progress.total(), progress.failed(), progress.chestItems()));
                onProgress.accept(progress);
            } else if (channel.equals(JesNetwork.TRANSFER)) {
                Blobs.Part part = Blobs.Part.read(buf);
                if (part.kind() != JesNetwork.KIND_INDEX) {
                    return;
                }
                ByteArrayOutputStream bytes = parts.computeIfAbsent(part.transferId(), id -> new ByteArrayOutputStream());
                bytes.writeBytes(part.data());
                if (part.index() == part.count() - 1) {
                    parts.remove(part.transferId());
                    FriendlyByteBuf message = Blobs.fromBytes(registries, Blobs.inflate(bytes.toByteArray()));
                    LootIndex read = Codecs.readIndex(message);
                    Boolean early = message.isReadable() ? Codecs.readIndexEarly(message) : null;
                    // Then what structures place, as the client reads it.
                    LootIndex index = new LootIndex(read.tablesByStructure(), read.itemsByTable(), Codecs.readIndexPlaced(message));
                    sent.add(new Sent(index, early, 0, 0, false, Set.of()));
                }
            }
        };
    }

    /** What a player was sent, as "an index" or how far the build had got, and "failed" when it had. */
    private static List<String> described(List<Sent> sent) {
        return sent.stream().map(s -> s.index() != null ? "an index" : s.done() + "/" + s.total() + (s.failed() ? " failed" : "")).toList();
    }

    /**
     * Keeps a waiting test's looks at least a tick's length apart, as the game paces a real server. The
     * test server never waits between ticks, so with nothing to do it would get through a test's time
     * limit, counted in ticks, in moments while the index's thread works. The wait goes between ticks,
     * where a real server waits. In the tick it would count as the tick's own time, and the index's
     * rolls, which only get what a tick leaves over ({@link SpareTime}), would be left almost nothing.
     */
    static final class Pace {
        private final MinecraftServer server;
        // Server thread only: when the last wait ended, and whether the next one is queued.
        private long last = System.nanoTime();
        private boolean queued;

        Pace(MinecraftServer server) {
            this.server = server;
        }

        /** At a look that found nothing yet: once this tick is over, waits until a tick's length after the last wait ended. */
        void keep() {
            if (queued) {
                return;
            }
            queued = true;
            betweenTicks(server, () -> {
                long left = last + SpareTime.tickNanos(server) - System.nanoTime();
                if (left > 0) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(left);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                last = System.nanoTime();
                queued = false;
            });
        }
    }

    /** Runs {@code task} on the server thread once this tick is over, before the next one starts. */
    private static void betweenTicks(MinecraftServer server, Runnable task) {
        //? if >=26.1 {
        /*server.schedule(new TickTask(server.getTickCount(), task));
        *///?} else {
        server.tell(new TickTask(server.getTickCount(), task));
        //?}
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits for {@code latch} up to {@code seconds}, and says whether it got there. */
    private static boolean await(CountDownLatch latch, long seconds) {
        try {
            return latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
