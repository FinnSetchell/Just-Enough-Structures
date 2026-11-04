package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A loot table's hash, which the loot index keeps each table's rolls under: the same whatever order
 * the game writes a table's maps out in, which can change from one start to the next, and rolls kept
 * under the hash JES 1.1.0 and earlier made are kept, not rolled again (card 353).
 */
public final class TableHashTests {
    /** The test mod's table, with six enchantments in one set_enchantments. */
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:chests/many_enchantments");

    private TableHashTests() {
    }

    /**
     * Two tables that differ only in the order of their objects' keys, at any depth, hash the same.
     * Two whose arrays are in another order don't, as an array's order means something in a loot table.
     */
    public static void tableHashIgnoresKeyOrder(GameTestHelper helper) {
        JsonElement one = JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":["
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond_sword\",\"functions\":[{\"function\":\"minecraft:set_enchantments\","
                + "\"enchantments\":{\"minecraft:sharpness\":5,\"minecraft:knockback\":2,\"minecraft:mending\":1}}]},"
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:apple\"}]}]}");
        JsonElement keysTurned = JsonParser.parseString("{\"pools\":[{\"entries\":["
                + "{\"functions\":[{\"enchantments\":{\"minecraft:mending\":1,\"minecraft:knockback\":2,\"minecraft:sharpness\":5},"
                + "\"function\":\"minecraft:set_enchantments\"}],\"name\":\"minecraft:diamond_sword\",\"type\":\"minecraft:item\"},"
                + "{\"name\":\"minecraft:apple\",\"type\":\"minecraft:item\"}],\"rolls\":1}],\"type\":\"minecraft:chest\"}");
        JsonElement entriesTurned = JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":["
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:apple\"},"
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond_sword\",\"functions\":[{\"function\":\"minecraft:set_enchantments\","
                + "\"enchantments\":{\"minecraft:sharpness\":5,\"minecraft:knockback\":2,\"minecraft:mending\":1}}]}]}]}");
        helper.assertTrue(LootIndex.hashOf(one).equals(LootIndex.hashOf(keysTurned)), "a table with its keys in another order hashed differently");
        helper.assertFalse(LootIndex.hashOf(one).equals(LootIndex.hashOf(entriesTurned)), "a table with its entries in another order hashed the same");
        MinecraftServer server = helper.getLevel().getServer();
        String hash = LootIndex.tableHash(server, TABLE);
        helper.assertFalse(hash.isEmpty(), TABLE + " isn't loaded");
        helper.assertTrue(hash.equals(LootIndex.tableHash(server, TABLE)), TABLE + " hashed differently a second time");
        helper.succeed();
    }

    /**
     * A saved scan whose roll of a table was kept under the hash JES 1.1.0 and earlier made keeps that
     * roll once read back: it's moved to the hash now, its items still count, and the table isn't
     * rolled again. A roll kept under neither hash is left alone, and rolled again.
     */
    public static void rollsKeptUnderTheOldHashAreKept(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation structure = Ids.parse("justenoughstructures_gametest:hash_test");
        ResourceLocation extra = Ids.parse("minecraft:apple");
        String now = LootIndex.tableHash(server, TABLE);
        String old = LootIndex.oldTableHash(server, TABLE);
        helper.assertFalse(now.isEmpty() || old.isEmpty(), TABLE + " isn't loaded");
        helper.assertFalse(now.equals(old), "the hash now and the old one are the same, so this shows nothing");
        StructureScan saved = saveAndRead(new StructureScan(Map.of(structure, Set.of(TABLE)), Map.of(), Map.of(), Set.of(), Map.of(),
                Map.of(TABLE, new StructureScan.Rolled(old, Set.of(extra)))));
        Map<ResourceLocation, String> hashes = LootIndex.hashes(server, saved);
        StructureScan moved = LootIndex.rehashed(server, saved, hashes);
        StructureScan.Rolled kept = moved.rolled().get(TABLE);
        helper.assertTrue(kept != null && kept.hash().equals(now), "the roll kept under the old hash wasn't moved to the hash now: " + kept);
        helper.assertTrue(kept.items().equals(Set.of(extra)), "the roll's items were lost: " + kept.items());
        helper.assertTrue(LootIndex.unrolled(moved, hashes).isEmpty(), "the table is rolled again: " + LootIndex.unrolled(moved, hashes));
        Set<ResourceLocation> items = LootIndex.of(server, moved, hashes).itemsByTable().get(TABLE);
        helper.assertTrue(items != null && items.contains(extra), "the kept roll's items aren't in the index: " + items);
        helper.assertTrue(LootIndex.rehashed(server, moved, hashes) == moved, "a scan already under the hash now was changed again");
        StructureScan stale = new StructureScan(Map.of(structure, Set.of(TABLE)), Map.of(), Map.of(), Set.of(), Map.of(),
                Map.of(TABLE, new StructureScan.Rolled("not a hash of it", Set.of(extra))));
        helper.assertTrue(LootIndex.rehashed(server, stale, hashes) == stale, "a roll kept under neither hash was moved");
        helper.assertTrue(LootIndex.unrolled(stale, hashes).containsKey(TABLE), "a roll kept under neither hash isn't rolled again");
        JustEnoughStructures.LOGGER.info("In the test of rolls kept under the old hash: {} moved to {}", old, now);
        helper.succeed();
    }

    /** The scan as written to disk and read back, as the index saves it. */
    private static StructureScan saveAndRead(StructureScan scan) {
        try {
            Path dir = Files.createTempDirectory("jes-hash-test");
            try {
                LootIndexStore.write(dir, "test", scan);
                StructureScan read = LootIndexStore.read(dir, "test");
                if (read == null) {
                    throw new IllegalStateException("the saved scan couldn't be read back");
                }
                return read;
            } finally {
                Files.deleteIfExists(dir.resolve("test.bin"));
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
