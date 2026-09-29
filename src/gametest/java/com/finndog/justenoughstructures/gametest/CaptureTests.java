package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/** Loader-neutral test bodies for the capture pipeline. */
public final class CaptureTests {
    public static final long SEED = 20260929L;

    /** Every structure in vanilla 1.20.1. */
    public static final List<String> VANILLA = List.of(
            "ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city", "fortress", "igloo",
            "jungle_pyramid", "mansion", "mineshaft", "mineshaft_mesa", "monument", "nether_fossil", "ocean_ruin_cold",
            "ocean_ruin_warm", "pillager_outpost", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
            "ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp", "shipwreck",
            "shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins", "village_desert", "village_plains",
            "village_savanna", "village_snowy", "village_taiga");

    /** Structures that always generate with loot, and a loot table at least one container must use. */
    private static final Map<String, Set<String>> ALWAYS_HAS_LOOT = Map.of(
            "desert_pyramid", Set.of("minecraft:chests/desert_pyramid"),
            "jungle_pyramid", Set.of("minecraft:chests/jungle_temple"),
            "shipwreck", Set.of("minecraft:chests/shipwreck_supply", "minecraft:chests/shipwreck_map", "minecraft:chests/shipwreck_treasure"),
            "buried_treasure", Set.of("minecraft:chests/buried_treasure"),
            "pillager_outpost", Set.of("minecraft:chests/pillager_outpost"),
            "bastion_remnant", Set.of("minecraft:chests/bastion_other", "minecraft:chests/bastion_treasure",
                    "minecraft:chests/bastion_bridge", "minecraft:chests/bastion_hoglin_stable"),
            "ancient_city", Set.of("minecraft:chests/ancient_city", "minecraft:chests/ancient_city_ice_box"));

    private CaptureTests() {
    }

    public static void capturesVanillaStructure(GameTestHelper helper, String name) {
        ResourceLocation id = new ResourceLocation("minecraft", name);
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), id, SEED);
        if (!result.succeeded()) {
            helper.fail(name + " did not capture: " + result.error() + " " + result.attempts());
            return;
        }
        StructureSnapshot snapshot = result.snapshot();
        helper.assertTrue(snapshot.blockCount() > 0, name + " captured no blocks");
        helper.assertTrue(snapshot.pieceCount() > 0, name + " has no pieces");

        Set<String> expected = ALWAYS_HAS_LOOT.get(name);
        if (expected != null) {
            Set<String> found = snapshot.containers().stream()
                    .map(StructureSnapshot.Container::lootTable)
                    .filter(t -> t != null)
                    .collect(Collectors.toSet());
            helper.assertTrue(found.stream().anyMatch(expected::contains),
                    name + " should have a container using one of " + expected + " but had " + found);
        }
        helper.succeed();
    }

    /** Shipwreck NBT has no loot tables. They're set by vanilla code from data markers during placement. */
    public static void lootSetByStructureCodeIsCaptured(GameTestHelper helper) {
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), new ResourceLocation("shipwreck"), SEED);
        helper.assertTrue(result.succeeded(), "shipwreck did not capture: " + result.error());
        boolean shipwreckLoot = result.snapshot().containers().stream()
                .anyMatch(c -> c.lootTable() != null && c.lootTable().startsWith("minecraft:chests/shipwreck_"));
        helper.assertTrue(shipwreckLoot, "no shipwreck loot table was captured");
        helper.succeed();
    }

    public static void sameSeedGivesSameSnapshot(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation("village_plains");
        StructureSnapshot a = StructureCapture.capture(helper.getLevel().getServer(), id, SEED).snapshot();
        StructureSnapshot b = StructureCapture.capture(helper.getLevel().getServer(), id, SEED).snapshot();
        helper.assertTrue(a != null && b != null, "village_plains did not capture");
        helper.assertTrue(a.blockCount() == b.blockCount(), "block counts differ: " + a.blockCount() + " vs " + b.blockCount());
        for (int i = 0; i < a.blockCount(); i++) {
            if (a.packedPosition(i) != b.packedPosition(i) || a.state(i) != b.state(i)) {
                helper.fail("snapshots differ at block " + i);
                return;
            }
        }
        StructureSnapshot c = StructureCapture.capture(helper.getLevel().getServer(), id, SEED + 1).snapshot();
        helper.assertTrue(c != null, "village_plains did not capture with another seed");
        helper.succeed();
    }

    /**
     * A village is full of beds and workstations. Capturing one must not register any of them as
     * points of interest in the real world, which WorldGenRegion.setBlock would normally do.
     */
    public static void captureLeavesTheWorldAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().overworld();
        // A seed no other test uses, so these positions can't already hold POIs from another capture.
        StructureSnapshot snapshot = StructureCapture.capture(level.getServer(), new ResourceLocation("village_plains"), SEED + 7919).snapshot();
        helper.assertTrue(snapshot != null, "village_plains did not capture");
        List<BlockPos> poiBlocks = new ArrayList<>();
        for (int i = 0; i < snapshot.blockCount(); i++) {
            if (PoiTypes.forState(snapshot.state(i)).isPresent()) {
                int packed = snapshot.packedPosition(i);
                poiBlocks.add(snapshot.origin().offset(StructureSnapshot.unpackX(packed), StructureSnapshot.unpackY(packed), StructureSnapshot.unpackZ(packed)));
            }
        }
        helper.assertTrue(!poiBlocks.isEmpty(), "the village had no beds or workstations to check");
        // POI changes are queued with server.execute, so give them a few ticks to show up.
        helper.runAfterDelay(5, () -> {
            List<BlockPos> leaked = poiBlocks.stream().filter(pos -> level.getPoiManager().getType(pos).isPresent()).toList();
            if (!leaked.isEmpty()) {
                helper.fail("capturing a village added " + leaked.size() + " points of interest to the world, e.g. at " + leaked.get(0).toShortString());
                return;
            }
            helper.succeed();
        });
    }

    public static void unknownStructureFailsCleanly(GameTestHelper helper) {
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), new ResourceLocation("justenoughstructures", "does_not_exist"), SEED);
        helper.assertFalse(result.succeeded(), "an unknown structure should not capture");
        helper.succeed();
    }
}
