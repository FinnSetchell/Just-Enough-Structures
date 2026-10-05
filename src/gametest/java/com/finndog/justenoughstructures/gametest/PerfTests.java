package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A diagnostic rather than a check: captures every structure the game has, vanilla and modded,
 * then builds the loot index, and writes how long each took and how big it came out to
 * perf.csv in the game test folder. Only runs with -Pperf, since with a lot of structure mods
 * installed it takes minutes.
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
            int bytes = Blobs.deflate(Blobs.toBytes(server.registryAccess(), buf -> Codecs.writeCapture(buf, id, seed, result))).length;
            rows.add(new Row(id, true, result.snapshot().blockCount(), result.snapshot().containers().size(), millis, bytes, ""));
        }
        long captureMillis = (System.nanoTime() - started) / 1_000_000;

        long indexStarted = System.nanoTime();
        LootIndex index = LootIndex.build(server, done -> { }, () -> false);
        long indexMillis = (System.nanoTime() - indexStarted) / 1_000_000;

        StringBuilder csv = new StringBuilder("structure,ok,blocks,containers,millis,bytes,error\n");
        for (Row r : rows) {
            csv.append(r.id()).append(',').append(r.ok()).append(',').append(r.blocks()).append(',').append(r.containers()).append(',')
                    .append(r.millis()).append(',').append(r.bytes()).append(',').append('"').append(r.error().replace("\"", "'")).append("\"\n");
        }
        Path out = Path.of("perf.csv").toAbsolutePath();
        try {
            Files.writeString(out, csv);
        } catch (IOException e) {
            helper.fail("couldn't write " + out + ": " + e);
            return;
        }

        long failed = rows.stream().filter(r -> !r.ok()).count();
        JustEnoughStructures.LOGGER.info("Perf: captured {} structures in {} ms ({} failed), loot index in {} ms ({} tables); details in {}",
                rows.size(), captureMillis, failed, indexMillis, index == null ? 0 : index.itemsByTable().size(), out);
        rows.stream().sorted(Comparator.comparingLong(Row::millis).reversed()).limit(10).forEach(r ->
                JesLog.debug("Perf: slow   {} ms  {} ({} blocks, {} KB)", r.millis(), r.id(), r.blocks(), r.bytes() / 1024));
        rows.stream().sorted(Comparator.comparingInt(Row::bytes).reversed()).limit(10).forEach(r ->
                JesLog.debug("Perf: big    {} KB  {} ({} blocks, {} ms)", r.bytes() / 1024, r.id(), r.blocks(), r.millis()));
        rows.stream().filter(r -> !r.ok()).forEach(r -> JesLog.debug("Perf: failed {}: {}", r.id(), r.error()));
        // With every installed structure placed, this is where one drawing from the real world's random shows up.
        helper.assertTrue(RealRandoms.places() == 0, RealRandoms.places() + " places drew from a real world's random during captures, see the log");
        helper.succeed();
    }
}
