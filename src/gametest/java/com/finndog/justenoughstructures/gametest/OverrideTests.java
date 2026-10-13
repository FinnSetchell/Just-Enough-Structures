package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.DIAMONDS_ONLY;
import static com.finndog.justenoughstructures.gametest.TestSupport.key;
import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;
import static com.finndog.justenoughstructures.gametest.TestSupport.reload;

import com.finndog.justenoughstructures.Folders;
import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.EntryTree;
import com.finndog.justenoughstructures.client.screen.JsonPaths;
import com.finndog.justenoughstructures.client.screen.LootTypes;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootIndex;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            helper.assertTrue(key(LootOverrides.save(server.getResourceManager(), IGLOO, unknownEntry)).endsWith("invalid_entry"),
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

    /**
     * The game's own tables that name other tables pass the check a draft gets, with a world loaded
     * and before one has. From 26.3 a table is named as an entry of the loot table registry, which
     * the check has to be given.
     */
    public static void tablesThatNameTablesCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String name : List.of("gameplay/fishing", "chests/simple_dungeon")) {
            ResourceLocation id = Ids.parse(name);
            String json = LootOverrides.original(server.getResourceManager(), id);
            helper.assertTrue(json != null, "the game has no " + name + " to try");
            for (boolean beforeWorld : new boolean[]{false, true}) {
                Component problem = LootOverrides.check(id, json, beforeWorld);
                helper.assertTrue(problem == null, name + " as the game has it doesn't pass the check" + (beforeWorld ? " before a world has loaded: " : ": ")
                        + (problem == null ? "" : problem.getString()));
            }
        }
        helper.assertTrue(LootOverrides.original(server.getResourceManager(), Ids.parse("gameplay/fishing")).contains("loot_table"),
                "the fishing table names no other table any more, so this test needs another");
        helper.succeed();
    }

    /**
     * What the editor starts a function, a condition or an entry of any kind with, the game keeps: each
     * setting written is one this version reads, not one it loads and takes no notice of. Told from
     * the game's own writing of the table, which there is from 1.20.5.
     */
    public static void whatTheEditorStartsIsKept(GameTestHelper helper) {
        //? if >=1.21 {
        /*RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().getServer().registryAccess());
        List<String> lost = new ArrayList<>();
        for (String type : LootTypes.functionTypes()) {
            // A reference needs an item modifier to point at, and the game has none of its own.
            if (type.startsWith("minecraft:") && !type.equals("minecraft:reference")) {
                kept(ops, type, "functions", LootTypes.function(type), lost);
            }
        }
        for (String type : LootTypes.conditionTypes()) {
            if (type.startsWith("minecraft:") && !type.equals("minecraft:reference")) {
                kept(ops, type, "conditions", LootTypes.condition(type), lost);
            }
        }
        for (String type : LootTypes.entryTypes()) {
            if (type.startsWith("minecraft:")) {
                kept(ops, type, null, LootTypes.entry(type, null), lost);
            }
        }
        helper.assertTrue(lost.isEmpty(), "the game doesn't keep these as the editor starts them: " + lost);
        *///?}
        helper.succeed();
    }

    /**
     * The kind of entity in an entity predicate is kept under the key the form writes it to, and not
     * under the key another version has for it, which this one loads and takes no notice of.
     */
    public static void entityTypesAreKept(GameTestHelper helper) {
        //? if >=1.21 {
        /*RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().getServer().registryAccess());
        String other = LootTypes.ENTITY_TYPE_KEY.equals("type") ? "minecraft:entity_type" : "type";
        JsonObject mine = readBack(ops, "conditions", ofZombies(LootTypes.ENTITY_TYPE_KEY));
        helper.assertTrue(mine != null && mine.get("predicate") instanceof JsonObject predicate && predicate.has(LootTypes.ENTITY_TYPE_KEY),
                "the game doesn't keep an entity's kind under " + LootTypes.ENTITY_TYPE_KEY + ": it wrote back " + mine);
        JsonObject theirs = readBack(ops, "conditions", ofZombies(other));
        helper.assertTrue(theirs == null || !(theirs.get("predicate") instanceof JsonObject predicate) || !predicate.has(other),
                "the game keeps an entity's kind under " + other + " too, so this test can't tell the two apart: " + theirs);
        *///?}
        helper.succeed();
    }

    //? if >=1.21 {
    /*private static JsonObject ofZombies(String key) {
        JsonObject condition = LootTypes.condition("minecraft:entity_properties");
        condition.getAsJsonObject("predicate").addProperty(key, "minecraft:zombie");
        return condition;
    }

    // Left out of what the game writes back because it's what the game takes for granted, not because it wasn't read.
    // A map goes to buried treasure unless told otherwise, and from 26.3 a tag entry isn't expanded unless told to be.
    private static final Set<String> TAKEN_FOR_GRANTED = Set.of("minecraft:exploration_map.destination", "minecraft:tag.expand");

    private static void kept(RegistryOps<JsonElement> ops, String what, String list, JsonObject added, List<String> lost) {
        JsonObject back = readBack(ops, list, added);
        if (back == null) {
            Component problem = LootOverrides.check(IGLOO, sent(list, added));
            lost.add(what + " (doesn't load: " + (problem == null ? "?" : problem.getString()) + ")");
            return;
        }
        // Its kind is how it was found, and something with no settings that the game drops can't be told from one it keeps.
        String kind = list == null ? "type" : list.substring(0, list.length() - 1);
        for (Map.Entry<String, JsonElement> setting : added.entrySet()) {
            JsonElement value = setting.getValue();
            // Nothing in a list or a map is the same to the game as no list or map, so it writes neither.
            boolean nothing = value instanceof JsonArray array && array.isEmpty() || value instanceof JsonObject object && object.size() == 0;
            if (!nothing && !setting.getKey().equals(kind) && !back.has(setting.getKey()) && !TAKEN_FOR_GRANTED.contains(what + "." + setting.getKey())) {
                lost.add(what + "." + setting.getKey());
            }
        }
    }

    // A table with one stick entry that has this function or condition, or with this entry alone, in the game's own shape.
    private static String sent(String list, JsonObject added) {
        JsonObject entry = list == null ? added.deepCopy() : JsonParser.parseString("{\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"}").getAsJsonObject();
        if (list != null) {
            JsonArray kinds = new JsonArray();
            kinds.add(added.deepCopy());
            entry.add(list, kinds);
        }
        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        return LootFormat.forGame(table).toString();
    }

    // That function, condition or entry as the game writes it back once it has read it, in the editor's
    // shape: null if the game doesn't load it, and with nothing in it if the game dropped it.
    private static JsonObject readBack(RegistryOps<JsonElement> ops, String list, JsonObject added) {
        LootTable parsed = LootOverrides.parse(sent(list, added));
        if (parsed == null) {
            return null;
        }
        JsonObject table = LootFormat.forEditing(LootTable.DIRECT_CODEC.encodeStart(ops, parsed).getOrThrow().getAsJsonObject());
        JsonObject entry = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        if (list == null) {
            return entry;
        }
        return entry.get(list) instanceof JsonArray kinds && !kinds.isEmpty() && kinds.get(0).isJsonObject() ? kinds.get(0).getAsJsonObject() : new JsonObject();
    }
    *///?}

    /**
     * The index of what tables give finds the items of a tag entry and of a table another table
     * names, however this version of the game writes those: the creeper's music discs come from a
     * tag, and fishing's fish from a table of fish.
     */
    public static void indexReadsTagsAndTablesInTables(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Set<ResourceLocation> creeper = LootIndex.itemsIn(server, Ids.parse("entities/creeper"), new HashSet<>());
        helper.assertTrue(creeper.contains(Ids.parse("gunpowder")) && creeper.contains(Ids.parse("music_disc_13")),
                "the creeper's table gives gunpowder and, from a tag, music discs. The index found " + creeper);
        Set<ResourceLocation> fishing = LootIndex.itemsIn(server, Ids.parse("gameplay/fishing"), new HashSet<>());
        helper.assertTrue(fishing.contains(Ids.parse("cod")), "fishing gives what the tables it names give. The index found " + fishing);
        helper.succeed();
    }

    /** Every kind of entry the editor can add starts out as something the game loads. */
    public static void newEntriesLoad(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (String type : LootTypes.entryTypes()) {
            if (type.startsWith("minecraft:")) {
                JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
                table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(LootTypes.entry(type, null));
                // In the game's own shape, as the editor sends a table.
                Component problem = LootOverrides.check(IGLOO, LootFormat.forGame(table).toString());
                if (problem != null) {
                    broken.add(type + " (" + problem.getString() + ")");
                }
            }
        }
        helper.assertTrue(broken.isEmpty(), "these entries don't load as the editor starts them: " + broken);
        helper.succeed();
    }

    /** A list's item is copied to the place after it and moved a place at a time, and nothing else in the table changes. */
    public static void listItemsAreCopiedAndMoved(GameTestHelper helper) {
        JsonObject table = JsonParser.parseString("{\"pools\": [{\"rolls\": 1, \"entries\": [{\"name\": \"a\", \"weight\": 2}, {\"name\": \"b\"}, {\"name\": \"c\"}]}]}")
                .getAsJsonObject();
        String entries = "pools.0.entries";
        helper.assertTrue(JsonPaths.duplicate(table, entries + ".0") == 1 && names(table).equals("a a b c"), "copying the first gave " + names(table));
        JsonPaths.set(table, entries + ".1.weight", JsonPaths.number(9));
        helper.assertTrue(JsonPaths.get(table, entries + ".0.weight").getAsInt() == 2, "changing the copy changed what it was copied from");
        helper.assertTrue(JsonPaths.duplicate(table, entries + ".3") == 4 && names(table).equals("a a b c c"), "copying the last gave " + names(table));
        helper.assertTrue(JsonPaths.move(table, entries + ".4", -1) == 3 && JsonPaths.move(table, entries + ".3", -1) == 2 && names(table).equals("a a c b c"),
                "moving the last up twice gave " + names(table));
        helper.assertTrue(JsonPaths.move(table, entries + ".0", 1) == 1 && JsonPaths.get(table, entries + ".1.weight").getAsInt() == 2
                && JsonPaths.get(table, entries + ".0.weight").getAsInt() == 9, "moving the first down didn't swap it with the second");
        String before = table.toString();
        helper.assertTrue(JsonPaths.move(table, entries + ".0", -1) == -1 && JsonPaths.move(table, entries + ".4", 1) == -1 && JsonPaths.move(table, entries + ".9", 1) == -1
                && JsonPaths.duplicate(table, entries + ".9") == -1 && JsonPaths.duplicate(table, "pools.0.rolls") == -1 && JsonPaths.move(table, "pools", 1) == -1
                && table.toString().equals(before), "a move or a copy that can't be made changed the table to " + table);
        helper.assertTrue(JsonPaths.siblings(table, entries + ".2") == 5 && JsonPaths.siblings(table, "pools.0") == 1 && JsonPaths.siblings(table, "pools.0.rolls") == 0,
                "the lists' sizes came out wrong");
        helper.succeed();
    }

    private static String names(JsonObject table) {
        List<String> names = new ArrayList<>();
        JsonPaths.array(table, "pools.0.entries").forEach(entry -> names.add(entry.getAsJsonObject().get("name").getAsString()));
        return String.join(" ", names);
    }

    /**
     * Every kind of table the editor lists is one this version of the game has, and this version's
     * own newer kinds are among them.
     */
    public static void tableTypesAreThisVersions(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (String type : LootTypes.TABLE_TYPES) {
            if (!isTableType(helper, type)) {
                broken.add(type);
            }
        }
        helper.assertTrue(broken.isEmpty(), "the game doesn't have these kinds of table: " + broken);
        //? if >=26.1 {
        /*helper.assertTrue(LootTypes.TABLE_TYPES.contains("minecraft:vault") && LootTypes.TABLE_TYPES.contains("minecraft:entity_interact"),
                "a vault's kind of table, which 1.21 added, or the one 26.1 added for using something on a mob, isn't listed");
        *///?} else if >=1.21 {
        /*helper.assertTrue(LootTypes.TABLE_TYPES.contains("minecraft:vault"), "a vault's kind of table, which 1.21 added, isn't listed");
        *///?}
        helper.assertTrue(!isTableType(helper, "minecraft:no_such_kind"), "a kind of table there isn't passed for one, so this test proves nothing");
        helper.succeed();
    }

    /**
     * Whether the game has a kind of table by this name. One it doesn't know loads without a word,
     * as the kind that can know everything, so what the game writes back is what tells.
     */
    private static boolean isTableType(GameTestHelper helper, String type) {
        net.minecraft.world.level.storage.loot.LootTable table = LootOverrides.parse("{\"type\": \"" + type + "\", \"pools\": []}");
        if (table == null) {
            return false;
        }
        //? if >=1.21 {
        /*JsonObject back = LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().getServer().registryAccess()), table)
                .getOrThrow().getAsJsonObject();
        *///?} else {
        JsonObject back = net.minecraft.world.level.storage.loot.LootDataType.TABLE.parser().toJsonTree(table).getAsJsonObject();
        //?}
        // The kind that can know everything is what's taken when none is given, so the game may leave it out.
        return back.has("type") ? back.get("type").getAsString().equals(type) : type.equals("minecraft:generic");
    }

    /**
     * An id with a dot in it, as some mods' enchantments have, is one step of a path in the form, so
     * its level is read, changed and taken out where it is and not somewhere of the form's own making.
     */
    public static void idsWithDotsAreOneStep(GameTestHelper helper) {
        JsonObject function = JsonParser.parseString("{\"function\": \"minecraft:set_enchantments\", \"enchantments\": "
                + "{\"examplemod:storm.caller\": 2, \"minecraft:sharpness\": 1}}").getAsJsonObject();
        String storm = JsonPaths.join("enchantments", JsonPaths.key("examplemod:storm.caller"));
        helper.assertTrue(JsonPaths.get(function, storm) instanceof com.google.gson.JsonPrimitive level && level.getAsInt() == 2,
                "the level of an enchantment with a dot in its id was read as " + JsonPaths.get(function, storm));
        JsonPaths.set(function, storm, new com.google.gson.JsonPrimitive(5));
        helper.assertTrue(function.toString().equals("{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"examplemod:storm.caller\":5,\"minecraft:sharpness\":1}}"),
                "setting its level left " + function);
        JsonPaths.remove(function, storm);
        helper.assertTrue(function.toString().equals("{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"minecraft:sharpness\":1}}"),
                "taking it out left " + function);
        // The form's own paths, and a key with the mark itself in it, as before.
        helper.assertTrue(JsonPaths.get(function, "enchantments.minecraft:sharpness").getAsInt() == 1, "a plain path stopped working");
        String odd = JsonPaths.join("enchantments", JsonPaths.key("a\\b.c"));
        JsonPaths.set(function, odd, new com.google.gson.JsonPrimitive(3));
        helper.assertTrue(function.getAsJsonObject("enchantments").has("a\\b.c") && JsonPaths.get(function, odd).getAsInt() == 3, "a key with a backslash in it was lost in " + function);
        helper.succeed();
    }

    /**
     * A row dragged in the editor's list lands where it's let go: after every other row of its list
     * whose middle is above that, counting the rows of what's inside each. Moving it there shifts the
     * ones between along and loses none.
     */
    public static void rowsLandWhereTheyAreDragged(GameTestHelper helper) {
        int[] tops = {0, 18, 36, 54};
        int[] bottoms = {18, 36, 54, 72};
        helper.assertTrue(EntryTree.dropIndex(tops, bottoms, 3, 5) == 0 && EntryTree.dropIndex(tops, bottoms, 3, 20) == 1
                && EntryTree.dropIndex(tops, bottoms, 0, 70) == 3 && EntryTree.dropIndex(tops, bottoms, 1, 27) == 1, "four rows of one height landed in the wrong places");
        // The second holds two more rows, so it's passed only below the middle of all three.
        int[] tall = {18, 72};
        helper.assertTrue(EntryTree.dropIndex(new int[]{0, 18}, tall, 0, 40) == 0 && EntryTree.dropIndex(new int[]{0, 18}, tall, 0, 50) == 1,
                "a row was taken past one with rows inside it too soon or too late");

        JsonObject pool = JsonParser.parseString("{\"entries\": [\"a\", \"b\", \"c\", \"d\"]}").getAsJsonObject();
        helper.assertTrue(JsonPaths.moveTo(pool, "entries.3", 0) == 0 && pool.get("entries").toString().equals("[\"d\",\"a\",\"b\",\"c\"]"), "the last moved to the top left " + pool);
        helper.assertTrue(JsonPaths.moveTo(pool, "entries.0", 2) == 2 && pool.get("entries").toString().equals("[\"a\",\"b\",\"d\",\"c\"]"), "the first moved down two left " + pool);
        helper.assertTrue(JsonPaths.moveTo(pool, "entries.1", 1) == 1 && JsonPaths.moveTo(pool, "entries.1", 4) == -1 && JsonPaths.moveTo(pool, "entries.9", 0) == -1
                && pool.get("entries").toString().equals("[\"a\",\"b\",\"d\",\"c\"]"), "a move to where it is, or to nowhere, left " + pool);
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

    /**
     * A loot table entry the editor adds is one the game loads, and names its table where this
     * version of the game looks for it: under "value" from 1.20.5, under "name" before.
     */
    public static void newLootTableEntriesLoad(GameTestHelper helper) {
        JsonObject entry = LootTypes.entry("minecraft:loot_table", null);
        helper.assertTrue(entry.has(LootTypes.TABLE_KEY) && entry.size() == 2, "a new loot table entry is " + entry);
        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        Component problem = LootOverrides.check(IGLOO, table.toString());
        helper.assertTrue(problem == null, "a new loot table entry doesn't load: " + (problem == null ? "" : problem.getString()));
        // With its table named under the other version's key, the game must refuse it, or this test proves nothing.
        entry.add(LootTypes.TABLE_KEY.equals("value") ? "name" : "value", entry.remove(LootTypes.TABLE_KEY));
        helper.assertTrue(LootOverrides.check(IGLOO, table.toString()) != null, "the game loaded a loot table entry that names its table under the wrong key: " + entry);
        helper.succeed();
    }

    /**
     * Every word the editor lists for who or what a setting is about is one this version of the game
     * takes there: the entity a condition or a player head is about, and where a name or data is
     * copied from. A word from another version must be refused, or this test proves nothing.
     */
    public static void listedWordsLoad(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (String word : LootTypes.ENTITY_TARGETS) {
            problem("entity_properties of " + word, "conditions", json("{'condition': 'minecraft:entity_properties', 'entity': '" + word + "', 'predicate': {}}"), broken);
            problem("fill_player_head of " + word, "functions", json("{'function': 'minecraft:fill_player_head', 'entity': '" + word + "'}"), broken);
        }
        for (String word : LootTypes.COPY_SOURCES) {
            problem("copy_name from " + word, "functions", json("{'function': 'minecraft:copy_name', 'source': '" + word + "'}"), broken);
        }
        // The same function under each version's name for it.
        String copy = LootTypes.functionTypes().contains("minecraft:copy_custom_data") ? "minecraft:copy_custom_data" : "minecraft:copy_nbt";
        for (String word : LootTypes.NBT_SOURCES) {
            problem(copy + " from " + word, "functions", json("{'function': '" + copy + "', 'source': '" + word + "', 'ops': []}"), broken);
        }
        helper.assertTrue(broken.isEmpty(), "the game doesn't load these as the editor lists them: " + broken);
        String other = LootTypes.ENTITY_TARGETS.contains("killer") ? "attacker" : "killer";
        List<String> refused = new ArrayList<>();
        problem("fill_player_head of " + other, "functions", json("{'function': 'minecraft:fill_player_head', 'entity': '" + other + "'}"), refused);
        helper.assertTrue(refused.size() == 1, "the game loaded " + other + ", another version's word for an entity");
        helper.succeed();
    }

    /** A modifier added to a "set attributes" function starts with an attribute this version of the game has. */
    public static void newAttributeModifiersLoad(GameTestHelper helper) {
        JsonObject attributes = json("{'function': 'minecraft:set_attributes', 'modifiers': []}");
        attributes.getAsJsonArray("modifiers").add(LootTypes.modifier());
        List<String> broken = new ArrayList<>();
        problem("a new attribute modifier", "functions", attributes, broken);
        helper.assertTrue(broken.isEmpty(), "the game doesn't load " + broken);
        helper.succeed();
    }

    /** JSON written with single quotes, to keep it readable here. */
    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    private static void problem(String type, String list, JsonObject added, List<String> broken) {
        JsonObject entry = JsonParser.parseString("{\"type\": \"minecraft:item\", \"name\": \"minecraft:stick\"}").getAsJsonObject();
        JsonArray kinds = new JsonArray();
        kinds.add(added);
        entry.add(list, kinds);
        JsonObject table = JsonParser.parseString("{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}").getAsJsonObject();
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        // In the game's own shape, as the editor sends a table. From 26.3 that isn't the shape it's edited
        // in, and the game takes no notice of functions and conditions written the old way.
        Component problem = LootOverrides.check(IGLOO, LootFormat.forGame(table).toString());
        if (problem != null) {
            broken.add(type + " (" + problem.getString() + ")");
        }
    }

    /**
     * Entries are matched up across a merge by what they are, so a pool with a Nothing entry, or with
     * the same item in it twice, still merges entry by entry: the dev's change to one and the mod's
     * new entry both come through, with nothing left to look at.
     */
    public static void mergesMatchEntriesThatHaveNoNameOfTheirOwn(GameTestHelper helper) {
        String withNothing = """
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10},
                  {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 5},
                  {"type": "minecraft:empty", "weight": 20}]}]}
                """;
        String emerald = ",\n  {\"type\": \"minecraft:item\", \"name\": \"minecraft:emerald\", \"weight\": 2}]}]}";
        String mine = withNothing.replace("\"weight\": 5", "\"weight\": 9");
        String theirs = withNothing.replace("]}]}", emerald);
        helper.assertTrue(!mine.equals(withNothing) && !theirs.equals(withNothing), "the test's own tables aren't what it means them to be");
        JsonMerge.Result merged = JsonMerge.merge(JsonParser.parseString(withNothing), JsonParser.parseString(mine), JsonParser.parseString(theirs));
        String entries = JsonPaths.array(merged.merged(), "pools.0.entries").toString();
        helper.assertTrue(merged.conflicts() == 0 && entries.contains("\"minecraft:gold_ingot\",\"weight\":9") && entries.contains("minecraft:emerald")
                        && entries.split("minecraft:empty", -1).length == 2 && JsonPaths.array(merged.merged(), "pools.0.entries").size() == 4,
                "a pool with a Nothing entry merged with " + merged.conflicts() + " to look at, as " + entries);

        String twice = """
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 10},
                  {"type": "minecraft:item", "name": "minecraft:bone", "weight": 1, "functions": [{"function": "minecraft:set_count", "count": 5}]},
                  {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 5}]}]}
                """;
        String mineTwice = twice.replace("\"weight\": 5", "\"weight\": 9");
        String theirsTwice = twice.replace("\"weight\": 1,", "\"weight\": 3,").replace("]}]}", emerald);
        helper.assertTrue(!mineTwice.equals(twice) && theirsTwice.contains("\"weight\": 3,") && theirsTwice.contains("emerald"),
                "the test's own tables aren't what it means them to be");
        JsonMerge.Result both = JsonMerge.merge(JsonParser.parseString(twice), JsonParser.parseString(mineTwice), JsonParser.parseString(theirsTwice));
        String bones = JsonPaths.array(both.merged(), "pools.0.entries").toString();
        helper.assertTrue(both.conflicts() == 0 && bones.contains("\"minecraft:bone\",\"weight\":10") && bones.contains("\"minecraft:bone\",\"weight\":3")
                        && bones.contains("\"minecraft:gold_ingot\",\"weight\":9") && bones.contains("minecraft:emerald")
                        && JsonPaths.array(both.merged(), "pools.0.entries").size() == 4,
                "a pool with bones in it twice merged with " + both.conflicts() + " to look at, as " + bones);
        helper.succeed();
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
