package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Levels;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.KeptPreviews;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.handler.codec.DecoderException;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.RequestLimits;
import com.finndog.justenoughstructures.server.SavedPreviews;
import com.finndog.justenoughstructures.server.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * Blocks read back in the order they were sent, whatever it is: scattered back and forth through
     * their box, or a list picture's copy of a structure big enough for bigger blocks. One whose size
     * makes no sense is turned away rather than misread.
     */
    public static void snapshotsInAnyOrderSurviveTheWire(GameTestHelper helper) {
        List<BlockState> palette = List.of(Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState());
        int[] positions = {StructureSnapshot.pack(4, 2, 3), StructureSnapshot.pack(0, 0, 0), StructureSnapshot.pack(9, 4, 6),
                StructureSnapshot.pack(1, 0, 0), StructureSnapshot.pack(0, 4, 0), StructureSnapshot.pack(9, 0, 6), StructureSnapshot.pack(5, 3, 1)};
        int[] states = {2, 0, 1, 1, 0, 2, 1};
        StructureSnapshot scattered = new StructureSnapshot(Ids.of("test", "scattered"), 3L, SandboxTerrain.LAND, BlockPos.ZERO, new Vec3i(10, 5, 7),
                palette, positions, states, List.of(), List.of(), 1);
        List<Integer> line = new ArrayList<>();
        List<Integer> lineStates = new ArrayList<>();
        for (int x = 0; x < 300; x++) {
            for (int y = 0; y < 3; y++) {
                line.add(StructureSnapshot.pack(x, y, x % 4));
                lineStates.add((x + y) % 3);
            }
        }
        StructureSnapshot picture = new StructureSnapshot(Ids.of("test", "line"), 4L, SandboxTerrain.LAND, BlockPos.ZERO, new Vec3i(300, 3, 4), palette,
                line.stream().mapToInt(Integer::intValue).toArray(), lineStates.stream().mapToInt(Integer::intValue).toArray(), List.of(), List.of(), 1)
                .forPicture();
        for (StructureSnapshot sent : List.of(scattered, picture)) {
            StructureSnapshot back = throughTheWire(helper, sent);
            helper.assertTrue(back.size().equals(sent.size()) && back.blockCount() == sent.blockCount(), sent.structureId() + " came back a different size");
            for (int i = 0; i < sent.blockCount(); i++) {
                if (back.packedPosition(i) != sent.packedPosition(i) || back.state(i) != sent.state(i)) {
                    helper.fail("block " + i + " of " + sent.structureId() + " changed on the way through");
                    return;
                }
            }
        }
        StructureSnapshot flat = new StructureSnapshot(Ids.of("test", "flat"), 5L, SandboxTerrain.LAND, BlockPos.ZERO, new Vec3i(0, 1, 1), palette,
                new int[0], new int[0], List.of(), List.of(), 1);
        try {
            throughTheWire(helper, flat);
            helper.fail("a structure with no width was read");
            return;
        } catch (DecoderException e) {
            // Turned away, as it should be.
        }
        helper.succeed();
    }

    private static StructureSnapshot throughTheWire(GameTestHelper helper, StructureSnapshot sent) {
        CaptureResult result = CaptureResult.success(sent, List.of(), 0);
        byte[] wire = Blobs.deflate(Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCapture(buf, sent.structureId(), sent.seed(), result)));
        return Codecs.readCapture(Blobs.fromBytes(helper.getLevel().registryAccess(), Blobs.inflate(wire))).result().snapshot();
    }

    /** A failure reads back as it was sent, along with whether it may work another time. */
    public static void failedCaptureSurvivesTheWire(GameTestHelper helper) {
        ResourceLocation id = Ids.of("justenoughstructures", "nope");
        CaptureResult crashed = CaptureResult.failure(Component.translatable("screen.justenoughstructures.error.crashed", "it didn't work"),
                List.of(Component.translatable("screen.justenoughstructures.attempt.no_start", "LAND")), 12);
        CaptureResult shortOfMemory = CaptureResult.temporaryFailure(Component.translatable("screen.justenoughstructures.error.low_memory"), List.of(), 0);
        for (CaptureResult failed : List.of(crashed, shortOfMemory)) {
            Codecs.CaptureReply reply = Codecs.readCapture(Blobs.fromBytes(helper.getLevel().registryAccess(), Blobs.inflate(
                    Blobs.deflate(Blobs.toBytes(helper.getLevel().registryAccess(), buf -> Codecs.writeCapture(buf, id, 5, failed))))));
            helper.assertFalse(reply.result().succeeded(), "a failure came back as a success");
            helper.assertTrue(failed.reason().equals(reply.result().reason()), "the error message changed");
            helper.assertTrue(reply.result().attempts().equals(failed.attempts()), "the attempts changed");
            helper.assertTrue(reply.result().temporary() == failed.temporary(), "whether it may work later changed on the way through");
        }
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

    /**
     * A file written safely is all there afterwards, writing it again replaces it, and nothing's
     * left beside it.
     */
    public static void filesAreWrittenSafely(GameTestHelper helper) {
        try {
            Path dir = Files.createTempDirectory("jes-safe");
            Path file = dir.resolve("containers.json");
            SafeFiles.write(file, "{\"first\": true}");
            SafeFiles.write(file, "{\"second\": true}");
            helper.assertTrue(Files.readString(file).equals("{\"second\": true}"), "the file reads " + Files.readString(file));
            try (var files = Files.list(dir)) {
                List<String> names = files.map(f -> f.getFileName().toString()).toList();
                helper.assertTrue(names.equals(List.of("containers.json")), "the folder holds " + names);
            }
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary folder", e);
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

    /** Saved first views read back as they were saved, and a missing or damaged one reads as nothing. */
    public static void savedFirstViewsReadBack(GameTestHelper helper) {
        ResourceLocation id = Ids.of("test", "tower");
        byte[] view = {1, 2, 3, 4, 5};
        byte[] picture = {6, 7};
        try {
            Path dir = Files.createTempDirectory("jes-previews");
            SavedPreviews.save(dir, id, false, view);
            SavedPreviews.save(dir, id, true, picture);
            byte[] read = readSaved(dir, id, false);
            helper.assertTrue(Arrays.equals(read, view), "the saved view read back as " + Arrays.toString(read));
            byte[] readPicture = readSaved(dir, id, true);
            helper.assertTrue(Arrays.equals(readPicture, picture), "the saved list picture read back as " + Arrays.toString(readPicture));
            helper.assertTrue(readSaved(dir, Ids.of("test", "never_saved"), false) == null, "a view that was never saved was read");
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    Files.write(file, new byte[]{9, 9});
                }
            }
            helper.assertTrue(readSaved(dir, id, false) == null, "a damaged file was read as a view");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary folder", e);
        }
        helper.succeed();
    }

    /** Only a structure's first layout is saved, made with its first seed, as that's the one players see first. */
    public static void onlyFirstViewsAreSaved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation igloo = Ids.of("minecraft", "igloo");
        long first = StructureCapture.defaultSeed(igloo);
        CaptureResult result = StructureCapture.capture(server, igloo, first);
        helper.assertTrue(result.succeeded(), "the igloo didn't generate: " + result.error());
        try {
            Path saved = Files.createTempDirectory("jes-previews");
            SavedPreviews.offer(saved, server, igloo, first, result);
            byte[] view = readSaved(saved, igloo, false);
            helper.assertTrue(view != null, "the igloo's first view wasn't saved");
            helper.assertTrue(readSaved(saved, igloo, true) != null, "the igloo's list picture wasn't saved");
            long seed = Codecs.readCapture(Blobs.fromBytes(server.registryAccess(), Blobs.inflate(view))).seed();
            helper.assertTrue(seed == first, "the saved view was made with seed " + seed + " rather than the first");
            Path other = Files.createTempDirectory("jes-previews");
            SavedPreviews.offer(other, server, igloo, first + 1, result);
            helper.assertTrue(readSaved(other, igloo, false) == null && readSaved(other, igloo, true) == null, "a new layout was saved");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary folder", e);
        }
        helper.succeed();
    }

    /**
     * A list picture of a structure too long to show block for block uses one block for each cube, the
     * one found most in it, leaving out air so thin walls stay, and nothing else.
     */
    public static void bigStructuresPicturesUseBiggerBlocks(GameTestHelper helper) {
        // A line of stone 200 long, with planks on top of it in the middle and air beside it.
        List<BlockState> palette = List.of(Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.AIR.defaultBlockState());
        List<Integer> positions = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (int x = 0; x < 200; x++) {
            positions.add(StructureSnapshot.pack(x, 0, 0));
            states.add(0);
            positions.add(StructureSnapshot.pack(x, 0, 1));
            states.add(2);
        }
        positions.add(StructureSnapshot.pack(100, 1, 0));
        states.add(1);
        positions.add(StructureSnapshot.pack(101, 1, 0));
        states.add(1);
        StructureSnapshot line = new StructureSnapshot(Ids.of("test", "line"), 1L, SandboxTerrain.LAND, BlockPos.ZERO, new Vec3i(200, 2, 2),
                palette, positions.stream().mapToInt(Integer::intValue).toArray(), states.stream().mapToInt(Integer::intValue).toArray(),
                List.of(), List.of(), 1);
        StructureSnapshot picture = line.forPicture();
        helper.assertTrue(picture.size().equals(new Vec3i(67, 1, 1)), "the picture's size is " + picture.size());
        helper.assertTrue(picture.blockCount() == 67, "the picture has " + picture.blockCount() + " blocks, expected 67");
        for (int i = 0; i < picture.blockCount(); i++) {
            helper.assertTrue(picture.state(i).is(Blocks.STONE), "block " + i + " of the picture is " + picture.state(i));
        }
        helper.succeed();
    }

    /** A big structure's list picture fits in the picture's limit and is lighter, and a small one's keeps every block. */
    public static void picturesAreLighter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation city = Ids.of("minecraft", "ancient_city");
        CaptureResult big = StructureCapture.capture(server, city, StructureCapture.defaultSeed(city));
        helper.assertTrue(big.succeeded(), "the ancient city didn't generate: " + big.error());
        StructureSnapshot whole = big.snapshot();
        StructureSnapshot picture = whole.forPicture();
        Vec3i size = picture.size();
        helper.assertTrue(Math.max(size.getX(), Math.max(size.getY(), size.getZ())) <= StructureSnapshot.PICTURE_MOST,
                "the ancient city's picture is " + size + " blocks");
        helper.assertTrue(picture.blockCount() < whole.blockCount() && picture.blockEntities().isEmpty() && picture.entities().isEmpty(),
                "the ancient city's picture has " + picture.blockCount() + " of its " + whole.blockCount() + " blocks and "
                        + picture.blockEntities().size() + " block entities");
        ResourceLocation igloo = Ids.of("minecraft", "igloo");
        CaptureResult small = StructureCapture.capture(server, igloo, StructureCapture.defaultSeed(igloo));
        helper.assertTrue(small.succeeded(), "the igloo didn't generate: " + small.error());
        StructureSnapshot iglooPicture = small.snapshot().forPicture();
        helper.assertTrue(iglooPicture.blockCount() == small.snapshot().blockCount(), "the igloo's picture lost blocks");
        helper.assertTrue(iglooPicture.containers().stream().allMatch(c -> c.lootTable() == null), "the igloo's picture says where its loot is");
        helper.succeed();
    }

    private static byte[] readSaved(Path dir, ResourceLocation id, boolean picture) {
        CompletableFuture<byte[]> read = new CompletableFuture<>();
        SavedPreviews.load(dir, id, picture, read::complete);
        try {
            return read.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("reading a saved view took too long", e);
        }
    }

    /**
     * A first view the player's game kept reads back as it was sent and is replaced by a newer one.
     * It's still there after leaving and joining again, and forgotten once damaged. Each server's
     * are kept apart, and none are used when told not to keep any.
     */
    public static void keptPreviewsReadBack(GameTestHelper helper) {
        ResourceLocation id = Ids.of("test", "tower");
        byte[] first = {1, 2, 3, 4, 5};
        byte[] second = {6, 7, 8};
        String fingerprint = "game test " + System.nanoTime();
        Path dir = JustEnoughStructures.cacheDir().resolve("kept-previews").resolve(shortName(fingerprint + "|" + JesNetwork.PROTOCOL));
        try {
            KeptPreviews.use(fingerprint, true);
            waitForKept();
            helper.assertTrue(KeptPreviews.inUse() && KeptPreviews.kept(id) == 0, "a new server's folder wasn't used, or had a view in it");

            KeptPreviews.keep(id, first);
            helper.assertTrue(KeptPreviews.kept(id) == Blobs.hash(first), "a kept view wasn't known straight away");
            byte[] read = readKept(id);
            helper.assertTrue(Arrays.equals(read, first), "the kept view read back as " + Arrays.toString(read));
            KeptPreviews.keep(id, second);
            read = readKept(id);
            helper.assertTrue(Arrays.equals(read, second), "the changed view read back as " + Arrays.toString(read));
            helper.assertTrue(keptFiles(dir, id, ".bin").size() == 1, "the old view's file was left behind: " + keptFiles(dir, id, ".bin"));

            KeptPreviews.use(fingerprint, true);
            helper.assertTrue(KeptPreviews.inUse(), "the same server's list arriving again stopped its views being used");
            KeptPreviews.use(null, false);
            helper.assertTrue(!KeptPreviews.inUse() && KeptPreviews.kept(id) == 0, "a view was still used after leaving");
            KeptPreviews.use(fingerprint, true);
            waitForKept();
            helper.assertTrue(KeptPreviews.kept(id) == Blobs.hash(second), "the kept view wasn't found after joining again");

            KeptPreviews.use("another " + fingerprint, true);
            waitForKept();
            helper.assertTrue(KeptPreviews.inUse() && KeptPreviews.kept(id) == 0, "another server's view was used");
            KeptPreviews.use(fingerprint, false);
            helper.assertTrue(!KeptPreviews.inUse() && KeptPreviews.kept(id) == 0, "a view was used when none were to be kept");

            KeptPreviews.use(fingerprint, true);
            waitForKept();
            for (Path file : keptFiles(dir, id, ".bin")) {
                byte[] bytes = Files.readAllBytes(file);
                bytes[bytes.length - 1] ^= 1;
                Files.write(file, bytes);
            }
            helper.assertTrue(readKept(id) == null, "a damaged view was read");
            helper.assertTrue(KeptPreviews.kept(id) == 0 && keptFiles(dir, id, ".bin").isEmpty(), "a damaged view wasn't forgotten");
        } catch (IOException e) {
            throw new AssertionError("couldn't look in the kept previews' folder", e);
        } finally {
            KeptPreviews.use(null, false);
        }
        helper.succeed();
    }

    /**
     * The copy a list picture is drawn from reads back while it's of the version of the structure
     * asked for, and a newer version's takes its place. It sits beside the structure's first view
     * without either replacing the other, and one that's damaged is forgotten. A structure's version
     * changes with its definition and only then.
     */
    public static void keptPictureCopiesReadBack(GameTestHelper helper) {
        ResourceLocation id = Ids.of("test", "tower");
        byte[] view = {1, 2, 3};
        byte[] picture = {4, 5};
        byte[] newer = {6, 7, 8, 9};
        String fingerprint = "game test " + System.nanoTime();
        Path dir = JustEnoughStructures.cacheDir().resolve("kept-previews").resolve(shortName(fingerprint + "|" + JesNetwork.PROTOCOL));
        try {
            KeptPreviews.use(fingerprint, true);
            waitForKept();
            helper.assertTrue(!KeptPreviews.hasPicture(id, "0a") && readKeptPicture(id, "0a") == null, "a new server's folder had a picture's copy in it");

            KeptPreviews.keep(id, view);
            KeptPreviews.keepPicture(id, "0a", picture);
            helper.assertTrue(KeptPreviews.hasPicture(id, "0a") && !KeptPreviews.hasPicture(id, "0b"),
                    "a kept picture's copy wasn't known straight away, or was taken for another version");
            byte[] read = readKeptPicture(id, "0a");
            helper.assertTrue(Arrays.equals(read, picture), "the picture's copy read back as " + Arrays.toString(read));
            helper.assertTrue(readKeptPicture(id, "0b") == null, "a picture's copy of another version was read");
            helper.assertTrue(Arrays.equals(readKept(id), view), "keeping a picture's copy replaced the first view");

            KeptPreviews.keepPicture(id, "0b", newer);
            read = readKeptPicture(id, "0b");
            helper.assertTrue(Arrays.equals(read, newer) && !KeptPreviews.hasPicture(id, "0a"), "a newer version's copy didn't take the old one's place");
            helper.assertTrue(keptFiles(dir, id, ".pic").size() == 1, "the old copy's file was left behind: " + keptFiles(dir, id, ".pic"));
            helper.assertTrue(Arrays.equals(readKept(id), view), "a newer picture's copy replaced the first view");

            KeptPreviews.use(null, false);
            KeptPreviews.use(fingerprint, true);
            waitForKept();
            helper.assertTrue(KeptPreviews.hasPicture(id, "0b"), "the picture's copy wasn't found after joining again");

            for (Path file : keptFiles(dir, id, ".pic")) {
                byte[] bytes = Files.readAllBytes(file);
                bytes[bytes.length - 1] ^= 1;
                Files.write(file, bytes);
            }
            helper.assertTrue(readKeptPicture(id, "0b") == null, "a damaged picture's copy was read");
            helper.assertTrue(!KeptPreviews.hasPicture(id, "0b") && keptFiles(dir, id, ".pic").isEmpty(), "a damaged picture's copy wasn't forgotten");
            helper.assertTrue(Arrays.equals(readKept(id), view), "forgetting a damaged picture's copy took the first view with it");

            List<StructureCatalog.Entry> entries = StructureCatalog.build(helper.getLevel().getServer());
            StructureCatalog.Entry entry = entries.stream().filter(e -> e.definition() != null).findFirst().orElseThrow();
            KeptPreviews.structures(entries);
            String version = KeptPreviews.version(entry.id());
            KeptPreviews.structures(StructureCatalog.build(helper.getLevel().getServer()));
            helper.assertTrue(version != null && version.equals(KeptPreviews.version(entry.id())), "a structure's version changed when its definition didn't");
            JsonObject changed = entry.definition().deepCopy();
            changed.addProperty("changed", true);
            KeptPreviews.structures(List.of(new StructureCatalog.Entry(entry.id(), entry.type(), changed, entry.sets(), entry.info(), entry.availability(),
                    entry.dimensions())));
            helper.assertTrue(!version.equals(KeptPreviews.version(entry.id())), "a structure's version didn't change with its definition");
        } catch (IOException e) {
            throw new AssertionError("couldn't look in the kept previews' folder", e);
        } finally {
            KeptPreviews.use(null, false);
        }
        helper.succeed();
    }

    /** Waits for everything handed to the kept previews' thread so far. */
    private static void waitForKept() {
        readKept(Ids.of("test", "nothing"));
    }

    private static byte[] readKept(ResourceLocation id) {
        CompletableFuture<byte[]> read = new CompletableFuture<>();
        KeptPreviews.read(id, read::complete);
        try {
            return read.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("reading a kept view took too long", e);
        }
    }

    /** A structure's kept files of one kind: first views end in .bin, pictures' copies in .pic. */
    private static List<Path> keptFiles(Path dir, ResourceLocation id, String ending) throws IOException {
        waitForKept();
        String stem = shortName(id.toString()) + ".";
        try (var files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().startsWith(stem) && file.getFileName().toString().endsWith(ending)).toList();
        }
    }

    private static byte[] readKeptPicture(ResourceLocation id, String version) {
        CompletableFuture<byte[]> read = new CompletableFuture<>();
        KeptPreviews.readPicture(id, version, read::complete);
        try {
            return read.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("reading a kept picture's copy took too long", e);
        }
    }

    /** How kept previews name their folders and files. */
    private static String shortName(String text) {
        return Hashing.sha256().hashString(text, StandardCharsets.UTF_8).toString().substring(0, 24);
    }

    /**
     * Asked for a first view the player's game kept, the server only says it's the same, and sends
     * one that's changed since in full.
     */
    public static void keptFirstViewsAreNotSentAgain(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        ResourceLocation igloo = Ids.of("minecraft", "igloo");
        long seed = StructureCapture.defaultSeed(igloo);
        record Answer(int kind, byte[] bytes) {
        }
        Map<Integer, Answer> answers = new ConcurrentHashMap<>();
        Map<Integer, ByteArrayOutputStream> parts = new HashMap<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender((target, channel, buf) -> {
            if (target != player || !channel.equals(JesNetwork.TRANSFER)) {
                before.send(target, channel, buf);
                return;
            }
            Blobs.Part part = Blobs.Part.read(buf);
            ByteArrayOutputStream bytes = parts.computeIfAbsent(part.transferId(), transfer -> new ByteArrayOutputStream());
            bytes.writeBytes(part.data());
            if (part.index() == part.count() - 1) {
                parts.remove(part.transferId());
                answers.put(part.requestId(), new Answer(part.kind(), bytes.toByteArray()));
            }
        });
        JesServer.onRequestCapture(player, 1, igloo, seed, true, 0);
        boolean[] askedAgain = new boolean[1];
        helper.succeedWhen(() -> {
            Answer whole = answers.get(1);
            helper.assertTrue(whole != null, "the igloo hasn't been sent yet");
            if (!askedAgain[0]) {
                askedAgain[0] = true;
                helper.assertTrue(whole.kind() == JesNetwork.KIND_CAPTURE && whole.bytes().length > 0, "the igloo was sent as " + whole.kind());
                JesServer.onRequestCapture(player, 2, igloo, seed, true, Blobs.hash(whole.bytes()));
                JesServer.onRequestCapture(player, 3, igloo, seed, true, Blobs.hash(whole.bytes()) ^ 1);
            }
            Answer same = answers.get(2);
            Answer changed = answers.get(3);
            helper.assertTrue(same != null && changed != null, "the igloo hasn't been answered again yet");
            JesNetwork.setServerSender(before);
            helper.assertTrue(same.kind() == JesNetwork.KIND_SAME && same.bytes().length == 0,
                    "the kept igloo was answered with " + same.bytes().length + " bytes of kind " + same.kind());
            helper.assertTrue(changed.kind() == JesNetwork.KIND_CAPTURE && Arrays.equals(changed.bytes(), whole.bytes()),
                    "an igloo kept from before it changed wasn't sent again in full");
        });
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
