package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.DIAMONDS_ONLY;
import static com.finndog.justenoughstructures.gametest.TestSupport.expect;
import static com.finndog.justenoughstructures.gametest.TestSupport.firstBlock;
import static com.finndog.justenoughstructures.gametest.TestSupport.key;
import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsServer;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.SavedPreviews;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.finndog.justenoughstructures.server.Uploads;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** What Pack tools changes on the server besides loot tables: its rules, and what players are told about structures. */
public final class PackToolsTests {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final ResourceLocation IGLOO_TABLE = Ids.parse("chests/igloo_chest");
    /** Has a chest with a loot table. */
    private static final ResourceLocation TOWER = Ids.parse("pillager_outpost/watchtower");
    /** Always has chests, and nothing in the test datapack keeps them secret. */
    private static final ResourceLocation DESERT_PYRAMID = Ids.parse("desert_pyramid");
    /** What {@link #containers} gives for a view that isn't saved, and for a capture that failed. */
    private static final int NOT_SAVED = -1;
    private static final int FAILED = -2;

    private PackToolsTests() {
    }

    private static Path temp(String name) {
        try {
            return Files.createTempDirectory(name);
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
    }

    /**
     * Rules from Pack tools are saved in server.json5 and used straight away, kept to what the file
     * allows. Only using changed containers waits for a /reload, and switching it back undoes that.
     */
    public static void rulesSaveAndApply(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path configRoot = JustEnoughStructures.configDir().getParent();
        ServerConfig.Settings before = ServerConfig.get();
        JustEnoughStructures.setConfigDir(temp("jes-config"));
        try {
            ServerConfig.Settings asked = new ServerConfig.Settings(Set.of(IGLOO), Set.of("examplemod", "Not A Mod!"), 0, 9, false,
                    new ServerConfig.PackTools(List.of("Steve", "not a name!", "steve"), 7), !before.containerChanges());
            Component reply = PackToolsServer.saveRules(server, asked);
            helper.assertTrue(key(reply).endsWith("tools.rules_saved"), "saving the rules got " + reply.getString());

            ServerConfig.Settings now = ServerConfig.get();
            helper.assertTrue(now.hides(IGLOO) && now.hides(Ids.of("examplemod", "tower")), "the hidden structure and mod aren't hidden");
            helper.assertTrue(now.hiddenMods().equals(Set.of("examplemod")), "a mod name that can't be one was kept: " + now.hiddenMods());
            helper.assertTrue(now.locatePermission() == 0 && now.teleportPermission() == 4, "levels weren't kept from 0 to 4: "
                    + now.locatePermission() + ", " + now.teleportPermission());
            helper.assertTrue(now.packTools().players().equals(List.of("Steve")), "names weren't checked: " + now.packTools().players());
            helper.assertTrue(now.packTools().permissionLevel() == 4 && !now.showLootLocations(), "the rest wasn't kept: " + now);
            helper.assertTrue(ServerConfig.read(ServerConfig.file()).equals(now), "server.json5 doesn't read back as what's in use");
            helper.assertTrue(PackToolsServer.state(server).pending().contains(PackToolsState.RULES),
                    "switching changed containers should wait for a /reload");

            PackToolsServer.saveRules(server, before);
            helper.assertTrue(!PackToolsServer.state(server).pending().contains(PackToolsState.RULES),
                    "switching changed containers back should leave nothing waiting");
            helper.assertTrue(PackToolsServer.state(server).hidden().stream().allMatch(entry -> before.hides(entry.id())),
                    "a structure shown again is still listed as hidden");
        } finally {
            JustEnoughStructures.setConfigDir(configRoot);
            ServerConfig.set(before);
            JesServer.structuresChanged(server);
        }
        helper.succeed();
    }

    /**
     * The settings screen's server settings are put in use the way Pack tools' rules are, so changes
     * waiting for a /reload are still waiting and an undone patch is still known. With no world open
     * in the game they're only written to server.json5.
     */
    public static void settingsScreenKeepsWaitingChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        Path configRoot = JustEnoughStructures.configDir().getParent();
        ServerConfig.Settings before = ServerConfig.get();
        JustEnoughStructures.setConfigDir(temp("jes-config"));
        LootOverrides.setFolder(temp("jes-overrides"));
        ContainerPatches.load();
        try {
            ServerConfig.set(packToolsFor(0));
            StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
            helper.assertTrue(tower != null, "the watchtower template didn't load");
            StructureTemplate.StructureBlockInfo chest = firstBlock(tower, info -> info.nbt() != null && Nbt.hasString(info.nbt(), "LootTable"));
            helper.assertTrue(chest != null, "the watchtower has no container with a loot table");
            String original = Nbt.string(chest.nbt(), "LootTable");
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), IGLOO_TABLE), "container.saved");
            expect(helper, JesServer.unpatchContainer(player, TOWER, chest.pos()), "container.removed");
            expect(helper, JesServer.saveTable(player, IGLOO_TABLE, DIAMONDS_ONLY), "override.saved");
            helper.assertTrue(original.equals(ContainerPatches.ownTable(TOWER, chest.pos(), IGLOO_TABLE.toString())),
                    "the undone patch isn't known before the settings are saved");

            // The igloo hidden, locating for everyone, and changed containers switched off.
            ServerConfig.Settings asked = new ServerConfig.Settings(Set.of(IGLOO), Set.of(), 0, 2, true, ServerConfig.PackTools.level(0), false);
            PackToolsServer.saveFromSettingsScreen(server, asked);

            List<String> wrong = new ArrayList<>();
            ServerConfig.Settings now = ServerConfig.get();
            if (!now.hides(IGLOO) || now.locatePermission() != 0 || now.containerChanges()) {
                wrong.add("the settings aren't in use: " + now);
            }
            if (!ServerConfig.read(ServerConfig.file()).equals(now)) {
                wrong.add("server.json5 doesn't read back as what's in use");
            }
            if (JesServer.visibleCatalog(server).stream().anyMatch(entry -> entry.id().equals(IGLOO))) {
                wrong.add("the hidden igloo is still listed");
            }
            Set<String> pending = PackToolsServer.state(server).pending();
            if (!pending.contains(PackToolsState.tableKey(IGLOO_TABLE))) {
                wrong.add("the loot table edit stopped waiting for a /reload");
            }
            if (!pending.contains(PackToolsState.chestKey(TOWER, chest.pos()))) {
                wrong.add("the undone container stopped waiting for a /reload");
            }
            if (!pending.contains(PackToolsState.RULES)) {
                wrong.add("switching changed containers off isn't waiting for a /reload");
            }
            if (!original.equals(ContainerPatches.ownTable(TOWER, chest.pos(), IGLOO_TABLE.toString()))) {
                wrong.add("the undone patch was forgotten");
            }
            helper.assertTrue(wrong.isEmpty(), "saving the settings screen: " + String.join("; ", wrong) + " (waiting: " + pending + ")");

            ServerConfig.Settings later = new ServerConfig.Settings(Set.of(), Set.of(), 3, 3, false, ServerConfig.PackTools.level(0), true);
            PackToolsServer.saveFromSettingsScreen(null, later);
            helper.assertTrue(ServerConfig.read(ServerConfig.file()).equals(later), "with no world open, server.json5 wasn't written");
            helper.assertTrue(ServerConfig.get().equals(now), "with no world open, the settings in use changed");
        } finally {
            JustEnoughStructures.setConfigDir(configRoot);
            LootOverrides.setFolder(null);
            ContainerPatches.reloaded();
            PackToolsServer.reloaded();
            ServerConfig.set(before);
            JesServer.structuresChanged(server);
        }
        helper.succeed();
    }

    /**
     * Notes and keeping loot a secret go in the JES pack as a structure info file and apply at
     * once. Put back the way its mod has it, the structure gets no file at all.
     */
    public static void structureNotesGoInThePack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = temp("jes-overrides");
        LootOverrides.setFolder(dir);
        try {
            Component reply = PackToolsServer.saveStructure(server, IGLOO, "  Mind the trapdoor.  ", true);
            helper.assertTrue(key(reply).endsWith("tools.structure_saved"), "saving notes got " + reply.getString());
            Path file = dir.resolve("data/minecraft/justenoughstructures/structures/igloo.json");
            helper.assertTrue(Files.exists(file), "no structure info file in the pack");
            helper.assertTrue(Files.exists(dir.resolve("pack.mcmeta")), "the folder wasn't made a datapack");
            StructureInfo info = StructureInfo.forStructure(IGLOO);
            helper.assertTrue(info.notes() != null && info.notes().getString().equals("Mind the trapdoor.") && info.hideLootLocations(),
                    "the notes aren't in use straight away: " + info);
            helper.assertTrue(StructureInfo.parse(com.google.gson.JsonParser.parseString(Files.readString(file))).equals(info),
                    "the file doesn't read back as what's in use");
            PackToolsState.Written written = PackToolsServer.state(server).structures().get(IGLOO);
            helper.assertTrue(written != null && written.fromPack(), "Pack tools doesn't see its own notes");
            helper.assertTrue(JesServer.hidesLootLocations(IGLOO), "the loot isn't kept a secret");

            Component missing = PackToolsServer.saveStructure(server, Ids.parse("no_such_structure"), "x", false);
            helper.assertTrue(key(missing).endsWith("tools.no_structure"), "notes for a structure that isn't there got " + missing.getString());

            PackToolsServer.saveStructure(server, IGLOO, "", false);
            helper.assertTrue(!Files.exists(file), "the igloo has nothing said about it by its mod, so it shouldn't need a file");
            helper.assertTrue(StructureInfo.forStructure(IGLOO).equals(StructureInfo.NONE), "the notes are still in use");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            StructureInfo.put(IGLOO, StructureInfo.NONE);
            LootOverrides.setFolder(null);
            JesServer.structuresChanged(server);
        }
        helper.succeed();
    }

    /** Saving a table or turning an edit off waits for a /reload, and Pack tools says so. */
    public static void changesWaitForReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        LootOverrides.setFolder(temp("jes-overrides"));
        try {
            ServerConfig.set(packToolsFor(0));
            String waiting = PackToolsState.tableKey(IGLOO_TABLE);
            Component saved = JesServer.saveTable(player, IGLOO_TABLE, DIAMONDS_ONLY);
            helper.assertTrue(key(saved).endsWith("override.saved"), "saving got " + saved.getString());
            PackToolsState state = PackToolsServer.state(server);
            helper.assertTrue(state.pending().contains(waiting), "a saved table isn't waiting for a /reload");
            helper.assertTrue(state.overrides().get(IGLOO_TABLE) == LootOverrides.Status.ACTIVE, "the edit isn't listed");
            helper.assertTrue(state.tables().contains(IGLOO_TABLE) && state.tables().size() > 100, "the server's loot tables aren't all listed");

            PackToolsServer.reloaded();
            helper.assertTrue(PackToolsServer.state(server).pending().isEmpty(), "a /reload should leave nothing waiting");
            Component removed = JesServer.tableAction(player, IGLOO_TABLE, com.finndog.justenoughstructures.network.JesNetwork.ACTION_REMOVE);
            helper.assertTrue(key(removed).endsWith("override.removed"), "turning the edit off got " + removed.getString());
            helper.assertTrue(PackToolsServer.state(server).pending().contains(waiting), "turning an edit off isn't waiting for a /reload");
        } finally {
            PackToolsServer.reloaded();
            ServerConfig.set(before);
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /**
     * An upload unpacks to no more than a loot table could ever need, and what a player had sent goes
     * when they leave, so a half-sent upload can't be finished afterwards.
     */
    public static void uploadsAreCapped(GameTestHelper helper) {
        byte[] bomb = Blobs.deflate(new byte[32 << 20]);
        helper.assertTrue(bomb.length < 1 << 20, "32 MB of nothing packed into " + bomb.length + " bytes");
        boolean refused = false;
        try {
            Blobs.inflate(bomb, Uploads.MOST_UNPACKED);
        } catch (IllegalStateException e) {
            refused = true;
        }
        helper.assertTrue(refused, "an upload unpacked to 32 MB");
        byte[] table = DIAMONDS_ONLY.repeat(1000).getBytes(StandardCharsets.UTF_8);
        helper.assertTrue(Arrays.equals(table, Blobs.inflate(Blobs.deflate(table), Uploads.MOST_UNPACKED)), "an upload the size of a big loot table didn't unpack");

        UUID player = UUID.randomUUID();
        byte[] first = {1};
        byte[] second = {2};
        helper.assertTrue(Uploads.accept(player, new Blobs.Part(5, JesNetwork.KIND_SAVE, 9, 0, 2, first)) == null, "half an upload was taken for all of it");
        Uploads.forget(player);
        helper.assertTrue(Uploads.accept(player, new Blobs.Part(5, JesNetwork.KIND_SAVE, 9, 1, 2, second)) == null,
                "an upload was finished with a part sent before its player left");
        Uploads.Done done = Uploads.accept(player, new Blobs.Part(5, JesNetwork.KIND_SAVE, 9, 0, 2, first));
        helper.assertTrue(done != null && Arrays.equals(done.bytes(), new byte[]{1, 2}), "an upload didn't come back together");
        Uploads.forget(player);
        helper.succeed();
    }

    /** The editor's roll of an edit not saved yet fills a chest from it; one the game can't load fills nothing. */
    public static void draftsRollIntoAChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(packToolsFor(0));
            List<ItemStack> items = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, DIAMONDS_ONLY), 42L, 27));
            helper.assertTrue(items.size() == 27, "a chest has 27 slots, got " + items.size());
            helper.assertTrue(items.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "a table of only diamonds rolled no diamond: " + items);
            List<ItemStack> broken = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, "{\"pools\": [}"), 42L, 27));
            helper.assertTrue(broken.size() == 27 && broken.stream().allMatch(ItemStack::isEmpty), "an edit that doesn't load filled the chest");

            ServerConfig.set(packToolsFor(4));
            List<ItemStack> refused = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, DIAMONDS_ONLY), 42L, 27));
            helper.assertTrue(refused.stream().allMatch(ItemStack::isEmpty), "a player who can't use Pack tools rolled an edit");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /**
     * Keeping a structure's loot secret in Pack tools, or turning show_loot_locations off there,
     * reaches its saved first view straight away: a preview asked for afterwards has no containers,
     * and neither has the first view saved for it then. Switched back, the containers are there again.
     */
    public static void secretLootLeavesSavedFirstViews(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registries = server.registryAccess();
        ServerPlayer player = TestPlayers.mock(helper);
        long seed = StructureCapture.defaultSeed(DESERT_PYRAMID);
        ServerConfig.Settings before = ServerConfig.get();
        ServerConfig.Settings noLootShown = new ServerConfig.Settings(before.hiddenStructures(), before.hiddenMods(), before.locatePermission(),
                before.teleportPermission(), false, before.packTools(), before.containerChanges());
        StructureInfo infoBefore = StructureInfo.forStructure(DESERT_PYRAMID);
        Path configRoot = JustEnoughStructures.configDir().getParent();
        JesNetwork.ServerSender sender = JesNetwork.serverSender();
        Map<Integer, byte[]> answers = new ConcurrentHashMap<>();
        // What each step saw, and what of that was wrong.
        List<String> seen = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        int[] step = {0};
        boolean[] logged = {false};
        // The saved first view read for each step, so a step that waits for it doesn't start another.
        Map<Integer, CompletableFuture<byte[]>> reads = new HashMap<>();
        Runnable restore = () -> {
            JesNetwork.setServerSender(sender);
            JesServer.left(player);
            JustEnoughStructures.setConfigDir(configRoot);
            LootOverrides.setFolder(null);
            StructureInfo.put(DESERT_PYRAMID, infoBefore);
            ServerConfig.set(before);
            JesServer.structuresChanged(server);
        };
        helper.succeedWhen(() -> {
            while (step[0] < 7) {
                switch (step[0]) {
                    case 0 -> {
                        Path saved = SavedPreviews.current();
                        waitFor(helper, saved != null, "no folder of saved first views is in use yet");
                        // A first view with the pyramid's chests is saved, as the loot index saves one.
                        if (!reads.containsKey(0)) {
                            SavedPreviews.offer(saved, server, DESERT_PYRAMID, seed, StructureCapture.capture(server, DESERT_PYRAMID, seed));
                        }
                        int chests = containers(registries, readSaved(helper, reads, 0, saved, DESERT_PYRAMID));
                        seen.add("to start with, the desert pyramid's saved first view had " + described(chests));
                        if (chests <= 0) {
                            wrong.add("the desert pyramid's saved first view had no chests to start with, so this test can't see them go");
                            restore.run();
                            step[0] = 7;
                            break;
                        }
                        JustEnoughStructures.setConfigDir(temp("jes-config"));
                        LootOverrides.setFolder(temp("jes-overrides"));
                        JesNetwork.setServerSender(captures(player, sender, answers));
                        PackToolsServer.saveStructure(server, DESERT_PYRAMID, "", true);
                        JesServer.onRequestCapture(player, 1, DESERT_PYRAMID, seed, true, 0);
                        step[0] = 1;
                    }
                    case 1 -> {
                        byte[] answer = answers.get(1);
                        waitFor(helper, answer != null, "the preview asked for after keeping its loot secret hasn't come yet");
                        checkContainers(seen, wrong, "after keeping its loot secret, the preview sent", containers(registries, answer), false, false);
                        step[0] = 2;
                    }
                    case 2 -> {
                        Path saved = SavedPreviews.current();
                        waitFor(helper, saved != null, "no folder of saved first views is in use after keeping its loot secret");
                        checkContainers(seen, wrong, "and the saved first view", containers(registries, readSaved(helper, reads, step[0], saved, DESERT_PYRAMID)), false, true);
                        PackToolsServer.saveStructure(server, DESERT_PYRAMID, "", false);
                        PackToolsServer.saveRules(server, noLootShown);
                        JesServer.onRequestCapture(player, 2, DESERT_PYRAMID, seed, true, 0);
                        step[0] = 3;
                    }
                    case 3 -> {
                        byte[] answer = answers.get(2);
                        waitFor(helper, answer != null, "the preview asked for after turning show_loot_locations off hasn't come yet");
                        checkContainers(seen, wrong, "after turning show_loot_locations off, the preview sent", containers(registries, answer), false, false);
                        step[0] = 4;
                    }
                    case 4 -> {
                        Path saved = SavedPreviews.current();
                        waitFor(helper, saved != null, "no folder of saved first views is in use after turning show_loot_locations off");
                        checkContainers(seen, wrong, "and the saved first view", containers(registries, readSaved(helper, reads, step[0], saved, DESERT_PYRAMID)), false, true);
                        PackToolsServer.saveRules(server, before);
                        JesServer.onRequestCapture(player, 3, DESERT_PYRAMID, seed, true, 0);
                        step[0] = 5;
                    }
                    case 5 -> {
                        byte[] answer = answers.get(3);
                        waitFor(helper, answer != null, "the preview asked for after showing loot again hasn't come yet");
                        checkContainers(seen, wrong, "with loot shown again, the preview sent", containers(registries, answer), true, false);
                        step[0] = 6;
                    }
                    default -> {
                        Path saved = SavedPreviews.current();
                        waitFor(helper, saved != null, "no folder of saved first views is in use after showing loot again");
                        checkContainers(seen, wrong, "and the saved first view", containers(registries, readSaved(helper, reads, step[0], saved, DESERT_PYRAMID)), true, false);
                        restore.run();
                        step[0] = 7;
                    }
                }
            }
            if (!logged[0]) {
                logged[0] = true;
                // The game's summary of a failed test leaves out the reason on some versions.
                JustEnoughStructures.LOGGER.info("In the test of secret loot in saved first views: {}", String.join("; ", seen));
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        });
    }

    /**
     * Hiding a structure in Pack tools sends the loot index again straight away, without it, to
     * everyone who has the index, and a player asking afterwards gets it without it too. Shown
     * again, it's back in the next one.
     */
    public static void hidingAStructureSendsTheIndexAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        Set<ResourceLocation> hidden = new HashSet<>(before.hiddenStructures());
        hidden.add(DESERT_PYRAMID);
        ServerConfig.Settings hiding = new ServerConfig.Settings(Set.copyOf(hidden), before.hiddenMods(), before.locatePermission(),
                before.teleportPermission(), before.showLootLocations(), before.packTools(), before.containerChanges());
        Path configRoot = JustEnoughStructures.configDir().getParent();
        JesNetwork.ServerSender sender = JesNetwork.serverSender();
        List<SentIndex> sent = new CopyOnWriteArrayList<>();
        JesNetwork.setServerSender(indexes(player, server.registryAccess(), sender, sent));
        JesServer.onRequestIndex(player);
        List<String> seen = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        boolean[] done = {false};
        boolean[] logged = {false};
        helper.succeedWhen(() -> {
            if (!done[0]) {
                SentIndex had = sent.stream().filter(s -> !s.early()).reduce((a, b) -> b).orElse(null);
                waitFor(helper, had != null, "the player hasn't been sent a finished loot index yet");
                done[0] = true;
                try {
                    if (!had.index().tablesByStructure().containsKey(DESERT_PYRAMID)) {
                        wrong.add("the index the player had doesn't list the desert pyramid, so hiding it can't be seen");
                    } else {
                        // All in one go on the server thread, so nothing else the index does comes in between.
                        JustEnoughStructures.setConfigDir(temp("jes-config"));
                        int count = sent.size();
                        PackToolsServer.saveRules(server, hiding);
                        checkIndexes(seen, wrong, "hiding the desert pyramid", List.copyOf(sent.subList(count, sent.size())), false);
                        count = sent.size();
                        JesServer.onRequestIndex(player);
                        checkIndexes(seen, wrong, "asking for the index after that", List.copyOf(sent.subList(count, sent.size())), false);
                        count = sent.size();
                        PackToolsServer.saveRules(server, before);
                        checkIndexes(seen, wrong, "showing it again", List.copyOf(sent.subList(count, sent.size())), true);
                    }
                } finally {
                    JesNetwork.setServerSender(sender);
                    // Otherwise the mock player would be sent every index after this test.
                    JesServer.left(player);
                    JustEnoughStructures.setConfigDir(configRoot);
                    ServerConfig.set(before);
                    JesServer.structuresChanged(server);
                }
            }
            if (!logged[0]) {
                logged[0] = true;
                // The game's summary of a failed test leaves out the reason on some versions.
                JustEnoughStructures.LOGGER.info("In the test of hiding a structure from the loot index: {}", String.join("; ", seen));
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        });
    }

    /** Fails this look at the test, after a short wait, unless {@code ready}. The idle test server ticks very fast. */
    private static void waitFor(GameTestHelper helper, boolean ready, String waiting) {
        if (!ready) {
            pause(50);
        }
        helper.assertTrue(ready, waiting);
    }

    /** Notes how many containers a view had, and whether that's wrong when they're {@code shown} or not. */
    private static void checkContainers(List<String> seen, List<String> wrong, String what, int containers, boolean shown, boolean saved) {
        String line = what + " had " + described(containers);
        seen.add(line);
        boolean right = shown ? containers > 0 : containers == 0 || saved && containers == NOT_SAVED;
        if (!right) {
            wrong.add(line + (shown ? ", not the desert pyramid's chests" : ", not none"));
        }
    }

    private static String described(int containers) {
        return containers == NOT_SAVED ? "nothing saved" : containers == FAILED ? "a failed capture" : containers + " containers";
    }

    /** How many containers a capture as players are sent it has, or {@link #NOT_SAVED} for none, or {@link #FAILED}. */
    private static int containers(RegistryAccess registries, byte[] payload) {
        if (payload == null) {
            return NOT_SAVED;
        }
        CaptureResult result = Codecs.unpackCapture(registries, payload).result();
        return result.succeeded() ? result.snapshot().containers().size() : FAILED;
    }

    /**
     * A structure's saved first view in this folder, or null. It's read on the saved previews' thread,
     * which can be busy saving every first view again after a change, so the step is tried again on
     * later ticks until the read started for it has finished. The server thread doesn't wait.
     */
    private static byte[] readSaved(GameTestHelper helper, Map<Integer, CompletableFuture<byte[]>> reads, int step, Path dir, ResourceLocation id) {
        CompletableFuture<byte[]> read = reads.computeIfAbsent(step, s -> {
            CompletableFuture<byte[]> started = new CompletableFuture<>();
            SavedPreviews.load(dir, id, false, started::complete);
            return started;
        });
        waitFor(helper, read.isDone(), "the saved first view hasn't been read yet");
        return read.join();
    }

    /**
     * Stands in for sending to {@code player}: the captures it's sent go in {@code answers} by request,
     * and nothing reaches the mock player, which NeoForge refuses to send to. Anything for anyone else
     * goes on to {@code others}.
     */
    private static JesNetwork.ServerSender captures(ServerPlayer player, JesNetwork.ServerSender others, Map<Integer, byte[]> answers) {
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        return (target, channel, buf) -> {
            if (target != player) {
                others.send(target, channel, buf);
                return;
            }
            if (!channel.equals(JesNetwork.TRANSFER)) {
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
        };
    }

    /** A loot index a player was sent, and whether it was an early one. */
    private record SentIndex(LootIndex index, boolean early) {
    }

    /** Notes the indexes sent after something, and whether that's wrong: one should come, listing the desert pyramid or not. */
    private static void checkIndexes(List<String> seen, List<String> wrong, String what, List<SentIndex> got, boolean listed) {
        String line = what + " sent " + got.size() + " indexes" + (got.isEmpty() ? "" : " " + got.stream()
                .map(s -> s.index().tablesByStructure().containsKey(DESERT_PYRAMID) ? "listing it" : "without it").toList());
        seen.add(line);
        if (got.size() != 1 || got.get(0).index().tablesByStructure().containsKey(DESERT_PYRAMID) != listed) {
            wrong.add(line + ", not one " + (listed ? "listing" : "without") + " the desert pyramid");
        }
    }

    /**
     * Stands in for sending to {@code player}: the loot indexes it's sent go in {@code sent}, and nothing
     * reaches the mock player, which NeoForge refuses to send to. Anything for anyone else goes on to
     * {@code others}.
     */
    private static JesNetwork.ServerSender indexes(ServerPlayer player, RegistryAccess registries, JesNetwork.ServerSender others, List<SentIndex> sent) {
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        return (target, channel, buf) -> {
            if (target != player) {
                others.send(target, channel, buf);
                return;
            }
            if (!channel.equals(JesNetwork.TRANSFER)) {
                return;
            }
            Blobs.Part part = Blobs.Part.read(buf);
            if (part.kind() != JesNetwork.KIND_INDEX) {
                return;
            }
            ByteArrayOutputStream bytes = parts.computeIfAbsent(part.transferId(), transfer -> new ByteArrayOutputStream());
            bytes.writeBytes(part.data());
            if (part.index() == part.count() - 1) {
                parts.remove(part.transferId());
                FriendlyByteBuf message = Blobs.fromBytes(registries, Blobs.inflate(bytes.toByteArray()));
                LootIndex index = Codecs.readIndex(message);
                sent.add(new SentIndex(index, Codecs.readIndexEarly(message)));
            }
        };
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
