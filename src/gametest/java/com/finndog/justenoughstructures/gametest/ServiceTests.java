package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
        CaptureResult failed = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.crashed", "it didn't work"),
                List.of(Component.translatable("screen.justenoughstructures.attempt.no_start", "LAND")), 12);
        Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(Blobs.inflate(
                Blobs.deflate(Blobs.toBytes(buf -> Codecs.writeCapture(buf, id, 5, failed))))));
        helper.assertFalse(reply.result().succeeded(), "a failure came back as a success");
        helper.assertTrue(failed.reason().equals(reply.result().reason()), "the error message changed");
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

    /** JEI gets one entry per item per structure, listing every table in the structure that gives it. */
    public static void foundInRecipesComeFromTheIndex(GameTestHelper helper) {
        ResourceLocation a = new ResourceLocation("test", "a");
        ResourceLocation b = new ResourceLocation("test", "b");
        ResourceLocation t1 = new ResourceLocation("test", "chests/one");
        ResourceLocation t2 = new ResourceLocation("test", "chests/two");
        LootIndex index = new LootIndex(
                Map.of(b, Set.of(t2), a, Set.of(t1, t2)),
                Map.of(t1, Set.of(new ResourceLocation("diamond"), new ResourceLocation("gold_ingot")),
                        t2, Set.of(new ResourceLocation("diamond"), new ResourceLocation("test", "not_an_item"))));
        List<FoundInRecipe> recipes = FoundInRecipe.fromIndex(index, ResourceLocation::toString);
        List<String> got = recipes.stream()
                .map(r -> r.structure().getPath() + " " + BuiltInRegistries.ITEM.getKey(r.item().getItem()).getPath() + " " + r.tables().size())
                .toList();
        helper.assertTrue(got.equals(List.of("a diamond 2", "a gold_ingot 1", "b diamond 1")), "expected diamonds from both tables in a, gold in a and diamonds in b, got " + got);
        helper.succeed();
    }

    /** The saved index reads back as it was, a damaged one is rebuilt rather than trusted, and old ones are cleared out. */
    public static void savedLootIndexReadsBack(GameTestHelper helper) {
        ResourceLocation structure = new ResourceLocation("test", "tower");
        ResourceLocation table = new ResourceLocation("test", "chests/tower");
        LootIndex index = new LootIndex(Map.of(structure, Set.of(table)), Map.of(table, Set.of(new ResourceLocation("diamond"))));
        try {
            Path dir = Files.createTempDirectory("jes-index");
            LootIndexStore.write(dir, "abc", index);
            LootIndex read = LootIndexStore.read(dir, "abc");
            helper.assertTrue(read != null && read.tablesByStructure().equals(index.tablesByStructure()) && read.itemsByTable().equals(index.itemsByTable()),
                    "the saved index read back as " + read);
            helper.assertTrue(LootIndexStore.read(dir, "missing") == null, "an index that was never saved was read");
            Files.write(dir.resolve("broken.bin"), new byte[]{1, 2, 3});
            helper.assertTrue(LootIndexStore.read(dir, "broken") == null, "a damaged file was read as an index");
            for (int i = 0; i < 6; i++) {
                LootIndexStore.write(dir, "extra" + i, index);
            }
            try (var files = Files.list(dir)) {
                long kept = files.filter(p -> p.toString().endsWith(".bin")).count();
                helper.assertTrue(kept == 4, kept + " saved indexes were kept, expected the 4 newest");
            }
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary folder", e);
        }
        helper.succeed();
    }

    /** The fingerprint that says whether a saved index still holds comes out the same each time. */
    public static void lootIndexFingerprintIsStable(GameTestHelper helper) {
        long started = System.nanoTime();
        String first = LootIndexStore.fingerprintOf(helper.getLevel().getServer());
        long millis = (System.nanoTime() - started) / 1_000_000L;
        String second = LootIndexStore.fingerprintOf(helper.getLevel().getServer());
        helper.assertTrue(first != null && first.equals(second), "the fingerprint changed from " + first + " to " + second);
        JustEnoughStructures.LOGGER.info("Fingerprinting the loot index's sources took {} ms", millis);
        helper.assertTrue(millis < 30_000, "fingerprinting took " + millis + " ms");
        helper.succeed();
    }

    /** Players never get hidden structures in the index, or loot tables only they use. */
    public static void hiddenStructuresLeaveTheLootIndex(GameTestHelper helper) {
        ResourceLocation shown = new ResourceLocation("test", "shown");
        ResourceLocation hidden = new ResourceLocation("test", "secret");
        ResourceLocation shared = new ResourceLocation("test", "chests/shared");
        ResourceLocation own = new ResourceLocation("test", "chests/secret");
        LootIndex full = new LootIndex(Map.of(shown, Set.of(shared), hidden, Set.of(shared, own)),
                Map.of(shared, Set.of(new ResourceLocation("bread")), own, Set.of(new ResourceLocation("diamond"))));
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(hidden), Set.of(), 2, 2, true, 4));
            LootIndex visible = LootIndexStore.visible(full);
            helper.assertTrue(visible.tablesByStructure().keySet().equals(Set.of(shown)), "the hidden structure is still in the index");
            helper.assertTrue(visible.itemsByTable().keySet().equals(Set.of(shared)), "a loot table only the hidden structure uses is still there");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** Looking for something that can't generate here must answer straight away, not search for minutes. */
    public static void impossibleLocateIsQuick(GameTestHelper helper) {
        long started = System.nanoTime();
        Component reply = JesServer.locate(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), new ResourceLocation("end_city"));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        helper.assertTrue(millis < 2_000, "locating an end city in the overworld took " + millis + " ms");
        // The test world has structures turned off, and an end city can't be in the overworld anyway.
        helper.assertTrue(reply.getContents() instanceof TranslatableContents t
                        && (t.getKey().endsWith("locate_structures_off") || t.getKey().endsWith("locate_wrong_dimension")),
                "expected to be told it can't generate here, got " + reply.getString());
        helper.succeed();
    }

    /** Teleporting lands on top of the ground in the overworld, and on a floor under the roof in the Nether. */
    public static void teleportLandsSomewhereSafe(GameTestHelper helper) {
        BlockPos near = helper.absolutePos(BlockPos.ZERO);
        checkStandingSpot(helper, helper.getLevel(), near.getX() + 40, near.getZ() + 40);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test world has no Nether to try");
        checkStandingSpot(helper, nether, 0, 0);
        checkStandingSpot(helper, nether, 200, -300);
        helper.succeed();
    }

    private static void checkStandingSpot(GameTestHelper helper, ServerLevel level, int x, int z) {
        String where = x + ", " + z + " in " + level.dimension().location();
        Optional<BlockPos> spot = JesServer.standingSpot(level, x, z);
        helper.assertTrue(spot.isPresent(), "nowhere to stand at " + where);
        BlockPos feet = spot.get();
        helper.assertFalse(level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty(), "nothing solid under " + feet + " at " + where);
        helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "no room to stand at " + feet + " at " + where);
        helper.assertFalse(level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA), "stood in lava at " + where);
        if (level.dimensionType().hasCeiling()) {
            int roof = level.getMinBuildHeight() + level.dimensionType().logicalHeight();
            helper.assertTrue(feet.getY() < roof - 3, "stood on the roof at " + feet + " at " + where);
        }
    }

    /** Only operators can teleport, the same as /tp. */
    public static void onlyOperatorsCanTeleport(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 before = player.position();
        Component reply = JesServer.locateFor(player, new ResourceLocation("village_plains"), true);
        helper.assertTrue(reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith("locate_no_permission"),
                "a player who isn't an operator got " + reply.getString());
        helper.assertTrue(player.position().equals(before), "a player who isn't an operator was moved");
        helper.succeed();
    }

    public static void unknownLootTableIsEmpty(GameTestHelper helper) {
        ResourceLocation table = new ResourceLocation("justenoughstructures", "nope");
        helper.assertFalse(LootRolls.exists(helper.getLevel(), table), "a made up loot table exists");
        helper.assertTrue(LootRolls.fill(helper.getLevel(), table, 1L, 27).stream().allMatch(ItemStack::isEmpty), "a made up loot table gave items");
        helper.succeed();
    }
}
