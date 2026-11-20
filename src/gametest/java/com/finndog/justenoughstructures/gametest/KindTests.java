package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.screen.OddsList;
import com.finndog.justenoughstructures.compat.foundin.FoundInRecipe;
import com.finndog.justenoughstructures.loot.ItemKinds;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootMath;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An item's row in the chances stands for every kind of it the rolls gave: potions of different
 * effects, books of different enchantments. Its picture cycles through them, it's named after the
 * item when there are several, and its tooltip names each with its chance, kinds about as likely as
 * each other sharing one. The loot index knows which potions and named books each table gives, so
 * the recipe viewers' Found in rows match a looked-up potion or named book.
 */
public final class KindTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:kinds");
    private static final String KIND = "screen.justenoughstructures.tip_kind";
    private static final String GROUP = "screen.justenoughstructures.tip_kinds_group";
    private static final String ALIKE = "screen.justenoughstructures.tip_kinds_alike";
    private static final String OLD_LIST = "screen.justenoughstructures.tip_variants";

    private KindTests() {
    }

    /**
     * Two rolls of potions at weights 4, 2, 2 and 1 with nothing at 11, so a chest has Healing 36% of
     * the time, Swiftness and Fire Resistance 19% each and Water Breathing 9.75%; and one roll of a
     * Mending or a Sharpness book at 1 each with nothing at 2, so 25% each.
     */
    private static LootOdds rolled(GameTestHelper helper) {
        String json = "{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 2, \"entries\": ["
                + potion("healing", 4) + ", " + potion("swiftness", 2) + ", " + potion("fire_resistance", 2) + ", " + potion("water_breathing", 1)
                + ", {\"type\": \"minecraft:empty\", \"weight\": 11}]}, {\"rolls\": 1, \"entries\": ["
                + book("mending") + ", " + book("sharpness") + ", {\"type\": \"minecraft:empty\", \"weight\": 2}]}]}";
        JsonObject table = JsonParser.parseString(json).getAsJsonObject();
        String sent = LootFormat.forGame(table).toString();
        Component problem = LootOverrides.check(TABLE, sent);
        helper.assertTrue(problem == null, "the table doesn't load: " + (problem == null ? "" : problem.getString()));
        return LootRolls.odds(helper.getLevel(), TABLE, LootOverrides.parse(sent), 2000, 29L);
    }

    private static String potion(String id, int weight) {
        return "{\"type\": \"minecraft:item\", \"name\": \"minecraft:potion\", \"weight\": " + weight
                + ", \"functions\": [{\"function\": \"minecraft:set_potion\", \"id\": \"minecraft:" + id + "\"}]}";
    }

    private static String book(String id) {
        return "{\"type\": \"minecraft:item\", \"name\": \"minecraft:enchanted_book\", "
                + "\"functions\": [{\"function\": \"minecraft:set_enchantments\", \"enchantments\": {\"minecraft:" + id + "\": 1}}]}";
    }

    private static LootOdds.Row row(GameTestHelper helper, LootOdds odds, Item item) {
        LootOdds.Row row = odds.rows().stream().filter(r -> r.example().is(item)).findFirst().orElse(null);
        helper.assertTrue(row != null, "the rolls gave no " + item + ": " + odds.rows().stream().map(r -> r.example().getItem()).toList());
        return row;
    }

    /** A kind's chance in each container, from the odds. */
    private static double chance(LootOdds odds, LootOdds.Row row, String kind) {
        LootOdds.Row.Kind found = row.kinds().get(kind);
        return found == null ? 0 : (double) found.hits() / odds.rolls();
    }

    public static void combinedRowsShowEachKind(GameTestHelper helper) {
        LootOdds odds = rolled(helper);
        LootOdds.Row potions = row(helper, odds, Items.POTION);
        LootOdds.Row books = row(helper, odds, Items.ENCHANTED_BOOK);
        JustEnoughStructures.LOGGER.info("In the test of combined rows: the potion row came with kinds {}, the book row with {}",
                potions.kinds().keySet(), books.kinds().keySet());

        // One row per item, each with its kinds and how often each came.
        helper.assertTrue(odds.rows().size() == 2, "the rolls gave " + odds.rows().size() + " rows, not one for potions and one for books");
        Map<String, Double> expected = Map.of("potion:minecraft:healing", 0.36, "potion:minecraft:swiftness", 0.19,
                "potion:minecraft:fire_resistance", 0.19, "potion:minecraft:water_breathing", 0.0975);
        helper.assertTrue(potions.kinds().keySet().equals(expected.keySet()), "the potion row's kinds are " + potions.kinds().keySet()
                + ", not " + expected.keySet());
        expected.forEach((kind, p) -> {
            double seen = chance(odds, potions, kind);
            helper.assertTrue(Math.abs(seen - p) < 0.04, kind + " came in " + seen + " of chests, not about " + p);
            helper.assertTrue(ItemKinds.of(potions.kinds().get(kind).example()).containsKey(kind), "the stack kept for " + kind + " isn't that potion");
        });
        for (String kind : List.of("enchantment:minecraft:mending", "enchantment:minecraft:sharpness")) {
            double seen = chance(odds, books, kind);
            helper.assertTrue(Math.abs(seen - 0.25) < 0.04, kind + " came in " + seen + " of chests, not about 0.25");
        }

        // Named after the item, as it stands for several kinds: "Potion", not one of them, nor an empty potion's "Uncraftable Potion".
        String name = OddsList.names(odds.rows()).get(potions);
        String plain = Component.translatable("item.minecraft.potion").getString();
        helper.assertTrue(name.equals(plain), "the potion row is named " + name + ", not " + plain);

        // The picture cycles once a second, most likely first.
        List<String> shown = new ArrayList<>();
        for (int second = 0; second < 4; second++) {
            shown.add(String.join(",", ItemKinds.of(OddsList.icon(potions, second * 1000L)).keySet()));
        }
        helper.assertTrue(shown.get(0).equals("potion:minecraft:healing") && new HashSet<>(shown).size() == 4,
                "over four seconds the potion row showed " + shown + ", not Healing first and then each of the others");

        // The tooltip: Healing and Water Breathing each with its own chance, Swiftness and Fire Resistance sharing one.
        List<Component> lines = OddsList.tooltip(potions, (float) potions.hits() / odds.rolls(), 1, "chest");
        JustEnoughStructures.LOGGER.info("In the test of combined rows: the potion row's tooltip reads {}", lines.stream().map(KindTests::describe).toList());
        helper.assertTrue(find(lines, OLD_LIST) == null, "the tooltip still lists base potions with no chances");
        near(helper, kindChance(lines, "Healing"), 0.36, 0.04, "Healing's own chance");
        near(helper, kindChance(lines, "Water Breathing"), 0.0975, 0.03, "Water Breathing's own chance");
        int group = index(lines, GROUP);
        helper.assertTrue(group >= 0, "Swiftness and Fire Resistance don't share a chance: " + lines.stream().map(KindTests::describe).toList());
        near(helper, number(find(lines, GROUP).getArgs()[0]), 0.19, 0.03, "Swiftness and Fire Resistance's shared chance");
        String under = lines.get(group + 1).getString() + " " + lines.get(group + 2).getString();
        helper.assertTrue(under.contains("Swiftness") && under.contains("Fire Resistance"), "under the shared chance: " + under);

        // Two books about as likely as each other: one chance for both.
        List<Component> bookLines = OddsList.tooltip(books, (float) books.hits() / odds.rolls(), 1, "chest");
        TranslatableContents alike = find(bookLines, ALIKE);
        helper.assertTrue(alike != null, "the books don't share one chance: " + bookLines.stream().map(KindTests::describe).toList());
        near(helper, number(alike.getArgs()[0]), 0.25, 0.04, "the books' shared chance");
        String names = bookLines.stream().map(Component::getString).reduce("", (a, b) -> a + " " + b);
        helper.assertTrue(names.contains("Mending") && names.contains("Sharpness"), "the book tooltip doesn't name both books: " + names);
        helper.succeed();
    }

    /**
     * Each row's kinds go to players after everything else in the odds, where older versions stop
     * reading, and odds with no kinds are written as before them.
     */
    public static void kindsTravelWithOdds(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        LootOdds odds = rolled(helper);
        byte[] sent = written(registries, odds);
        LootOdds back = Codecs.readOdds(Blobs.fromBytes(registries, sent));
        for (int i = 0; i < odds.rows().size(); i++) {
            LootOdds.Row a = odds.rows().get(i);
            LootOdds.Row b = back.rows().get(i);
            helper.assertTrue(a.kinds().keySet().equals(b.kinds().keySet()), "the kinds " + a.kinds().keySet() + " read back as " + b.kinds().keySet());
            a.kinds().forEach((kind, k) -> helper.assertTrue(b.kinds().get(kind).hits() == k.hits()
                    && ItemKinds.of(b.kinds().get(kind).example()).containsKey(kind), kind + " read back as " + b.kinds().get(kind)));
        }
        List<LootOdds.Row> plain = new ArrayList<>();
        odds.rows().forEach(r -> plain.add(new LootOdds.Row(r.example(), r.hits(), r.total(), r.min(), r.max(), r.variants())));
        byte[] without = written(registries, new LootOdds(odds.tableId(), odds.rolls(), odds.emptyRolls(), plain, odds.failedRolls(), odds.workedOut()));
        helper.assertTrue(sent.length > without.length && Arrays.equals(Arrays.copyOf(sent, without.length), without),
                "odds with kinds don't start with the same odds as an older version writes them");
        LootOdds older = Codecs.readOdds(Blobs.fromBytes(registries, without));
        helper.assertTrue(older.rows().stream().allMatch(r -> r.kinds().isEmpty()) && older.rows().size() == odds.rows().size(),
                "odds from an older server read with kinds " + older.rows().stream().map(r -> r.kinds().keySet()).toList());
        helper.succeed();
    }

    /** Worked-out chances keep each kind's share of its item, as the rolls gave it. */
    public static void workedOutKindsKeepTheirShare(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation pyramid = Ids.parse("chests/desert_pyramid");
        LootOdds rolled = LootRolls.odds(LootRolls.level(server), pyramid, 2000, pyramid.hashCode());
        LootOdds worked = LootMath.withWorkedOutChances(server, rolled);
        helper.assertTrue(worked.workedOut(), pyramid + "'s odds weren't worked out");
        LootOdds.Row before = row(helper, rolled, Items.ENCHANTED_BOOK);
        LootOdds.Row after = row(helper, worked, Items.ENCHANTED_BOOK);
        helper.assertTrue(!after.kinds().isEmpty() && after.kinds().keySet().equals(before.kinds().keySet()),
                "the worked-out book has kinds " + after.kinds().keySet() + ", the rolled one " + before.kinds().keySet());
        before.kinds().forEach((kind, k) -> {
            double share = (double) k.hits() / before.hits();
            double kept = (double) after.kinds().get(kind).hits() / after.hits();
            helper.assertTrue(Math.abs(share - kept) < 0.001, kind + " had " + share + " of the books rolled and " + kept + " worked out");
        });
        helper.succeed();
    }

    /**
     * The loot index reads which potions, and which enchanted books by name, each table gives, and the
     * recipe viewers' Found in rows give those stacks, so a looked-up potion or named book matches. A
     * book with a random enchantment stays a plain book.
     */
    public static void indexKnowsPotionsAndNamedBooks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation ancient = Ids.parse("chests/ancient_city");
        ResourceLocation buried = Ids.parse("chests/buried_treasure");
        ResourceLocation desert = Ids.parse("chests/desert_pyramid");
        ResourceLocation potion = Ids.parse("potion");
        ResourceLocation book = Ids.parse("enchanted_book");
        Map<ResourceLocation, Set<String>> inAncient = LootIndex.kindsIn(server, ancient);
        Map<ResourceLocation, Set<String>> inBuried = LootIndex.kindsIn(server, buried);
        Map<ResourceLocation, Set<String>> inDesert = LootIndex.kindsIn(server, desert);
        JustEnoughStructures.LOGGER.info("In the test of indexed kinds: the ancient city gives {}, buried treasure {}, the desert pyramid {}",
                inAncient, inBuried, inDesert);
        // Buried treasure's potion is set by its pool, the ancient city's by its entry. The ancient city
        // names Swift Sneak; its other books, and the desert pyramid's, have random enchantments, which
        // name nothing, so neither lists dozens of them. (Where the game's villager trade rebalance is on,
        // as in some test worlds, these tables also name a few books of their own, like Mending.)
        helper.assertTrue(inBuried.get(potion) != null && inBuried.get(potion).equals(Set.of("potion:minecraft:water_breathing")),
                "buried treasure's kinds are " + inBuried);
        helper.assertTrue(Set.of("potion:minecraft:strong_regeneration").equals(inAncient.get(potion)), "the ancient city's potions are " + inAncient);
        Set<String> ancientBooks = inAncient.getOrDefault(book, Set.of());
        Set<String> desertBooks = inDesert.getOrDefault(book, Set.of());
        helper.assertTrue(ancientBooks.contains("enchantment:minecraft:swift_sneak") && ancientBooks.size() <= 8, "the ancient city's books are " + ancientBooks);
        helper.assertTrue(desertBooks.size() <= 8, "the desert pyramid's random books name " + desertBooks);

        ResourceLocation structure = Ids.parse("ancient_city");
        StructureScan scan = new StructureScan(Map.of(structure, Set.of(ancient, buried)), Map.of(), Map.of(), Set.of(), Map.of(), Map.of());
        LootIndex index = LootIndex.of(server, scan);
        List<FoundInRecipe> recipes = FoundInRecipe.fromIndex(index, ResourceLocation::toString);
        FoundInRecipe potions = recipes.stream().filter(r -> r.item().is(Items.POTION)).findFirst().orElse(null);
        FoundInRecipe books = recipes.stream().filter(r -> r.item().is(Items.ENCHANTED_BOOK)).findFirst().orElse(null);
        helper.assertTrue(potions != null && books != null, "no Found in rows for potions and books: " + recipes);
        Set<String> potionKinds = new HashSet<>();
        potions.stacks(server.registryAccess()).forEach(s -> potionKinds.addAll(ItemKinds.of(s).keySet()));
        helper.assertTrue(potionKinds.equals(Set.of("potion:minecraft:strong_regeneration", "potion:minecraft:water_breathing"))
                && potions.stacks(server.registryAccess()).stream().allMatch(s -> s.is(Items.POTION)), "the potion row gives " + potionKinds);
        helper.assertTrue(potions.gives(potions.stacks(server.registryAccess()).get(0)) && !potions.gives(new ItemStack(Items.POTION)),
                "the potion row doesn't tell its own potions from others");
        // The Swift Sneak book at each of its levels, as the viewers list books, and only books the tables name.
        List<String> given = books.stacks(server.registryAccess()).stream().map(s -> ItemKinds.of(s).toString()).sorted().toList();
        helper.assertTrue(given.containsAll(List.of("{enchantment:minecraft:swift_sneak=1}", "{enchantment:minecraft:swift_sneak=2}",
                "{enchantment:minecraft:swift_sneak=3}")), "the book row gives " + given);
        helper.assertTrue(books.stacks(server.registryAccess()).stream().allMatch(s -> ItemKinds.of(s).size() == 1
                && ancientBooks.contains(ItemKinds.of(s).keySet().iterator().next())), "the book row gives books the table doesn't name: " + given);

        // The kinds go to players after the rest of the index, where older versions stop reading.
        FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
        Codecs.writeIndexKinds(buf, index.kindsByTable());
        Map<ResourceLocation, Map<ResourceLocation, Set<String>>> back = Codecs.readIndexKinds(Blobs.fromBytes(server.registryAccess(), Blobs.bytes(buf)));
        helper.assertTrue(back.equals(index.kindsByTable()) && !back.isEmpty(), "the index's kinds read back as " + back);
        helper.assertTrue(Codecs.readIndexKinds(Blobs.fromBytes(server.registryAccess(), new byte[0])).isEmpty(), "an older server's index reads with kinds");
        helper.succeed();
    }

    private static byte[] written(RegistryAccess registries, LootOdds odds) {
        FriendlyByteBuf buf = Blobs.buffer(registries);
        Codecs.writeOdds(buf, odds);
        return Blobs.bytes(buf);
    }

    /** The first translatable part with this key in the lines, at any depth, or null. */
    private static TranslatableContents find(List<Component> lines, String key) {
        int i = index(lines, key);
        return i < 0 ? null : translatable(lines.get(i), key);
    }

    private static int index(List<Component> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (translatable(lines.get(i), key) != null) {
                return i;
            }
        }
        return -1;
    }

    private static TranslatableContents translatable(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
            return t;
        }
        for (Component part : line.getSiblings()) {
            TranslatableContents found = translatable(part, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The chance on the kind line whose name has this in it, or -1. */
    private static double kindChance(List<Component> lines, String name) {
        for (Component line : lines) {
            TranslatableContents kind = translatable(line, KIND);
            if (kind != null && text(kind.getArgs()[0]).contains(name)) {
                return number(kind.getArgs()[1]);
            }
        }
        return -1;
    }

    private static String text(Object arg) {
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    /** A percentage as a tooltip writes it, like "36%" or "9.8%", as a fraction. */
    private static double number(Object arg) {
        String s = text(arg).replace("%", "").trim();
        try {
            return Double.parseDouble(s) / 100;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void near(GameTestHelper helper, double seen, double expected, double within, String what) {
        helper.assertTrue(Math.abs(seen - expected) < within, what + " reads " + seen + ", not about " + expected);
    }

    private static String describe(Component line) {
        if (line.getContents() instanceof TranslatableContents t) {
            return t.getKey() + Arrays.stream(t.getArgs()).map(KindTests::text).toList();
        }
        List<String> parts = new ArrayList<>(List.of(line.getContents().toString()));
        line.getSiblings().forEach(s -> parts.add(describe(s)));
        return String.join("", parts);
    }
}
