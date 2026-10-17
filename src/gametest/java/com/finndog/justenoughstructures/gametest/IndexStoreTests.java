package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** What a player is sent while the server builds its loot index from nothing. */
public final class IndexStoreTests {
    private IndexStoreTests() {
    }

    /**
     * Something the player was sent: an index, and what the byte after it said of it being early
     * (null when there wasn't that one byte after it), or how far the build had got.
     */
    private record Sent(LootIndex index, Boolean early, int done, int total) {
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
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
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
        JesNetwork.setServerSender((target, channel, buf) -> {
            if (target != player) {
                before.send(target, channel, buf);
            } else if (channel.equals(JesNetwork.INDEX_PROGRESS)) {
                Codecs.IndexProgress progress = Codecs.readIndexProgress(buf);
                sent.add(new Sent(null, null, progress.done(), progress.total()));
                if (progress.total() > 0) {
                    structures.set(progress.total());
                }
                if (sent.stream().anyMatch(s -> s.index() != null) && holding.compareAndSet(false, true)) {
                    // Told straight after the early index: the server waits here until the rest are done.
                    held.add(await(allDone, 240));
                }
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
                    sent.add(new Sent(index, early, 0, 0));
                }
            }
        });
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
                    // The build takes the time it takes, and a test server with nothing else to do gets
                    // through its ticks in no time at all, so each look waits a moment first.
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
     * Whether an index is early comes after the index itself, where a version from before stops
     * reading, so the two versions still get on: one byte, and from a server before it nothing at
     * all, which reads as not early.
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
            // Read as a version from before reads it, then on as this one does.
            FriendlyByteBuf buf = Blobs.fromBytes(registries, sent);
            helper.assertTrue(Codecs.readIndex(buf).equals(index), "the index didn't read as before");
            helper.assertTrue(Codecs.readIndexEarly(buf) == early, "an index sent as " + (early ? "early" : "finished") + " read as the other");
            helper.assertFalse(buf.isReadable(), "something was left after saying whether the index is early");
        }

        // From a server before early indexes said so, nothing comes after the index.
        FriendlyByteBuf old = Blobs.fromBytes(registries, alone);
        Codecs.readIndex(old);
        helper.assertFalse(Codecs.readIndexEarly(old), "an index with nothing after it read as early");
        helper.succeed();
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
