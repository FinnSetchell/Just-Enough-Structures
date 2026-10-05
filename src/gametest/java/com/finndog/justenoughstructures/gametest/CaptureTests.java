package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.levelgen.structure.Structure;

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
        checkVanillaStructure(helper, name);
        helper.succeed();
    }

    /** Every vanilla structure in one test, one a tick, for 26.1, which has no way to make a test for each. */
    public static void capturesEveryVanillaStructure(GameTestHelper helper) {
        for (int i = 0; i < VANILLA.size(); i++) {
            String name = VANILLA.get(i);
            helper.runAtTickTime(i + 1, () -> checkVanillaStructure(helper, name));
        }
        helper.runAtTickTime(VANILLA.size() + 1, helper::succeed);
    }

    private static void checkVanillaStructure(GameTestHelper helper, String name) {
        ResourceLocation id = Ids.of("minecraft", name);
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), id, SEED);
        if (!result.succeeded()) {
            helper.fail(name + " did not capture: " + result.error() + " " + attempts(result));
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
    }

    /** Shipwreck NBT has no loot tables. They're set by vanilla code from data markers during placement. */
    public static void lootSetByStructureCodeIsCaptured(GameTestHelper helper) {
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), Ids.parse("shipwreck"), SEED);
        helper.assertTrue(result.succeeded(), "shipwreck did not capture: " + result.error());
        boolean shipwreckLoot = result.snapshot().containers().stream()
                .anyMatch(c -> c.lootTable() != null && c.lootTable().startsWith("minecraft:chests/shipwreck_"));
        helper.assertTrue(shipwreckLoot, "no shipwreck loot table was captured");
        helper.succeed();
    }

    /**
     * Spawners whose mob a processor picked from a list carry the whole list: a Repurposed Structures
     * spawner file, and the lists Moog's Structure Lib processors carry. These structures come from
     * other mods, so only the ones installed are checked.
     */
    public static void spawnerPoolsAreRecorded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<String, Set<String>> expected = Map.of(
                "repurposed_structures:stronghold_nether", Set.of("minecraft:blaze", "minecraft:zoglin", "minecraft:zombified_piglin"),
                "mns:small_arena", Set.of("minecraft:zombified_piglin", "minecraft:magma_cube", "minecraft:skeleton"),
                "mss:small_tower", Set.of("minecraft:witch", "minecraft:wither_skeleton"));
        Registry<Structure> structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
            ResourceLocation id = Ids.parse(e.getKey());
            if (!structures.containsKey(id)) {
                continue;
            }
            Set<Set<String>> pools = new HashSet<>();
            for (int i = 0; i < 4 && !pools.contains(e.getValue()); i++) {
                CaptureResult result = StructureCapture.capture(server, id, SEED + i);
                if (result.succeeded()) {
                    pools.addAll(pools(result.snapshot()));
                }
            }
            helper.assertTrue(pools.contains(e.getValue()), id + "'s spawners pick from " + pools + ", none of them " + e.getValue());
        }
        helper.succeed();
    }

    /** The mobs of each spawner in a snapshot that carries a list. */
    private static Set<Set<String>> pools(StructureSnapshot snapshot) {
        Set<Set<String>> out = new HashSet<>();
        for (CompoundTag tag : snapshot.blockEntities()) {
            ListTag pool = Nbt.list(tag, SpawnerPools.TAG, Tag.TAG_COMPOUND);
            if (!pool.isEmpty()) {
                out.add(pool.stream().map(t -> Nbt.string((CompoundTag) t, "entity")).collect(Collectors.toSet()));
            }
        }
        return out;
    }

    public static void sameSeedGivesSameSnapshot(GameTestHelper helper) {
        ResourceLocation id = Ids.parse("village_plains");
        StructureSnapshot a = StructureCapture.capture(helper.getLevel().getServer(), id, SEED).snapshot();
        StructureSnapshot b = StructureCapture.capture(helper.getLevel().getServer(), id, SEED).snapshot();
        helper.assertTrue(a != null && b != null, "village_plains did not capture");
        String difference = difference(a, b);
        if (difference != null) {
            helper.fail(difference);
            return;
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
        StructureSnapshot snapshot = StructureCapture.capture(level.getServer(), Ids.parse("village_plains"), SEED + 7919).snapshot();
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

    private record Job(ResourceLocation id, long seed) {
    }

    /**
     * Previews and the loot index capture on separate threads, and structure code shares caches
     * that aren't thread safe. Run several captures at once on freshly loaded templates and check
     * each comes out exactly as it does on its own.
     */
    public static void parallelCapturesMatchSerialOnes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Job> jobs = List.of(
                new Job(Ids.parse("village_plains"), SEED + 1), new Job(Ids.parse("village_plains"), SEED + 2),
                new Job(Ids.parse("village_plains"), SEED + 3), new Job(Ids.parse("bastion_remnant"), SEED + 1),
                new Job(Ids.parse("bastion_remnant"), SEED + 2), new Job(Ids.parse("pillager_outpost"), SEED + 1));
        // Drop the loaded templates so their block caches start empty, which is when they get filled in.
        server.getStructureManager().onResourceManagerReload(server.getResourceManager());
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        List<Future<CaptureResult>> parallel = new ArrayList<>();
        for (Job job : jobs) {
            parallel.add(pool.submit(() -> StructureCapture.capture(server, job.id(), job.seed())));
        }
        pool.shutdown();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            CaptureResult together;
            try {
                // The server thread is blocked here, so a capture that needs it (to load a real
                // chunk, say) never finishes.
                together = parallel.get(i).get(1, TimeUnit.MINUTES);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                helper.fail(job + " didn't finish, it may be waiting on the server thread: " + e);
                return;
            }
            helper.assertTrue(together.succeeded(), job + " failed alongside the others: " + together.error() + " " + attempts(together));
            helper.assertTrue(together.attempts().stream().noneMatch(a -> a.getContents() instanceof TranslatableContents t
                    && t.getKey().endsWith("attempt.crashed")), job + " crashed alongside the others: " + attempts(together));
            CaptureResult alone = StructureCapture.capture(server, job.id(), job.seed());
            helper.assertTrue(alone.succeeded(), job + " failed on its own: " + alone.error());
            String difference = difference(together.snapshot(), alone.snapshot());
            if (difference != null) {
                helper.fail(job + ": " + difference);
                return;
            }
        }
        helper.succeed();
    }

    private static List<String> attempts(CaptureResult result) {
        return result.attempts().stream().map(Component::getString).toList();
    }

    /** Why two snapshots aren't the same, or null if they are. */
    private static String difference(StructureSnapshot a, StructureSnapshot b) {
        if (a.blockCount() != b.blockCount()) {
            return "block counts differ: " + a.blockCount() + " vs " + b.blockCount();
        }
        for (int i = 0; i < a.blockCount(); i++) {
            if (a.packedPosition(i) != b.packedPosition(i) || a.state(i) != b.state(i)) {
                return "snapshots differ at block " + i;
            }
        }
        return null;
    }

    public static void unknownStructureFailsCleanly(GameTestHelper helper) {
        CaptureResult result = StructureCapture.capture(helper.getLevel().getServer(), Ids.of("justenoughstructures", "does_not_exist"), SEED);
        helper.assertFalse(result.succeeded(), "an unknown structure should not capture");
        helper.succeed();
    }
}
