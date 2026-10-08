package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.RequestLimits;
import com.finndog.justenoughstructures.server.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
//? if >=26.1 {
/*import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Tests for the catalog, the wire format and loot rolls. */
public final class ServiceTests {
    private static final ResourceLocation DESERT_PYRAMID_LOOT = Ids.parse("chests/desert_pyramid");

    private ServiceTests() {
    }

    public static void catalogListsEveryVanillaStructure(GameTestHelper helper) {
        List<StructureCatalog.Entry> entries = StructureCatalog.build(helper.getLevel().getServer());
        Map<ResourceLocation, StructureCatalog.Entry> byId = entries.stream()
                .collect(Collectors.toMap(StructureCatalog.Entry::id, Function.identity()));
        for (String name : CaptureTests.VANILLA) {
            StructureCatalog.Entry entry = byId.get(Ids.parse(name));
            helper.assertTrue(entry != null, name + " is missing from the catalog");
            helper.assertTrue(entry.definition() != null && entry.definition().has("type"), name + " has no definition");
            helper.assertTrue(!entry.sets().isEmpty() && entry.sets().get(0).placement() != null, name + " has no placement");
        }
        // Each says which dimensions it's found in, from where the world's generators place it.
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether != null && JesServer.searchedIn(nether, Ids.parse("fortress")) == nether) {
            helper.assertTrue(byId.get(Ids.parse("fortress")).dimensions().contains(Ids.of(Level.NETHER)),
                    "the fortress doesn't say it's found in the Nether: " + byId.get(Ids.parse("fortress")).dimensions());
        }

        List<StructureCatalog.Entry> decoded = Codecs.readCatalog(Blobs.fromBytes(helper.getLevel().registryAccess(), Blobs.inflate(
                Blobs.deflate(Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCatalog(buf, entries))))));
        helper.assertTrue(decoded.size() == entries.size(), "catalog lost entries on the way through");
        for (int i = 0; i < entries.size(); i++) {
            helper.assertTrue(decoded.get(i).id().equals(entries.get(i).id()), "catalog order changed at " + i);
            helper.assertTrue(String.valueOf(decoded.get(i).definition()).equals(String.valueOf(entries.get(i).definition())),
                    entries.get(i).id() + " definition changed on the way through");
            helper.assertTrue(decoded.get(i).availability().equals(entries.get(i).availability()),
                    entries.get(i).id() + " whether it generates changed on the way through");
            helper.assertTrue(decoded.get(i).dimensions().equals(entries.get(i).dimensions()),
                    entries.get(i).id() + " where it's found changed on the way through");
        }
        helper.succeed();
    }

    /**
     * Every structure says whether it turns up in new worlds. Vanilla ones do unless a mod replaces
     * them, as Better Strongholds always does the stronghold.
     */
    public static void structuresSayIfTheyGenerate(GameTestHelper helper) {
        Map<ResourceLocation, StructureCatalog.Entry> byId = StructureCatalog.build(helper.getLevel().getServer()).stream()
                .collect(Collectors.toMap(StructureCatalog.Entry::id, Function.identity()));
        for (String name : CaptureTests.VANILLA) {
            Availability availability = byId.get(Ids.parse(name)).availability();
            helper.assertTrue(availability != null, name + " doesn't say whether it generates");
            helper.assertTrue(availability.generates() || availability.reason() == Availability.Reason.REPLACED
                    || availability.reason() == Availability.Reason.TURNED_OFF, name + " is " + availability + ", but it's in a structure set");
        }
        if (byId.containsKey(Ids.of("betterstrongholds", "stronghold"))) {
            Availability stronghold = byId.get(Ids.parse("stronghold")).availability();
            helper.assertTrue(stronghold.reason() == Availability.Reason.REPLACED && "betterstrongholds".equals(stronghold.by())
                            && Ids.of("betterstrongholds", "stronghold").equals(stronghold.replacedBy()),
                    "the vanilla stronghold should be replaced by Better Strongholds' one, but is " + stronghold);
        }
        helper.succeed();
    }

    /** A command's result, 0 when it's refused, as running it from chat would give. */
    private static int command(MinecraftServer server, CommandSourceStack source, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    /** /jes open is there for anyone, and turns away a player without the mod, or a structure that doesn't exist, without failing. */
    public static void jesOpenCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("jes") != null, "/jes isn't registered");
        ServerPlayer player = TestPlayers.mock(helper);
        //? if >=26.1 {
        /*CommandSourceStack source = player.createCommandSourceStack()
                .withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)).withSuppressedOutput();
        *///?} else {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        //?}
        helper.assertTrue(command(server, source, "jes open minecraft:igloo") == 0,
                "/jes open worked for a player whose game doesn't have the mod");
        helper.assertTrue(command(server, source, "jes open nothing:here") == 0,
                "/jes open worked for a structure that doesn't exist");
        helper.succeed();
    }

    public static void snapshotSurvivesTheWire(GameTestHelper helper) {
        ResourceLocation id = Ids.parse("desert_pyramid");
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), id, CaptureTests.SEED);
        helper.assertTrue(result.succeeded(), "desert_pyramid did not capture");
        byte[] wire = Blobs.deflate(Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCapture(buf, id, CaptureTests.SEED, result)));
        Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(helper.getLevel().registryAccess(), Blobs.inflate(wire)));

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
        ResourceLocation id = Ids.of("justenoughstructures", "nope");
        CaptureResult failed = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.crashed", "it didn't work"),
                List.of(Component.translatable("screen.justenoughstructures.attempt.no_start", "LAND")), 12);
        Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(helper.getLevel().registryAccess(), Blobs.inflate(
                Blobs.deflate(Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCapture(buf, id, 5, failed))))));
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

        // A few rolls at a time, as the server works them out, comes to the same odds as all at once.
        LootRolls.Roller roller = new LootRolls.Roller(level, DESERT_PYRAMID_LOOT, 500, 7L);
        helper.assertFalse(roller.rollFor(0), "rolling for no time at all did every roll");
        helper.assertTrue(roller.odds().rolls() == 1, "rolling for no time at all should still roll once");
        while (!roller.rollFor(1_000_000L)) {
            // A millisecond at a time.
        }
        LootOdds sliced = roller.odds();
        helper.assertTrue(sliced.rolls() == 500 && sliced.emptyRolls() == odds.emptyRolls() && sliced.rows().size() == odds.rows().size(),
                "rolling a few at a time changed the odds");
        for (int i = 0; i < odds.rows().size(); i++) {
            LootOdds.Row a = odds.rows().get(i);
            LootOdds.Row b = sliced.rows().get(i);
            helper.assertTrue(a.example().getItem() == b.example().getItem() && a.hits() == b.hits() && a.total() == b.total(),
                    "rolling a few at a time changed the odds of " + a.example());
        }
        helper.succeed();
    }

    /** A treasure map roll must not go looking for a real buried treasure, which would take ages. */
    public static void treasureMapRollsAreQuick(GameTestHelper helper) {
        long started = System.nanoTime();
        LootOdds odds = LootRolls.odds(helper.getLevel(), Ids.parse("chests/shipwreck_map"), 200, 3L);
        long millis = (System.nanoTime() - started) / 1_000_000L;
        helper.assertTrue(millis < 5_000, "200 shipwreck map rolls took " + millis + " ms");
        helper.assertTrue(odds.rows().stream().anyMatch(r -> BuiltInRegistries.ITEM.getKey(r.example().getItem()).getPath().contains("map")),
                "no map came out of the shipwreck map table");
        helper.succeed();
    }

    public static void lootIndexFindsItemsInStructures(GameTestHelper helper) {
        List<ResourceLocation> ids = List.of(Ids.parse("desert_pyramid"), Ids.parse("shipwreck"));
        LootIndex index = LootIndex.build(helper.getLevel().getServer(), ids, done -> {
        }, () -> false);
        helper.assertTrue(index != null, "the index wasn't built");
        helper.assertTrue(index.tablesByStructure().getOrDefault(ids.get(0), Set.of()).contains(DESERT_PYRAMID_LOOT),
                "the desert pyramid should use its chest loot table, found " + index.tablesByStructure().get(ids.get(0)));
        Set<ResourceLocation> pyramidItems = index.itemsByTable().getOrDefault(DESERT_PYRAMID_LOOT, Set.of());
        helper.assertTrue(pyramidItems.contains(Ids.parse("diamond")), "diamonds are missing from the desert pyramid table");
        helper.assertTrue(pyramidItems.contains(Ids.parse("enchanted_book")), "books enchanted by the table should count as enchanted books");
        Set<ResourceLocation> mapItems = index.itemsByTable().getOrDefault(Ids.parse("chests/shipwreck_map"), Set.of());
        //? if >=26.3 {
        /*// From 26.3 a treasure map is an item of its own, which the map is drawn straight onto.
        ResourceLocation map = Ids.parse("buried_treasure_map");
        *///?} else {
        ResourceLocation map = Ids.parse("filled_map");
        //?}
        helper.assertTrue(mapItems.contains(map), "the shipwreck map table should list " + map + ", found " + mapItems);
        //? if >=1.21 {
        /*// The ominous vault's table names others, one of which has the heavy core.
        Set<ResourceLocation> vault = LootIndex.itemsIn(helper.getLevel().getServer(), Ids.parse("chests/trial_chambers/reward_ominous"), new HashSet<>());
        helper.assertTrue(vault.contains(Ids.parse("heavy_core")), "the ominous vault's table should list the heavy core, found " + vault);
        *///?}
        helper.succeed();
    }

    /** JEI gets one entry per item per structure, listing every table in the structure that gives it. */
    public static void foundInRecipesComeFromTheIndex(GameTestHelper helper) {
        ResourceLocation a = Ids.of("test", "a");
        ResourceLocation b = Ids.of("test", "b");
        ResourceLocation t1 = Ids.of("test", "chests/one");
        ResourceLocation t2 = Ids.of("test", "chests/two");
        LootIndex index = new LootIndex(
                Map.of(b, Set.of(t2), a, Set.of(t1, t2)),
                Map.of(t1, Set.of(Ids.parse("diamond"), Ids.parse("gold_ingot")),
                        t2, Set.of(Ids.parse("diamond"), Ids.of("test", "not_an_item"))));
        List<FoundInRecipe> recipes = FoundInRecipe.fromIndex(index, ResourceLocation::toString);
        List<String> got = recipes.stream()
                .map(r -> r.structure().getPath() + " " + BuiltInRegistries.ITEM.getKey(r.item().getItem()).getPath() + " " + r.tables().size())
                .toList();
        helper.assertTrue(got.equals(List.of("a diamond 2", "a gold_ingot 1", "b diamond 1")), "expected diamonds from both tables in a, gold in a and diamonds in b, got " + got);
        helper.succeed();
    }

    /**
     * A client asking far faster than the browser does is turned away once it's used up its allowance,
     * and one big answer bigger than the whole allowance still goes, but nothing straight after it.
     */
    public static void requestsAreLimited(GameTestHelper helper) {
        UUID asking = UUID.randomUUID();
        UUID sent = UUID.randomUUID();
        try {
            int allowed = 0;
            while (allowed < 1000 && RequestLimits.request(asking, 1)) {
                allowed++;
            }
            helper.assertTrue(allowed >= 200 && allowed < 210, allowed + " requests in a row were let through, expected about 200");
            helper.assertTrue(RequestLimits.send(sent, 100L << 20), "an answer bigger than the whole allowance wasn't sent to a player who'd been sent nothing");
            helper.assertFalse(RequestLimits.send(sent, 1 << 20), "another answer went straight after one bigger than the whole allowance");
        } finally {
            RequestLimits.forget(asking);
            RequestLimits.forget(sent);
        }
        helper.succeed();
    }

    /** The saved scan reads back as it was, a damaged one is rebuilt rather than trusted, and old ones are cleared out. */
    public static void savedLootIndexReadsBack(GameTestHelper helper) {
        ResourceLocation structure = Ids.of("test", "tower");
        ResourceLocation table = Ids.of("test", "chests/tower");
        ResourceLocation template = Ids.of("test", "tower/top");
        StructureScan index = new StructureScan(Map.of(structure, Set.of(table)), Map.of(structure, Set.of(template)),
                Map.of(template, "1, 2, 3 minecraft:chest test:chests/other"), Set.of(Ids.of("test", "too_big_for_now")));
        try {
            Path dir = Files.createTempDirectory("jes-index");
            LootIndexStore.write(dir, "abc", index);
            StructureScan read = LootIndexStore.read(dir, "abc");
            helper.assertTrue(read != null && read.tables().equals(index.tables()) && read.templates().equals(index.templates())
                    && read.patches().equals(index.patches()) && read.retry().equals(index.retry()), "the saved scan read back as " + read);
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
        JesLog.debug("Fingerprinting the loot index's sources took {} ms", millis);
        helper.assertTrue(millis < 30_000, "fingerprinting took " + millis + " ms");
        helper.succeed();
    }

    /** Players never get hidden structures in the index, or loot tables only they use. */
    public static void hiddenStructuresLeaveTheLootIndex(GameTestHelper helper) {
        ResourceLocation shown = Ids.of("test", "shown");
        ResourceLocation hidden = Ids.of("test", "secret");
        ResourceLocation shared = Ids.of("test", "chests/shared");
        ResourceLocation own = Ids.of("test", "chests/secret");
        LootIndex full = new LootIndex(Map.of(shown, Set.of(shared), hidden, Set.of(shared, own)),
                Map.of(shared, Set.of(Ids.parse("bread")), own, Set.of(Ids.parse("diamond"))));
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(hidden), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
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
        Component reply = JesServer.locate(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), Ids.parse("end_city"));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        helper.assertTrue(millis < 2_000, "locating an end city in the overworld took " + millis + " ms");
        // The test world has structures turned off, and an end city can't be in the overworld anyway.
        helper.assertTrue(reply.getContents() instanceof TranslatableContents t
                        && (t.getKey().endsWith("locate_structures_off") || t.getKey().endsWith("locate_wrong_dimension")),
                "expected to be told it can't generate here, got " + reply.getString());
        helper.succeed();
    }

    /**
     * Something that can't generate where the player is gets looked for in the dimension where it
     * does: an end city, asked for in the overworld, in the End. (The test world has structures
     * turned off, so this checks where the search goes rather than what it finds.)
     */
    public static void locateLooksInOtherDimensions(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel endCity = JesServer.searchedIn(overworld, Ids.parse("end_city"));
        //? if >=26.3 {
        /*// From 26.3 every dimension of the test world is a desert superflat, with only villages and
        // strongholds, so no end city can generate anywhere.
        helper.assertTrue(endCity == null, "an end city is looked for in " + (endCity == null ? "nowhere" : endCity.dimension()) + ", where none can generate");
        ServerLevel village = JesServer.searchedIn(overworld, Ids.parse("village_desert"));
        *///?} else {
        helper.assertTrue(endCity != null && endCity.dimension() == Level.END, "an end city is looked for in " + (endCity == null ? "nowhere" : endCity.dimension()));
        ServerLevel village = JesServer.searchedIn(overworld, Ids.parse("village_plains"));
        //?}
        helper.assertTrue(village == overworld, "a village is looked for in " + (village == null ? "nowhere" : village.dimension()) + ", not here");
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
        String where = x + ", " + z + " in " + Ids.of(level.dimension());
        Optional<BlockPos> spot = JesServer.standingSpot(level, x, z);
        helper.assertTrue(spot.isPresent(), "nowhere to stand at " + where);
        BlockPos feet = spot.get();
        helper.assertFalse(level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty(), "nothing solid under " + feet + " at " + where);
        helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "no room to stand at " + feet + " at " + where);
        helper.assertFalse(level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA), "stood in lava at " + where);
        if (level.dimensionType().hasCeiling()) {
            int roof = Levels.minY(level) + level.dimensionType().logicalHeight();
            helper.assertTrue(feet.getY() < roof - 3, "stood on the roof at " + feet + " at " + where);
        }
    }

    /** Only operators can teleport, the same as /tp. */
    public static void onlyOperatorsCanTeleport(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        Vec3 before = player.position();
        Component reply = JesServer.locateFor(player, Ids.parse("village_plains"), true);
        helper.assertTrue(reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith("locate_no_permission"),
                "a player who isn't an operator got " + reply.getString());
        helper.assertTrue(player.position().equals(before), "a player who isn't an operator was moved");
        helper.succeed();
    }

    public static void unknownLootTableIsEmpty(GameTestHelper helper) {
        ResourceLocation table = Ids.of("justenoughstructures", "nope");
        helper.assertFalse(LootRolls.exists(helper.getLevel(), table), "a made up loot table exists");
        helper.assertTrue(LootRolls.fill(helper.getLevel(), table, 1L, 27).stream().allMatch(ItemStack::isEmpty), "a made up loot table gave items");
        helper.succeed();
    }
}
