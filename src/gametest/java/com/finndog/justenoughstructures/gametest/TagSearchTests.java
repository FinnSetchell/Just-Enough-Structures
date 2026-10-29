package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.TagSearch;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.server.JesServer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Searching the browser by structure tag: the tags the server sends, and what a #tag search finds. */
public final class TagSearchTests {
    private static final ResourceLocation PLAINS = Ids.parse("village_plains");
    private static final ResourceLocation DESERT = Ids.parse("village_desert");

    private TagSearchTests() {
    }

    /** Each tag holds only the structures the list has, and a tag with none of them isn't sent. */
    public static void tagsHoldOnlyListedStructures(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<StructureCatalog.Entry> listed = JesServer.visibleCatalog(server).stream()
                .filter(entry -> entry.id().equals(PLAINS) || entry.id().equals(Ids.parse("igloo")))
                .toList();
        helper.assertTrue(listed.size() == 2, "the list doesn't have the plains village and the igloo: " + listed.size());
        Map<ResourceLocation, List<ResourceLocation>> tags = JesServer.structureTags(server, listed);
        helper.assertTrue(List.of(PLAINS).equals(tags.get(Ids.parse("village"))),
                "#minecraft:village holds " + tags.get(Ids.parse("village")) + ", not only the listed plains village");
        helper.assertFalse(tags.containsKey(Ids.parse("ocean_ruin")), "#minecraft:ocean_ruin was sent with no listed ruins");
        helper.assertTrue(tags.values().stream().allMatch(members -> !members.isEmpty() && members.stream().allMatch(id -> id.equals(PLAINS) || id.equals(Ids.parse("igloo")))),
                "a tag was sent empty, or with structures the list doesn't have: " + tags);
        helper.succeed();
    }

    /** The tags come after the names, where clients from before tag searches stop reading. */
    public static void structureTagsComeAfterTheNames(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Map<String, Map<String, String>> names = Map.of("en_us", Map.of("structure.minecraft.igloo", "Snow House"));
        Map<ResourceLocation, List<ResourceLocation>> tags = Map.of(Ids.parse("village"), List.of(PLAINS, DESERT));
        byte[] sent = Blobs.toBytes(registries, buf -> {
            Codecs.writeFingerprint(buf, "abc");
            Codecs.writeCatalog(buf, List.of());
            Codecs.writeNames(buf, names);
            Codecs.writeTags(buf, tags);
        });
        // Read as a client from before tags reads it, then the rest as this version does.
        FriendlyByteBuf buf = Blobs.fromBytes(registries, sent);
        Codecs.readFingerprint(buf);
        Codecs.readCatalog(buf);
        helper.assertTrue(Codecs.readNames(buf).equals(names), "the names didn't read as before");
        helper.assertTrue(buf.isReadable(), "nothing came after the names");
        helper.assertTrue(Codecs.readTags(buf).equals(tags), "the tags didn't come back");
        helper.assertFalse(buf.isReadable(), "something was left after the tags");
        helper.succeed();
    }

    /**
     * A tag's whole id finds that tag's structures, and not those of a longer tag that starts the same.
     * Part of one finds every tag it's in, as when typing.
     */
    public static void tagSearchFindsTheTagsStructures(GameTestHelper helper) {
        ResourceLocation hut = Ids.of("some_mod", "hut");
        ResourceLocation ruins = Ids.of("some_mod", "ruins");
        Map<ResourceLocation, List<ResourceLocation>> tags = Map.of(
                Ids.parse("village"), List.of(PLAINS, DESERT),
                Ids.parse("village_ruins"), List.of(ruins),
                Ids.of("some_mod", "village_huts"), List.of(hut),
                Ids.parse("mineshaft"), List.of(Ids.parse("mineshaft")));
        helper.assertTrue(TagSearch.structuresIn(tags, "minecraft:village").equals(Set.of(PLAINS, DESERT)),
                "#minecraft:village found " + TagSearch.structuresIn(tags, "minecraft:village"));
        helper.assertTrue(TagSearch.structuresIn(tags, "village").equals(Set.of(PLAINS, DESERT, ruins, hut)),
                "#village found " + TagSearch.structuresIn(tags, "village"));
        helper.assertTrue(TagSearch.structuresIn(tags, "minecraft:vill").equals(Set.of(PLAINS, DESERT, ruins)),
                "#minecraft:vill found " + TagSearch.structuresIn(tags, "minecraft:vill"));
        helper.assertTrue(TagSearch.structuresIn(tags, "end_city").isEmpty(), "#end_city found structures in no such tag");
        helper.succeed();
    }
}
