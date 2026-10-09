package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.DIAMONDS_ONLY;
import static com.finndog.justenoughstructures.gametest.TestSupport.key;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsServer;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.finndog.justenoughstructures.server.Uploads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What Pack tools changes on the server besides loot tables: its rules, and what players are told about structures. */
public final class PackToolsTests {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final ResourceLocation IGLOO_TABLE = Ids.parse("chests/igloo_chest");

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
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
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
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
            List<ItemStack> items = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, DIAMONDS_ONLY), 42L, 27));
            helper.assertTrue(items.size() == 27, "a chest has 27 slots, got " + items.size());
            helper.assertTrue(items.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "a table of only diamonds rolled no diamond: " + items);
            List<ItemStack> broken = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, "{\"pools\": [}"), 42L, 27));
            helper.assertTrue(broken.size() == 27 && broken.stream().allMatch(ItemStack::isEmpty), "an edit that doesn't load filled the chest");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
            List<ItemStack> refused = JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(IGLOO_TABLE, DIAMONDS_ONLY), 42L, 27));
            helper.assertTrue(refused.stream().allMatch(ItemStack::isEmpty), "a player who can't use Pack tools rolled an edit");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }
}
