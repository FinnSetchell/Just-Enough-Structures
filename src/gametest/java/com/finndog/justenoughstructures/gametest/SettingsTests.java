package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.key;
import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
//? if >=1.21 {
/*import net.minecraft.server.level.ClientInformation;
*///?}

/** Tests for the server settings file and the browser remembering how it was left. */
public final class SettingsTests {
    private SettingsTests() {
    }

    public static void browserStateSurvivesARestart(GameTestHelper helper) {
        boolean[] before = {ClientState.spin, ClientState.markers, ClientState.ground, ClientState.maximised, ClientState.details, ClientState.rarestFirst};
        Set<String> favouritesBefore = new LinkedHashSet<>(ClientState.favourites);
        try {
            Path file = Files.createTempDirectory("jes-state").resolve("state.json");
            ClientState.spin = false;
            ClientState.markers = false;
            ClientState.ground = true;
            ClientState.maximised = true;
            ClientState.details = true;
            ClientState.rarestFirst = true;
            ClientState.favourites.clear();
            ClientState.favourites.add("minecraft:igloo");
            ClientState.favourites.add("test:tower");
            ClientState.write(file);

            ClientState.spin = true;
            ClientState.markers = true;
            ClientState.ground = false;
            ClientState.maximised = false;
            ClientState.details = false;
            ClientState.rarestFirst = false;
            ClientState.favourites.clear();
            ClientState.read(file);
            helper.assertTrue(!ClientState.spin && !ClientState.markers && ClientState.ground && ClientState.maximised
                    && ClientState.details && ClientState.rarestFirst, "the saved state didn't come back as it was");
            helper.assertTrue(ClientState.favourites.equals(Set.of("minecraft:igloo", "test:tower")),
                    "the favourites came back as " + ClientState.favourites);

            // A broken file changes nothing rather than resetting everything.
            Files.writeString(file, "{ this isn't json");
            ClientState.read(file);
            helper.assertTrue(!ClientState.spin && ClientState.maximised && ClientState.favourites.size() == 2, "a broken file changed the state");

            // One a newer version saved is left as it is: not read, and not written over.
            String newer = "{\"format\": 99, \"spin\": true, \"favourites\": [\"minecraft:mansion\"]}";
            Files.writeString(file, newer);
            ClientState.read(file);
            helper.assertTrue(!ClientState.spin && ClientState.favourites.size() == 2, "a newer version's file was read");
            ClientState.write(file);
            helper.assertTrue(Files.readString(file).equals(newer), "a newer version's file was written over");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        } finally {
            ClientState.favourites.clear();
            ClientState.favourites.addAll(favouritesBefore);
            ClientState.spin = before[0];
            ClientState.markers = before[1];
            ClientState.ground = before[2];
            ClientState.maximised = before[3];
            ClientState.details = before[4];
            ClientState.rarestFirst = before[5];
        }
        helper.succeed();
    }

    public static void serverSettingsReadTheirFile(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.get();
        try {
            // The file written for a new server has to read back as the defaults, comments and all.
            Path file = Files.createTempDirectory("jes-config").resolve("server.json5");
            ServerConfig.Settings fresh = ServerConfig.load(file);
            helper.assertTrue(Files.exists(file), "no settings file was written");
            helper.assertTrue(fresh.equals(ServerConfig.DEFAULTS), "a new settings file didn't read as the defaults: " + fresh);

            ServerConfig.Settings settings = ServerConfig.parse("""
                    // a comment
                    {
                      "hidden": ["minecraft:igloo", "somemod:*", "Not an id!", 5],
                      "locate_permission": 0,
                      "teleport_permission": 9
                    }
                    """, "test");
            helper.assertTrue(settings.hides(Ids.parse("igloo")), "a hidden structure isn't hidden");
            helper.assertTrue(settings.hides(Ids.of("somemod", "tower")), "a hidden mod's structure isn't hidden");
            helper.assertFalse(settings.hides(Ids.parse("village_plains")), "a structure nobody hid is hidden");
            helper.assertTrue(settings.hiddenStructures().size() == 1, "bad entries in hidden weren't skipped: " + settings.hiddenStructures());
            helper.assertTrue(settings.locatePermission() == 0, "locate_permission wasn't read");
            helper.assertTrue(settings.teleportPermission() == ServerConfig.DEFAULTS.teleportPermission(), "an impossible teleport_permission was kept");

            helper.assertTrue(ServerConfig.parse("[1, 2]", "test").equals(ServerConfig.DEFAULTS), "a file that isn't settings wasn't ignored");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** A file from before newer settings existed gains them, keeps its owner's values, and the old one is kept. */
    public static void oldSettingsFilesGainNewSettings(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.get();
        try {
            Path file = Files.createTempDirectory("jes-config").resolve("server.json5");
            String old = """
                    {
                      "hidden": ["minecraft:igloo"],
                      "locate_permission": 0,
                      "teleport_permission": 3
                    }
                    """;
            Files.writeString(file, old);
            ServerConfig.Settings loaded = ServerConfig.load(file);
            String now = Files.readString(file);
            helper.assertTrue(now.contains("\"pack_tools\"") && now.contains("\"container_changes\"") && now.contains("\"show_loot_locations\""),
                    "the new settings weren't added to the file");
            helper.assertTrue(now.contains("// Who can use the locate button"), "the file wasn't given the template's comments");
            ServerConfig.Settings reread = ServerConfig.parse(now, "test");
            helper.assertTrue(reread.equals(loaded) && loaded.locatePermission() == 0 && loaded.teleportPermission() == 3
                    && loaded.hides(Ids.parse("igloo")), "the owner's values changed: " + reread);
            Path kept = file.resolveSibling("server.json5.old");
            helper.assertTrue(Files.exists(kept) && Files.readString(kept).equals(old), "the old file wasn't kept as it was");

            // A complete file is left exactly as it is, and a broken one isn't touched.
            String complete = now;
            ServerConfig.load(file);
            helper.assertTrue(Files.readString(file).equals(complete), "a complete file was written again");
            Files.writeString(file, "{ not json");
            ServerConfig.load(file);
            helper.assertTrue(Files.readString(file).equals("{ not json"), "a broken file was written over");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /**
     * Who gets Pack tools on a server: a listed name goes to the first player to join with it and
     * stays with them after a rename, someone else taking the name doesn't get it, and a permission
     * level or a permissions mod's node lets players in too.
     */
    public static void packToolsFollowTheRules(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerConfig.Settings before = ServerConfig.get();
        PackToolsAccess.PermissionCheck permissions = PackToolsAccess.permissions();
        UUID steve = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        try {
            PackToolsAccess.forget();
            PackToolsAccess.setPermissions(null);
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, new ServerConfig.PackTools(List.of("Steve"), -1)));
            helper.assertTrue(PackToolsAccess.allowed(player(helper, steve, "Steve")), "Steve, listed by name, wasn't let in");
            helper.assertFalse(PackToolsAccess.allowed(player(helper, other, "steve")), "someone else using Steve's name was let in");
            helper.assertTrue(PackToolsAccess.allowed(player(helper, steve, "Steve_Renamed")), "Steve lost access after a rename");
            helper.assertFalse(PackToolsAccess.allowed(player(helper, other, "Alex")), "a player who isn't listed was let in");

            ServerConfig.set(packToolsFor(0));
            helper.assertTrue(PackToolsAccess.allowed(player(helper, other, "Alex")), "permission level 0 didn't let everyone in");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.NONE));
            helper.assertFalse(PackToolsAccess.allowed(player(helper, other, "Alex")), "nobody's listed, yet Alex was let in");
            PackToolsAccess.setPermissions((player, node) -> player.getUUID().equals(other) && node.equals(PackToolsAccess.NODE));
            helper.assertTrue(PackToolsAccess.allowed(player(helper, other, "Alex")), "the permissions mod's node didn't let Alex in");
            helper.assertFalse(PackToolsAccess.allowed(player(helper, steve, "Steve")), "the node let in someone it wasn't given to");
        } finally {
            PackToolsAccess.setPermissions(permissions);
            PackToolsAccess.forget();
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        //? if >=1.21 {
        /*return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name), ClientInformation.createDefault());
        *///?} else {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        //?}
    }

    /** Settings saved from the config screen read back the same, and the file keeps its comments. */
    public static void serverSettingsWriteBack(GameTestHelper helper) {
        ServerConfig.Settings settings = new ServerConfig.Settings(Set.of(Ids.parse("igloo"), Ids.of("somemod", "tower")),
                Set.of("othermod"), 0, 4, false, new ServerConfig.PackTools(List.of("Steve", "Alex_2"), 3));
        String written = ServerConfig.render(settings);
        helper.assertTrue(written.contains("// Who can use the locate button"), "the saved file lost its comments");
        ServerConfig.Settings read = ServerConfig.parse(written, "test");
        helper.assertTrue(read.equals(settings), "saved " + settings + " but read back " + read);
        helper.succeed();
    }

    /** Hidden structures are left out of the list and can't be located. */
    public static void hiddenStructuresStayHidden(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(Ids.parse("igloo")), Set.of(), 0, 2, true, ServerConfig.PackTools.level(4)));
            List<ResourceLocation> ids = JesServer.visibleCatalog(helper.getLevel().getServer()).stream().map(StructureCatalog.Entry::id).toList();
            helper.assertFalse(ids.contains(Ids.parse("igloo")), "a hidden structure is in the list");
            helper.assertTrue(ids.contains(Ids.parse("desert_pyramid")), "a structure that isn't hidden is missing");

            ServerPlayer player = TestPlayers.mock(helper);
            Component reply = JesServer.locateFor(player, Ids.parse("igloo"), false);
            helper.assertTrue(key(reply).endsWith("locate_hidden"), "locating a hidden structure got " + reply.getString());

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of("minecraft"), 2, 2, true, ServerConfig.PackTools.level(4)));
            helper.assertTrue(JesServer.visibleCatalog(helper.getLevel().getServer()).stream().noneMatch(e -> e.id().getNamespace().equals("minecraft")),
                    "hiding a whole mod left some of its structures in the list");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** The server can let everyone locate while keeping teleporting to operators. */
    public static void locateLevelsComeFromTheSettings(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerPlayer player = TestPlayers.mock(helper);
            Vec3 start = player.position();
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 0, 2, true, ServerConfig.PackTools.level(4)));
            Component reply = JesServer.locateFor(player, Ids.parse("village_plains"), true);
            helper.assertFalse(key(reply).endsWith("locate_no_permission"), "locating was refused with locate_permission at 0");
            helper.assertTrue(player.position().equals(start), "a player who isn't an operator was moved");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 3, 3, true, ServerConfig.PackTools.level(4)));
            reply = JesServer.locateFor(player, Ids.parse("village_plains"), false);
            helper.assertTrue(key(reply).endsWith("locate_no_permission"), "locating wasn't refused at level 3, got " + reply.getString());
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** A structure's datapack file is read, and bad ones are refused rather than half read. */
    public static void structureInfoFilesAreRead(GameTestHelper helper) {
        StructureInfo pyramid = StructureInfo.forStructure(Ids.parse("jungle_pyramid"));
        helper.assertTrue(pyramid.notes() != null && pyramid.notes().getString().startsWith("A note from the game tests"),
                "the jungle pyramid's notes from the test datapack weren't loaded, got " + pyramid);
        helper.assertTrue("the game tests".equals(pyramid.author()) && pyramid.hideLootLocations(), "the rest of the test file wasn't read: " + pyramid);
        helper.assertTrue(StructureInfo.forStructure(Ids.parse("igloo")).equals(StructureInfo.NONE), "a structure without a file got info");

        StructureInfo styled = StructureInfo.parse(JsonParser.parseString("{\"notes\": {\"text\": \"Bold\", \"bold\": true}}"));
        helper.assertTrue(styled.notes().getString().equals("Bold") && styled.notes().getStyle().isBold() && !styled.hideLootLocations(),
                "a text component in notes wasn't read as one: " + styled);
        for (String bad : List.of("[]", "{\"author\": 5}", "{\"hide_loot_locations\": \"yes\"}")) {
            try {
                StructureInfo.parse(JsonParser.parseString(bad));
                helper.fail("this should have been refused: " + bad);
                return;
            } catch (JsonParseException expected) {
                // refused, as it should be
            }
        }
        helper.succeed();
    }

    /** Players get previews of a structure that hides its loot without anything saying where the loot is. */
    public static void hiddenLootLeavesThePreview(GameTestHelper helper) {
        ResourceLocation id = Ids.parse("jungle_pyramid");
        CaptureResult raw = StructureCapture.capture(helper.getLevel().getServer(), id, CaptureTests.SEED);
        helper.assertTrue(raw.succeeded() && !raw.snapshot().containers().isEmpty(), "the jungle pyramid has no loot to hide: " + raw.error());
        CaptureResult sent = JesServer.forPlayers(id, raw);
        helper.assertTrue(sent.snapshot().containers().isEmpty(), "the preview players get still has " + sent.snapshot().containers().size() + " containers");
        helper.assertTrue(sent.snapshot().blockCount() == raw.snapshot().blockCount(), "hiding the loot took blocks away");
        helper.assertTrue(JesServer.forPlayers(Ids.parse("desert_pyramid"), raw) == raw, "a structure that doesn't hide its loot lost it");

        // The catalog tells the client, and survives the trip.
        List<StructureCatalog.Entry> decoded = Codecs.readCatalog(Blobs.fromBytes(helper.getLevel().registryAccess(),
                Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCatalog(buf, JesServer.visibleCatalog(helper.getLevel().getServer())))));
        StructureCatalog.Entry pyramid = decoded.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        helper.assertTrue(pyramid.info().hideLootLocations() && pyramid.info().notes() != null && "the game tests".equals(pyramid.info().author()),
                "the jungle pyramid's info didn't reach the client: " + pyramid.info());

        // The server switch hides every structure's.
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, false, ServerConfig.PackTools.level(4)));
            helper.assertTrue(JesServer.hidesLootLocations(Ids.parse("desert_pyramid")), "show_loot_locations false didn't hide the desert pyramid's");
            helper.assertTrue(JesServer.visibleCatalog(helper.getLevel().getServer()).stream().allMatch(e -> e.info().hideLootLocations()),
                    "show_loot_locations false left some structures' loot locations showing");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    //? if >=1.21 {
    /*// Trial spawners don't say what they drop either, when a structure hides its loot.
    public static void hiddenLootLeavesTrialSpawners(GameTestHelper helper) {
        CaptureResult raw = StructureCapture.capture(helper.getLevel().getServer(), Ids.parse("trial_chambers"), CaptureTests.SEED);
        helper.assertTrue(raw.succeeded(), "the trial chambers didn't capture: " + raw.error());
        Set<String> drops = new HashSet<>();
        raw.snapshot().blockEntities().forEach(tag -> drops.addAll(TrialSpawners.loot(tag)));
        helper.assertFalse(drops.isEmpty(), "the trial chambers' trial spawners drop nothing to hide");
        for (CompoundTag tag : raw.snapshot().withoutLoot().blockEntities()) {
            String text = tag.toString();
            helper.assertTrue(drops.stream().noneMatch(text::contains), "a trial spawner still says what it drops: " + text);
        }
        helper.succeed();
    }
    *///?}
}
