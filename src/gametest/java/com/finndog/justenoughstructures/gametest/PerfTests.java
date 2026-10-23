package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.LoadedPieces;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.mixin.StructureTemplateManagerAccessor;
import com.finndog.justenoughstructures.network.Codecs;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A diagnostic rather than a check: captures every structure the game has, vanilla and modded,
 * then builds the loot index, and writes how long each took and how big it came out to
 * perf.csv in the game test folder. Only runs with -Pperf, since with a lot of structure mods
 * installed it takes minutes. On Fabric, -Pperf=index_first builds the loot index before the captures
 * instead of after, as in a world just opened: with none of the saved pieces loaded yet.
 * It ends by logging what the game is left holding: the heap in use after a collection, and the
 * saved pieces still loaded. How much memory building the loot index went through is logged too,
 * and the index itself is written to perf-index.txt, for comparing one build's with another's.
 */
public final class PerfTests {
    private PerfTests() {
    }

    private record Row(ResourceLocation id, boolean ok, int blocks, int containers, long millis, int bytes, String error) {
    }

    public static void captureEveryStructure(GameTestHelper helper) {
        if (!Boolean.getBoolean("jes.perf")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        List<ResourceLocation> ids = server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        boolean indexFirst = "index_first".equals(System.getProperty("jes.perf.order"));
        // -Pperf_room, Fabric only: how many blocks of saved pieces are left loaded, to see what each amount costs.
        String room = System.getProperty("jes.perf.room", "");
        if (!room.isEmpty()) {
            LoadedPieces.leaveLoaded("all".equals(room) ? Long.MAX_VALUE : Long.parseLong(room));
        }
        LootIndex index = null;
        long indexMillis = 0;
        long indexBytes = 0;
        if (indexFirst) {
            long indexStarted = System.nanoTime();
            long before = allocated();
            index = LootIndex.build(server, done -> { }, () -> false);
            indexBytes = allocated() - before;
            indexMillis = (System.nanoTime() - indexStarted) / 1_000_000;
        }
        List<Row> rows = new ArrayList<>();
        long started = System.nanoTime();
        for (ResourceLocation id : ids) {
            long t = System.nanoTime();
            CaptureResult result;
            try {
                result = StructureCapture.capture(server, id, StructureCapture.defaultSeed(id));
            } catch (RuntimeException | LinkageError e) {
                rows.add(new Row(id, false, 0, 0, (System.nanoTime() - t) / 1_000_000, 0, "threw " + e));
                continue;
            }
            long millis = (System.nanoTime() - t) / 1_000_000;
            if (!result.succeeded()) {
                rows.add(new Row(id, false, 0, 0, millis, 0, result.error()));
                continue;
            }
            long seed = StructureCapture.defaultSeed(id);
            int bytes = Codecs.packCapture(server.registryAccess(), id, seed, result).length;
            rows.add(new Row(id, true, result.snapshot().blockCount(), result.snapshot().containers().size(), millis, bytes, ""));
        }
        long captureMillis = (System.nanoTime() - started) / 1_000_000;

        if (!indexFirst) {
            long indexStarted = System.nanoTime();
            long before = allocated();
            index = LootIndex.build(server, done -> { }, () -> false);
            indexBytes = allocated() - before;
            indexMillis = (System.nanoTime() - indexStarted) / 1_000_000;
        }

        StringBuilder csv = new StringBuilder("structure,ok,blocks,containers,millis,bytes,error\n");
        for (Row r : rows) {
            csv.append(r.id()).append(',').append(r.ok()).append(',').append(r.blocks()).append(',').append(r.containers()).append(',')
                    .append(r.millis()).append(',').append(r.bytes()).append(',').append('"').append(r.error().replace("\"", "'")).append("\"\n");
        }
        Path out = Path.of("perf.csv").toAbsolutePath();
        try {
            Files.writeString(out, csv);
            Files.writeString(Path.of("perf-index.txt"), written(index));
        } catch (IOException e) {
            helper.fail("couldn't write " + out + ": " + e);
            return;
        }

        long failed = rows.stream().filter(r -> !r.ok()).count();
        JustEnoughStructures.LOGGER.info("Perf: captured {} structures in {} ms ({} failed), loot index in {} ms ({} tables) built {} the captures; details in {}",
                rows.size(), captureMillis, failed, indexMillis, index == null ? 0 : index.itemsByTable().size(), indexFirst ? "before" : "after", out);
        rows.stream().sorted(Comparator.comparingLong(Row::millis).reversed()).limit(10).forEach(r ->
                JesLog.debug("Perf: slow   {} ms  {} ({} blocks, {} KB)", r.millis(), r.id(), r.blocks(), r.bytes() / 1024));
        rows.stream().sorted(Comparator.comparingInt(Row::bytes).reversed()).limit(10).forEach(r ->
                JesLog.debug("Perf: big    {} KB  {} ({} blocks, {} ms)", r.bytes() / 1024, r.id(), r.blocks(), r.millis()));
        rows.stream().filter(r -> !r.ok()).forEach(r -> JesLog.debug("Perf: failed {}: {}", r.id(), r.error()));
        JustEnoughStructures.LOGGER.info("Perf: the loot index went through {} MB of memory on its thread", indexBytes >> 20);
        long blocks = 0;
        int pieces = 0;
        for (Optional<StructureTemplate> piece : ((StructureTemplateManagerAccessor) server.getStructureManager()).justenoughstructures$loaded().values()) {
            if (piece.isPresent()) {
                pieces++;
                for (StructureTemplate.Palette set : ((StructureTemplateAccessor) piece.get()).justenoughstructures$palettes()) {
                    blocks += set.blocks().size();
                }
            }
        }
        System.gc();
        JustEnoughStructures.LOGGER.info("Perf: {} MB of heap in use after a collection, {} blocks in {} loaded pieces, room for {}",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >> 20, blocks, pieces, room.isEmpty() ? "usual" : room);
        LoadedPieces.leaveLoaded(-1);
        // With every installed structure placed, this is where one drawing from the real world's random shows up.
        helper.assertTrue(RealRandoms.places() == 0, RealRandoms.places() + " places drew from a real world's random during captures, see the log");
        helper.succeed();
    }

    /** What this thread has asked for in memory so far, in bytes. Nought where Java doesn't keep count. */
    private static long allocated() {
        return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads
                ? threads.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0;
    }

    /** The loot index as lines in a fixed order: each structure with its tables, then each table with its items. */
    private static String written(LootIndex index) {
        StringBuilder text = new StringBuilder();
        if (index != null) {
            for (Map<ResourceLocation, Set<ResourceLocation>> part : List.of(index.tablesByStructure(), index.itemsByTable())) {
                part.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(e -> text.append(e.getKey()).append(':')
                        .append(e.getValue().stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(" ", " ", "\n"))));
                text.append('\n');
            }
        }
        return text.toString();
    }
}
