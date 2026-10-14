package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.expect;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.StructureNameFiles;
import com.finndog.justenoughstructures.server.PackToolsServer;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;

/** Names Pack tools gives structures: kept as lang files in the JES pack, and sent after the structure list. */
public final class NameTests {
    private NameTests() {
    }

    public static void structureNamesAreKeptInLangFiles(GameTestHelper helper) {
        try {
            Path root = Files.createTempDirectory("jes-names");
            LootOverrides.setFolder(root);
            StructureNameFiles.forget();
            Path english = root.resolve("assets/justenoughstructures/lang/en_us.json");

            // Only a structure the server has can be named.
            expect(helper, PackToolsServer.saveName(helper.getLevel().getServer(), Ids.of("test", "nothing_here"), "en_us", "Name"), "tools.no_structure");
            helper.assertFalse(Files.exists(english), "a name was saved for a structure that isn't there");

            expect(helper, StructureNameFiles.save(Ids.parse("igloo"), "en_us", "  Snow\n House  "), "tools.structure_saved");
            helper.assertTrue(Files.readString(english).contains("\"structure.minecraft.igloo\": \"Snow House\""),
                    "the name wasn't written as a lang key: " + Files.readString(english));
            helper.assertTrue(Files.exists(root.resolve("pack.mcmeta")), "the folder wasn't made a pack");
            expect(helper, StructureNameFiles.save(Ids.of("test", "ruins/tower"), "fr_fr", "La tour"), "tools.structure_saved");
            helper.assertTrue(StructureNameFiles.all().equals(Map.of("en_us", Map.of("structure.minecraft.igloo", "Snow House"),
                    "fr_fr", Map.of("structure.test.ruins.tower", "La tour"))), "the names came back as " + StructureNameFiles.all());

            // Taking the only name away leaves no file, and a key written by hand beside one is kept.
            expect(helper, StructureNameFiles.save(Ids.of("test", "ruins/tower"), "fr_fr", " "), "tools.structure_saved");
            helper.assertFalse(Files.exists(root.resolve("assets/justenoughstructures/lang/fr_fr.json")), "an empty lang file was left");
            Files.writeString(english, "{\"structure.minecraft.igloo\": \"Snow House\", \"item.test.thing\": \"Written by hand\"}");
            StructureNameFiles.forget();
            expect(helper, StructureNameFiles.save(Ids.parse("igloo"), "en_us", ""), "tools.structure_saved");
            String left = Files.readString(english);
            helper.assertTrue(left.contains("Written by hand") && !left.contains("igloo"), "the hand-written key went, or the name stayed: " + left);
            helper.assertTrue(StructureNameFiles.all().isEmpty(), "a key that isn't a structure's would be sent: " + StructureNameFiles.all());

            // Something that isn't a language is refused, and a file that can't be read isn't written over.
            expect(helper, StructureNameFiles.save(Ids.parse("igloo"), "../en_us", "Bad"), "tools.no_language");
            Files.writeString(english, "{ this isn't json");
            expect(helper, StructureNameFiles.save(Ids.parse("igloo"), "en_us", "Snow House"), "override.save_failed");
            helper.assertTrue(Files.readString(english).equals("{ this isn't json"), "a broken lang file was written over");

            // A long name is cut to the limit.
            Files.delete(english);
            StructureNameFiles.save(Ids.parse("igloo"), "en_us", "x".repeat(StructureNameFiles.MAX_NAME + 10));
            helper.assertTrue(StructureNameFiles.all().get("en_us").get("structure.minecraft.igloo").length() == StructureNameFiles.MAX_NAME,
                    "a long name wasn't cut");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary folder", e);
        } finally {
            LootOverrides.setFolder(null);
            StructureNameFiles.forget();
        }
        helper.succeed();
    }

    /** The names come after the structure list, where a version from before them stops reading. */
    public static void structureNamesComeAfterTheList(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        List<StructureCatalog.Entry> entries = List.of(new StructureCatalog.Entry(Ids.parse("igloo"), null, new JsonObject(), List.of(),
                StructureInfo.NONE, Availability.GENERATES, List.of()));
        Map<String, Map<String, String>> names = Map.of("en_us", Map.of("structure.minecraft.igloo", "Snow House"));
        byte[] sent = Blobs.toBytes(registries, buf -> {
            Codecs.writeFingerprint(buf, "abc");
            Codecs.writeCatalog(buf, entries);
            Codecs.writeNames(buf, names);
        });
        // Read as a version from before names reads it, then on as this one does.
        FriendlyByteBuf buf = Blobs.fromBytes(registries, sent);
        helper.assertTrue("abc".equals(Codecs.readFingerprint(buf)), "the fingerprint didn't come first");
        List<StructureCatalog.Entry> read = Codecs.readCatalog(buf);
        helper.assertTrue(read.size() == 1 && read.get(0).id().equals(Ids.parse("igloo")), "the list didn't read as before: " + read);
        helper.assertTrue(buf.isReadable(), "nothing came after the list");
        helper.assertTrue(Codecs.readNames(buf).equals(names), "the names didn't come back");
        helper.assertFalse(buf.isReadable(), "something was left after the names");

        // From a server before names, nothing comes after the list.
        FriendlyByteBuf old = Blobs.fromBytes(registries, Blobs.toBytes(registries, b -> {
            Codecs.writeFingerprint(b, "abc");
            Codecs.writeCatalog(b, entries);
        }));
        Codecs.readFingerprint(old);
        Codecs.readCatalog(old);
        helper.assertFalse(old.isReadable(), "a list without names had more after it");
        helper.succeed();
    }
}
