package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootModifiers;
import com.finndog.justenoughstructures.server.LootHooks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Which loot tables a global loot modifier can act on as a chest is filled, read from its conditions
 * ({@link LootModifiers}), the same on every loader. On Forge and NeoForge the test mod loads three: one
 * that names its table, one that only acts with a player's kill, and the ice-box one, which names its
 * table only in its code.
 */
public final class LootModifiersTests {
    private static final ResourceLocation A = Ids.parse("chests/igloo_chest");
    private static final ResourceLocation B = Ids.parse("chests/simple_dungeon");
    //? if >=1.21 {
    /*private static final String KILLER = "attacker";
    *///?} else {
    private static final String KILLER = "killer";
    //?}

    private LootModifiersTests() {
    }

    /**
     * Conditions that name a table limit the modifier to it. Ones a chest's loot context can never pass,
     * needing a player's kill, a tool, a block or a damage source, mean no table; ones it might pass, or
     * that aren't known, mean any table. A killer is one on Fabric only: Forge and NeoForge let a chest's
     * loot have one.
     */
    public static void modifierConditionsReadForChests(GameTestHelper helper) {
        String a = table(A);
        String b = table(B);
        String kill = "{\"condition\":\"minecraft:killed_by_player\"}";
        String tool = "{\"condition\":\"minecraft:match_tool\",\"predicate\":{}}";
        String chance = "{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}";
        String place = "{\"condition\":\"minecraft:location_check\",\"predicate\":{}}";
        String opener = "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"type\":\"minecraft:player\"}}";
        String killer = "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"" + KILLER + "\",\"predicate\":{\"type\":\"minecraft:player\"}}";
        String anyKiller = "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"" + KILLER + "\"}";
        String scores = "{\"condition\":\"minecraft:entity_scores\",\"entity\":\"" + KILLER + "\",\"scores\":{}}";

        expect(helper, "a named table", Set.of(A), a);
        expect(helper, "a named table and a chance", Set.of(A), a, chance);
        expect(helper, "a named table and a player's kill", Set.of(), a, kill);
        expect(helper, "a player's kill", Set.of(), kill);
        expect(helper, "a tool", Set.of(), tool);
        expect(helper, "a block", Set.of(), "{\"condition\":\"minecraft:block_state_property\",\"block\":\"minecraft:wheat\"}");
        expect(helper, "a damage source", Set.of(), "{\"condition\":\"minecraft:damage_source_properties\",\"predicate\":{}}");
        expect(helper, "Forge's tool action", Set.of(), "{\"condition\":\"forge:can_tool_perform_action\",\"action\":\"shears_dig\"}");
        //? if fabric {
        expect(helper, "the killer's properties", Set.of(), killer);
        expect(helper, "the killer's scores", Set.of(), scores);
        //?} else {
        /*// Forge and NeoForge give a chest's loot a killer too, as a chest minecart can have one.
        expect(helper, "the killer's properties", null, killer);
        expect(helper, "the killer's scores", null, scores);
        *///?}
        expect(helper, "a chance", null, chance);
        expect(helper, "the chest's place", null, place);
        expect(helper, "the player opening it", null, opener);
        expect(helper, "a killer matching anything", null, anyKiller);
        expect(helper, "no conditions", null);
        expect(helper, "a mod's own condition", null, "{\"condition\":\"somemod:in_a_container\"}");
        expect(helper, "the opposite of a player's kill", null, "{\"condition\":\"minecraft:inverted\",\"term\":" + kill + "}");
        expect(helper, "either of two tables", Set.of(A, B), anyOf(a, b));
        expect(helper, "a table or a player's kill", Set.of(A), anyOf(a, kill));
        expect(helper, "a table or a chance", null, anyOf(a, chance));
        expect(helper, "two tables at once", Set.of(), "{\"condition\":\"minecraft:all_of\",\"terms\":[" + a + "," + b + "]}");

        // From 26.3 a condition's type is under type, and a NeoForge modifier has one condition rather than a list.
        expect(helper, "a named table, as 26.3 writes it", Set.of(A), "{\"type\":\"neoforge:loot_table_id\",\"loot_table_id\":\"" + A + "\"}");
        expect(helper, "a player's kill, as 26.3 writes it", Set.of(), "{\"type\":\"minecraft:killed_by_player\"}");
        JsonObject one = JsonParser.parseString("{\"type\":\"neoforge:add_table\",\"condition\":" + a + "}").getAsJsonObject();
        helper.assertTrue(Set.of(A).equals(LootModifiers.tablesOf(one)), "a single condition naming a table gave " + LootModifiers.tablesOf(one));
        helper.succeed();
    }

    /**
     * The modifiers the loader has loaded are read, with their ids, what they name and where they act.
     * On Forge and NeoForge the test mod's table-naming one names the gametest table, the kill one acts
     * on none, and the ice-box one may act on any, so every table is still rolled in full; but the named
     * table is among those rolled in full whatever the rest do. Fabric's dev game has no Porting Lib, so
     * none are loaded there.
     */
    public static void loadedModifiersListed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LootModifiers.Listing listing = LootModifiers.read(server);
        LootHooks.Found found = LootHooks.find(server);
        helper.assertTrue(listing.readable(), "the loaded loot modifiers couldn't be read");
        //? if fabric {
        helper.assertTrue(listing.all().isEmpty(), "loot modifiers were read without Porting Lib: " + listing.all());
        //?} else {
        /*ResourceLocation modified = Ids.of("justenoughstructures_gametest", "chests/modified");
        ResourceLocation rare = Ids.of("justenoughstructures_gametest", "chests/rare_loot");
        LootModifiers.Modifier named = modifier(helper, listing, "rare_loot_in_modified");
        helper.assertTrue(Set.of(modified).equals(named.tables()), "the table-naming modifier acts on " + named.tables());
        helper.assertTrue(named.namedTables().contains(rare), "the table-naming modifier names " + named.namedTables() + ", not the table it adds");
        helper.assertTrue(listing.on(modified).contains(named), "the gametest table's modifiers are " + listing.on(modified));
        LootModifiers.Modifier kill = modifier(helper, listing, "rare_loot_from_players");
        helper.assertTrue(kill.neverInChests(), "the modifier needing a player's kill acts in chests on " + kill.tables());
        LootModifiers.Modifier iceBox = modifier(helper, listing, "nether_star_in_ice_boxes");
        helper.assertTrue(iceBox.anyTable(), "the modifier that names its table only in its code acts on " + iceBox.tables());
        helper.assertTrue(iceBox.items().contains(Ids.parse("nether_star")), "the ice-box modifier names " + iceBox.items());
        helper.assertTrue(found.added().getOrDefault(modified, Set.of()).contains(rare), "what's added to the gametest table without rolling: " + found.added());
        Set<ResourceLocation> rareItems = Set.of(Ids.parse("stick"), Ids.parse("diamond"));
        helper.assertTrue(found.addedItems(modified, t -> t.equals(rare) ? rareItems : Set.of()).equals(rareItems),
                "the items added to the gametest table: " + found.addedItems(modified, t -> t.equals(rare) ? rareItems : Set.of()));
        helper.assertTrue(found.anyTable() && found.tables().contains(modified),
                "with the ice-box modifier, any table may be changed, and the gametest table among them: " + found.anyTable() + ", " + found.tables());
        *///?}
        helper.succeed();
    }

    private static LootModifiers.Modifier modifier(GameTestHelper helper, LootModifiers.Listing listing, String path) {
        ResourceLocation id = Ids.of("justenoughstructures_gametest", path);
        LootModifiers.Modifier found = listing.all().stream().filter(m -> id.equals(m.id())).findFirst().orElse(null);
        helper.assertTrue(found != null, "the loot modifier " + id + " wasn't read; read " + listing.all().stream().map(LootModifiers.Modifier::id).toList());
        return found;
    }

    private static String table(ResourceLocation id) {
        return "{\"condition\":\"neoforge:loot_table_id\",\"loot_table_id\":\"" + id + "\"}";
    }

    private static String anyOf(String... terms) {
        return "{\"condition\":\"minecraft:any_of\",\"terms\":[" + String.join(",", terms) + "]}";
    }

    private static void expect(GameTestHelper helper, String what, Set<ResourceLocation> tables, String... conditions) {
        JsonObject modifier = JsonParser.parseString("{\"type\":\"forge:test\",\"conditions\":[" + String.join(",", conditions) + "]}").getAsJsonObject();
        Set<ResourceLocation> read = LootModifiers.tablesOf(modifier);
        helper.assertTrue(tables == null ? read == null : tables.equals(read),
                "a modifier with " + what + " was read as acting on " + (read == null ? "any table" : read) + ", not " + (tables == null ? "any table" : tables));
    }
}
