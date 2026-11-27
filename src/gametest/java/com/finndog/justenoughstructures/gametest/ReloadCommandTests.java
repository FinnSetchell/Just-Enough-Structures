package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.OddsStore;
import com.finndog.justenoughstructures.server.PackToolsServer;
import com.finndog.justenoughstructures.server.SavedPreviews;
import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
 * {@code /jes reload} forgets the saved first views as well. Only what's saved for the game as it's set
 * up now is deleted, not other worlds' or setups'. Only those who can use Pack tools, and the server
 * console, can use them, and not while Pack tools has changes waiting for a /reload.
 */
public final class ReloadCommandTests {
    private static final ResourceLocation TABLE = Ids.parse("chests/igloo_chest");
    private static final String KEYS = "commands.justenoughstructures.reload.";
    /** What a file the test puts where a setup's kept chances go holds, so it can tell whether it was deleted. */
    private static final byte[] DECOY = {7};

    private ReloadCommandTests() {
    }

    /**
     * With {@code unwritable}, the folder the loot index is saved in can't be changed, as when a virus
     * scanner holds its files, so nothing in it can be deleted: the index is still built from nothing.
     * That needs a system where a folder can be made read-only, as Linux; elsewhere it's said in the log.
     */
    public static void reloadCommandBuildsTheLootIndexAgain(GameTestHelper helper, boolean everything, boolean unwritable) {
        MinecraftServer server = helper.getLevel().getServer();
        String command = everything ? "jes reload" : "jes reload loot";
        // Pack tools tests in the batches before this one may leave changes waiting for a /reload,
        // which would make the command refuse.
        PackToolsServer.reloaded();
        Path folder = JustEnoughStructures.cacheDir().resolve("loot-index");
        List<Component> said = new CopyOnWriteArrayList<>();
        CommandSourceStack console = console(server, said);
        int[] stage = {0};
        String[] failed = {null};
        boolean[] indexAsked = {false};
        AtomicReference<LootOdds> odds = new AtomicReference<>();
        AtomicReference<CompletableFuture<?>> saved = new AtomicReference<>();
        AtomicInteger generated = new AtomicInteger();
        Path[] marker = {null};
        Path[] oddsFile = {null};
        // What other setups saved, which stays.
        List<Path> others = new CopyOnWriteArrayList<>();
        Runnable tidy = () -> {
            LootIndexStore.watchProgress(null);
            folder.toFile().setWritable(true);
            for (Path other : others) {
                try {
                    Files.deleteIfExists(other);
                } catch (IOException e) {
                    JustEnoughStructures.LOGGER.warn("Couldn't delete {} after the test of {}", other, command);
                }
            }
        };
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        helper.succeedWhen(() -> {
            if (failed[0] != null) {
                tidy.run();
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
                oddsFile[0] = OddsStore.fileNow();
                if (oddsFile[0] == null || !Files.exists(oddsFile[0])) {
                    failed[0] = "no loot chances were kept on disk before " + command + ", so this shows nothing";
                    helper.assertTrue(false, failed[0]);
                }
                Path previews = SavedPreviews.current();
                if (everything && previews == null) {
                    failed[0] = "no folder of saved first views was in use before " + command;
                    helper.assertTrue(false, failed[0]);
                }
                try {
                    others.add(Files.write(folder.resolve("reload-command-test-other-setup.bin"), new byte[] {1}));
                    others.add(Files.write(folder.resolve("odds-reload-command-test-other-setup.odds"), new byte[] {1}));
                    if (everything) {
                        marker[0] = Files.write(previews.resolve("reload-command-test.bin"), new byte[] {1});
                        Path otherSetup = Files.createDirectories(previews.getParent().resolve("reload-command-test-other-setup"));
                        others.add(Files.write(otherSetup.resolve("view.bin"), new byte[] {1}));
                        others.add(otherSetup);
                    }
                } catch (IOException e) {
                    failed[0] = "couldn't write the test's own files: " + e;
                    helper.assertTrue(false, failed[0]);
                }
                if (unwritable && !(folder.toFile().setWritable(false) && !Files.isWritable(folder))) {
                    JustEnoughStructures.LOGGER.info("In the test of {} with files that can't be deleted: {} can't be made read-only here, so this is the test without",
                            command, folder);
                }
                LootIndexStore.watchProgress(d -> generated.incrementAndGet());
                server.getCommands().performPrefixedCommand(console, command);
                if (!said(said, everything ? "all" : "loot")) {
                    failed[0] = command + " didn't say it started; it said " + said.stream().map(Component::getString).toList();
                    helper.assertTrue(false, failed[0]);
                }
                // Kept odds are deleted at once, on the server's thread.
                if (Files.isWritable(folder) && Files.exists(oddsFile[0])) {
                    failed[0] = "the loot chances kept on disk are still there after " + command;
                    helper.assertTrue(false, failed[0]);
                }
                stage[0] = 2;
            }
            if (said(said, "failed")) {
                failed[0] = command + " said the loot index couldn't be built again";
                helper.assertTrue(false, failed[0]);
            }
            boolean done = said(said, "done");
            if (!done) {
                pace.keep();
            }
            helper.assertTrue(done, command + " hasn't said the loot index is built again");
            List<Path> gone = others.stream().filter(other -> !Files.exists(other)).toList();
            tidy.run();
            helper.assertTrue(gone.isEmpty(), command + " deleted what other setups saved: " + gone);
            JustEnoughStructures.LOGGER.info("In the test of {}{}: {} structures generated again; it said {}", command,
                    unwritable ? " with files that can't be deleted" : "", generated.get(), said.stream().map(Component::getString).toList());
            helper.assertTrue(LootIndexStore.ready(), "the loot index isn't ready after " + command + " said it's built");
            helper.assertTrue(generated.get() > 0, "no structure was generated again after " + command + ", so the index wasn't built from nothing");
            if (everything) {
                helper.assertTrue(!Files.exists(marker[0]), "the saved first views are still there after " + command);
            }
        });
    }

    /**
     * While Pack tools has a change waiting for a /reload, like a chest's new loot table, which the
     * structures only get with one, the command says to /reload first and builds nothing.
     */
    public static void reloadCommandWaitsForPackToolsChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Component> said = new CopyOnWriteArrayList<>();
        PackToolsServer.waiting("reload-command-test");
        try {
            server.getCommands().performPrefixedCommand(console(server, said), "jes reload loot");
        } finally {
            // As the /reload that follows does.
            PackToolsServer.reloaded();
        }
        List<String> text = said.stream().map(Component::getString).toList();
        helper.assertTrue(said(said, "waiting"), "/jes reload loot didn't say Pack tools' changes wait for /reload; it said " + text);
        helper.assertFalse(said(said, "loot"), "/jes reload loot started building with Pack tools' changes waiting; it said " + text);
        helper.succeed();
    }

    /**
     * {@code /jes reload} straight after a /reload that changed what loot rolls depend on, as a loot
     * modifier does (here a test hook does it), before the kept chances
     * and first views have caught up with it: only what's saved for the game as it's set up after the
     * /reload is deleted. The kept chances of the setup before it stay, as another setup's do.
     */
    public static void reloadCommandRightAfterAReloadKeepsOtherSetups(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PackToolsServer.reloaded();
        Path folder = JustEnoughStructures.cacheDir().resolve("loot-index");
        List<Component> said = new CopyOnWriteArrayList<>();
        CommandSourceStack console = console(server, said);
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        boolean[] indexAsked = {false};
        AtomicReference<LootOdds> odds = new AtomicReference<>();
        AtomicReference<CompletableFuture<?>> saved = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> reloading = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> undone = new AtomicReference<>();
        int[] stage = {0};
        int[] reloadsBefore = {0};
        String[] failed = {null};
        // The kept chances of the setup before the edit and after it, the first views' folder in use
        // before the edit, and a file the test put there.
        Path[] before = {null};
        Path[] after = {null};
        Path[] views = {null};
        Path[] marker = {null};
        byte[][] keptBefore = {null};
        boolean[] logged = {false};
        helper.succeedWhen(() -> {
            if (stage[0] == 0) {
                waitUntilSaved(helper, server, pace, indexAsked, odds, saved);
                before[0] = OddsStore.fileNow();
                views[0] = SavedPreviews.current();
                if (before[0] == null || !Files.exists(before[0]) || views[0] == null) {
                    failed[0] = "no loot chances were kept on disk, or no folder of saved first views was in use, before the /reload, so this shows nothing";
                    stage[0] = 3;
                } else {
                    try {
                        keptBefore[0] = Files.readAllBytes(before[0]);
                        marker[0] = Files.write(views[0].resolve("reload-command-test.bin"), new byte[] {1});
                    } catch (IOException e) {
                        failed[0] = "couldn't read or write the test's files: " + e;
                        stage[0] = 3;
                    }
                }
                if (stage[0] == 0) {
                    OddsStore.saltKey("reload-command-test");
                    reloadsBefore[0] = JesServer.reloads();
                    reloading.set(TestSupport.reload(server));
                    stage[0] = 1;
                }
            }
            if (stage[0] == 1) {
                helper.assertTrue(reloading.get().isDone() && JesServer.reloads() > reloadsBefore[0], "still reloading with the change");
                // At once, as a pack maker typing /jes reload straight after /reload would.
                after[0] = folder.resolve("odds-" + OddsStore.keyOf(server) + ".odds");
                if (after[0].equals(before[0])) {
                    failed[0] = "the /reload didn't change the setup the loot chances are kept for, so this shows nothing";
                    stage[0] = 3;
                } else {
                    try {
                        Files.write(after[0], DECOY);
                    } catch (IOException e) {
                        failed[0] = "couldn't write the test's files: " + e;
                        stage[0] = 3;
                    }
                }
                if (stage[0] == 1) {
                    server.getCommands().performPrefixedCommand(console, "jes reload");
                    stage[0] = 2;
                }
            }
            if (stage[0] == 2) {
                if (said(said, "failed")) {
                    failed[0] = "jes reload said the loot index couldn't be built again";
                } else {
                    boolean done = said(said, "done");
                    if (!done) {
                        pace.keep();
                    }
                    helper.assertTrue(done, "jes reload hasn't said the loot index is built again");
                    Path now = SavedPreviews.current();
                    helper.assertTrue(now != null, "no folder of saved first views is in use after jes reload");
                    boolean marked = Files.exists(marker[0]);
                    if (!Arrays.equals(readOrNull(before[0]), keptBefore[0])) {
                        failed[0] = "jes reload straight after a /reload deleted or changed the loot chances kept for the setup before it, " + before[0];
                    } else if (Arrays.equals(readOrNull(after[0]), DECOY)) {
                        failed[0] = "jes reload straight after a /reload left the loot chances kept for the setup in use, " + after[0];
                    } else if (now.equals(views[0]) && marked) {
                        failed[0] = "jes reload straight after a /reload left the saved first views of the setup in use, in " + now;
                    } else if (!now.equals(views[0]) && !marked) {
                        failed[0] = "jes reload straight after a /reload deleted the saved first views of another setup, in " + views[0];
                    }
                }
                stage[0] = 3;
            }
            // Every stage before waits by throwing, so only the last one gets here.
            if (undone.get() == null) {
                OddsStore.saltKey("");
                deleteQuietly(marker[0]);
                if (after[0] != null && Arrays.equals(readOrNull(after[0]), DECOY)) {
                    deleteQuietly(after[0]);
                }
                undone.set(TestSupport.reload(server));
            }
            helper.assertTrue(undone.get().isDone(), "still reloading without the change");
            if (!logged[0]) {
                logged[0] = true;
                JustEnoughStructures.LOGGER.info("In the test of jes reload straight after a /reload: it said {}", said.stream().map(Component::getString).toList());
            }
            if (failed[0] != null) {
                helper.assertTrue(false, failed[0]);
            }
        });
    }

    /**
     * A /reload straight after {@code /jes reload loot}, before the loot index's thread has deleted the
     * saved scan, doesn't make it read that scan back: the index is still built from nothing before
     * the command says it's built.
     */
    public static void reloadCommandBuildsFromNothingThoughAReloadFollows(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PackToolsServer.reloaded();
        List<Component> said = new CopyOnWriteArrayList<>();
        CommandSourceStack console = console(server, said);
        IndexStoreTests.Pace pace = new IndexStoreTests.Pace(server);
        boolean[] indexAsked = {false};
        AtomicReference<LootOdds> odds = new AtomicReference<>();
        AtomicReference<CompletableFuture<?>> saved = new AtomicReference<>();
        AtomicInteger generated = new AtomicInteger();
        int[] stage = {0};
        helper.succeedWhen(() -> {
            if (stage[0] == 0) {
                waitUntilSaved(helper, server, pace, indexAsked, odds, saved);
                LootIndexStore.watchProgress(d -> generated.incrementAndGet());
                server.getCommands().performPrefixedCommand(console, "jes reload loot");
                // As a /reload's hook does, before the index's thread has started on the command.
                JesServer.reload(server);
                stage[0] = 1;
            }
            if (said(said, "failed")) {
                LootIndexStore.watchProgress(null);
                helper.assertTrue(false, "jes reload loot said the loot index couldn't be built again");
            }
            boolean done = said(said, "done");
            if (!done) {
                pace.keep();
            }
            helper.assertTrue(done, "jes reload loot hasn't said the loot index is built again");
            LootIndexStore.watchProgress(null);
            JustEnoughStructures.LOGGER.info("In the test of jes reload loot with a /reload straight after: {} structures generated again; it said {}",
                    generated.get(), said.stream().map(Component::getString).toList());
            helper.assertTrue(generated.get() > 0, "with a /reload straight after jes reload loot, no structure was generated again before it said "
                    + "the index is built, so it was read back from what was saved, not built from nothing");
        });
    }

    /**
     * Waits, as a test does by throwing, for the loot index and kept chances to be ready, a table's
     * chances to be kept on disk and the index's scan to be saved.
     */
    private static void waitUntilSaved(GameTestHelper helper, MinecraftServer server, IndexStoreTests.Pace pace, boolean[] indexAsked,
                                       AtomicReference<LootOdds> odds, AtomicReference<CompletableFuture<?>> saved) {
        if (saved.get() == null) {
            boolean ready = LootIndexStore.ready() && OddsStore.isReady();
            if (!ready) {
                if (!indexAsked[0]) {
                    indexAsked[0] = true;
                    KeptOddsTests.askForTheIndex(helper);
                }
                pace.keep();
            }
            helper.assertTrue(ready, "the loot index isn't built yet");
            if (odds.get() == null) {
                JesServer.forgetOdds(TABLE);
                JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), odds::set);
            }
            helper.assertTrue(odds.get() != null, "the odds of " + TABLE + " haven't been rolled yet");
            OddsStore.save(server);
            saved.set(LootIndexStore.workSoFarDone());
        }
        if (!saved.get().isDone()) {
            pace.keep();
        }
        helper.assertTrue(saved.get().isDone(), "the loot index is still saving");
    }

    private static byte[] readOrNull(Path file) {
        try {
            return file != null && Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't delete {} after a test of jes reload", file);
        }
    }

    /** The server console, its messages kept in {@code said}. */
    private static CommandSourceStack console(MinecraftServer server, List<Component> said) {
        return server.createCommandSourceStack().withSource(new CommandSource() {
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

    // A failure comes wrapped in an empty text, its own as one of that's siblings.
    private static boolean said(List<Component> said, String key) {
        return said.stream().anyMatch(message -> is(message, key) || message.getSiblings().stream().anyMatch(part -> is(part, key)));
    }

    private static boolean is(Component message, String key) {
        return message.getContents() instanceof TranslatableContents t && t.getKey().equals(KEYS + key);
    }

}
