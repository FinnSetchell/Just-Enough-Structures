package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.OddsStore;
import com.finndog.justenoughstructures.server.SavedPreviews;
import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /jes reload} and {@code /jes reload loot} forget the loot index and the kept loot chances, on
 * disk too, and build them again from nothing, saying so when they start and when the index is built.
 * {@code /jes reload} forgets the saved first views as well. Only those who can use Pack tools, and the
 * server console, can use them.
 */
public final class ReloadCommandTests {
    private static final ResourceLocation TABLE = Ids.parse("chests/igloo_chest");
    private static final String KEYS = "commands.justenoughstructures.reload.";

    private ReloadCommandTests() {
    }

    public static void reloadCommandBuildsTheLootIndexAgain(GameTestHelper helper, boolean everything) {
        MinecraftServer server = helper.getLevel().getServer();
        String command = everything ? "jes reload" : "jes reload loot";
        Path folder = JustEnoughStructures.cacheDir().resolve("loot-index");
        List<Component> said = new CopyOnWriteArrayList<>();
        CommandSourceStack console = server.createCommandSourceStack().withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
        int[] stage = {0};
        String[] failed = {null};
        boolean[] indexAsked = {false};
        AtomicReference<LootOdds> odds = new AtomicReference<>();
        AtomicReference<CompletableFuture<?>> saved = new AtomicReference<>();
        AtomicInteger generated = new AtomicInteger();
        Path[] marker = {null};
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        helper.succeedWhen(() -> {
            if (failed[0] != null) {
                LootIndexStore.watchProgress(null);
                helper.assertTrue(false, failed[0]);
            }
            if (stage[0] == 0) {
                boolean ready = LootIndexStore.ready() && OddsStore.isReady();
                if (!ready) {
                    if (!indexAsked[0]) {
                        indexAsked[0] = true;
                        KeptOddsTests.askForTheIndex(helper);
                    }
                    pace.keep();
                }
                helper.assertTrue(ready, "the loot index isn't built yet, before " + command);
                if (odds.get() == null) {
                    JesServer.forgetOdds(TABLE);
                    JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), odds::set);
                }
                helper.assertTrue(odds.get() != null, "the odds of " + TABLE + " haven't been rolled yet");
                // So there's a file of kept odds, and the saved scan is written, before the command forgets them.
                OddsStore.save(server);
                saved.set(LootIndexStore.workSoFarDone());
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                if (!saved.get().isDone()) {
                    pace.keep();
                }
                helper.assertTrue(saved.get().isDone(), "the loot index is still saving, before " + command);
                if (!keptOdds(folder)) {
                    failed[0] = "no loot chances were kept on disk before " + command + ", so this shows nothing";
                    LootIndexStore.watchProgress(null);
                    helper.assertTrue(false, failed[0]);
                }
                if (everything) {
                    Path previews = SavedPreviews.current();
                    if (previews == null) {
                        failed[0] = "no folder of saved first views was in use before " + command;
                        LootIndexStore.watchProgress(null);
                        helper.assertTrue(false, failed[0]);
                    }
                    marker[0] = previews.resolve("reload-command-test.bin");
                    try {
                        Files.write(marker[0], new byte[] {1});
                    } catch (IOException e) {
                        failed[0] = "couldn't write " + marker[0] + ": " + e;
                        LootIndexStore.watchProgress(null);
                        helper.assertTrue(false, failed[0]);
                    }
                }
                LootIndexStore.watchProgress(d -> generated.incrementAndGet());
                server.getCommands().performPrefixedCommand(console, command);
                if (!said(said, everything ? "all" : "loot")) {
                    failed[0] = command + " didn't say it started; it said " + said.stream().map(Component::getString).toList();
                    LootIndexStore.watchProgress(null);
                    helper.assertTrue(false, failed[0]);
                }
                // Kept odds are deleted at once, on the server's thread.
                if (keptOdds(folder)) {
                    failed[0] = "the loot chances kept on disk are still there after " + command;
                    LootIndexStore.watchProgress(null);
                    helper.assertTrue(false, failed[0]);
                }
                stage[0] = 2;
            }
            if (said(said, "failed")) {
                failed[0] = command + " said the loot index couldn't be built again";
                LootIndexStore.watchProgress(null);
                helper.assertTrue(false, failed[0]);
            }
            boolean done = said(said, "done");
            if (!done) {
                pace.keep();
            }
            helper.assertTrue(done, command + " hasn't said the loot index is built again");
            LootIndexStore.watchProgress(null);
            JustEnoughStructures.LOGGER.info("In the test of {}: {} structures generated again; it said {}", command, generated.get(),
                    said.stream().map(Component::getString).toList());
            helper.assertTrue(LootIndexStore.ready(), "the loot index isn't ready after " + command + " said it's built");
            helper.assertTrue(generated.get() > 0, "no structure was generated again after " + command + ", so the index wasn't built from nothing");
            if (everything) {
                helper.assertTrue(!Files.exists(marker[0]), "the saved first views are still there after " + command);
            }
        });
    }

    /** Only those who can use Pack tools see the command: not a player who isn't an operator, but the server console. */
    public static void reloadCommandIsForPackToolsUsers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> reload = server.getCommands().getDispatcher().getRoot().getChild("jes").getChild("reload");
        helper.assertTrue(reload != null, "there's no /jes reload");
        CommandSourceStack player = TestPlayers.mock(helper).createCommandSourceStack();
        helper.assertFalse(reload.canUse(player), "a player who can't use Pack tools can use /jes reload");
        helper.assertTrue(reload.canUse(server.createCommandSourceStack()), "the server console can't use /jes reload");
        helper.succeed();
    }

    private static boolean said(List<Component> said, String key) {
        return said.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents t && t.getKey().equals(KEYS + key));
    }

    private static boolean keptOdds(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (var files = Files.list(folder)) {
            return files.anyMatch(p -> p.toString().endsWith(".odds"));
        } catch (IOException e) {
            return false;
        }
    }
}
