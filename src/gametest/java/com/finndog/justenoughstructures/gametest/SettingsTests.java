package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Tests for the server settings file and the browser remembering how it was left. */
public final class SettingsTests {
    private SettingsTests() {
    }

    public static void browserStateSurvivesARestart(GameTestHelper helper) {
        boolean[] before = {ClientState.spin, ClientState.markers, ClientState.ground, ClientState.maximised, ClientState.details, ClientState.rarestFirst};
        try {
            Path file = Files.createTempDirectory("jes-state").resolve("state.json");
            ClientState.spin = false;
            ClientState.markers = false;
            ClientState.ground = true;
            ClientState.maximised = true;
            ClientState.details = true;
            ClientState.rarestFirst = true;
            ClientState.write(file);

            ClientState.spin = true;
            ClientState.markers = true;
            ClientState.ground = false;
            ClientState.maximised = false;
            ClientState.details = false;
            ClientState.rarestFirst = false;
            ClientState.read(file);
            helper.assertTrue(!ClientState.spin && !ClientState.markers && ClientState.ground && ClientState.maximised
                    && ClientState.details && ClientState.rarestFirst, "the saved state didn't come back as it was");

            // A broken file changes nothing rather than resetting everything.
            Files.writeString(file, "{ this isn't json");
            ClientState.read(file);
            helper.assertTrue(!ClientState.spin && ClientState.maximised, "a broken file changed the state");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        } finally {
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
            helper.assertTrue(settings.hides(new ResourceLocation("igloo")), "a hidden structure isn't hidden");
            helper.assertTrue(settings.hides(new ResourceLocation("somemod", "tower")), "a hidden mod's structure isn't hidden");
            helper.assertFalse(settings.hides(new ResourceLocation("village_plains")), "a structure nobody hid is hidden");
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

    /** Settings saved from the config screen read back the same, and the file keeps its comments. */
    public static void serverSettingsWriteBack(GameTestHelper helper) {
        ServerConfig.Settings settings = new ServerConfig.Settings(Set.of(new ResourceLocation("igloo"), new ResourceLocation("somemod", "tower")),
                Set.of("othermod"), 0, 4, false);
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
            ServerConfig.set(new ServerConfig.Settings(Set.of(new ResourceLocation("igloo")), Set.of(), 0, 2, true));
            List<ResourceLocation> ids = JesServer.visibleCatalog(helper.getLevel().getServer()).stream().map(StructureCatalog.Entry::id).toList();
            helper.assertFalse(ids.contains(new ResourceLocation("igloo")), "a hidden structure is in the list");
            helper.assertTrue(ids.contains(new ResourceLocation("desert_pyramid")), "a structure that isn't hidden is missing");

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Component reply = JesServer.locateFor(player, new ResourceLocation("igloo"), false);
            helper.assertTrue(key(reply).endsWith("locate_hidden"), "locating a hidden structure got " + reply.getString());

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of("minecraft"), 2, 2, true));
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
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Vec3 start = player.position();
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 0, 2, true));
            Component reply = JesServer.locateFor(player, new ResourceLocation("village_plains"), true);
            helper.assertFalse(key(reply).endsWith("locate_no_permission"), "locating was refused with locate_permission at 0");
            helper.assertTrue(player.position().equals(start), "a player who isn't an operator was moved");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 3, 3, true));
            reply = JesServer.locateFor(player, new ResourceLocation("village_plains"), false);
            helper.assertTrue(key(reply).endsWith("locate_no_permission"), "locating wasn't refused at level 3, got " + reply.getString());
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** A structure's datapack file is read, and bad ones are refused rather than half read. */
    public static void structureInfoFilesAreRead(GameTestHelper helper) {
        StructureInfo pyramid = StructureInfo.forStructure(new ResourceLocation("jungle_pyramid"));
        helper.assertTrue(pyramid.notes() != null && pyramid.notes().getString().startsWith("A note from the game tests"),
                "the jungle pyramid's notes from the test datapack weren't loaded, got " + pyramid);
        helper.assertTrue("the game tests".equals(pyramid.author()) && pyramid.hideLootLocations(), "the rest of the test file wasn't read: " + pyramid);
        helper.assertTrue(StructureInfo.forStructure(new ResourceLocation("igloo")).equals(StructureInfo.NONE), "a structure without a file got info");

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
        ResourceLocation id = new ResourceLocation("jungle_pyramid");
        CaptureResult raw = StructureCapture.capture(helper.getLevel().getServer(), id, CaptureTests.SEED);
        helper.assertTrue(raw.succeeded() && !raw.snapshot().containers().isEmpty(), "the jungle pyramid has no loot to hide: " + raw.error());
        CaptureResult sent = JesServer.forPlayers(id, raw);
        helper.assertTrue(sent.snapshot().containers().isEmpty(), "the preview players get still has " + sent.snapshot().containers().size() + " containers");
        helper.assertTrue(sent.snapshot().blockCount() == raw.snapshot().blockCount(), "hiding the loot took blocks away");
        helper.assertTrue(JesServer.forPlayers(new ResourceLocation("desert_pyramid"), raw) == raw, "a structure that doesn't hide its loot lost it");

        // The catalog tells the client, and survives the trip.
        List<StructureCatalog.Entry> decoded = Codecs.readCatalog(Blobs.fromBytes(
                Blobs.toBytes(buf -> Codecs.writeCatalog(buf, JesServer.visibleCatalog(helper.getLevel().getServer())))));
        StructureCatalog.Entry pyramid = decoded.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        helper.assertTrue(pyramid.info().hideLootLocations() && pyramid.info().notes() != null && "the game tests".equals(pyramid.info().author()),
                "the jungle pyramid's info didn't reach the client: " + pyramid.info());

        // The server switch hides every structure's.
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, false));
            helper.assertTrue(JesServer.hidesLootLocations(new ResourceLocation("desert_pyramid")), "show_loot_locations false didn't hide the desert pyramid's");
            helper.assertTrue(JesServer.visibleCatalog(helper.getLevel().getServer()).stream().allMatch(e -> e.info().hideLootLocations()),
                    "show_loot_locations false left some structures' loot locations showing");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    private static String key(Component reply) {
        return reply.getContents() instanceof TranslatableContents t ? t.getKey() : reply.getString();
    }
}
