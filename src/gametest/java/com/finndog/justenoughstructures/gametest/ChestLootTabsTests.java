package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.ClientState.ChestLoot;
import com.finndog.justenoughstructures.compat.foundin.ChestLootTabs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;

/** The setting that picks which chest loot tabs the recipe viewers show, without a viewer installed. */
public final class ChestLootTabsTests {
    /** Tabs of the same mods that aren't chest loot, and JES's own, which the setting never counts as another mod's. */
    private static final List<String> KEPT = List.of(
            "emi_loot:mob_drops", "emi_loot:block_drops", "emi_loot:gameplay_drops", "emi_loot:archaeology_drops",
            "ali:block_loot", "ali:entity_loot", "ali:gameplay_loot", "ali:trade_loot",
            "jeresources:mob", "jeresources:worldgen", "minecraft:crafting", "justenoughstructures:found_in", "", "chest_loot");

    private ChestLootTabsTests() {
    }

    /** It's saved in state.json, comes back after a restart, and is Both in a file saved before it existed. */
    public static void chestLootSettingSurvivesARestart(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        try {
            Path file = Files.createTempDirectory("jes-state").resolve("state.json");
            for (ChestLoot value : ChestLoot.values()) {
                ClientState.chestLoot = value;
                ClientState.write(file);
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                helper.assertTrue(json.has("chest_loot_in_viewers") && json.get("chest_loot_in_viewers").getAsString().equals(value.key()),
                        "state.json has " + json.get("chest_loot_in_viewers") + " for " + value);
                ClientState.chestLoot = value == ChestLoot.BOTH ? ChestLoot.OTHER_MODS : ChestLoot.BOTH;
                ClientState.read(file);
                helper.assertTrue(ClientState.chestLoot == value, value + " came back as " + ClientState.chestLoot);
            }
            helper.assertTrue(ChestLoot.of("just_enough_structures") == ChestLoot.JUST_ENOUGH_STRUCTURES
                    && ChestLoot.of("other_mods") == ChestLoot.OTHER_MODS && ChestLoot.of("both") == ChestLoot.BOTH,
                    "the values aren't written as both, just_enough_structures and other_mods");

            // A file from before the setting leaves the default, as a new player has.
            Files.writeString(file, "{\"format\": 1, \"spin\": false}");
            ClientState.chestLoot = ChestLoot.BOTH;
            ClientState.read(file);
            helper.assertTrue(ClientState.chestLoot == ChestLoot.BOTH, "a file without the setting made it " + ClientState.chestLoot);

            // A value this version doesn't know, or one that isn't text, changes nothing.
            ClientState.chestLoot = ChestLoot.OTHER_MODS;
            for (String odd : List.of("\"sideways\"", "\"BOTH\"", "[\"both\"]", "{}", "null", "3")) {
                Files.writeString(file, "{\"format\": 1, \"chest_loot_in_viewers\": " + odd + "}");
                ClientState.read(file);
                helper.assertTrue(ClientState.chestLoot == ChestLoot.OTHER_MODS, odd + " in state.json made the setting " + ClientState.chestLoot);
            }
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        } finally {
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }

    /**
     * Just Enough Structures hides exactly EMI Loot's, Advanced Loot Info's and Just Enough
     * Resources' chest loot tabs; Other mods leaves out JES's own; Both hides nothing.
     */
    public static void chestLootSettingPicksTheRightTabs(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        Set<String> others = Set.of("emi_loot:chest_loot", "emi_loot:trial_chamber", "ali:chest_loot", "ali:trial_chambers", "jeresources:dungeon");
        try {
            helper.assertTrue(ChestLootTabs.OTHER_TABS.equals(others), "other mods' chest loot tabs are " + ChestLootTabs.OTHER_TABS);
            for (ChestLoot value : ChestLoot.values()) {
                ClientState.chestLoot = value;
                boolean jesOnly = value == ChestLoot.JUST_ENOUGH_STRUCTURES;
                for (String id : others) {
                    helper.assertTrue(ChestLootTabs.hides(id) == jesOnly, value + " " + (jesOnly ? "doesn't hide " : "hides ") + id);
                }
                for (String id : KEPT) {
                    helper.assertTrue(!ChestLootTabs.hides(id), value + " hides " + id);
                }
                helper.assertTrue(ChestLootTabs.hidesOthers() == jesOnly, value + " says it hides other mods' tabs: " + ChestLootTabs.hidesOthers());
                helper.assertTrue(ChestLootTabs.showsOwn() == (value != ChestLoot.OTHER_MODS), value + " says it shows JES's rows: " + ChestLootTabs.showsOwn());
            }
        } finally {
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }

    /** Changing the setting tells the viewers once, keeping it the same doesn't, and a viewer that fails doesn't stop the rest. */
    public static void chestLootSettingTellsTheViewers(GameTestHelper helper) {
        ClientState.load();
        ChestLoot before = ClientState.chestLoot;
        AtomicBoolean listening = new AtomicBoolean(true);
        AtomicInteger told = new AtomicInteger();
        // The list of listeners lasts as long as the game, so these do nothing once the test is over.
        ChestLootTabs.onChange(() -> {
            if (listening.get()) {
                throw new IllegalStateException("a viewer that fails, in the test of the chest loot setting");
            }
        });
        ChestLootTabs.onChange(() -> {
            if (listening.get()) {
                told.incrementAndGet();
            }
        });
        try {
            ClientState.chestLoot = ChestLoot.BOTH;
            ChestLootTabs.choose(ChestLoot.BOTH);
            helper.assertTrue(told.get() == 0, "choosing what was already set told the viewers");
            ChestLootTabs.choose(ChestLoot.JUST_ENOUGH_STRUCTURES);
            helper.assertTrue(ClientState.chestLoot == ChestLoot.JUST_ENOUGH_STRUCTURES, "choosing didn't set it");
            helper.assertTrue(told.get() == 1, "a change told the viewers " + told.get() + " times");
            ChestLootTabs.choose(null);
            helper.assertTrue(ClientState.chestLoot == ChestLoot.JUST_ENOUGH_STRUCTURES && told.get() == 1, "choosing nothing changed it");
            ChestLootTabs.choose(ChestLoot.OTHER_MODS);
            helper.assertTrue(told.get() == 2 && ChestLootTabs.chosen() == ChestLoot.OTHER_MODS, "a second change told the viewers " + told.get() + " times");
        } finally {
            listening.set(false);
            ClientState.chestLoot = before;
        }
        helper.succeed();
    }
}
