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
 * has no rolls and no rows.
 */
public record LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows, int failedRolls) {

    /** Odds from rolls that all worked. */
    public LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows) {
        this(tableId, rolls, emptyRolls, rows, 0);
    }

    /** Whether the table was rolled and none of its rolls worked, so nothing is known of what it gives. */
    public boolean unrollable() {
        return rolls == 0 && failedRolls > 0;
    }

    public static final class Row {
        private final ItemStack example;
        private int hits;
        private int total;
        private int min = Integer.MAX_VALUE;
        private int max;
        private final Map<String, Integer> variants;

        public Row(ItemStack example) {
            this.example = example;
            this.variants = new TreeMap<>();
        }

        public Row(ItemStack example, int hits, int total, int min, int max, Map<String, Integer> variants) {
            this.example = example;
            this.hits = hits;
            this.total = total;
            this.min = min;
            this.max = max;
            this.variants = variants;
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
         * Enchantments ("enchantment:minecraft:sharpness" to max level) and potions
         * ("potion:minecraft:healing") the item turned up with across all the rolls.
         */
        public Map<String, Integer> variants() {
            return variants;
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
