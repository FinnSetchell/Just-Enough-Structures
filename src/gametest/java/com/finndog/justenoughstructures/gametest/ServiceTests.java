package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Tests for the catalog, the wire format and loot rolls. */
public final class ServiceTests {
    private static final ResourceLocation DESERT_PYRAMID_LOOT = new ResourceLocation("chests/desert_pyramid");

    private ServiceTests() {
    }

    public static void catalogListsEveryVanillaStructure(GameTestHelper helper) {
        List<StructureCatalog.Entry> entries = StructureCatalog.build(helper.getLevel().registryAccess());
        Map<ResourceLocation, StructureCatalog.Entry> byId = entries.stream()
                .collect(Collectors.toMap(StructureCatalog.Entry::id, Function.identity()));
        for (String name : CaptureTests.VANILLA) {
            StructureCatalog.Entry entry = byId.get(new ResourceLocation(name));
            helper.assertTrue(entry != null, name + " is missing from the catalog");
            helper.assertTrue(entry.definition() != null && entry.definition().has("type"), name + " has no definition");
            helper.assertTrue(!entry.sets().isEmpty() && entry.sets().get(0).placement() != null, name + " has no placement");
        }

        List<StructureCatalog.Entry> decoded = Codecs.readCatalog(Blobs.fromBytes(Blobs.inflate(
                Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCatalog(buf, entries))))));
        helper.assertTrue(decoded.size() == entries.size(), "catalog lost entries on the way through");
        for (int i = 0; i < entries.size(); i++) {
            helper.assertTrue(decoded.get(i).id().equals(entries.get(i).id()), "catalog order changed at " + i);
            helper.assertTrue(String.valueOf(decoded.get(i).definition()).equals(String.valueOf(entries.get(i).definition())),
                    entries.get(i).id() + " definition changed on the way through");
        }
        helper.succeed();
    }

    public static void snapshotSurvivesTheWire(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation("desert_pyramid");
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), id, CaptureTests.SEED);
        helper.assertTrue(result.succeeded(), "desert_pyramid did not capture");
        byte[] wire = Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, id, CaptureTests.SEED, result)));
        Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(Blobs.inflate(wire)));

        StructureSnapshot a = result.snapshot();
        StructureSnapshot b = reply.result().snapshot();
        helper.assertTrue(b != null, "snapshot was lost on the way through");
        helper.assertTrue(reply.id().equals(id) && reply.seed() == CaptureTests.SEED, "reply names the wrong capture");
        helper.assertTrue(a.blockCount() == b.blockCount(), "block count changed on the way through");
        for (int i = 0; i < a.blockCount(); i++) {
            if (a.packedPosition(i) != b.packedPosition(i) || a.state(i) != b.state(i)) {
                helper.fail("block " + i + " changed on the way through");
                return;
            }
        }
        helper.assertTrue(a.size().equals(b.size()) && a.origin().equals(b.origin()), "bounds changed on the way through");
        helper.assertTrue(a.blockEntities().equals(b.blockEntities()), "block entities changed on the way through");
        helper.assertTrue(a.entities().equals(b.entities()), "entities changed on the way through");
        helper.assertTrue(a.containers().equals(b.containers()), "containers changed on the way through");
        helper.succeed();
    }

    public static void failedCaptureSurvivesTheWire(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation("justenoughstructures", "nope");
        CaptureResult failed = CaptureResult.failure("It didn't work", List.of("LAND: no valid start"), 12);
        Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(Blobs.inflate(
                Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, id, 5, failed))))));
        helper.assertFalse(reply.result().succeeded(), "a failure came back as a success");
        helper.assertTrue("It didn't work".equals(reply.result().error()), "the error message changed");
        helper.assertTrue(reply.result().attempts().equals(failed.attempts()), "the attempts changed");
        helper.succeed();
    }

    public static void lootRollsUseTheRealTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> first = LootRolls.fill(level, DESERT_PYRAMID_LOOT, 42L, 27);
        List<ItemStack> again = LootRolls.fill(level, DESERT_PYRAMID_LOOT, 42L, 27);
        helper.assertTrue(first.size() == 27, "a chest should have 27 slots");
        helper.assertTrue(first.stream().anyMatch(s -> !s.isEmpty()), "a desert pyramid chest rolled empty");
        for (int i = 0; i < 27; i++) {
            helper.assertTrue(ItemStack.matches(first.get(i), again.get(i)), "the same seed filled slot " + i + " differently");
        }

        LootOdds odds = LootRolls.odds(level, DESERT_PYRAMID_LOOT, 500, 7L);
        helper.assertTrue(odds.rolls() == 500, "wrong roll count");
        helper.assertTrue(odds.rows().stream().anyMatch(r -> r.example().is(Items.BONE)), "bones never turned up in 500 desert pyramid rolls");
        helper.assertTrue(odds.rows().stream().allMatch(r -> r.hits() <= odds.rolls() && r.min() <= r.max()), "odds rows don't add up");
        helper.succeed();
    }

    /** A treasure map roll must not go looking for a real buried treasure, which would take ages. */
    public static void treasureMapRollsAreQuick(GameTestHelper helper) {
        long started = System.nanoTime();
        LootOdds odds = LootRolls.odds(helper.getLevel(), new ResourceLocation("chests/shipwreck_map"), 200, 3L);
        long millis = (System.nanoTime() - started) / 1_000_000L;
        helper.assertTrue(millis < 5_000, "200 shipwreck map rolls took " + millis + " ms");
        helper.assertTrue(odds.rows().stream().anyMatch(r -> BuiltInRegistries.ITEM.getKey(r.example().getItem()).getPath().contains("map")),
                "no map came out of the shipwreck map table");
        helper.succeed();
    }

    public static void lootIndexFindsItemsInStructures(GameTestHelper helper) {
        List<ResourceLocation> ids = List.of(new ResourceLocation("desert_pyramid"), new ResourceLocation("shipwreck"));
        LootIndex index = LootIndex.build(helper.getLevel().getServer(), ids, done -> {
        }, () -> false);
        helper.assertTrue(index != null, "the index wasn't built");
        helper.assertTrue(index.tablesByStructure().getOrDefault(ids.get(0), Set.of()).contains(DESERT_PYRAMID_LOOT),
                "the desert pyramid should use its chest loot table, found " + index.tablesByStructure().get(ids.get(0)));
        Set<ResourceLocation> pyramidItems = index.itemsByTable().getOrDefault(DESERT_PYRAMID_LOOT, Set.of());
        helper.assertTrue(pyramidItems.contains(new ResourceLocation("diamond")), "diamonds are missing from the desert pyramid table");
        helper.assertTrue(pyramidItems.contains(new ResourceLocation("enchanted_book")), "books enchanted by the table should count as enchanted books");
        Set<ResourceLocation> mapItems = index.itemsByTable().getOrDefault(new ResourceLocation("chests/shipwreck_map"), Set.of());
        helper.assertTrue(mapItems.contains(new ResourceLocation("filled_map")), "the shipwreck map table should list a filled map, found " + mapItems);
        helper.succeed();
    }

    public static void unknownLootTableIsEmpty(GameTestHelper helper) {
        ResourceLocation table = new ResourceLocation("justenoughstructures", "nope");
        helper.assertFalse(LootRolls.exists(helper.getLevel(), table), "a made up loot table exists");
        helper.assertTrue(LootRolls.fill(helper.getLevel(), table, 1L, 27).stream().allMatch(ItemStack::isEmpty), "a made up loot table gave items");
        helper.succeed();
    }
}
