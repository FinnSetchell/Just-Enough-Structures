package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.loot.KnownLoot;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.StructureScan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The loot index's scan of the structures, which goes through them twice: first what is found
 * without generating anything, handed over early so players have most of the loot at once, then
 * every structure generated.
 */
public final class ScanTests {
    private ScanTests() {
    }

    /**
     * The scan handed over early comes before anything is generated: it holds what template pools
     * show and what's known of vanilla's structures without pools, never anything the finished scan
     * doesn't, and the finished scan is what each structure gives when it's scanned on its own.
     */
    public static void earlyScanIsPartOfTheFinishedOne(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // Two built from template pools, and two that aren't.
        ResourceLocation outpost = Ids.parse("pillager_outpost");
        ResourceLocation village = Ids.parse("village_plains");
        ResourceLocation pyramid = Ids.parse("desert_pyramid");
        ResourceLocation shipwreck = Ids.parse("shipwreck");
        List<ResourceLocation> ids = List.of(outpost, pyramid, village, shipwreck);

        StructureScan[] early = new StructureScan[1];
        List<Integer> progress = new ArrayList<>();
        List<Integer> doneWhenEarly = new ArrayList<>();
        List<ResourceLocation> generated = new ArrayList<>();
        List<Integer> generatedWhenEarly = new ArrayList<>();
        StructureScan finished = LootIndex.scan(server, ids, progress::add, () -> false, new AtomicInteger(), (id, firstView) -> generated.add(id), scan -> {
            early[0] = scan;
            doneWhenEarly.add(progress.size());
            generatedWhenEarly.add(generated.size());
        });
        helper.assertTrue(finished != null, "the scan was cancelled");
        // Every one of them is generated after the early scan, once each for its first view: those without pools first.
        helper.assertTrue(generated.equals(List.of(pyramid, shipwreck, outpost, village)), "the structures were generated in the order " + generated);
        helper.assertTrue(early[0] != null && doneWhenEarly.size() == 1, "the scan was handed over early " + doneWhenEarly.size() + " times");
        helper.assertTrue(generatedWhenEarly.get(0) == 0 && doneWhenEarly.get(0) == 0,
                generatedWhenEarly.get(0) + " structures were generated, and " + doneWhenEarly.get(0) + " counted as done, when the scan was handed over early");
        helper.assertTrue(progress.equals(List.of(1, 2, 3, 4)), "progress went " + progress);

        for (ResourceLocation id : ids) {
            Set<ResourceLocation> soon = early[0].tables().getOrDefault(id, Set.of());
            Set<ResourceLocation> last = finished.tables().getOrDefault(id, Set.of());
            helper.assertTrue(!last.isEmpty(), id + " has no loot in the finished scan");
            helper.assertTrue(last.containsAll(soon), id + " had loot early that the finished scan doesn't: " + soon + " against " + last);
            helper.assertTrue(finished.templates().getOrDefault(id, Set.of()).containsAll(early[0].templates().getOrDefault(id, Set.of())),
                    id + " had templates early that the finished scan doesn't");
        }
        for (ResourceLocation id : List.of(pyramid, shipwreck)) {
            helper.assertTrue(early[0].tables().get(id) != null && early[0].tables().get(id).equals(finished.tables().get(id)),
                    id + "'s known loot isn't what generating it finds: " + early[0].tables().get(id) + " then " + finished.tables().get(id));
        }
        for (ResourceLocation id : List.of(outpost, village)) {
            helper.assertTrue(!early[0].tables().getOrDefault(id, Set.of()).isEmpty(), id + "'s pools showed no loot early");
            helper.assertTrue(!early[0].templates().getOrDefault(id, Set.of()).isEmpty(), id + "'s pools showed no templates early");
        }
        // All four are still to generate, so a copy of the early scan kept from a build cut short has them to try again.
        helper.assertTrue(early[0].retry().containsAll(ids), "the early scan lists " + early[0].retry() + " to try again, not all four still to generate");
        // The early scan is a copy: the finished one's later changes didn't reach into it.
        helper.assertTrue(early[0].tables() != finished.tables() && early[0].templates() != finished.templates(), "the early scan shares its maps with the finished one");

        // Going through them in two passes gives each structure what scanning it on its own does.
        for (ResourceLocation id : ids) {
            StructureScan alone = LootIndex.scan(server, List.of(id), done -> {
            }, () -> false, new AtomicInteger());
            helper.assertTrue(alone.tables().get(id).equals(finished.tables().get(id)),
                    id + " on its own has " + alone.tables().get(id) + ", in the scan of all four " + finished.tables().get(id));
            helper.assertTrue(alone.templates().getOrDefault(id, Set.of()).equals(finished.templates().getOrDefault(id, Set.of())),
                    id + " on its own places " + alone.templates().get(id) + ", in the scan of all four " + finished.templates().get(id));
        }
        helper.succeed();
    }

    /**
     * What the first loot index knows of vanilla's structures without pools is what generating them
     * for the index finds, on this build: their loot tables, and the items they place themselves.
     */
    public static void knownLootIsWhatGeneratingFinds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : new TreeSet<>(KnownLoot.listed())) {
            if (KnownLoot.of(registry, id) != null) {
                ids.add(id);
            }
        }
        helper.assertTrue(ids.size() >= 15, "only " + ids.size() + " of the known vanilla structures are here as vanilla's: " + ids);
        StructureScan scan = LootIndex.scan(server, ids, done -> {
        }, () -> false, new AtomicInteger());
        helper.assertTrue(scan != null, "the scan was cancelled");
        List<String> wrong = new ArrayList<>();
        for (ResourceLocation id : ids) {
            KnownLoot.Known known = KnownLoot.of(registry, id);
            Set<ResourceLocation> tables = scan.tables().getOrDefault(id, Set.of());
            Set<ResourceLocation> placed = scan.placed().getOrDefault(id, Set.of());
            if (!tables.equals(known.tables()) || !placed.equals(known.placed())) {
                wrong.add(id + " gives tables " + new TreeSet<>(tables) + " and places " + new TreeSet<>(placed));
            }
        }
        helper.assertTrue(wrong.isEmpty(), "what's known isn't what generating finds: " + String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * A structure without pools can place a part only now and then, like the igloo's basement, which
     * holds the igloo's one chest and comes with about one igloo in two. The index still finds the
     * chest, so Found in lists the igloo for what the chest gives. Built twice, the index comes out
     * the same.
     */
    public static void partsPlacedNowAndThenAreIndexed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation igloo = Ids.parse("igloo");
        ResourceLocation chest = Ids.parse("chests/igloo_chest");
        LootIndex index = LootIndex.build(server, List.of(igloo), done -> {
        }, () -> false);
        helper.assertTrue(index != null, "the index was cancelled");
        Set<ResourceLocation> tables = index.tablesByStructure().getOrDefault(igloo, Set.of());
        helper.assertTrue(tables.contains(chest), "the igloo's basement chest isn't in the loot index, which has " + tables + " for the igloo");
        helper.assertTrue(index.itemsByTable().getOrDefault(chest, Set.of()).contains(Ids.parse("golden_apple")),
                "the igloo chest's golden apple isn't in the loot index");
        LootIndex again = LootIndex.build(server, List.of(igloo), done -> {
        }, () -> false);
        helper.assertTrue(again != null && again.tablesByStructure().equals(index.tablesByStructure()),
                "the igloo's loot came out different the second time: " + index.tablesByStructure() + " then " + (again == null ? null : again.tablesByStructure()));
        helper.succeed();
    }

    /**
     * The first layout the index generates of a structure is the one it's shown with, and the next
     * three use the seeds after it, so the index finds at least what those seeds find. The ones after
     * those start the structure's random differently. The game starts it from the layout's seed, and
     * neighbouring seeds draw nearly the same first number, which picks the way the igloo faces and
     * could just as well pick whether a part is placed.
     */
    public static void layoutsStartTheirRandomsApart(GameTestHelper helper) {
        for (String name : List.of("igloo", "shipwreck", "ocean_ruin_cold", "desert_pyramid")) {
            ResourceLocation id = Ids.parse(name);
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(LootIndex.layoutSeed(id, i) == StructureCapture.defaultSeed(id) + i, name + "'s layout " + i + " has a seed of its own");
            }
            Set<Integer> turns = new TreeSet<>();
            for (int i = 4; i < 12; i++) {
                // The random a structure is laid out with, made as the game makes it, at the chunk captures start in.
                WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
                random.setLargeFeatureSeed(LootIndex.layoutSeed(id, i), 0, 0);
                turns.add(random.nextInt(4));
            }
            helper.assertTrue(turns.size() > 1, name + "'s layouts after the first four all draw the same first number, so they all face one way: " + turns);
        }
        helper.succeed();
    }
}
