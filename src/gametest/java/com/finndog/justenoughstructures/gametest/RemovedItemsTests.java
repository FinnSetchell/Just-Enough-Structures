package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootMath;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.RemovedItems;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.server.OddsStore;
import elocindev.item_obliterator.fabric_quilt.ItemObliterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Items Item Obliterator removes are left out of what JES says loot gives, as no chest or player keeps
 * them. The game tests have a stand-in for its check and config, shaped as in its real jars
 * ({@code elocindev.item_obliterator.fabric_quilt}); nothing is removed until a test lists it.
 */
public final class RemovedItemsTests {
    private RemovedItemsTests() {
    }

    /**
     * With spider eyes removed, the desert pyramid's odds, its worked-out chances, a chest of it, a roll
     * as the odds make them, the loot index (what the table names, what its rolls found and what a
     * structure places) and the items chest loot gives leave them out, and its kept odds go under
     * another name. Every check runs in one go on the server thread, and the item goes back at the
     * end, so no other test sees it gone.
     */
    public static void removedItemsLeaveLoot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = LootRolls.level(server);
        ResourceLocation pyramid = Ids.parse("chests/desert_pyramid");
        ResourceLocation eye = Ids.parse("spider_eye");
        ResourceLocation bone = Ids.parse("bone");
        ResourceLocation structure = Ids.parse("desert_pyramid");
        String keyBefore = OddsStore.keyOf(server);
        ItemObliterator.Config.blacklisted_items.add("// spider eyes, for the game test");
        ItemObliterator.Config.blacklisted_items.add("!minecraft:spider_e.e");
        RemovedItems.forget();
        try {
            helper.assertTrue(RemovedItems.removed(Items.SPIDER_EYE) && !RemovedItems.removed(Items.BONE),
                    "Item Obliterator's check says spider eyes " + RemovedItems.removed(Items.SPIDER_EYE) + ", bones " + RemovedItems.removed(Items.BONE));

            LootOdds rolled = LootRolls.odds(level, pyramid, 2000, pyramid.hashCode());
            helper.assertTrue(rolled.rows().stream().noneMatch(r -> r.example().is(Items.SPIDER_EYE)), "the desert pyramid's odds still give spider eyes");
            helper.assertTrue(rolled.rows().stream().anyMatch(r -> r.example().is(Items.BONE)), "the desert pyramid's odds lost its bones too");
            LootMath.Checked checked = LootMath.check(server, rolled);
            helper.assertTrue(checked.fits(), "the desert pyramid's rolls don't fit its worked-out chances once spider eyes are removed");
            helper.assertTrue(checked.odds().rows().stream().noneMatch(r -> r.example().is(Items.SPIDER_EYE)),
                    "the desert pyramid's worked-out chances still give spider eyes");

            for (long seed = 1; seed <= 40; seed++) {
                for (ItemStack stack : LootRolls.fill(level, pyramid, seed, 27)) {
                    helper.assertFalse(stack.is(Items.SPIDER_EYE), "a desert pyramid chest rolled with seed " + seed + " has a spider eye");
                }
                for (ItemStack stack : LootRolls.countedRoll(level, LootRolls.table(server, pyramid), seed)) {
                    helper.assertFalse(stack.is(Items.SPIDER_EYE), "a counted roll of the desert pyramid with seed " + seed + " has a spider eye");
                }
            }

            Set<ResourceLocation> chestItems = LootIndex.chestItems(server);
            helper.assertTrue(!chestItems.contains(eye) && chestItems.contains(bone), "the items chest loot gives have spider eyes " + chestItems.contains(eye)
                    + ", bones " + chestItems.contains(bone));
            StructureScan scan = new StructureScan(Map.of(structure, Set.of(pyramid)), Map.of(), Map.of(), Set.of(), Map.of(structure, Set.of(eye, bone)),
                    Map.of(pyramid, new StructureScan.Rolled(LootIndex.tableHash(server, pyramid), Set.of(eye, Ids.parse("nether_star")))));
            LootIndex index = LootIndex.of(server, scan);
            Set<ResourceLocation> listed = index.itemsByTable().get(pyramid);
            helper.assertTrue(listed != null && !listed.contains(eye) && listed.contains(bone) && listed.contains(Ids.parse("nether_star")),
                    "the loot index lists the desert pyramid's items as " + listed);
            helper.assertTrue(Set.of(bone).equals(index.placedByStructure().get(structure)),
                    "the loot index lists what the structure places as " + index.placedByStructure().get(structure));

            helper.assertFalse(Objects.equals(keyBefore, OddsStore.keyOf(server)), "odds are kept under the same name whatever Item Obliterator removes");
        } finally {
            ItemObliterator.Config.blacklisted_items.clear();
            RemovedItems.forget();
        }
        helper.assertTrue(Objects.equals(keyBefore, OddsStore.keyOf(server)), "odds aren't kept under their old name once nothing is removed again");
        LootOdds again = LootRolls.odds(level, pyramid, 2000, pyramid.hashCode());
        helper.assertTrue(again.rows().stream().anyMatch(r -> r.example().is(Items.SPIDER_EYE)), "the desert pyramid doesn't give spider eyes once they're no longer removed");
        helper.succeed();
    }
}
