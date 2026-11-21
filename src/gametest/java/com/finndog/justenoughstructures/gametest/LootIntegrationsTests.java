package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootIntegrations;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.server.LootHooks;
import com.lootintegrations.loot.GlobalLootModifierIntegration;
import com.lootintegrations.loot.LootModifierManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The loot index lists what Loot Integrations adds to a table, read from its map, instead of what a
 * few rolls of the table happen to pick. The game tests have a stand-in for its map, shaped as in its
 * real jars ({@code com.lootintegrations.loot}); nothing is listed until a test lists it.
 */
public final class LootIntegrationsTests {
    private LootIntegrationsTests() {
    }

    /**
     * Laid out as Loot Integrations' own files are: a structure's chest (here the big underwater ruin's)
     * gets stacks from a category, a table that gives nothing itself, and the category gets them from
     * vanilla chest tables, one of which is the chest again. The chest then lists what those give, and
     * their named books and potions, less the items in the ignored tag (bones, in the game tests' data)
     * and exploration maps; what rolls of a vanilla table find later reaches the chest too. On Fabric
     * before 26.1, where other mods' loot code is listed, the chest is listed as needing only a few
     * rolls, and a table it only takes stacks out of is rolled in full; elsewhere every table is rolled
     * in full. Every check runs in one go on the server thread, and the map is emptied at the end, so
     * no other test sees it.
     */
    public static void listsWhatLootIntegrationsAdds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation ruin = Ids.parse("chests/underwater_ruin_big");
        ResourceLocation category = Ids.of("justenoughstructures_gametest", "chests/category");
        ResourceLocation trimmed = Ids.of("justenoughstructures_gametest", "chests/trimmed");
        ResourceLocation empty = Ids.of("justenoughstructures_gametest", "chests/empty");
        ResourceLocation dungeon = Ids.parse("chests/simple_dungeon");
        ResourceLocation ancient = Ids.parse("chests/ancient_city");
        ResourceLocation shipwreck = Ids.parse("chests/shipwreck_map");
        ResourceLocation bone = Ids.parse("bone");
        ResourceLocation star = Ids.parse("nether_star");
        StructureScan scan = new StructureScan(Map.of(Ids.parse("ocean_ruin_warm"), Set.of(ruin), Ids.parse("monster_room"), Set.of(dungeon)),
                Map.of(), Map.of(), Set.of(), Map.of(), Map.of());
        Set<ResourceLocation> own = LootIndex.of(server, scan).itemsByTable().get(ruin);

        Map<ResourceLocation, List<GlobalLootModifierIntegration>> map = LootModifierManager.lootOptionsMap;
        map.put(ruin, List.of(new GlobalLootModifierIntegration(category, Map.of(ruin, 2))));
        map.put(category, List.of(new GlobalLootModifierIntegration(dungeon, Map.of(category, 9)),
                new GlobalLootModifierIntegration(ancient, Map.of(category, 4)),
                new GlobalLootModifierIntegration(shipwreck, Map.of(category, 3)),
                new GlobalLootModifierIntegration(ruin, Map.of(category, 1))));
        map.put(trimmed, List.of(new GlobalLootModifierIntegration(empty, Map.of(trimmed, 0))));
        try {
            LootIntegrations.Listing listing = LootIntegrations.read();
            helper.assertTrue(listing.sourcesOf(ruin).equals(Set.of(category, dungeon, ancient, shipwreck)),
                    "Loot Integrations rolls into the ruin's chest " + listing.sourcesOf(ruin));
            helper.assertTrue(listing.leftOut().contains(bone) && listing.leftOut().contains(Ids.parse("filled_map")),
                    "Loot Integrations is said to leave out only " + listing.leftOut());

            LootIndex index = LootIndex.of(server, scan);
            Set<ResourceLocation> listed = index.itemsByTable().get(ruin);
            Set<ResourceLocation> expected = new TreeSet<>(own);
            for (ResourceLocation source : List.of(dungeon, ancient, shipwreck)) {
                Set<ResourceLocation> given = LootIndex.itemsIn(server, source, new HashSet<>());
                given.removeAll(listing.leftOut());
                expected.addAll(given);
            }
            helper.assertTrue(LootIndex.itemsIn(server, dungeon, new HashSet<>()).contains(bone) && !own.contains(bone),
                    "the dungeon doesn't give bones, or the ruin does, so the test shows nothing");
            helper.assertTrue(expected.equals(listed), "the loot index lists the ruin's chest as " + listed + ", not " + expected);
            Map<ResourceLocation, Set<String>> kinds = index.kindsByTable().getOrDefault(ruin, Map.of());
            helper.assertTrue(kinds.getOrDefault(Ids.parse("enchanted_book"), Set.of()).contains("enchantment:minecraft:swift_sneak")
                            && kinds.getOrDefault(Ids.parse("potion"), Set.of()).contains("potion:minecraft:strong_regeneration"),
                    "the ruin's chest doesn't list the ancient city's named book and potion: " + kinds);

            // What the dungeon's rolls find, which reading it doesn't show, reaches the chest it's rolled into.
            LootIndex rolled = index.withItems(Map.of(dungeon, Set.of(star)), listing);
            helper.assertTrue(rolled.itemsByTable().get(dungeon).contains(star) && rolled.itemsByTable().get(ruin).contains(star),
                    "what the dungeon's rolls found didn't reach the ruin's chest: " + rolled.itemsByTable().get(ruin));

            LootHooks.Found found = LootHooks.find();
            //? if fabric && >=26.1 {
            /*// From 26.1 the test mod listens to Fabric's drops event, which can change any table, so every table is rolled in full.
            helper.assertTrue(found.anyTable() && !found.listed(ruin), "the ruin's chest is listed, though the drops event may change any table");
            *///?} else if fabric {
            helper.assertTrue(found.listed(ruin) && !found.touches(ruin), "the ruin's chest is rolled in full, though what's added to it is listed");
            helper.assertTrue(found.touches(trimmed) && !found.listed(trimmed), "a table Loot Integrations may take stacks out of isn't rolled in full");
            //?} else {
            /*// Forge's and NeoForge's own loot modifiers aren't read, so every table is rolled in full there.
            helper.assertTrue(found.anyTable() && !found.listed(ruin), "the ruin's chest is listed, though Forge's loot modifiers may change any table");
            *///?}
        } finally {
            map.clear();
        }
        helper.assertTrue(own.equals(LootIndex.of(server, scan).itemsByTable().get(ruin)), "the ruin's chest still lists more once nothing is added to it");
        helper.succeed();
    }
}
