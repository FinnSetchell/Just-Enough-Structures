package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.server.LootIndexStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Whether a saved loot index is used as it is on the next start of the same game: its check rolls no
 * loot table again, and a table whose set_enchantments lists several enchantments hashes the same on
 * every start, though the game keeps them in a different order each time. Needs two starts of the
 * same game folder: the first saves the table's hash in it for the second to compare.
 */
final class RollsKeptScenario {
    /** The test mod's table, with six enchantments in one set_enchantments. */
    static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:chests/many_enchantments");

    private RollsKeptScenario() {
    }

    static Director build(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new UnsupportedOperationException("needs a world of its own");
        }
        Path file = mc.gameDirectory.toPath().resolve("jes-test").resolve("rolls_kept.txt");
        Director d = new Director(mc);
        d.then(run(ClientRequests::index))
                .then(until(() -> ClientRequests.indexReady() && !ClientRequests.indexFilling(), 20 * 60 * 20))
                .then(run(() -> {
                    if (!ClientRequests.indexReady() || ClientRequests.indexFilling()) {
                        throw new IllegalStateException("The finished loot index never came");
                    }
                    String hash = server.submit(() -> LootIndex.tableHash(server, TABLE)).join();
                    if (hash.isEmpty()) {
                        throw new IllegalStateException(TABLE + " isn't loaded, so this shows nothing");
                    }
                    Set<ResourceLocation> rolled = LootIndexStore.lastToRoll();
                    List<String> wrong = new ArrayList<>();
                    String before = read(file);
                    if (before == null) {
                        write(file, hash);
                        JustEnoughStructures.LOGGER.info("First start: {} hashes to {}, kept for the next start to compare", TABLE, hash);
                    } else if (!before.equals(hash)) {
                        wrong.add("the hash of " + TABLE + " changed between starts, from " + before + " to " + hash);
                    } else {
                        JustEnoughStructures.LOGGER.info("{} hashes to {} as on the start before", TABLE, hash);
                    }
                    if (rolled == null) {
                        JustEnoughStructures.LOGGER.info("The server built the loot index, with no saved one to check");
                    } else if (!rolled.isEmpty()) {
                        wrong.add("the check of the saved loot index rolled " + rolled.size() + " tables again: " + rolled);
                    } else {
                        JustEnoughStructures.LOGGER.info("The check of the saved loot index rolled no table again");
                    }
                    if (!wrong.isEmpty()) {
                        throw new IllegalStateException(String.join("; ", wrong));
                    }
                }));
        return d;
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String hash) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, hash, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
