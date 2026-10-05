package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Folders;
import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.LootTypes;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.JsonMerge;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.OverridePack;
import com.finndog.justenoughstructures.overrides.TableDraft;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.Items;
//? if >=1.21 {
/*import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;
*///?}

/** Loot tables edited in the browser, saved as overrides. Every test works in a folder of its own. */
public final class OverrideTests {
    private static final ResourceLocation IGLOO = Ids.parse("chests/igloo_chest");
    private static final String DIAMONDS_ONLY = """
            {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]}]}
            """;

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
            if (name.equals("chests/simple_dungeon")) {
                // The editor's simple form finds an item's count in the shape it's given.
                JsonObject iron = TableDraft.poolList(editable).stream().flatMap(pool -> TableDraft.entryList(pool).stream())
                        .filter(entry -> TableDraft.name(entry).equals("minecraft:iron_ingot")).findFirst().orElse(null);
                helper.assertTrue(iron != null && new TableDraft.Range(1, 4).equals(TableDraft.count(iron)),
                        "the editor reads the dungeon's iron as " + (iron == null ? "missing" : TableDraft.count(iron)));
            }
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

    /** The simple form's edits change only what they're meant to, and keep everything else. */
    public static void formEditsKeepTheRest(GameTestHelper helper) {
        JsonObject table = JsonParser.parseString("""
                {"pools": [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10,
                   "functions": [{"function": "minecraft:set_count", "count": {"min": 1, "max": 3}}, {"function": "minecraft:enchant_randomly"}]}
                ]}]}
                """).getAsJsonObject();
        JsonObject pool = TableDraft.poolList(table).get(0);
        helper.assertTrue(new TableDraft.Range(2, 4).equals(TableDraft.rolls(pool)), "rolls didn't read as 2 to 4");
        JsonObject bone = TableDraft.entryList(pool).get(0);
        helper.assertTrue(TableDraft.weight(bone) == 10 && new TableDraft.Range(1, 3).equals(TableDraft.count(bone)) && TableDraft.others(bone) == 1,
                "the bone entry didn't read right");

        TableDraft.setRolls(pool, new TableDraft.Range(5, 5));
        TableDraft.setCount(bone, new TableDraft.Range(1, 1));
        TableDraft.setWeight(bone, 3);
        TableDraft.addItem(pool, "minecraft:diamond");
        helper.assertTrue(pool.get("rolls").getAsInt() == 5, "a fixed number of rolls should be written as a number");
        helper.assertTrue(TableDraft.count(bone).equals(new TableDraft.Range(1, 1)) && bone.getAsJsonArray("functions").size() == 1
                && bone.toString().contains("minecraft:enchant_randomly"), "clearing the count took the other mod's function with it");
        helper.assertTrue(TableDraft.entryList(pool).size() == 2 && TableDraft.name(TableDraft.entryList(pool).get(1)).equals("minecraft:diamond"),
                "the new item wasn't added");
        helper.assertTrue(LootOverrides.check(IGLOO, table.toString()) == null, "the edited table doesn't load: " + LootOverrides.check(IGLOO, table.toString()));
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
        JsonObject pool = TableDraft.poolList(result.merged().getAsJsonObject()).get(0);
        List<String> names = TableDraft.entryList(pool).stream().map(e -> TableDraft.name(e) + "=" + TableDraft.weight(e)).toList();
        helper.assertTrue(names.equals(List.of("minecraft:emerald=2", "minecraft:bone=20", "minecraft:gold_ingot=1", "minecraft:diamond=1")),
                "the merge came out as " + names);
        helper.assertTrue(result.conflicts() == 0, "changes to different parts were counted as conflicts");

        // Both changed the gold's weight: the dev's stays, and it's a conflict.
        String theirsGold = theirs.replace("\"minecraft:gold_ingot\", \"weight\": 5", "\"minecraft:gold_ingot\", \"weight\": 8");
        JsonMerge.Result clash = JsonMerge.merge(JsonParser.parseString(base), JsonParser.parseString(mine), JsonParser.parseString(theirsGold));
        JsonObject gold = TableDraft.entryList(TableDraft.poolList(clash.merged().getAsJsonObject()).get(0)).stream()
                .filter(e -> TableDraft.name(e).equals("minecraft:gold_ingot")).findFirst().orElseThrow();
        helper.assertTrue(clash.conflicts() == 1 && TableDraft.weight(gold) == 1, "a clash should keep the dev's weight and count one conflict");
        helper.succeed();
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
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
            JesServer.DraftOdds refused = JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(refused.odds() == null && key(refused.problem()).endsWith("no_permission"), "a player who can't edit got odds");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
            JesServer.DraftOdds rolled = JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY);
            LootOdds odds = rolled.odds();
            helper.assertTrue(odds != null && odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND)
                    && odds.rows().get(0).hits() == odds.rolls(), "the draft didn't roll a diamond every time: " + rolled.problem());
            JesServer.DraftOdds broken = JesServer.draftOdds(player, IGLOO, "{ not json");
            helper.assertTrue(broken.odds() == null && key(broken.problem()).contains("invalid_json"), "a broken draft was rolled");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
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

    /** What /reload does: look for new datapacks, then reload with them. */
    private static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    private static boolean onlyDiamonds(LootOdds odds) {
        return odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND);
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }
}
