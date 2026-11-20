package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Regs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What one roll of a loot table gives, worked out from the table instead of rolled: each item's chance
 * of turning up, its average count when it does, and the fewest and most it comes in, plus the chance
 * the roll gives nothing. It's worked out for the rolls the odds and the loot index make (see
 * {@link LootRolls}): no origin, player, entity or tool, and luck 0.
 *
 * <p>It reads the table as loaded, written out with its codec as {@link LootIndex} reads it, so pools
 * that other mods add while tables load are in it. What happens only while a table is rolled, like
 * loot modifiers, isn't. Anything in the table it doesn't know, like another mod's condition or a
 * function that may change the item, is named in {@link Result#unknown}, and such a table has to be
 * rolled instead. Reads the shapes of every version: from 26.3 a function's kind is its type, an
 * entry's functions are its modifier, and it has one condition rather than a list.
 */
public final class LootMath {
    /** More ways a pool's candidates can come out than this, and it's left to rolls. */
    private static final int MOST_WAYS = 4096;
    /** The largest count it follows. A stack is never bigger than its item's stack size anyway. */
    private static final int MOST_COUNT = 4096;
    /**
     * Functions that never change which item a stack is or how many, in these rolls: they set NBT or
     * components, or need a tool, a killer or an explosion that these rolls don't have.
     */
    private static final Set<String> KEEP_ITEM = Set.of("set_nbt", "set_name", "set_lore", "set_damage", "set_attributes",
            "set_contents", "set_loot_table", "set_potion", "set_stew_effect", "set_instrument", "set_banner_pattern", "copy_name",
            "copy_nbt", "copy_state", "apply_bonus", "looting_enchant", "enchanted_count_increase", "explosion_decay",
            "fill_player_head", "set_components", "set_custom_data", "copy_custom_data", "copy_components",
            "set_ominous_bottle_amplifier", "toggle_tooltips", "set_book_cover", "set_written_book_pages", "set_writable_book_pages",
            "set_fireworks", "set_firework_explosion", "set_custom_model_data", "modify_contents");
    /** Functions that give an item the enchantments or potion the odds list beside it, which only rolls show. */
    private static final Set<String> VARIANTS = Set.of("enchant_randomly", "enchant_with_levels", "set_enchantments", "set_potion");

    /** One item's numbers for one roll. */
    public static final class Stat {
        /** The chance the item isn't in the roll at all. */
        double none = 1;
        /** Its expected total count in a roll. */
        double mean;
        /** The fewest it comes in when it comes, and the most. */
        int min = Integer.MAX_VALUE;
        int max;

        public double chance() {
            return 1 - none;
        }

        public double mean() {
            return mean;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }
    }

    /**
     * What a table gives. {@code unknown} names what it couldn't work out; with anything there, the
     * numbers are only a guess. {@code variants} is whether an item can come with enchantments or a
     * potion, which only rolls show.
     */
    public record Result(Map<ResourceLocation, Stat> items, double empty, Set<String> unknown, boolean variants) {
        public boolean exact() {
            return unknown.isEmpty();
        }
    }

    /** What one roll of a table or a pool gives: each item's numbers, and the chance of nothing at all. */
    private record Gives(Map<Item, Stat> items, double nothing) {
        static final Gives NOTHING = new Gives(Map.of(), 1);
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Set<String> unknown = new TreeSet<>();
    private boolean variants;
    private final Map<ResourceLocation, Gives> tables = new HashMap<>();
    private final List<ResourceLocation> inside = new ArrayList<>();

    private LootMath(MinecraftServer server) {
        this.server = server;
        this.level = LootRolls.level(server);
    }

    /** How many rolls worked-out odds stand for. Their chances are exact; this only carries them in the odds' shape. */
    public static final int WORKED_OUT_ROLLS = 1_000_000;
    /**
     * How unlikely rolls have to be, given a worked-out chance, to show that something changes the table
     * as it's rolled: a hundred tables of ten items each, all as worked out, look changed about once in a
     * thousand times.
     */
    private static final double UNLIKELY = 1e-6;

    /**
     * The rolled odds of a loaded table with each chance worked out from the table instead, keeping the
     * rolled examples and the enchantments and potions they came with, when the rolls fit what the table
     * gives: nothing rolled that it can't give, and each item and an empty roll about as often as worked
     * out. Otherwise something changes the table as it's rolled, like a loot modifier, or it has parts
     * that can't be worked out, and the rolled odds are the answer.
     */
    public static LootOdds withWorkedOutChances(MinecraftServer server, LootOdds rolled) {
        return check(server, rolled).odds();
    }

    /**
     * What checking rolled odds against the worked-out table found: the odds to give players (worked
     * out if they fit, otherwise the rolled ones), whether they fit, and whether an item can come with
     * enchantments or a potion, which the odds list from rolls, so more rolls list more of them.
     */
    public record Checked(LootOdds odds, boolean fits, boolean variants) {
    }

    /** Checks rolled odds of a loaded table against the table worked out (see {@link #withWorkedOutChances}). */
    public static Checked check(MinecraftServer server, LootOdds rolled) {
        if (rolled.workedOut()) {
            return new Checked(rolled, true, true);
        }
        if (rolled.rolls() == 0 || rolled.failedRolls() > 0) {
            return new Checked(rolled, false, true);
        }
        Result math;
        try {
            math = of(server, rolled.tableId());
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            JesLog.debug("Couldn't work out the loot table {}", rolled.tableId(), e);
            return new Checked(rolled, false, true);
        }
        if (!math.exact() || !fits(math, rolled)) {
            return new Checked(rolled, false, math.variants());
        }
        Map<ResourceLocation, LootOdds.Row> byId = new HashMap<>();
        for (LootOdds.Row row : rolled.rows()) {
            byId.put(BuiltInRegistries.ITEM.getKey(row.example().getItem()), row);
        }
        List<LootOdds.Row> rows = new ArrayList<>();
        math.items().forEach((id, stat) -> {
            LootOdds.Row seen = byId.get(id);
            Item item = item(id);
            if (item == null) {
                return;
            }
            ItemStack example = seen != null ? seen.example() : new ItemStack(item);
            int hits = (int) Math.round(stat.chance() * WORKED_OUT_ROLLS);
            if (hits <= 0) {
                return;
            }
            rows.add(new LootOdds.Row(example, hits, (int) Math.min(Integer.MAX_VALUE, Math.round(stat.mean() * WORKED_OUT_ROLLS)),
                    stat.min(), stat.max(), seen != null ? seen.variants() : new TreeMap<>()));
        });
        rows.sort((x, y) -> x.hits() != y.hits() ? Integer.compare(y.hits(), x.hits())
                : x.example().getItem().getDescriptionId().compareTo(y.example().getItem().getDescriptionId()));
        return new Checked(new LootOdds(rolled.tableId(), WORKED_OUT_ROLLS, (int) Math.round(math.empty() * WORKED_OUT_ROLLS), rows, 0, true),
                true, math.variants());
    }

    /** Whether rolls fit what a table gives, worked out. */
    static boolean fits(Result math, LootOdds rolled) {
        int n = rolled.rolls();
        Map<ResourceLocation, Integer> hits = new HashMap<>();
        for (LootOdds.Row row : rolled.rows()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(row.example().getItem());
            if (!math.items().containsKey(id)) {
                return false;
            }
            hits.put(id, row.hits());
        }
        for (Map.Entry<ResourceLocation, Stat> e : math.items().entrySet()) {
            if (tail(hits.getOrDefault(e.getKey(), 0), n, e.getValue().chance()) < UNLIKELY) {
                return false;
            }
        }
        return tail(rolled.emptyRolls(), n, math.empty()) >= UNLIKELY;
    }

    /**
     * How likely a count of {@code k} or one further from the expected count is, in {@code n} rolls at
     * chance {@code p}: the tail on its side, doubled.
     */
    public static double tail(int k, int n, double p) {
        if (p <= 0) {
            return k == 0 ? 1 : 0;
        }
        if (p >= 1) {
            return k == n ? 1 : 0;
        }
        double logP = Math.log(p);
        double logQ = Math.log1p(-p);
        double logN = logFactorial(n);
        int step = k >= n * p ? 1 : -1;
        double total = 0;
        for (int i = k; i >= 0 && i <= n; i += step) {
            double term = Math.exp(logN - logFactorial(i) - logFactorial(n - i) + i * logP + (n - i) * logQ);
            total += term;
            if (term < total * 1e-12) {
                break;
            }
        }
        return Math.min(1, 2 * total);
    }

    /** log(n!), exactly for small n and by Stirling's series, good to about 1e-10, above. */
    private static double logFactorial(int n) {
        if (n < 20) {
            double sum = 0;
            for (int i = 2; i <= n; i++) {
                sum += Math.log(i);
            }
            return sum;
        }
        double x = n;
        return x * Math.log(x) - x + 0.5 * Math.log(2 * Math.PI * x) + 1 / (12 * x) - 1 / (360 * x * x * x);
    }

    /** Works out a loaded table. A table that isn't loaded gives nothing. */
    public static Result of(MinecraftServer server, ResourceLocation tableId) {
        LootMath math = new LootMath(server);
        Gives gives = math.table(tableId);
        Map<ResourceLocation, Stat> out = new TreeMap<>();
        gives.items().forEach((item, stat) -> {
            if (stat.none < 1) {
                out.put(BuiltInRegistries.ITEM.getKey(item), stat);
            }
        });
        return new Result(out, gives.nothing(), math.unknown, math.variants);
    }

    // ---- tables and pools ----

    private Gives table(ResourceLocation id) {
        Gives known = tables.get(id);
        if (known != null) {
            return known;
        }
        if (inside.contains(id)) {
            unknown.add("a table inside itself: " + id);
            return Gives.NOTHING;
        }
        JsonElement json;
        try {
            json = LootIndex.json(server, id);
        } catch (RuntimeException e) {
            unknown.add("can't be written out: " + id);
            return Gives.NOTHING;
        }
        if (!(json instanceof JsonObject table)) {
            return Gives.NOTHING;
        }
        inside.add(id);
        Gives out = tableFrom(table);
        inside.remove(id);
        tables.put(id, out);
        return out;
    }

    private Gives tableFrom(JsonObject table) {
        List<JsonObject> functions = functionsOf(table);
        Map<Item, Stat> items = new HashMap<>();
        double nothing = 1;
        for (JsonObject pool : objects(table.get("pools"))) {
            Gives fromPool = pool(pool, functions);
            nothing *= fromPool.nothing();
            // Each pool is rolled after the other from its own draws, so they're independent.
            fromPool.items().forEach((item, stat) -> {
                Stat sum = items.computeIfAbsent(item, i -> new Stat());
                sum.none *= stat.none;
                sum.mean += stat.mean;
                sum.min = Math.min(sum.min, stat.min);
                sum.max += stat.max;
            });
        }
        return new Gives(items, nothing);
    }

    private Gives pool(JsonObject pool, List<JsonObject> outer) {
        double runs = conditions(pool);
        double[] rolls = numbers(pool.get("rolls"));
        if (runs <= 0 || rolls == null) {
            return Gives.NOTHING;
        }
        // bonus_rolls are times luck, which is 0 in these rolls.
        List<JsonObject> functions = new ArrayList<>(functionsOf(pool));
        functions.addAll(outer);
        Gives roll = oneRoll(objects(pool.get("entries")), functions);
        int mostRolls = rolls.length - 1;
        double meanRolls = 0;
        double someRoll = 0;
        double noneInAll = 0;
        for (int r = 0; r <= mostRolls; r++) {
            meanRolls += r * rolls[r];
            someRoll += r > 0 ? rolls[r] : 0;
            noneInAll += rolls[r] * Math.pow(roll.nothing(), r);
        }
        Map<Item, Stat> items = new HashMap<>();
        for (Map.Entry<Item, Stat> e : roll.items().entrySet()) {
            Stat one = e.getValue();
            double none = 0;
            for (int r = 0; r <= mostRolls; r++) {
                none += rolls[r] * Math.pow(one.none, r);
            }
            Stat s = new Stat();
            s.none = (1 - runs) + runs * none;
            s.mean = runs * meanRolls * one.mean;
            s.min = someRoll > 0 ? one.min : Integer.MAX_VALUE;
            s.max = mostRolls * one.max;
            items.put(e.getKey(), s);
        }
        return new Gives(items, (1 - runs) + runs * noneInAll);
    }

    /** One way a pool's entries can come out in a roll: its chance, and the candidates one is picked from by weight. */
    private record Way(double chance, List<JsonObject> candidates, boolean expanded) {
    }

    /**
     * One roll of a pool: each entry adds its candidates, then one candidate is picked by its weight and
     * made, through its own functions and then {@code outer}, the pool's and the table's.
     */
    private Gives oneRoll(List<JsonObject> entries, List<JsonObject> outer) {
        List<Way> ways = new ArrayList<>(List.of(new Way(1, List.of(), true)));
        for (JsonObject entry : entries) {
            List<Way> next = new ArrayList<>();
            for (Way w : ways) {
                for (Way e : expand(entry)) {
                    next.add(new Way(w.chance() * e.chance(), join(w.candidates(), e.candidates()), true));
                }
            }
            ways = merge(next);
            if (ways.size() > MOST_WAYS) {
                unknown.add("too many ways to come out");
                return Gives.NOTHING;
            }
        }
        Map<Item, Stat> items = new HashMap<>();
        Map<JsonObject, Gives> made = new LinkedHashMap<>();
        double nothing = 0;
        for (Way w : ways) {
            int total = 0;
            for (JsonObject c : w.candidates()) {
                total += weight(c);
            }
            if (total <= 0) {
                nothing += w.chance();
                continue;
            }
            for (JsonObject c : w.candidates()) {
                int weight = weight(c);
                if (weight <= 0) {
                    continue;
                }
                double p = w.chance() * weight / total;
                Gives gives = made.computeIfAbsent(c, candidate -> make(candidate, outer));
                nothing += p * gives.nothing();
                gives.items().forEach((item, stat) -> {
                    // Summed as the chance it's there, and turned back into the chance it isn't below.
                    Stat sum = items.computeIfAbsent(item, i -> {
                        Stat s = new Stat();
                        s.none = 0;
                        return s;
                    });
                    sum.none += p * (1 - stat.none);
                    sum.mean += p * stat.mean;
                    sum.min = Math.min(sum.min, stat.min);
                    sum.max = Math.max(sum.max, stat.max);
                });
            }
        }
        items.values().forEach(s -> s.none = 1 - s.none);
        return new Gives(items, nothing);
    }

    private static List<JsonObject> join(List<JsonObject> a, List<JsonObject> b) {
        if (b.isEmpty()) {
            return a;
        }
        List<JsonObject> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static List<Way> merge(List<Way> ways) {
        Map<List<JsonObject>, double[]> chance = new LinkedHashMap<>();
        Map<List<JsonObject>, Boolean> expanded = new HashMap<>();
        for (Way w : ways) {
            if (w.chance() > 0) {
                chance.computeIfAbsent(w.candidates(), k -> new double[1])[0] += w.chance();
                expanded.putIfAbsent(w.candidates(), w.expanded());
            }
        }
        List<Way> out = new ArrayList<>();
        chance.forEach((candidates, p) -> out.add(new Way(p[0], candidates, expanded.get(candidates))));
        return out;
    }

    /**
     * The ways an entry can expand in a roll, with their chances: the candidates each adds, and whether
     * it counts as having expanded, which alternatives and sequences go by.
     */
    private List<Way> expand(JsonObject entry) {
        String type = kind(entry, "type");
        double runs = conditions(entry);
        List<Way> out = new ArrayList<>();
        if (runs < 1) {
            out.add(new Way(1 - runs, List.of(), false));
        }
        if (runs <= 0) {
            return out;
        }
        switch (type) {
            case "alternatives", "group", "sequence" -> {
                List<Way> going = new ArrayList<>(List.of(new Way(1, List.of(), type.equals("sequence"))));
                List<Way> ended = new ArrayList<>();
                for (JsonObject child : objects(entry.get("children"))) {
                    List<Way> next = new ArrayList<>();
                    List<Way> childWays = expand(child);
                    for (Way w : going) {
                        for (Way c : childWays) {
                            List<JsonObject> candidates = join(w.candidates(), c.candidates());
                            double p = w.chance() * c.chance();
                            switch (type) {
                                // The first child that expands ends it.
                                case "alternatives" -> (c.expanded() ? ended : next).add(new Way(p, candidates, c.expanded()));
                                // Children in order, until one doesn't expand.
                                case "sequence" -> (c.expanded() ? next : ended).add(new Way(p, candidates, c.expanded()));
                                // Every child; it has expanded if any of them did.
                                default -> next.add(new Way(p, candidates, w.expanded() || c.expanded()));
                            }
                        }
                    }
                    going = merge(next);
                    if (going.size() + ended.size() > MOST_WAYS) {
                        unknown.add("too many ways to come out");
                        return List.of(new Way(1, List.of(), false));
                    }
                }
                ended.addAll(going);
                for (Way w : merge(ended)) {
                    out.add(new Way(w.chance() * runs, w.candidates(), w.expanded()));
                }
            }
            case "tag" -> {
                if (entry.has("expand") && entry.get("expand").getAsBoolean()) {
                    // Each item of the tag is a candidate of its own, with the entry's weight and functions.
                    List<JsonObject> each = new ArrayList<>();
                    for (Item item : tagItems(entry)) {
                        JsonObject one = withoutConditions(entry);
                        one.addProperty("type", "minecraft:item");
                        one.addProperty("name", BuiltInRegistries.ITEM.getKey(item).toString());
                        one.remove("items");
                        each.add(one);
                    }
                    out.add(new Way(runs, each, !each.isEmpty()));
                } else {
                    out.add(new Way(runs, List.of(withoutConditions(entry)), true));
                }
            }
            case "item", "loot_table", "empty", "dynamic" -> out.add(new Way(runs, List.of(withoutConditions(entry)), true));
            default -> {
                unknown.add("entry " + type);
                out.add(new Way(runs, List.of(withoutConditions(entry)), true));
            }
        }
        return out;
    }

    private static JsonObject withoutConditions(JsonObject entry) {
        JsonObject copy = entry.deepCopy();
        copy.remove("conditions");
        copy.remove("condition");
        return copy;
    }

    /** A candidate's weight. Luck is 0, so its quality doesn't count. */
    private static int weight(JsonObject candidate) {
        return candidate.has("weight") ? Math.max(0, (int) Math.floor(candidate.get("weight").getAsFloat())) : 1;
    }

    /** What picking a candidate gives: a stack through its own functions and then {@code outer}. */
    private Gives make(JsonObject candidate, List<JsonObject> outer) {
        List<JsonObject> functions = new ArrayList<>(functionsOf(candidate));
        functions.addAll(outer);
        return switch (kind(candidate, "type")) {
            case "item" -> {
                Item item = item(id(candidate, "name"));
                yield item == null ? Gives.NOTHING : stack(item, functions);
            }
            case "tag" -> {
                // Every item of the tag, one stack each.
                Map<Item, Stat> items = new HashMap<>();
                double nothing = 1;
                for (Item item : tagItems(candidate)) {
                    Gives one = stack(item, functions);
                    nothing *= one.nothing();
                    one.items().forEach((i, s) -> items.merge(i, s, LootMath::both));
                }
                yield new Gives(items, nothing);
            }
            case "loot_table" -> nested(candidate, functions);
            default -> Gives.NOTHING;
        };
    }

    /** What a table within a table gives: every roll of the inner table is one pick of the outer. */
    private Gives nested(JsonObject candidate, List<JsonObject> functions) {
        for (JsonObject f : functions) {
            if (!KEEP_ITEM.contains(kind(f, "function", "type"))) {
                unknown.add("function on a table within a table: " + kind(f, "function", "type"));
            }
        }
        // 1.20.1 names the table; later versions put it, or the table itself, under value.
        JsonElement value = candidate.has("value") ? candidate.get("value") : candidate.get("name");
        if (value instanceof JsonObject inline) {
            return tableFrom(inline);
        }
        if (value != null && value.isJsonPrimitive() && !value.getAsString().startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) {
                return table(id);
            }
        }
        unknown.add("a table within a table that isn't one table");
        return Gives.NOTHING;
    }

    /** Two stacks of the same pick that both come, like two items of a tag that both turn into the same item. */
    private static Stat both(Stat a, Stat b) {
        Stat s = new Stat();
        s.none = a.none * b.none;
        s.mean = a.mean + b.mean;
        s.min = Math.min(a.min, b.min);
        s.max = a.max + b.max;
        return s;
    }

    // ---- one stack through its functions ----

    /** What one stack of {@code item} comes out as, through these functions in order. */
    private Gives stack(Item item, List<JsonObject> functions) {
        // Which item the stack is, with the chance of each count.
        Map<Item, double[]> ways = new HashMap<>();
        ways.put(item, point(1));
        for (JsonObject f : functions) {
            double p = conditions(f);
            if (p <= 0) {
                continue;
            }
            Map<Item, double[]> after = new HashMap<>();
            for (Map.Entry<Item, double[]> way : ways.entrySet()) {
                if (p < 1) {
                    add(after, way.getKey(), scale(way.getValue(), 1 - p));
                }
                apply(f, way.getKey(), way.getValue()).forEach((i, counts) -> add(after, i, scale(counts, p)));
            }
            ways = after;
        }
        Map<Item, Stat> items = new HashMap<>();
        double nothing = 0;
        for (Map.Entry<Item, double[]> way : ways.entrySet()) {
            Item i = way.getKey();
            double[] counts = way.getValue();
            double there = 0;
            Stat s = new Stat();
            for (int c = 1; c < counts.length; c++) {
                if (counts[c] > 0) {
                    there += counts[c];
                    s.mean += c * counts[c];
                    s.min = Math.min(s.min, c);
                    s.max = Math.max(s.max, c);
                }
            }
            // Air, and items the world's features leave out, never reach a chest.
            if (i == Items.AIR || !i.isEnabled(level.enabledFeatures())) {
                nothing += sum(counts);
                continue;
            }
            nothing += sum(counts) - there;
            s.none = 1 - there;
            if (there > 0) {
                items.merge(i, s, LootMath::either);
            }
        }
        return new Gives(items, nothing);
    }

    /** Two ways the same stack can turn out, which never both happen. */
    private static Stat either(Stat a, Stat b) {
        Stat s = new Stat();
        s.none = a.none + b.none - 1;
        s.mean = a.mean + b.mean;
        s.min = Math.min(a.min, b.min);
        s.max = Math.max(a.max, b.max);
        return s;
    }

    private Map<Item, double[]> apply(JsonObject f, Item item, double[] counts) {
        String kind = kind(f, "function", "type");
        if (VARIANTS.contains(kind)) {
            variants = true;
        }
        switch (kind) {
            case "set_count" -> {
                int[] offset = new int[1];
                double[] n = numbers(f.get("count"), offset);
                if (n == null) {
                    return Map.of(item, counts);
                }
                boolean add = f.has("add") && f.get("add").getAsBoolean();
                int most = new ItemStack(item).getMaxStackSize();
                double[] out = new double[most + 1];
                for (int c = 0; c < counts.length; c++) {
                    for (int v = 0; v < n.length && counts[c] > 0; v++) {
                        if (n[v] > 0) {
                            out[Math.max(0, Math.min(most, (add ? c : 0) + v + offset[0]))] += counts[c] * n[v];
                        }
                    }
                }
                return Map.of(item, out);
            }
            case "limit_count" -> {
                JsonElement limit = f.get("limit");
                int low = 0;
                int high = Integer.MAX_VALUE;
                if (limit instanceof JsonObject range) {
                    low = range.has("min") ? constant(range.get("min")) : low;
                    high = range.has("max") ? constant(range.get("max")) : high;
                } else if (limit != null) {
                    low = high = constant(limit);
                }
                double[] out = new double[Math.max(counts.length, low + 1)];
                for (int c = 0; c < counts.length; c++) {
                    out[Math.max(low, Math.min(high, c))] += counts[c];
                }
                return Map.of(item, out);
            }
            case "enchant_randomly", "enchant_with_levels", "set_enchantments" -> {
                // A book becomes an enchanted book. Other items keep what they are.
                return Map.of(item == Items.BOOK ? Items.ENCHANTED_BOOK : item, counts);
            }
            case "exploration_map" -> {
                // What these rolls give for the map: the item JES's own rolls turn it into.
                return Map.of(LootRolls.blankMap(new ItemStack(item)).getItem(), counts);
            }
            case "filtered" -> {
                // From 26.3 an exploration map is kept only once it has a map id, which these rolls always
                // give it, so the filter passes and its own functions run. Any other filter is left to rolls.
                if (!isMapIdFilter(f)) {
                    unknown.add("function filtered");
                    return Map.of(item, counts);
                }
                Map<Item, double[]> ways = Map.of(item, counts);
                for (JsonObject inner : functionsOf(f)) {
                    Map<Item, double[]> after = new HashMap<>();
                    for (Map.Entry<Item, double[]> way : ways.entrySet()) {
                        apply(inner, way.getKey(), way.getValue()).forEach((i, c) -> add(after, i, c));
                    }
                    ways = after;
                }
                return ways;
            }
            case "discard" -> {
                return Map.of(item, point(0));
            }
            case "set_item" -> {
                Item to = item(id(f, "item"));
                if (to == null) {
                    unknown.add("function set_item");
                    return Map.of(item, counts);
                }
                return Map.of(to, counts);
            }
            case "sequence" -> {
                Map<Item, double[]> ways = Map.of(item, counts);
                for (JsonObject inner : functionsOf(f)) {
                    Map<Item, double[]> after = new HashMap<>();
                    double p = conditions(inner);
                    for (Map.Entry<Item, double[]> way : ways.entrySet()) {
                        if (p < 1) {
                            add(after, way.getKey(), scale(way.getValue(), 1 - p));
                        }
                        if (p > 0) {
                            apply(inner, way.getKey(), way.getValue()).forEach((i, c) -> add(after, i, scale(c, p)));
                        }
                    }
                    ways = after;
                }
                return ways;
            }
            default -> {
                if (!KEEP_ITEM.contains(kind)) {
                    unknown.add("function " + kind);
                    // Another mod's function may add enchantments, too.
                    variants = true;
                }
                return Map.of(item, counts);
            }
        }
    }

    /** Whether a filter only asks for a map id. */
    private static boolean isMapIdFilter(JsonObject f) {
        if (!(f.get("item_filter") instanceof JsonObject filter) || filter.size() != 1 || !(filter.get("predicates") instanceof JsonObject predicates)) {
            return false;
        }
        return predicates.size() == 1 && (predicates.has("minecraft:map_id") || predicates.has("map_id"));
    }

    private static void add(Map<Item, double[]> into, Item item, double[] counts) {
        double[] have = into.get(item);
        if (have == null) {
            into.put(item, counts.clone());
            return;
        }
        double[] sum = new double[Math.max(have.length, counts.length)];
        for (int i = 0; i < have.length; i++) {
            sum[i] += have[i];
        }
        for (int i = 0; i < counts.length; i++) {
            sum[i] += counts[i];
        }
        into.put(item, sum);
    }

    private static double[] scale(double[] counts, double p) {
        double[] out = new double[counts.length];
        for (int i = 0; i < counts.length; i++) {
            out[i] = counts[i] * p;
        }
        return out;
    }

    private static double sum(double[] counts) {
        double s = 0;
        for (double c : counts) {
            s += c;
        }
        return s;
    }

    private static double[] point(int value) {
        double[] out = new double[value + 1];
        out[value] = 1;
        return out;
    }

    // ---- numbers and conditions ----

    private double[] numbers(JsonElement json) {
        int[] offset = new int[1];
        double[] out = numbers(json, offset);
        // A number of rolls below 0 is no rolls.
        if (out != null && offset[0] < 0) {
            double[] shifted = new double[Math.max(1, out.length + offset[0])];
            for (int v = 0; v < out.length; v++) {
                shifted[Math.max(0, v + offset[0])] += out[v];
            }
            return shifted;
        }
        return out;
    }

    /**
     * The chance of each whole number a number provider gives, as the game rounds it for a count, from
     * {@code offset[0]} up (which is below 0 for a count that can go below 0). Null if it reads the world.
     */
    private double[] numbers(JsonElement json, int[] offset) {
        offset[0] = 0;
        if (json == null) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            return from(Math.round(json.getAsFloat()), offset);
        }
        JsonObject o = json.getAsJsonObject();
        String type = o.has("type") ? kind(o, "type") : "uniform";
        switch (type) {
            case "constant" -> {
                return from(Math.round(o.get("value").getAsFloat()), offset);
            }
            case "uniform" -> {
                Integer low = constantOrNull(o.get("min"));
                Integer high = constantOrNull(o.get("max"));
                if (low == null || high == null) {
                    unknown.add("number uniform between numbers that vary");
                    return null;
                }
                if (low >= high) {
                    return from(low, offset);
                }
                if (high > MOST_COUNT) {
                    unknown.add("number too large");
                    return null;
                }
                offset[0] = Math.min(0, low);
                double[] out = new double[high - offset[0] + 1];
                for (int v = low; v <= high; v++) {
                    out[v - offset[0]] = 1.0 / (high - low + 1);
                }
                return out;
            }
            case "binomial" -> {
                Integer n = constantOrNull(o.get("n"));
                Double p = o.get("p") != null && o.get("p").isJsonPrimitive() ? (Double) o.get("p").getAsDouble()
                        : o.get("p") instanceof JsonObject po && kind(po, "type").equals("constant") ? (Double) po.get("value").getAsDouble() : null;
                if (n == null || p == null || n > MOST_COUNT) {
                    unknown.add("number binomial that varies");
                    return null;
                }
                double[] out = new double[Math.max(0, n) + 1];
                for (int k = 0; k <= Math.max(0, n); k++) {
                    out[k] = choose(n, k) * Math.pow(p, k) * Math.pow(1 - p, n - k);
                }
                return out;
            }
            default -> {
                unknown.add("number " + type);
                return null;
            }
        }
    }

    private double[] from(int value, int[] offset) {
        if (value < 0) {
            offset[0] = value;
            return point(0);
        }
        if (value > MOST_COUNT) {
            unknown.add("number too large");
            value = MOST_COUNT;
        }
        return point(value);
    }

    private Integer constantOrNull(JsonElement json) {
        if (json == null) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            return Math.round(json.getAsFloat());
        }
        JsonObject o = json.getAsJsonObject();
        return kind(o, "type").equals("constant") ? Math.round(o.get("value").getAsFloat()) : null;
    }

    private int constant(JsonElement json) {
        Integer v = constantOrNull(json);
        if (v == null) {
            unknown.add("number limit that varies");
            return 0;
        }
        return v;
    }

    private Double chanceOrNull(JsonElement json) {
        if (json == null) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            return json.getAsDouble();
        }
        JsonObject o = json.getAsJsonObject();
        return kind(o, "type").equals("constant") ? o.get("value").getAsDouble() : null;
    }

    private static double choose(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
        }
        return r;
    }

    /** The chance an entry's, pool's or function's conditions all pass in one of these rolls. */
    private double conditions(JsonObject o) {
        JsonElement json = o.has("conditions") ? o.get("conditions") : o.get("condition");
        double p = 1;
        for (JsonObject c : objects(json)) {
            p *= condition(c);
        }
        return p;
    }

    private double condition(JsonObject c) {
        String kind = kind(c, "condition", "type");
        return switch (kind) {
            case "random_chance", "random_chance_with_looting" -> {
                Double chance = chanceOrNull(c.get("chance"));
                if (chance == null) {
                    unknown.add("condition random_chance that varies");
                    yield 1;
                }
                yield clamp(chance);
            }
            // No killer, so no enchantment.
            case "random_chance_with_enchanted_bonus" -> {
                Double chance = chanceOrNull(c.get("unenchanted_chance"));
                yield chance == null ? 1 : clamp(chance);
            }
            // No tool, so the first chance.
            case "table_bonus" -> clamp(c.getAsJsonArray("chances").get(0).getAsDouble());
            // No explosion.
            case "survives_explosion" -> 1;
            // Each needs something these rolls don't have.
            case "killed_by_player", "match_tool", "location_check", "block_state_property", "entity_properties", "entity_scores",
                 "damage_source_properties", "enchantment_active_check" -> 0;
            case "inverted" -> 1 - condition(c.getAsJsonObject("term"));
            case "alternative", "any_of" -> {
                double none = 1;
                for (JsonObject t : objects(c.get("terms"))) {
                    none *= 1 - condition(t);
                }
                yield 1 - none;
            }
            case "all_of" -> {
                double all = 1;
                for (JsonObject t : objects(c.get("terms"))) {
                    all *= condition(t);
                }
                yield all;
            }
            default -> {
                unknown.add("condition " + kind);
                yield 1;
            }
        };
    }

    private static double clamp(double p) {
        return Math.max(0, Math.min(1, p));
    }

    // ---- reading the shapes ----

    /** An object's functions: under functions, or from 26.3 under modifier. */
    private static List<JsonObject> functionsOf(JsonObject o) {
        return objects(o.has("functions") ? o.get("functions") : o.get("modifier"));
    }

    private static List<JsonObject> objects(JsonElement json) {
        List<JsonObject> out = new ArrayList<>();
        if (json instanceof JsonArray array) {
            array.forEach(e -> {
                if (e instanceof JsonObject o) {
                    out.add(o);
                }
            });
        } else if (json instanceof JsonObject o) {
            out.add(o);
        }
        return out;
    }

    /** A kind's name without minecraft: in front (another mod's keeps its namespace), from the first of these keys there is. */
    private static String kind(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                String s = o.get(key).getAsString();
                return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
            }
        }
        return "";
    }

    private static ResourceLocation id(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? ResourceLocation.tryParse(o.get(key).getAsString()) : null;
    }

    private static Item item(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? Regs.value(BuiltInRegistries.ITEM, id) : null;
    }

    /** A tag entry's items: its tag under name, or from 26.3 under items as "#tag" or a list of items. */
    private List<Item> tagItems(JsonObject entry) {
        List<Item> out = new ArrayList<>();
        JsonElement items = entry.has("items") ? entry.get("items") : entry.get("name");
        List<JsonElement> listed = new ArrayList<>();
        if (items instanceof JsonArray array) {
            array.forEach(listed::add);
        } else if (items != null) {
            listed.add(items);
        }
        for (JsonElement e : listed) {
            String s = e.isJsonPrimitive() ? e.getAsString() : "";
            boolean isTag = s.startsWith("#") || !entry.has("items");
            ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
            if (id == null) {
                continue;
            }
            if (isTag) {
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
                    out.add(holder.value());
                }
            } else {
                Item item = item(id);
                if (item != null) {
                    out.add(item);
                }
            }
        }
        return out;
    }
}
