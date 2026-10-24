package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** What a player is sent while the server builds its loot index from nothing, and once that server has stopped. */
public final class IndexStoreTests {
    private IndexStoreTests() {
    }

    /**
     * One message the player was sent: an index with its early flag (null if the flag byte was
     * missing), or progress with done, total and whether the build failed.
     */
    private record Sent(LootIndex index, Boolean early, int done, int total, boolean failed) {
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
        helper.succeedWhen(() -> {
            if (seen.isEmpty()) {
                List<Sent> all = List.copyOf(sent);
                long sentIndexes = all.stream().filter(s -> s.index() != null).count();
                if (sentIndexes < 2) {
                    // The idle test server ticks very fast, so wait a little between looks.
                    pause(50);
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
            // EMI, which loads all over again for each index, goes by these to wait for the finished one.
            helper.assertTrue(Boolean.TRUE.equals(indexes.get(0).early()), "the early index was sent saying " + indexes.get(0).early() + " to it being early");
            helper.assertTrue(Boolean.FALSE.equals(indexes.get(1).early()), "the finished index was sent saying " + indexes.get(1).early() + " to it being early");
            helper.assertTrue(held.equals(List.of(true)), "the server was held up after the early index " + held + ", not once until every structure was done");
            // Told how far along it was straight after the early index, with some still to do, and again as it went.
            List<Sent> between = all.subList(firstIndex + 1, lastIndex);
            helper.assertTrue(!between.isEmpty() && between.stream().allMatch(s -> s.total() > 0 && s.done() < s.total()),
                    "between the two indexes the player was told " + between.stream().map(s -> s.done() + "/" + s.total()).toList());
            helper.assertTrue(!early.tablesByStructure().isEmpty(), "the early index is empty");
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : early.tablesByStructure().entrySet()) {
                helper.assertTrue(finished.tablesByStructure().getOrDefault(entry.getKey(), Set.of()).containsAll(entry.getValue()),
                        entry.getKey() + " had loot in the early index that the finished one doesn't");
            }
            helper.assertTrue(finished.tablesByStructure().size() >= early.tablesByStructure().size(), "the finished index has fewer structures than the early one");
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
        helper.succeedWhen(() -> {
            if (seen.isEmpty()) {
                boolean finished = sent.stream().anyMatch(s -> Boolean.FALSE.equals(s.early()));
                if (!finished) {
                    // The idle test server ticks very fast, so wait a little between looks.
                    pause(50);
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
                pause(50);
            }
            helper.assertTrue(ready, "the next server's index isn't ready yet");
        });
    }

    /**
     * Once a build fails, nothing more is built until the next /reload. A player waiting for the
     * index, or for the rest of an early one, is told it failed: when it fails, and again whenever
     * they ask. Otherwise the browser would show a stuck percentage. Progress still comes first, so
     * older clients can read it. The build is made to fail twice: at its second structure, with no
     * index yet, and once the player has the early one, with the rest still to come.
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
        helper.succeedWhen(() -> {
            if (askedAfter.size() < 2) {
                if (!failed.get()) {
                    // The idle test server ticks very fast, so wait a little between looks.
                    pause(50);
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
            // Asked after the early index, the player is still sent that, as it's all there is; with none, nothing.
            helper.assertTrue(askedAfter.get(0).stream().noneMatch(s -> s.index() != null), told);
            helper.assertTrue(!askedAfter.get(1).isEmpty() && Boolean.TRUE.equals(askedAfter.get(1).get(0).early()), told);
        });
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
                sent.add(new Sent(null, null, progress.done(), progress.total(), progress.failed()));
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
                    LootIndex index = Codecs.readIndex(message);
                    Boolean early = message.readableBytes() == 1 ? Codecs.readIndexEarly(message) : null;
                    sent.add(new Sent(index, early, 0, 0, false));
                }
            }
        };
    }

    /** What a player was sent, as "an index" or how far the build had got, and "failed" when it had. */
    private static List<String> described(List<Sent> sent) {
        return sent.stream().map(s -> s.index() != null ? "an index" : s.done() + "/" + s.total() + (s.failed() ? " failed" : "")).toList();
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
