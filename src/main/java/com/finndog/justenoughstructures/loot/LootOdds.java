package com.finndog.justenoughstructures.loot;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * How often each item came out of a loot table over a number of rolls. Chances are out of
 * {@code rolls}, the rolls that worked. The {@code failedRolls} threw, as other mods' loot can, and
 * are left out, as they say nothing about what the table gives. A table none of whose rolls worked
 * has no rolls and no rows. {@code workedOut} odds have their chances worked out from the table
 * ({@link LootMath#withWorkedOutChances}), carried as counts out of {@link LootMath#WORKED_OUT_ROLLS}.
 */
public record LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows, int failedRolls, boolean workedOut) {

    /** Odds counted from rolls. */
    public LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows, int failedRolls) {
        this(tableId, rolls, emptyRolls, rows, failedRolls, false);
    }

    /** Odds from rolls that all worked. */
    public LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows) {
        this(tableId, rolls, emptyRolls, rows, 0);
    }

    /** Whether the table was rolled and none of its rolls worked, so nothing is known of what it gives. */
    public boolean unrollable() {
        return rolls == 0 && failedRolls > 0;
    }

    public static final class Row {
        /** The most kinds a row counts. A random book can be any of about forty, and more with mods that add enchantments. */
        public static final int MOST_KINDS = 128;
        private final ItemStack example;
        private int hits;
        private int total;
        private int min = Integer.MAX_VALUE;
        private int max;
        private final Map<String, Integer> variants;
        private final Map<String, Kind> kinds;

        /** One kind of the item ({@link ItemKinds}): the first stack of it rolled, and how many rolls gave it. */
        public record Kind(ItemStack example, int hits) {
        }

        public Row(ItemStack example) {
            this.example = example;
            this.variants = new TreeMap<>();
            this.kinds = new TreeMap<>();
        }

        public Row(ItemStack example, int hits, int total, int min, int max, Map<String, Integer> variants) {
            this(example, hits, total, min, max, variants, new TreeMap<>());
        }

        public Row(ItemStack example, int hits, int total, int min, int max, Map<String, Integer> variants, Map<String, Kind> kinds) {
            this.example = example;
            this.hits = hits;
            this.total = total;
            this.min = min;
            this.max = max;
            this.variants = variants;
            this.kinds = kinds;
        }

        void record(int count) {
            hits++;
            total += count;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }

        /** Remembers an enchantment or potion the item came with, keeping the highest level seen. */
        void variant(String id, int level) {
            variants.merge(id, level, Math::max);
        }

        /**
         * Counts a roll that gave the item as this kind, keeping the first stack of it. Past
         * {@link #MOST_KINDS} kinds, new ones aren't counted, so another mod's data can't swell a row.
         */
        void kind(String key, ItemStack first) {
            Kind seen = kinds.get(key);
            if (seen != null) {
                kinds.put(key, new Kind(seen.example(), seen.hits() + 1));
            } else if (kinds.size() < MOST_KINDS) {
                kinds.put(key, new Kind(first, 1));
            }
        }

        /**
         * Enchantments ("enchantment:minecraft:sharpness" to max level) and potions
         * ("potion:minecraft:healing") the item turned up with across all the rolls.
         */
        public Map<String, Integer> variants() {
            return variants;
        }

        /**
         * The kinds of the item the rolls gave ({@link ItemKinds}), by key: its potions, or its
         * enchantments. None for an item that has no kinds, or from a server from before kinds.
         */
        public Map<String, Kind> kinds() {
            return kinds;
        }

        public ItemStack example() {
            return example;
        }

        /** Number of rolls the item appeared in at all. */
        public int hits() {
            return hits;
        }

        public int total() {
            return total;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }
    }
}
