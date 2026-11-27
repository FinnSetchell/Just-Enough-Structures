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
