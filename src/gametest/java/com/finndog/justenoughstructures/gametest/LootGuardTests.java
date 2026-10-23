package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A chest's roll runs other mods' loot functions and modifiers on the server's own thread. Whatever
 * one of them throws mustn't get out: the player is still answered, with an empty chest, and the
 * server carries on.
 */
public final class LootGuardTests {
    private static final ResourceLocation BROKEN = Ids.parse("justenoughstructures_gametest:broken_loot");
    private static final ResourceLocation DESERT_PYRAMID_LOOT = Ids.parse("chests/desert_pyramid");

    private LootGuardTests() {
    }

    /** What another mod's loot has been seen to throw: an exception, a class that isn't there, and a stack that ran out. */
    private static Map<String, Supplier<Throwable>> failures() {
        Map<String, Supplier<Throwable>> failures = new LinkedHashMap<>();
        failures.put("an exception", () -> new IllegalStateException("a test's loot function"));
        failures.put("a missing class", () -> new NoClassDefFoundError("a/test/LootFunction"));
        failures.put("a stack overflow", StackOverflowError::new);
        return failures;
    }

    public static void fillSurvivesLootThatThrows(GameTestHelper helper) {
        failures().forEach((name, failure) -> {
            int before = ThrowingLoot.thrown;
            List<ItemStack> items = null;
            Throwable got = null;
            try {
                items = LootRolls.fill(helper.getLevel(), BROKEN, ThrowingLoot.table(failure), 1L, 27);
            } catch (Throwable t) {
                got = t;
            }
            helper.assertTrue(ThrowingLoot.thrown > before, "the table that throws " + name + " was never rolled");
            helper.assertTrue(got == null, "a roll that threw " + name + " got out of the fill: " + got);
            helper.assertTrue(items.size() == 27 && items.stream().allMatch(ItemStack::isEmpty),
                    "a roll that threw " + name + " didn't leave 27 empty slots: " + items);
        });
        // A working table still rolls afterwards.
        helper.assertTrue(LootRolls.fill(helper.getLevel(), DESERT_PYRAMID_LOOT, 42L, 27).stream().anyMatch(stack -> !stack.isEmpty()),
                "a table that works gave nothing after ones that threw");
        helper.succeed();
    }

    public static void chestRollThatThrowsIsAnswered(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        try {
            failures().forEach((name, failure) -> {
                Throwable[] got = new Throwable[1];
                List<byte[]> messages = MessageTests.sent(player, JesNetwork.LOOT, () -> {
                    try {
                        JesServer.answerLoot(player, 7, BROKEN, ThrowingLoot.table(failure), 1L, 27);
                    } catch (Throwable t) {
                        got[0] = t;
                    }
                });
                helper.assertTrue(got[0] == null, "a roll that threw " + name + " got out of the server's answer: " + got[0]);
                FriendlyByteBuf answer = Blobs.fromBytes(helper.getLevel().registryAccess(), MessageTests.only(helper, messages, "loot"));
                helper.assertTrue(answer.readVarInt() == 7, "the answer to a roll that threw " + name + " wasn't for the request that asked");
                List<ItemStack> items = Codecs.readItems(answer);
                helper.assertTrue(items.size() == 27 && items.stream().allMatch(ItemStack::isEmpty),
                        "the answer to a roll that threw " + name + " wasn't 27 empty slots: " + items);
            });
        } finally {
            JesServer.left(player);
        }
        helper.succeed();
    }
}
