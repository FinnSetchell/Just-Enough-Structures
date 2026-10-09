package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.DIAMONDS_ONLY;
import static com.finndog.justenoughstructures.gametest.TestSupport.key;
import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;
import static com.finndog.justenoughstructures.gametest.TestSupport.reload;

import com.finndog.justenoughstructures.Folders;
import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JsonPaths;
import com.finndog.justenoughstructures.client.screen.LootTypes;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.JsonMerge;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.OverridePack;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.Items;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;
*///?}

/** Loot tables edited in the browser, saved as overrides. Every test works in a folder of its own. */
public final class OverrideTests {
    private static final ResourceLocation IGLOO = Ids.parse("chests/igloo_chest");

    // Vanilla tables that between them write modifiers and conditions every way 26.3 does: one or a
    // list, conditions on modifiers, predicates by id, and entries inside entries.
    private static final List<String> SHAPES = List.of("chests/simple_dungeon", "chests/shipwreck_map", "chests/end_city_treasure",
            "chests/bastion_treasure", "blocks/acacia_slab", "blocks/oak_leaves", "blocks/gravel", "entities/zombie");

    private OverrideTests() {
    }

    /** A table goes into the editor's shape and comes back out as the same table. */
    public static void editorShapeRoundTrips(GameTestHelper helper) {
        //? if >=1.21 {
        /*MinecraftServer server = helper.getLevel().getServer();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for (String name : SHAPES) {
            JsonObject game = LootTable.DIRECT_CODEC.encodeStart(ops, LootRolls.table(server, Ids.parse(name))).getOrThrow().getAsJsonObject();
            JsonObject editable = LootFormat.forEditing(game);
            JsonObject back = LootFormat.forGame(editable);
            JsonElement again = LootTable.DIRECT_CODEC.encodeStart(ops, LootTable.DIRECT_CODEC.parse(ops, back).getOrThrow(AssertionError::new)).getOrThrow();
            helper.assertTrue(again.equals(game), name + " came back from the editor as " + back);
        }
        *///?}
        helper.succeed();
    }

    private static Path freshFolder() {
        try {
            Path dir = Files.createTempDirectory("jes-overrides");
            LootOverrides.setFolder(dir);
            return dir;
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
    }

    /** A saved edit is what the editor gets back, next to the table it replaced. */
    public static void editsSaveAndReadBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.View before = LootOverrides.view(server.getResourceManager(), IGLOO);
            helper.assertTrue(before.status() == LootOverrides.Status.NONE && before.original() != null && before.original().equals(before.current()),
                    "an untouched table should read as the vanilla one, got " + before.status());

            Component saved = LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(key(saved).endsWith("override.saved"), "saving got " + saved.getString());
            helper.assertTrue(Files.exists(dir.resolve("pack.mcmeta")), "the folder wasn't made a datapack");

            LootOverrides.View after = LootOverrides.view(server.getResourceManager(), IGLOO);
            helper.assertTrue(after.status() == LootOverrides.Status.ACTIVE, "a fresh edit should be active, got " + after.status());
            helper.assertTrue(after.current().contains("minecraft:diamond"), "the edit didn't read back");
            helper.assertTrue(after.original().equals(before.original()), "the original changed when the edit was saved");
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /**
     * A table named to reach outside the overrides folder, with ".." in its id, is turned down, and
     * the file it names is never read, written or moved.
     */
    public static void tableIdsStayInTheFolder(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        Path outside = dir.resolveSibling(dir.getFileName() + "-outside.json");
        try {
            Files.writeString(outside, DIAMONDS_ONLY);
            // data/minecraft/loot_table(s)/ and four steps back up is the folder's own parent.
            ResourceLocation escape = Ids.parse("minecraft:../../../../" + dir.getFileName() + "-outside");
            LootOverrides.View view = LootOverrides.view(server.getResourceManager(), escape);
            helper.assertTrue(view.status() == LootOverrides.Status.NONE && (view.current() == null || !view.current().contains("diamond")),
                    "a file outside the folder was read as an override");
            helper.assertFalse(LootOverrides.exists(server, escape), "a file outside the folder counted as a table");
            Component saved = LootOverrides.save(server.getResourceManager(), escape, "{\"pools\": []}");
            helper.assertTrue(key(saved).endsWith("override.bad_id"), "saving outside the folder got " + saved.getString());
            helper.assertTrue(key(LootOverrides.remove(escape)).endsWith("override.none"), "removing outside the folder wasn't refused");
            helper.assertTrue(key(LootOverrides.keep(server.getResourceManager(), escape)).endsWith("override.none"), "keeping outside the folder wasn't refused");
            helper.assertTrue(Files.readString(outside).equals(DIAMONDS_ONLY), "the file outside the folder was changed");
            // Some versions don't let an id be made with these at all, which is just as safe.
            ResourceLocation dots = ResourceLocation.tryParse("..:chests/igloo");
            helper.assertTrue(dots == null || !Ids.fileSafe(dots), "a namespace of .. counted as safe");
            ResourceLocation empty = ResourceLocation.tryParse("minecraft:chests//igloo");
            helper.assertTrue(empty == null || !Ids.fileSafe(empty), "an empty part counted as safe");
            helper.assertTrue(Ids.fileSafe(Ids.parse("chests/igloo_chest")), "an ordinary table counted as unsafe");
        } catch (IOException e) {
            throw new AssertionError("couldn't write the test's files", e);
        } finally {
            LootOverrides.setFolder(null);
            try {
                Files.deleteIfExists(outside);
            } catch (IOException e) {
                // Left in the temp folder.
            }
        }
        helper.succeed();
    }

    //? if >=1.21 {
    /*// Before a world has loaded only the game's own registries are there, so an override naming a
    // mod's enchantment is kept for the world to load, while one the world itself couldn't load,
    // checked once it has, is still caught.
    public static void overridesWithModdedEntriesLoad(GameTestHelper helper) {
        String modded = LootFormat.forGame(JsonParser.parseString("""
                {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:book",
                  "functions": [{"function": "minecraft:set_enchantments", "enchantments": {"somemod:frostbite": 1}}]}]}]}
                """).getAsJsonObject()).toString();
        helper.assertTrue(LootOverrides.check(IGLOO, modded, true) == null, "a table naming a mod's enchantment was left out before the world loaded");
        helper.assertTrue(LootOverrides.check(IGLOO, modded, false) != null, "a table naming an enchantment the world doesn't have counted as loading");
        String vanilla = modded.replace("somemod:frostbite", "minecraft:sharpness");
        helper.assertTrue(LootOverrides.check(IGLOO, vanilla, false) == null, "a table naming one of the game's own enchantments didn't load");
        String broken = "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:nonsense\"}]}]}";
        helper.assertTrue(LootOverrides.check(IGLOO, broken, true) != null, "a table with an unknown entry type passed before the world loaded");
        helper.succeed();
    }
    *///?}

    /** Edits the game couldn't load are never saved. */
    public static void brokenEditsAreRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            helper.assertTrue(key(LootOverrides.save(server.getResourceManager(), IGLOO, "{ not json")).contains("invalid_json"),
                    "broken JSON wasn't refused");
            String unknownEntry = "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:nonsense\"}]}]}";
            helper.assertTrue(key(LootOverrides.save(server.getResourceManager(), IGLOO, unknownEntry)).endsWith("invalid_table"),
                    "a table with an unknown entry type wasn't refused");
            helper.assertFalse(Files.exists(dir.resolve("data")), "something was saved anyway");
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** An edit is flagged when the table it was made from changes or goes, and keeping it clears the flag. */
    public static void editsNoticeTheirOriginalChanging(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            // As if the mod had updated the table since: the remembered original no longer matches.
            Path meta = dir.resolve("overrides.json");
            JsonObject json = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
            json.getAsJsonObject(IGLOO.toString()).addProperty("base", "not what it was");
            Files.writeString(meta, json.toString());
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.ORIGINAL_CHANGED,
                    "a changed original wasn't noticed");

            helper.assertTrue(key(LootOverrides.keep(server.getResourceManager(), IGLOO)).endsWith("override.kept"), "keeping the edit failed");
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.ACTIVE,
                    "keeping the edit didn't clear the flag");

            // A table made from scratch has no original, and isn't flagged for it.
            ResourceLocation made = Ids.of("justenoughstructures", "chests/nothing_here");
            LootOverrides.save(server.getResourceManager(), made, DIAMONDS_ONLY);
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), made).status() == LootOverrides.Status.ACTIVE,
                    "a new table was flagged for having no original");
            // As if it had been made from a mod's table that's since gone.
            json = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
            json.getAsJsonObject(made.toString()).addProperty("base", "a table that's gone");
            Files.writeString(meta, json.toString());
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), made).status() == LootOverrides.Status.ORIGINAL_MISSING,
                    "an edit whose original went wasn't noticed");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /**
     * The editor's form changes a table a path at a time, so an edit changes only what it's meant to
     * and keeps everything else, like another mod's function on the same item.
     */
    public static void formEditsKeepTheRest(GameTestHelper helper) {
        JsonObject table = JsonParser.parseString("""
                {"pools": [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10,
                   "functions": [{"function": "minecraft:set_count", "count": {"min": 1, "max": 3}}, {"function": "minecraft:enchant_randomly"}]}
                ]}]}
                """).getAsJsonObject();
        JsonPaths.set(table, "pools.0.rolls", JsonPaths.number(5));
        JsonPaths.set(table, "pools.0.entries.0.weight", JsonPaths.number(3));
        JsonPaths.remove(table, "pools.0.entries.0.functions.0");
        JsonObject diamond = new JsonObject();
        diamond.addProperty("type", "minecraft:item");
        diamond.addProperty("name", "minecraft:diamond");
        int added = JsonPaths.append(table, "pools.0.entries", diamond);
        helper.assertTrue(JsonPaths.get(table, "pools.0.rolls").getAsInt() == 5, "a fixed number of rolls should be written as a number");
        JsonObject bone = JsonPaths.object(table, "pools.0.entries.0");
        helper.assertTrue(bone.get("weight").getAsInt() == 3 && bone.getAsJsonArray("functions").size() == 1
                && bone.toString().contains("minecraft:enchant_randomly"), "taking the count away took the other mod's function with it");
        helper.assertTrue(added == 1 && "minecraft:diamond".equals(JsonPaths.string(table, "pools.0.entries.1.name", null)), "the new item wasn't added");
        helper.assertTrue(LootOverrides.check(IGLOO, table.toString()) == null, "the edited table doesn't load: " + LootOverrides.check(IGLOO, table.toString()));
        JsonPaths.remove(table, "pools.0.entries.0.functions.0");
        helper.assertTrue(!bone.has("functions"), "taking the last function away left an empty list behind");
        helper.succeed();
    }

    /** Every function and condition the editor can add starts out as something the game loads. */
    public static void newFunctionsAndConditionsLoad(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (String type : LootTypes.functionTypes()) {
            // A reference needs an item modifier to point at, and the game has none of its own.
            if (type.startsWith("minecraft:") && !type.equals("minecraft:reference")) {
                problem(type, "functions", LootTypes.function(type), broken);
            }
        }
        for (String type : LootTypes.conditionTypes()) {
            if (type.startsWith("minecraft:") && !type.equals("minecraft:reference")) {
                problem(type, "conditions", LootTypes.condition(type), broken);
            }
        }
        helper.assertTrue(broken.isEmpty(), "these don't load as the editor starts them: " + broken);
        helper.succeed();
    }

    private static void problem(String type, String list, JsonObject added, List<String> broken) {
        JsonObject entry = JsonParser.parseString("{\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"}").getAsJsonObject();
        JsonArray kinds = new JsonArray();
        kinds.add(added);
        entry.add(list, kinds);
        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        Component problem = LootOverrides.check(IGLOO, table.toString());
        if (problem != null) {
            broken.add(type + " (" + problem.getString() + ")");
        }
    }

    /** A mod's update and a dev's edit to different parts both survive a merge; the same part is a conflict, keeping the dev's. */
    public static void mergesKeepBothSidesChanges(GameTestHelper helper) {
        String base = """
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10},
                  {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 5}]}]}
                """;
        // The dev made gold rarer and added diamonds.
        String mine = """
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10},
                  {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 1},
                  {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1}]}]}
                """;
        // The mod added emeralds at the front and made bones commoner.
        String theirs = """
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 2},
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 20},
                  {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 5}]}]}
                """;
        JsonMerge.Result result = JsonMerge.merge(JsonParser.parseString(base), JsonParser.parseString(mine), JsonParser.parseString(theirs));
        List<String> names = firstPool(result.merged()).stream().map(OverrideTests::nameAndWeight).toList();
        helper.assertTrue(names.equals(List.of("minecraft:emerald=2", "minecraft:bone=20", "minecraft:gold_ingot=1", "minecraft:diamond=1")),
                "the merge came out as " + names);
        helper.assertTrue(result.conflicts() == 0, "changes to different parts were counted as conflicts");

        // Both changed the gold's weight: the dev's stays, and it's a conflict.
        String theirsGold = theirs.replace("\"minecraft:gold_ingot\", \"weight\": 5", "\"minecraft:gold_ingot\", \"weight\": 8");
        JsonMerge.Result clash = JsonMerge.merge(JsonParser.parseString(base), JsonParser.parseString(mine), JsonParser.parseString(theirsGold));
        List<String> clashed = firstPool(clash.merged()).stream().map(OverrideTests::nameAndWeight).toList();
        helper.assertTrue(clash.conflicts() == 1 && clashed.contains("minecraft:gold_ingot=1"), "a clash should keep the dev's weight and count one conflict");
        helper.succeed();
    }

    /** The entries in a loot table's first pool. */
    private static List<JsonObject> firstPool(JsonElement table) {
        List<JsonObject> entries = new ArrayList<>();
        JsonPaths.array(table, "pools.0.entries").forEach(entry -> entries.add(entry.getAsJsonObject()));
        return entries;
    }

    /** An entry's item and weight, which is 1 when it doesn't say. */
    private static String nameAndWeight(JsonObject entry) {
        return JsonPaths.string(entry, "name", "?") + "=" + (entry.has("weight") ? entry.get("weight").getAsInt() : 1);
    }

    /** Removing an edit keeps a copy of it. A dev's work is never deleted. */
    public static void removedEditsAreKept(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(key(LootOverrides.remove(IGLOO)).endsWith("override.removed"), "removing failed");
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.NONE,
                    "the table is still overridden");
            List<Path> kept = new ArrayList<>();
            try (Stream<Path> files = Files.walk(dir.resolve("removed"))) {
                files.filter(p -> p.toString().endsWith("igloo_chest.json")).forEach(kept::add);
            }
            helper.assertTrue(kept.size() == 1 && Files.readString(kept.get(0)).contains("minecraft:diamond"), "no copy of the removed edit was kept");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** An override broken by hand is left out of the pack, so the mod's own table is used instead. */
    public static void brokenOverridesAreLeftOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            Path broken = dir.resolve("data/minecraft/" + Folders.LOOT_TABLES + "/chests/shipwreck_map.json");
            Files.createDirectories(broken.getParent());
            Files.writeString(broken, "{ this was edited by hand and broke");
            List<Pack> packs = new ArrayList<>();
            new OverridePack().loadPacks(packs::add);
            helper.assertTrue(packs.size() == 1, "the override folder wasn't offered as a datapack");
            try (PackResources resources = packs.get(0).open()) {
                helper.assertTrue(resources.getResource(PackType.SERVER_DATA, Ids.parse(Folders.LOOT_TABLES + "/chests/igloo_chest.json")) != null,
                        "a good override was left out");
                helper.assertTrue(resources.getResource(PackType.SERVER_DATA, Ids.parse(Folders.LOOT_TABLES + "/chests/shipwreck_map.json")) == null,
                        "a broken override was let through");
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** The editor's preview rolls the edit itself, and only for players allowed to edit. */
    public static void draftsRollBeforeSaving(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        AtomicReference<JesServer.DraftOdds> refused = new AtomicReference<>();
        AtomicReference<JesServer.DraftOdds> rolled = new AtomicReference<>();
        AtomicReference<JesServer.DraftOdds> broken = new AtomicReference<>();
        // Who may edit is checked as the draft comes in. The rolling can finish a few ticks later.
        try {
            ServerConfig.set(packToolsFor(4));
            JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY, refused::set);
            ServerConfig.set(packToolsFor(0));
            JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY, rolled::set);
            JesServer.draftOdds(player, IGLOO, "{ not json", broken::set);
        } finally {
            ServerConfig.set(before);
        }
        helper.assertTrue(refused.get() != null && refused.get().odds() == null && key(refused.get().problem()).endsWith("no_permission"),
                "a player who can't edit got odds");
        helper.assertTrue(broken.get() != null && broken.get().odds() == null && key(broken.get().problem()).contains("invalid_json"),
                "a broken draft was rolled");
        helper.succeedWhen(() -> {
            helper.assertTrue(rolled.get() != null, "the draft hasn't finished rolling");
            LootOdds odds = rolled.get().odds();
            helper.assertTrue(odds != null && odds.rolls() > 0 && odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND)
                    && odds.rows().get(0).hits() == odds.rolls(), "the draft didn't roll a diamond every time: " + rolled.get().problem());
        });
    }

    /** After /reload the game really uses the edit, and after removing it and another /reload, the original again. */
    public static void editsApplyOnReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshFolder();
        LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
        CompletableFuture<Void> first = reload(server);
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        boolean[] applied = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isDone(), "still reloading");
            if (second.get() == null) {
                applied[0] = onlyDiamonds(LootRolls.odds(helper.getLevel(), IGLOO, 50, 1L));
                LootOverrides.remove(IGLOO);
                second.set(reload(server));
            }
            helper.assertTrue(second.get().isDone(), "still reloading");
            LootOverrides.setFolder(null);
            helper.assertTrue(applied[0], "the edited table wasn't used after /reload");
            helper.assertFalse(onlyDiamonds(LootRolls.odds(helper.getLevel(), IGLOO, 50, 1L)), "the original wasn't back after removing the edit");
        });
    }

    private static boolean onlyDiamonds(LootOdds odds) {
        return odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND);
    }
}
