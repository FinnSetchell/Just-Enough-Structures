package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.OddsStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Odds rolled for players are kept on disk under the table's hash, so after a restart the same table's
 * odds are answered at once instead of rolled again.
 */
public final class KeptOddsTests {
    private static final ResourceLocation TABLE = Ids.parse("chests/igloo_chest");

    private KeptOddsTests() {
    }

    public static void keptOddsAnswerWithoutRolling(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<LootOdds> first = new AtomicReference<>();
        JesServer.forgetOdds();
        JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), first::set);
        helper.succeedWhen(() -> {
            helper.assertTrue(first.get() != null, "the odds of " + TABLE + " haven't been rolled yet");
            OddsStore.save(server);
            Path file = OddsStore.fileNow();
            helper.assertTrue(file != null && Files.exists(file), "no file of kept odds after rolling " + TABLE + ": " + file);
            // As after a restart: nothing in memory, and the answer has to come without a single tick of rolling.
            JesServer.forgetOdds();
            AtomicReference<LootOdds> again = new AtomicReference<>();
            JesServer.odds(server, TABLE, LootRolls.table(server, TABLE), again::set);
            helper.assertTrue(again.get() != null, "the kept odds of " + TABLE + " weren't used: they're being rolled again");
            helper.assertTrue(again.get().rows().size() == first.get().rows().size() && again.get().rolls() == first.get().rolls(),
                    "the kept odds of " + TABLE + " differ from the rolled ones");
        });
    }
}
