package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** What a player is sent while the server builds its loot index from nothing. */
public final class IndexStoreTests {
    private IndexStoreTests() {
    }

    /** Something the player was sent: an index, or how far the build had got. */
    private record Sent(LootIndex index, int done, int total) {
    }

    /**
     * A player who asks while there is no index gets one early, with most of the loot, then word of
     * how far the rest has got, then the finished index, which holds everything the early one did.
     * Someone asking after that is sent the finished one and nothing about progress.
     */
    public static void indexIsHandedOutEarlyThenFinished(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registries = server.registryAccess();
        ServerPlayer player = TestPlayers.mock(helper);
        List<Sent> sent = new CopyOnWriteArrayList<>();
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender((target, channel, buf) -> {
            if (target != player) {
                before.send(target, channel, buf);
            } else if (channel.equals(JesNetwork.INDEX_PROGRESS)) {
                Codecs.IndexProgress progress = Codecs.readIndexProgress(buf);
                sent.add(new Sent(null, progress.done(), progress.total()));
            } else if (channel.equals(JesNetwork.TRANSFER)) {
                Blobs.Part part = Blobs.Part.read(buf);
                if (part.kind() != JesNetwork.KIND_INDEX) {
                    return;
                }
                ByteArrayOutputStream bytes = parts.computeIfAbsent(part.transferId(), id -> new ByteArrayOutputStream());
                bytes.writeBytes(part.data());
                if (part.index() == part.count() - 1) {
                    parts.remove(part.transferId());
                    sent.add(new Sent(Codecs.readIndex(Blobs.fromBytes(registries, Blobs.inflate(bytes.toByteArray()))), 0, 0));
                }
            }
        });
        // As on a server that has only just started, with nothing saved to read: the build under way, if any, is dropped.
        LootIndexStore.stop();
        JesServer.onRequestIndex(player);
        boolean[] askedAgain = new boolean[1];
        helper.succeedWhen(() -> {
            List<Sent> indexes = sent.stream().filter(s -> s.index() != null).toList();
            if (indexes.size() < 2) {
                // The build takes the time it takes, and a test server with nothing else to do gets
                // through its ticks in no time at all, so each look waits a moment first.
                pause();
            }
            helper.assertTrue(indexes.size() >= 2, "the player has been sent " + indexes.size() + " indexes so far");
            // Where the two indexes are among everything sent, found by going through it: looking each
            // one up would find the early index twice when it already held everything the finished one does.
            List<Sent> all = List.copyOf(sent);
            int firstIndex = -1;
            int lastIndex = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).index() != null && firstIndex < 0) {
                    firstIndex = i;
                } else if (all.get(i).index() != null && lastIndex < 0) {
                    lastIndex = i;
                }
            }
            if (!askedAgain[0]) {
                askedAgain[0] = true;
                helper.assertTrue(indexes.size() == 2, "the player was sent " + indexes.size() + " indexes, not an early one and a finished one");
                LootIndex early = indexes.get(0).index();
                LootIndex finished = indexes.get(1).index();
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
                // Asking now gets the finished index, and nothing more about progress.
                int had = sent.size();
                JesServer.onRequestIndex(player);
                List<Sent> again = sent.subList(had, sent.size());
                helper.assertTrue(again.size() == 1 && finished.equals(again.get(0).index()),
                        "asking after the build was answered with " + again.size() + " messages");
            }
            JesNetwork.setServerSender(before);
            // Back to how a started server has it: what is saved is looked for, and built if it isn't there.
            LootIndexStore.refresh(server);
        });
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
