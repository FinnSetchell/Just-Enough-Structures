package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.key;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A preview is made on a separate thread, by other mods' structure code. If that code throws
 * anything, the player who asked is told the preview failed instead of being left looking at
 * "Generating".
 */
public final class PreviewGuardTests {
    /** Seeds no other test uses, so no saved or kept preview answers for them and no other test's captures fail. */
    private static final long FIRST_SEED = 147_000L;

    private PreviewGuardTests() {
    }

    public static void previewThatDiesIsAnswered(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        ResourceLocation igloo = Ids.of("minecraft", "igloo");
        // What other mods' code has thrown: an exception, a class that isn't there, a stack that ran out, and anything else.
        List<Supplier<Throwable>> failures = List.of(
                () -> new IllegalStateException("a test's structure"),
                () -> new NoClassDefFoundError("a/test/Structure"),
                StackOverflowError::new,
                () -> new AssertionError("a test's structure"));
        Map<Integer, byte[]> answers = new ConcurrentHashMap<>();
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        JesNetwork.ServerSender sender = JesNetwork.serverSender();
        JesNetwork.setServerSender((target, channel, buf) -> {
            if (target != player || !channel.equals(JesNetwork.TRANSFER)) {
                sender.send(target, channel, buf);
                return;
            }
            Blobs.Part part = Blobs.Part.read(buf);
            ByteArrayOutputStream bytes = parts.computeIfAbsent(part.transferId(), transfer -> new ByteArrayOutputStream());
            bytes.writeBytes(part.data());
            if (part.index() == part.count() - 1) {
                parts.remove(part.transferId());
                if (part.kind() == JesNetwork.KIND_CAPTURE) {
                    answers.put(part.requestId(), bytes.toByteArray());
                }
            }
        });
        JesServer.Capturer real = JesServer.capturer();
        JesServer.setCapturer((server, structure, seed, preview, unwanted) -> {
            if (seed < FIRST_SEED || seed >= FIRST_SEED + failures.size()) {
                return real.capture(server, structure, seed, preview, unwanted);
            }
            Throwable failure = failures.get((int) (seed - FIRST_SEED)).get();
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            throw (Error) failure;
        });
        // One at a time: a player's newer preview takes the place of the one before.
        int[] asked = {1};
        JesServer.onRequestCapture(player, 1, igloo, FIRST_SEED, true, 0);
        helper.succeedWhen(() -> {
            while (true) {
                byte[] answer = answers.get(asked[0]);
                if (answer == null) {
                    // The answer comes from another thread, and the idle test server ticks very fast, so wait a little between looks.
                    pause();
                }
                helper.assertTrue(answer != null, "the preview that died of failure " + asked[0] + " of " + failures.size() + " hasn't been answered");
                CaptureResult result = Codecs.unpackCapture(helper.getLevel().registryAccess(), answer).result();
                helper.assertTrue(!result.succeeded() && !result.temporary() && key(result.reason()).endsWith("error.went_wrong"),
                        "the preview that died of failure " + asked[0] + " was answered with " + result.error());
                if (asked[0] == failures.size()) {
                    break;
                }
                asked[0]++;
                JesServer.onRequestCapture(player, asked[0], igloo, FIRST_SEED + asked[0] - 1, true, 0);
            }
            JesServer.setCapturer(real);
            JesNetwork.setServerSender(sender);
            JesServer.left(player);
        });
    }

    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
